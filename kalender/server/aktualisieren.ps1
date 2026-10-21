<#
.SYNOPSIS
  Holt die aktuelle Kalender-Version aus dem GitHub-Repository und spielt sie ein.

.DESCRIPTION
  Lädt index.html, kalender-server.ps1, installieren.ps1 und aktualisieren.ps1 herunter,
  prüft sie (vollständig, PowerShell-Syntax fehlerfrei), sichert geänderte Dateien nach
  daten\backup\code-<Datum> und ersetzt sie. Hat sich kalender-server.ps1 geändert,
  wird der Server neu gestartet. Eine neue index.html laden die Browser von selbst nach.
  Die Kalenderdaten (Ordner "daten") werden nie angefasst.

  Manuell (als Administrator):
    powershell -ExecutionPolicy Bypass -File C:\Kalender\aktualisieren.ps1 -Branch main

.PARAMETER Repo
  GitHub-Repository (Besitzer/Name).
.PARAMETER Branch
  Branch, aus dem aktualisiert wird.
.PARAMETER Proxy
  Optionaler Proxy, z. B. http://proxy:8080 (falls der Server nur über einen Proxy ins Internet kommt).
.PARAMETER Quelle
  Basis-Adresse statt GitHub (nur für Tests).
#>
param(
  [string]$Repo = 'Shindo91/Battle',
  [string]$Branch = 'main',
  [string]$Proxy = '',
  [string]$Quelle = ''
)

$ErrorActionPreference = 'Stop'
$TaskName = 'Kalender-Server'
$Dir      = $PSScriptRoot
$DataDir  = Join-Path $Dir 'daten'
$LogFile  = Join-Path $DataDir 'update.log'
New-Item -ItemType Directory -Force -Path $DataDir | Out-Null

function Write-Log([string]$msg) {
  $line = '{0:yyyy-MM-dd HH:mm:ss}  {1}' -f (Get-Date), $msg
  Write-Host $line
  try { Add-Content -Path $LogFile -Value $line -Encoding UTF8 } catch { }
}

# Dateien im Repository → lokaler Name (alles in einem Ordner)
$Files = [ordered]@{
  'kalender/index.html'                = 'index.html'
  'kalender/server/kalender-server.ps1' = 'kalender-server.ps1'
  'kalender/server/installieren.ps1'    = 'installieren.ps1'
  'kalender/server/aktualisieren.ps1'   = 'aktualisieren.ps1'
}
# index.html kann auch eine Ebene höher liegen (Variante mit Unterordner "server")
$IndexPath = $(if (-not (Test-Path (Join-Path $Dir 'index.html')) -and (Test-Path (Join-Path (Split-Path $Dir -Parent) 'index.html'))) {
  Join-Path (Split-Path $Dir -Parent) 'index.html' } else { Join-Path $Dir 'index.html' })

$base = $(if ($Quelle) { $Quelle.TrimEnd('/') } else { "https://raw.githubusercontent.com/$Repo/$Branch" })
try { [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12 } catch { }

function Get-Sha256([byte[]]$bytes) {
  $sha = [Security.Cryptography.SHA256]::Create()
  try { return [BitConverter]::ToString($sha.ComputeHash($bytes)) } finally { $sha.Dispose() }
}

Write-Log "Prüfe auf Updates: $base"

# 1. Alles herunterladen und prüfen, bevor irgendetwas ersetzt wird
$downloaded = @{}
foreach ($src in $Files.Keys) {
  $url = "$base/$src"
  $params = @{ Uri = "$url`?t=$([DateTime]::UtcNow.Ticks)"; UseBasicParsing = $true; TimeoutSec = 60 }
  if ($Proxy) { $params.Proxy = $Proxy; $params.ProxyUseDefaultCredentials = $true }
  try {
    $resp = Invoke-WebRequest @params
  } catch {
    Write-Log "FEHLER: $src konnte nicht geladen werden ($($_.Exception.Message)). Nichts geändert."
    exit 1
  }
  $bytes = $resp.RawContentStream.ToArray()   # Rohdaten: BOM und Umlaute bleiben unverändert
  $text = [Text.Encoding]::UTF8.GetString($bytes)
  if ($bytes.Length -lt 200) { Write-Log "FEHLER: $src ist unvollständig. Nichts geändert."; exit 1 }
  if ($src -like '*.html' -and ($text -notmatch '(?i)<!doctype html' -or $text -notmatch '</html>')) {
    Write-Log "FEHLER: $src ist keine vollständige HTML-Seite. Nichts geändert."; exit 1
  }
  if ($src -like '*.ps1') {
    $tok = $null; $errs = $null
    [Management.Automation.Language.Parser]::ParseInput($text.TrimStart([char]0xFEFF), [ref]$tok, [ref]$errs) | Out-Null
    if ($errs.Count -gt 0) { Write-Log "FEHLER: $src enthält Syntaxfehler ($($errs[0].Message)). Nichts geändert."; exit 1 }
  }
  $downloaded[$src] = $bytes
}

# 2. Geänderte Dateien sichern und ersetzen
$changed = @()
$backup = Join-Path $DataDir ("backup\code-{0:yyyy-MM-dd_HHmm}" -f (Get-Date))
foreach ($src in $Files.Keys) {
  $target = $(if ($Files[$src] -eq 'index.html') { $IndexPath } else { Join-Path $Dir $Files[$src] })
  $new = $downloaded[$src]
  if ((Test-Path $target) -and ((Get-Sha256 ([IO.File]::ReadAllBytes($target))) -eq (Get-Sha256 $new))) { continue }
  if (Test-Path $target) {
    New-Item -ItemType Directory -Force -Path $backup | Out-Null
    Copy-Item $target (Join-Path $backup $Files[$src]) -Force
  }
  $tmp = "$target.neu"
  [IO.File]::WriteAllBytes($tmp, $new)
  Move-Item $tmp $target -Force
  $changed += $Files[$src]
}

if ($changed.Count -eq 0) { Write-Log 'Bereits aktuell.'; exit 0 }
Write-Log ("Aktualisiert: " + ($changed -join ', ') + "  (alte Versionen: $backup)")

# 3. Server neu starten, wenn sich das Server-Skript geändert hat
if ($changed -contains 'kalender-server.ps1') {
  if ((Get-Command Get-ScheduledTask -ErrorAction SilentlyContinue) -and (Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue)) {
    Stop-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2
    Start-ScheduledTask -TaskName $TaskName
    Write-Log 'Server neu gestartet.'
  } else {
    Write-Log "Hinweis: Aufgabe '$TaskName' nicht gefunden – Server bitte selbst neu starten."
  }
}
if ($changed -contains 'index.html') { Write-Log 'Neue Seite: geöffnete Browser und der Monitor laden sie automatisch neu.' }
