<#
.SYNOPSIS
  Holt eine neue Kalender-Version aus dem GitHub-Repository – nur auf Befehl, mit Rückfrage.

.DESCRIPTION
  1. Ermittelt die gewünschte Version (neuester Commit des Branches oder per -Version fest vorgegeben)
     und zeigt Commit, Autor, Datum und Beschreibung an.
  2. Lädt index.html, kalender-server.ps1, installieren.ps1 und aktualisieren.ps1 genau aus diesem
     Commit und prüft sie (vollständige Seite, PowerShell-Syntax fehlerfrei).
  3. Zeigt, welche Dateien sich ändern würden, und fragt nach (außer mit -Ja).
  4. Sichert die bisherigen Dateien nach daten\backup\code-<Datum>, ersetzt sie und startet den
     Server neu, falls sich das Server-Skript geändert hat. Eine neue Seite laden alle Browser
     und der Monitor selbst nach.
  Die Kalenderdaten (Ordner "daten") werden nie verändert.

  Als Administrator ausführen:
    powershell -ExecutionPolicy Bypass -File C:\Kalender\aktualisieren.ps1

.PARAMETER Version
  Bestimmter Commit (SHA, auch gekürzt) oder Tag statt des neuesten Stands im Branch.
.PARAMETER Branch
  Branch, dessen neuester Stand geholt wird.
.PARAMETER Repo
  GitHub-Repository (Besitzer/Name).
.PARAMETER Ja
  Ohne Rückfrage aktualisieren.
.PARAMETER Proxy
  Optionaler Proxy, z. B. http://proxy:8080.
.PARAMETER Ordner
  Statt aus dem Internet aus diesem Ordner aktualisieren (für Server ohne Internetzugang; die Dateien
  legt vom-pc-aktualisieren.ps1 dort ab – inkl. version.txt mit den Angaben zur Version).
.PARAMETER Ziel
  Installationsordner des Kalenders (Standard: Ordner dieses Skripts).
.PARAMETER Quelle
  Basis-Adresse statt GitHub (nur für Tests).
#>
param(
  [string]$Version = '',
  [string]$Branch = 'claude/nifty-curie-guwnkh',
  [string]$Repo = 'Shindo91/Battle',
  [switch]$Ja,
  [string]$Proxy = '',
  [string]$Ordner = '',
  [string]$Ziel = '',
  [string]$Quelle = ''
)

$ErrorActionPreference = 'Stop'
$TaskName = 'Kalender-Server'
$Dir      = $(if ($Ziel) { (Resolve-Path $Ziel).Path } else { $PSScriptRoot })
$DataDir  = Join-Path $Dir 'daten'
$LogFile  = Join-Path $DataDir 'update.log'
$VerFile  = Join-Path $DataDir 'installierte-version.txt'
New-Item -ItemType Directory -Force -Path $DataDir | Out-Null

function Write-Log([string]$msg) {
  $line = '{0:yyyy-MM-dd HH:mm:ss}  {1}' -f (Get-Date), $msg
  Write-Host $line
  try { Add-Content -Path $LogFile -Value $line -Encoding UTF8 } catch { }
}

# Dateien im Repository → lokaler Name (alles in einem Ordner)
$Files = [ordered]@{
  'kalender/index.html'                 = 'index.html'
  'kalender/server/kalender-server.ps1' = 'kalender-server.ps1'
  'kalender/server/installieren.ps1'    = 'installieren.ps1'
  'kalender/server/aktualisieren.ps1'   = 'aktualisieren.ps1'
}
# index.html kann auch eine Ebene höher liegen (Variante mit Unterordner "server")
$IndexPath = $(if (-not (Test-Path (Join-Path $Dir 'index.html')) -and (Test-Path (Join-Path (Split-Path $Dir -Parent) 'index.html'))) {
  Join-Path (Split-Path $Dir -Parent) 'index.html' } else { Join-Path $Dir 'index.html' })

