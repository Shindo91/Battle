<#
.SYNOPSIS
  Richtet den Kalender-Server auf Windows Server (auch Core) ein: Autostart + Firewall.

.DESCRIPTION
  - Geplante Aufgabe "Kalender-Server": startet beim Systemstart als SYSTEM, ohne Zeitlimit,
    wird bei einem Absturz automatisch neu gestartet.
  - Firewall-Regel für den gewählten Port (eingehend, TCP).
  - Startet den Server sofort und zeigt beim ersten Start das Admin-Passwort an.

  Als Administrator ausführen, z. B.:
    powershell -ExecutionPolicy Bypass -File C:\Kalender\server\installieren.ps1 -Port 8080

.PARAMETER Port
  TCP-Port des Kalenders (Standard 8080).
.PARAMETER Entfernen
  Entfernt geplante Aufgabe und Firewall-Regel wieder (Daten bleiben erhalten).

  Updates aus dem Repository gibt es nur von Hand: aktualisieren.ps1
#>
param(
  [int]$Port = 8080,
  [switch]$Entfernen
)

$ErrorActionPreference = 'Stop'
$TaskName = 'Kalender-Server'
$UpdateTaskName = 'Kalender-Update'   # gab es in einer früheren Version; wird entfernt, falls vorhanden
$RuleName = 'Kalender-Server'
$Script   = Join-Path $PSScriptRoot 'kalender-server.ps1'
$DataDir  = Join-Path $PSScriptRoot 'daten'

$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) { Write-Error 'Bitte in einer PowerShell als Administrator ausführen.'; exit 1 }

if ($Entfernen) {
  if (Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue) {
    Stop-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
    Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false
  }
  if (Get-ScheduledTask -TaskName $UpdateTaskName -ErrorAction SilentlyContinue) { Unregister-ScheduledTask -TaskName $UpdateTaskName -Confirm:$false }
  Get-NetFirewallRule -DisplayName $RuleName -ErrorAction SilentlyContinue | Remove-NetFirewallRule
  Write-Host "Entfernt. Die Daten in $DataDir wurden nicht gelöscht."
  exit 0
}

if (-not (Test-Path $Script)) { Write-Error "kalender-server.ps1 nicht gefunden: $Script"; exit 1 }
# index.html neben den Skripten oder eine Ebene darüber (Unterordner "server")
$WebRoot = $(if (Test-Path (Join-Path $PSScriptRoot 'index.html')) { $PSScriptRoot } else { Split-Path $PSScriptRoot -Parent })
if (-not (Test-Path (Join-Path $WebRoot 'index.html'))) {
  Write-Error "index.html fehlt – bitte in denselben Ordner wie die Skripte legen ($PSScriptRoot)."; exit 1
}

# Aus dem Internet geladene Dateien entsperren (sonst fragt Windows bei jedem Start nach)
Get-ChildItem $WebRoot -Recurse -File | Unblock-File -ErrorAction SilentlyContinue

# Geplante Aufgabe (ersetzt eine vorhandene)
if (Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue) {
  Stop-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
  Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false
}
$action    = New-ScheduledTaskAction -Execute 'powershell.exe' `
               -Argument "-NoProfile -NonInteractive -ExecutionPolicy Bypass -File `"$Script`" -Port $Port" `
               -WorkingDirectory $PSScriptRoot
$trigger   = New-ScheduledTaskTrigger -AtStartup
$principal = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest
$settings  = New-ScheduledTaskSettingsSet -ExecutionTimeLimit ([TimeSpan]::Zero) -RestartCount 999 `
               -RestartInterval (New-TimeSpan -Minutes 1) -StartWhenAvailable -AllowStartIfOnBatteries `
               -DontStopIfGoingOnBatteries -MultipleInstances IgnoreNew
Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger $trigger -Principal $principal `
  -Settings $settings -Description "Kalender (http://$($env:COMPUTERNAME):$Port/)" | Out-Null

# Firewall
Get-NetFirewallRule -DisplayName $RuleName -ErrorAction SilentlyContinue | Remove-NetFirewallRule
New-NetFirewallRule -DisplayName $RuleName -Direction Inbound -Protocol TCP -LocalPort $Port -Action Allow -Profile Domain,Private | Out-Null

# Keine automatischen Updates: eine früher eingerichtete Update-Aufgabe entfernen
if (Get-ScheduledTask -TaskName $UpdateTaskName -ErrorAction SilentlyContinue) {
  Unregister-ScheduledTask -TaskName $UpdateTaskName -Confirm:$false
  Write-Host "Automatische Update-Aufgabe '$UpdateTaskName' entfernt – Updates nur noch mit aktualisieren.ps1."
}

Start-ScheduledTask -TaskName $TaskName
Write-Host "Kalender-Server eingerichtet und gestartet: http://$($env:COMPUTERNAME):$Port/"

# Beim ersten Start: Admin-Passwort anzeigen
$pwFile = Join-Path $DataDir 'ERSTES-ADMIN-PASSWORT.txt'
for ($i = 0; $i -lt 20 -and -not (Test-Path $pwFile); $i++) { Start-Sleep -Milliseconds 500 }
if (Test-Path $pwFile) {
  Write-Host ''
  Get-Content $pwFile | Write-Host
} else {
  Write-Host "Server läuft. Protokoll: $(Join-Path $DataDir 'server.log')"
}
