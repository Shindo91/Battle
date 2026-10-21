<#
.SYNOPSIS
  Aktualisiert den Kalender auf dem Server vom Arbeitsplatz-PC aus (für Server ohne Internetzugang).

.DESCRIPTION
  Läuft auf dem Arbeitsplatz-PC (Windows PowerShell 5.1):
  1. Holt die gewünschte Version aus dem GitHub-Repository und zeigt Commit, Beschreibung, Autor, Datum.
  2. Lädt index.html, kalender-server.ps1, installieren.ps1, aktualisieren.ps1 aus genau diesem Commit
     und prüft sie (vollständige Seite, PowerShell-Syntax fehlerfrei).
  3. Vergleicht mit den Dateien auf dem Server, zeigt die Änderungen und fragt nach (außer mit -Ja).
  4. Kopiert die Dateien nach \\SERVER\C$\Kalender\neu und startet dort per PowerShell-Remoting
     aktualisieren.ps1 (Sicherung, Austausch, ggf. Server-Neustart).
     Ist Remoting nicht möglich, wird der Befehl angezeigt, der auf dem Server einzugeben ist.

  Aufruf, z. B.:
    powershell -ExecutionPolicy Bypass -File .\vom-pc-aktualisieren.ps1 -Server GASWP003010

.PARAMETER Server
  Name des Kalender-Servers.
.PARAMETER Pfad
  Installationsordner auf dem Server (Standard C:\Kalender).
.PARAMETER Version
  Bestimmter Commit (SHA, auch gekürzt) oder Tag statt des neuesten Stands im Branch.
.PARAMETER Branch
  Branch, dessen neuester Stand geholt wird.
.PARAMETER Repo
  GitHub-Repository (Besitzer/Name).
.PARAMETER Credential
  Andere Anmeldedaten für den Server, z. B. -Credential (Get-Credential DOMÄNE\admin).
.PARAMETER Ja
  Ohne Rückfrage.
.PARAMETER ZielOrdner
  Statt \\Server\... direkt in diesen Ordner und lokal ausführen (z. B. auf dem Server selbst oder für Tests).
#>
param(
  [string]$Server = 'GASWP003010',
  [string]$Pfad = 'C:\Kalender',
  [string]$Version = '',
  [string]$Branch = 'claude/nifty-curie-guwnkh',
  [string]$Repo = 'Shindo91/Battle',
  [pscredential]$Credential,
  [switch]$Ja,
  [string]$ZielOrdner = ''
)

$ErrorActionPreference = 'Stop'
$Files = [ordered]@{
  'kalender/index.html'                 = 'index.html'
  'kalender/server/kalender-server.ps1' = 'kalender-server.ps1'
  'kalender/server/installieren.ps1'    = 'installieren.ps1'
  'kalender/server/aktualisieren.ps1'   = 'aktualisieren.ps1'
}

function Stop-WithError([string]$msg) { Write-Host "FEHLER: $msg" -ForegroundColor Red; Write-Host 'Auf dem Server wurde nichts geändert.'; exit 1 }
function Get-Sha256([byte[]]$bytes) {
  $sha = [Security.Cryptography.SHA256]::Create()
  try { return [BitConverter]::ToString($sha.ComputeHash($bytes)) } finally { $sha.Dispose() }
}