try { [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12 } catch { }
$web = @{ UseBasicParsing = $true; TimeoutSec = 60 }
if ($Proxy) { $web.Proxy = $Proxy; $web.ProxyUseDefaultCredentials = $true }

function Get-Sha256([byte[]]$bytes) {
  $sha = [Security.Cryptography.SHA256]::Create()
  try { return [BitConverter]::ToString($sha.ComputeHash($bytes)) } finally { $sha.Dispose() }
}

# 1. Version bestimmen
if ($Ordner) {
  if (-not (Test-Path $Ordner)) { Write-Log "FEHLER: Ordner $Ordner nicht gefunden. Nichts geändert."; exit 1 }
  $Ordner = (Resolve-Path $Ordner).Path
  $info = @(); $vt = Join-Path $Ordner 'version.txt'
  if (Test-Path $vt) { $info = @(Get-Content $vt -Encoding UTF8) }
  $sha = $(if ($info.Count -gt 0 -and $info[0]) { $info[0].Trim() } else { 'unbekannt' })
  Write-Host ''
  Write-Host "Quelle:       $Ordner"
  Write-Host "Version:      $(if ($sha -match '^[0-9a-f]{40}$') { $sha.Substring(0, 7) } else { $sha })"
  if ($info.Count -gt 1) { Write-Host "Beschreibung: $($info[1])" }
  if ($info.Count -gt 2) { Write-Host "Autor/Datum:  $($info[2])" }
} elseif ($Quelle) {
  $base = $Quelle.TrimEnd('/')
  $sha = 'test'
  Write-Host "Quelle: $base"
} else {
  $ref = $(if ($Version) { $Version } else { $Branch })
  try {
    $c = Invoke-RestMethod @web -Uri "https://api.github.com/repos/$Repo/commits/$([uri]::EscapeDataString($ref))" -Headers @{ Accept = 'application/vnd.github+json' }
  } catch {
    Write-Log "FEHLER: Version '$ref' in $Repo nicht gefunden bzw. GitHub nicht erreichbar ($($_.Exception.Message)). Nichts geändert."
    exit 1
  }
  $sha = $c.sha
  $base = "https://raw.githubusercontent.com/$Repo/$sha"
  $msg = ([string]$c.commit.message -split "`n")[0]
  Write-Host ''
  Write-Host "Version:      $($sha.Substring(0, 7))  ($(if ($Version) { "fest vorgegeben: $Version" } else { "neuester Stand von '$Branch'" }))"
  Write-Host "Beschreibung: $msg"
  Write-Host "Autor/Datum:  $($c.commit.author.name), $(([datetime]$c.commit.author.date).ToLocalTime().ToString('dd.MM.yyyy HH:mm'))"
  Write-Host "Änderungen:   https://github.com/$Repo/commit/$sha"
}
if (Test-Path $VerFile) { Write-Host "Installiert:  $((Get-Content $VerFile -TotalCount 1))" }
Write-Host ''

# 2. Alles herunterladen und prüfen, bevor irgendetwas ersetzt wird
$downloaded = @{}
foreach ($src in $Files.Keys) {
  if ($Ordner) {
    $f = Join-Path $Ordner $Files[$src]
    if (-not (Test-Path $f)) {
      if ($src -notmatch 'index\.html$|kalender-server\.ps1$') { Write-Host "($($Files[$src]) liegt nicht im Ordner – bleibt unverändert)"; continue }
      Write-Log "FEHLER: $($Files[$src]) fehlt in $Ordner. Nichts geändert."; exit 1
    }
    $resp = [pscustomobject]@{ RawContentStream = New-Object IO.MemoryStream(, [IO.File]::ReadAllBytes($f)) }
  } else { try {
    $resp = Invoke-WebRequest @web -Uri "$base/$src"
  } catch {
    # Hilfsskripte gibt es in älteren Versionen evtl. noch nicht – dann bleiben die vorhandenen
    $notFound = $_.Exception.Message -match '404'
    if ($notFound -and $src -notmatch 'index\.html$|kalender-server\.ps1$') { Write-Host "($($Files[$src]) gibt es in dieser Version nicht – bleibt unverändert)"; continue }
    Write-Log "FEHLER: $src konnte nicht geladen werden ($($_.Exception.Message)). Nichts geändert."
    exit 1
  } }
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

# 3. Welche Dateien ändern sich?
$changes = @()
foreach ($src in $Files.Keys) {
  if (-not $downloaded.ContainsKey($src)) { continue }
  $target = $(if ($Files[$src] -eq 'index.html') { $IndexPath } else { Join-Path $Dir $Files[$src] })
  $isNew = -not (Test-Path $target)
  if (-not $isNew -and (Get-Sha256 ([IO.File]::ReadAllBytes($target))) -eq (Get-Sha256 $downloaded[$src])) { continue }
  $changes += [pscustomobject]@{ Src = $src; Name = $Files[$src]; Target = $target; New = $isNew }
}
if ($changes.Count -eq 0) {
  Write-Host 'Bereits aktuell – nichts zu tun.'
  if ($sha -ne 'test' -and $sha -ne 'unbekannt') { Set-Content -Path $VerFile -Value $sha -Encoding ASCII }
  exit 0
}
Write-Host 'Folgende Dateien würden ersetzt:'
$changes | ForEach-Object { Write-Host ("  - {0}{1}" -f $_.Name, $(if ($_.New) { ' (neu)' } else { '' })) }
if ($changes.Name -contains 'kalender-server.ps1') { Write-Host '  → Der Server wird danach neu gestartet (Benutzer müssen sich neu anmelden).' }
Write-Host ''

if (-not $Ja) {
  $answer = Read-Host 'Jetzt aktualisieren? (J/N)'
  if ($answer -notmatch '^(j|ja|y|yes)$') { Write-Host 'Abgebrochen – nichts geändert.'; exit 0 }
}

# 4. Sichern und ersetzen
$backup = Join-Path $DataDir ("backup\code-{0:yyyy-MM-dd_HHmmss}" -f (Get-Date))
foreach ($ch in $changes) {
  if (-not $ch.New) {
    New-Item -ItemType Directory -Force -Path $backup | Out-Null
    Copy-Item $ch.Target (Join-Path $backup $ch.Name) -Force
  }
  $tmp = "$($ch.Target).neu"
  [IO.File]::WriteAllBytes($tmp, $downloaded[$ch.Src])
  Move-Item $tmp $ch.Target -Force
}
if ($sha -ne 'test' -and $sha -ne 'unbekannt') { Set-Content -Path $VerFile -Value $sha -Encoding ASCII }
Write-Log ("Aktualisiert auf $(if ($sha -match '^[0-9a-f]{40}$') { $sha.Substring(0, 7) } else { $sha }): " + ($changes.Name -join ', ') + "  (alte Versionen: $backup)")

# 5. Server neu starten, wenn sich das Server-Skript geändert hat
if ($changes.Name -contains 'kalender-server.ps1') {
  if ((Get-Command Get-ScheduledTask -ErrorAction SilentlyContinue) -and (Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue)) {
    Stop-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2
    Start-ScheduledTask -TaskName $TaskName
    Write-Log 'Server neu gestartet.'
  } else {
    Write-Log "Hinweis: Aufgabe '$TaskName' nicht gefunden – Server bitte selbst neu starten."
  }
}
if ($changes.Name -contains 'index.html') { Write-Log 'Neue Seite: geöffnete Browser und der Monitor laden sie automatisch neu.' }
