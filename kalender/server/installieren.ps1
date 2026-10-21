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
.PARAMETER AutoUpdate
  Richtet zusätzlich die geplante Aufgabe "Kalender-Update" ein: holt täglich die aktuelle Version
  aus dem GitHub-Repository (siehe aktualisieren.ps1).
.PARAMETER Branch
  Branch für die automatische Aktualisierung (Standard: main).
.PARAMETER UpdateUhrzeit
  Uhrzeit der täglichen Aktualisierung (Standard 03:00).
.PARAMETER Proxy
  Optionaler Proxy für die Aktualisierung, z. B. http://proxy:8080.
.PARAMETER Entfernen
  Entfernt geplante Aufgaben und Firewall-Regel wieder (Daten bleiben erhalten).
#>
param(
  [int]$Port = 8080,
  [switch]$AutoUpdate,
  [string]$Branch = 'main',
  [string]$UpdateUhrzeit = '03:00',
  [string]$Proxy = '',
  [switch]$Entfernen
)

$ErrorActionPreference = 'Stop'
$TaskName = 'Kalender-Server'
$UpdateTaskName = 'Kalender-Update'
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

# Automatische Aktualisierung aus GitHub (optional)
if (Get-ScheduledTask -TaskName $UpdateTaskName -ErrorAction SilentlyContinue) { Unregister-ScheduledTask -TaskName $UpdateTaskName -Confirm:$false }
if ($AutoUpdate) {
  $upd = Join-Path $PSScriptRoot 'aktualisieren.ps1'
  if (-not (Test-Path $upd)) { Write-Error "aktualisieren.ps1 nicht gefunden: $upd"; exit 1 }
  $uArgs = "-NoProfile -NonInteractive -ExecutionPolicy Bypass -File `"$upd`" -Branch `"$Branch`""
  if ($Proxy) { $uArgs += " -Proxy `"$Proxy`"" }
  $uAction   = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument $uArgs -WorkingDirectory $PSScriptRoot
  $uTrigger  = New-ScheduledTaskTrigger -Daily -At $UpdateUhrzeit
  $uSettings = New-ScheduledTaskSettingsSet -ExecutionTimeLimit (New-TimeSpan -Minutes 15) -StartWhenAvailable -MultipleInstances IgnoreNew
  Register-ScheduledTask -TaskName $UpdateTaskName -Action $uAction -Trigger $uTrigger -Principal $principal `
    -Settings $uSettings -Description "Kalender aus GitHub aktualisieren (Branch $Branch)" | Out-Null
  Write-Host "Automatische Aktualisierung: täglich um $UpdateUhrzeit aus Branch '$Branch'."
  Write-Host "Sofort aktualisieren: Start-ScheduledTask $UpdateTaskName   (Protokoll: $(Join-Path $DataDir 'update.log'))"
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