# Internet über den im Windows eingestellten Proxy, mit der eigenen Windows-Anmeldung
try { [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12 } catch { }
try { [Net.WebRequest]::DefaultWebProxy.Credentials = [Net.CredentialCache]::DefaultNetworkCredentials } catch { }
$web = @{ UseBasicParsing = $true; TimeoutSec = 60 }

# 1. Version bestimmen
$ref = $(if ($Version) { $Version } else { $Branch })
try {
  $c = Invoke-RestMethod @web -Uri "https://api.github.com/repos/$Repo/commits/$([uri]::EscapeDataString($ref))" -Headers @{ Accept = 'application/vnd.github+json' }
} catch {
  Stop-WithError "Version '$ref' in $Repo nicht gefunden bzw. GitHub nicht erreichbar ($($_.Exception.Message))."
}
$sha = $c.sha
$msg = ([string]$c.commit.message -split "`n")[0]
$when = "$($c.commit.author.name), $(([datetime]$c.commit.author.date).ToLocalTime().ToString('dd.MM.yyyy HH:mm'))"
Write-Host ''
Write-Host "Version:      $($sha.Substring(0, 7))  ($(if ($Version) { "fest vorgegeben: $Version" } else { "neuester Stand von '$Branch'" }))"
Write-Host "Beschreibung: $msg"
Write-Host "Autor/Datum:  $when"
Write-Host "Änderungen:   https://github.com/$Repo/commit/$sha"

# 2. Herunterladen und prüfen
$downloaded = [ordered]@{}
foreach ($src in $Files.Keys) {
  try {
    $resp = Invoke-WebRequest @web -Uri "https://raw.githubusercontent.com/$Repo/$sha/$src"
  } catch {
    if ($_.Exception.Message -match '404' -and $src -notmatch 'index\.html$|kalender-server\.ps1$') { continue }
    Stop-WithError "$src konnte nicht geladen werden ($($_.Exception.Message))."
  }
  $bytes = $resp.RawContentStream.ToArray()
  $text = [Text.Encoding]::UTF8.GetString($bytes)
  if ($bytes.Length -lt 200) { Stop-WithError "$src ist unvollständig." }
  if ($src -like '*.html' -and ($text -notmatch '(?i)<!doctype html' -or $text -notmatch '</html>')) { Stop-WithError "$src ist keine vollständige HTML-Seite." }
  if ($src -like '*.ps1') {
    $tok = $null; $errs = $null
    [Management.Automation.Language.Parser]::ParseInput($text.TrimStart([char]0xFEFF), [ref]$tok, [ref]$errs) | Out-Null
    if ($errs.Count -gt 0) { Stop-WithError "$src enthält Syntaxfehler ($($errs[0].Message))." }
  }
  $downloaded[$Files[$src]] = $bytes
}

# 3. Server-Ordner erreichen
$remote = -not $ZielOrdner
if ($remote) {
  $unc = "\\$Server\$($Pfad.Substring(0, 1))`$$($Pfad.Substring(2))"   # C:\Kalender → \\SERVER\C$\Kalender
  if ($Credential) {
    try { New-PSDrive -Name KalenderZiel -PSProvider FileSystem -Root $unc -Credential $Credential -Scope Script | Out-Null }
    catch { Stop-WithError "Kein Zugriff auf $unc ($($_.Exception.Message))." }
  }
  $root = $unc
} else {
  $root = $ZielOrdner
}
if (-not (Test-Path (Join-Path $root 'kalender-server.ps1'))) {
  Stop-WithError "In $root liegt kein Kalender (kalender-server.ps1 fehlt). Server/Pfad prüfen – ggf. -Credential angeben."
}

# 4. Vergleichen und nachfragen
$changes = @()
foreach ($name in $downloaded.Keys) {
  $target = Join-Path $root $name
  if ((Test-Path $target) -and (Get-Sha256 ([IO.File]::ReadAllBytes($target))) -eq (Get-Sha256 $downloaded[$name])) { continue }
  $changes += $name
}
$verFile = Join-Path $root 'daten\installierte-version.txt'
if (Test-Path $verFile) { Write-Host "Installiert:  $((Get-Content $verFile -TotalCount 1))" }
Write-Host ''
if ($changes.Count -eq 0) { Write-Host 'Der Server ist bereits auf diesem Stand – nichts zu tun.'; exit 0 }
Write-Host "Auf $(if ($remote) { $Server } else { $root }) würden ersetzt:"
$changes | ForEach-Object { Write-Host "  - $_" }
if ($changes -contains 'kalender-server.ps1') { Write-Host '  → Der Server wird danach neu gestartet (Benutzer müssen sich neu anmelden).' }
Write-Host ''
if (-not $Ja) {
  $answer = Read-Host 'Jetzt aktualisieren? (J/N)'
  if ($answer -notmatch '^(j|ja|y|yes)$') { Write-Host 'Abgebrochen – nichts geändert.'; exit 0 }
}

# 5. In den Ordner "neu" auf dem Server kopieren
$neu = Join-Path $root 'neu'
if (Test-Path $neu) { Remove-Item $neu -Recurse -Force }
New-Item -ItemType Directory -Path $neu | Out-Null
foreach ($name in $downloaded.Keys) { [IO.File]::WriteAllBytes((Join-Path $neu $name), $downloaded[$name]) }
[IO.File]::WriteAllLines((Join-Path $neu 'version.txt'), [string[]]@($sha, $msg, $when), (New-Object Text.UTF8Encoding $true))
Write-Host "Dateien nach $neu kopiert."

# 6. Auf dem Server einspielen (die neue aktualisieren.ps1 aus dem Ordner "neu")
$localCmd = "powershell -ExecutionPolicy Bypass -File `"$Pfad\neu\aktualisieren.ps1`" -Ziel `"$Pfad`" -Ordner `"$Pfad\neu`" -Ja"
if (-not $remote) {
  & (Get-Process -Id $PID).Path -NoProfile -ExecutionPolicy Bypass -File (Join-Path $neu 'aktualisieren.ps1') -Ziel $root -Ordner $neu -Ja
  exit $LASTEXITCODE
}
$ic = @{ ComputerName = $Server; ArgumentList = $Pfad
         ScriptBlock = { param($p) & powershell -NoProfile -ExecutionPolicy Bypass -File "$p\neu\aktualisieren.ps1" -Ziel $p -Ordner "$p\neu" -Ja } }
if ($Credential) { $ic.Credential = $Credential }
try {
  Invoke-Command @ic
} catch {
  Write-Host ''
  Write-Host "Fernzugriff auf $Server nicht möglich ($($_.Exception.Message))." -ForegroundColor Yellow
  Write-Host 'Die Dateien liegen bereit. Bitte auf dem Server in einer PowerShell als Administrator ausführen:'
  Write-Host ''
  Write-Host "  $localCmd" -ForegroundColor Cyan
}
