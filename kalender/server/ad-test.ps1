<#
.SYNOPSIS
  Prüft Schritt für Schritt, ob der Kalender-Server das Active Directory erreicht.

.DESCRIPTION
  Auf dem Kalender-Server in einer PowerShell als Administrator ausführen:

    powershell -ExecutionPolicy Bypass -File C:\Kalender\ad-test.ps1 -Benutzer mmuster
    powershell -ExecutionPolicy Bypass -File C:\Kalender\ad-test.ps1 -Benutzer mmuster -AlsSystem
    powershell -ExecutionPolicy Bypass -File C:\Kalender\ad-test.ps1 -Benutzer mmuster -MitPasswort

  -AlsSystem führt dieselben Prüfungen als Konto SYSTEM aus – so, wie der Kalender-Dienst läuft.
  -MitPasswort prüft zusätzlich die Anmeldung (Passwort wird verdeckt abgefragt, nicht gespeichert).
  -AdServer dc01.firma.local nutzt einen bestimmten Domänencontroller statt der automatischen Suche.
  Ändert nichts am System.

.PARAMETER Benutzer
  Windows-Benutzername, der geprüft werden soll.
.PARAMETER AdServer
  Bestimmter Domänencontroller (Name oder IP); leer = automatisch über DNS.
.PARAMETER AdNutzerGruppe
  Gruppe für den Zugang (Standard L_Kalender_Nutzer).
.PARAMETER AdAdminGruppe
  Gruppe der Administratoren (Standard L_Kalender_Admins).
#>
param(
  [string]$Benutzer = '',
  [string]$AdServer = '',
  [string]$AdNutzerGruppe = 'L_Kalender_Nutzer',
  [string]$AdAdminGruppe = 'L_Kalender_Admins',
  [switch]$MitPasswort,
  [switch]$AlsSystem,
  [string]$Ausgabe = ''   # intern: Ergebnisdatei beim Lauf als SYSTEM
)

$ErrorActionPreference = 'Stop'
$lines = New-Object System.Collections.ArrayList
function Out-Line([string]$text, [string]$color = 'Gray') {
  [void]$lines.Add($text)
  if (-not $Ausgabe) { Write-Host $text -ForegroundColor $color }
}
function Get-ErrorText($err) {
  $e = $(if ($err -is [System.Management.Automation.ErrorRecord]) { $err.Exception } else { $err })
  $parts = @()
  while ($e) { $parts += ("[{0}] {1} (0x{2:X8})" -f $e.GetType().FullName, ([string]$e.Message).Trim(), $e.HResult); $e = $e.InnerException }
  return ($parts -join ' --> ')
}
function Step([string]$title, [scriptblock]$check) {
  try {
    $r = & $check
    Out-Line ("OK      {0}{1}" -f $title, $(if ($r) { ": $r" } else { '' })) 'Green'
    return $true
  } catch {
    Out-Line ("FEHLER  {0}: {1}" -f $title, (Get-ErrorText $_)) 'Red'
    return $false
  }
}

# ---- Als SYSTEM: sich selbst als einmalige geplante Aufgabe starten und Ergebnis anzeigen
if ($AlsSystem) {
  $out = Join-Path $env:TEMP "kalender-ad-test-$([guid]::NewGuid().ToString('N')).txt"
  $out = Join-Path $env:SystemRoot "Temp\$(Split-Path $out -Leaf)"
  $args2 = "-NoProfile -NonInteractive -ExecutionPolicy Bypass -File `"$PSCommandPath`" -Benutzer `"$Benutzer`" -AdServer `"$AdServer`" " +
           "-AdNutzerGruppe `"$AdNutzerGruppe`" -AdAdminGruppe `"$AdAdminGruppe`" -Ausgabe `"$out`""
  $name = 'Kalender-AD-Test'
  Unregister-ScheduledTask -TaskName $name -Confirm:$false -ErrorAction SilentlyContinue
  Register-ScheduledTask -TaskName $name -Action (New-ScheduledTaskAction -Execute 'powershell.exe' -Argument $args2) `
    -Principal (New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest) | Out-Null
  Start-ScheduledTask -TaskName $name
  Write-Host 'Prüfung läuft als SYSTEM …'
  for ($i = 0; $i -lt 60 -and -not (Test-Path $out); $i++) { Start-Sleep -Seconds 1 }
  Start-Sleep -Seconds 1
  Unregister-ScheduledTask -TaskName $name -Confirm:$false -ErrorAction SilentlyContinue
  if (-not (Test-Path $out)) { Write-Host 'Keine Antwort vom SYSTEM-Lauf (Zeitüberschreitung).' -ForegroundColor Red; exit 1 }
  Get-Content $out -Encoding UTF8 | ForEach-Object {
    $c = $(if ($_ -like 'OK*') { 'Green' } elseif ($_ -like 'FEHLER*') { 'Red' } elseif ($_ -like 'HINWEIS*') { 'Yellow' } else { 'Gray' })
    Write-Host $_ -ForegroundColor $c
  }
  Remove-Item $out -Force
  exit 0
}

$who = try { [Security.Principal.WindowsIdentity]::GetCurrent().Name } catch { [Environment]::UserName }
Out-Line "Kalender – AD-Prüfung als $who auf $([Environment]::MachineName)"
Out-Line ''

$cs = $null
$ok = Step 'Computer ist Mitglied einer Domäne' {
  $script:cs = Get-CimInstance Win32_ComputerSystem
  if (-not $script:cs.PartOfDomain) { throw 'Nein – der Server ist in keiner Domäne (nur Arbeitsgruppe).' }
  $script:cs.Domain
}
$dc = $AdServer
if ($ok) {
  Step 'Domänencontroller gefunden (über DNS)' {
    $o = @(& nltest.exe "/dsgetdc:$($script:cs.Domain)" 2>&1 | ForEach-Object { [string]$_ })
    if ($LASTEXITCODE -ne 0) { throw ($o -join ' ') }
    $line = @($o | Where-Object { $_ -match '^\s*DC:\s*\\\\' })[0]
    if (-not $line) { throw ('Kein DC in der Antwort: ' + ($o -join ' ')) }
    $found = ([string]$line -replace '^\s*DC:\s*\\*', '').Trim()
    $addr = @($o | Where-Object { $_ -match '^\s*Address:' })[0]
    if (-not $script:dc) { $script:dc = $found }
    $found + $(if ($addr) { ', ' + ([string]$addr -replace '^\s*Address:\s*\\*', '').Trim() } else { '' })
  } | Out-Null
  Step 'DNS-Auflösung des Domänennamens' {
    $ips = @([Net.Dns]::GetHostAddresses($script:cs.Domain) | ForEach-Object { $_.IPAddressToString })
    if (-not $ips) { throw "Keine Adresse für $($script:cs.Domain)" }
    $ips -join ', '
  } | Out-Null
  Step 'Sicherer Kanal zur Domäne' {
    if (-not (Test-ComputerSecureChannel)) { throw 'Vertrauensstellung des Computers zur Domäne gestört (Test-ComputerSecureChannel = False).' }
    'in Ordnung'
  } | Out-Null
}
# Erreicht der Kalender-Server den Domänencontroller über die nötigen Ports?
if (-not $dc -and $cs) { $dc = $cs.Domain }   # ohne gefundenen DC: Domänenname selbst prüfen
if ($dc) {
  foreach ($port in @(@{ p = 389; n = 'LDAP' }, @{ p = 88; n = 'Kerberos' }, @{ p = 445; n = 'SMB' }, @{ p = 3268; n = 'Globaler Katalog' })) {
    Step ("Port {0} ({1}) zu {2}" -f $port.p, $port.n, $dc) {
      $c = New-Object Net.Sockets.TcpClient
      try {
        $t = $c.BeginConnect($dc, $port.p, $null, $null)
        if (-not $t.AsyncWaitHandle.WaitOne(3000) -or -not $c.Connected) { throw 'nicht erreichbar (Firewall?)' }
        'offen'
      } finally { $c.Close() }
    } | Out-Null
  }
}
$lib = Step 'AD-Bibliothek laden (System.DirectoryServices.AccountManagement)' {
  Add-Type -AssemblyName System.DirectoryServices.AccountManagement
  'geladen'
}
$ctx = $null
if ($lib) {
  $ctxOk = Step 'Verbindung zum AD (PrincipalContext Domain)' {
    $script:ctx = $(if ($AdServer) { New-Object System.DirectoryServices.AccountManagement.PrincipalContext([System.DirectoryServices.AccountManagement.ContextType]::Domain, $AdServer) }
                    else { New-Object System.DirectoryServices.AccountManagement.PrincipalContext([System.DirectoryServices.AccountManagement.ContextType]::Domain) })
    "Domäne $($script:ctx.Name), Server $($script:ctx.ConnectedServer)"
  }
  if ($ctxOk) {
    foreach ($g in @($AdNutzerGruppe, $AdAdminGruppe)) {
      Step "Gruppe '$g' im AD vorhanden" {
        $gp = [System.DirectoryServices.AccountManagement.GroupPrincipal]::FindByIdentity($script:ctx, $g)
        if (-not $gp) { throw "Gruppe '$g' nicht gefunden (Name prüfen – gemeint ist der Anmeldename vor Windows 2000 / sAMAccountName)." }
        "$($gp.DistinguishedName), $(@($gp.GetMembers($true)).Count) Mitglied(er) inkl. verschachtelter"
      } | Out-Null
    }
    if ($Benutzer) {
      $name = $(if ($Benutzer -match '\\') { ($Benutzer -split '\\')[-1] } else { $Benutzer })
      $up = $null
      $found = Step "Benutzer '$name' im AD" {
        $script:up = [System.DirectoryServices.AccountManagement.UserPrincipal]::FindByIdentity($script:ctx, $name)
        if (-not $script:up) { throw "Kein Konto '$name' gefunden." }
        "$($script:up.SamAccountName), aktiv: $($script:up.Enabled), $($script:up.DistinguishedName)"
      }
      if ($found) {
        $groups = @()
        $ag = Step 'Gruppen des Benutzers (inkl. verschachtelter, GetAuthorizationGroups)' {
          $script:groups = @($script:up.GetAuthorizationGroups() | ForEach-Object { $_.SamAccountName })
          "$($script:groups.Count) Gruppen"
        }
        if (-not $ag) { Out-Line 'HINWEIS Der Kalender nutzt dann automatisch die Ersatzprüfung über die Gruppenmitglieder.' 'Yellow' }
        foreach ($g in @($AdNutzerGruppe, $AdAdminGruppe)) {
          Step "Mitglied in '$g'" {
            $member = $script:groups -contains $g
            if (-not $member) {
              $gp = [System.DirectoryServices.AccountManagement.GroupPrincipal]::FindByIdentity($script:ctx, $g)
              $member = $gp -and @($gp.GetMembers($true) | Where-Object { $_.Sid -eq $script:up.Sid }).Count -gt 0
            }
            $(if ($member) { 'ja' } else { 'nein' })
          } | Out-Null
        }
        if ($MitPasswort -and -not $Ausgabe) {
          $sec = Read-Host "Passwort für $name (wird nur geprüft, nicht gespeichert)" -AsSecureString
          $pw = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec))
          Step 'Anmeldung mit Passwort (ValidateCredentials, Negotiate)' {
            if (-not $pw) { throw 'leeres Passwort' }
            $r = $script:ctx.ValidateCredentials($name, $pw, [System.DirectoryServices.AccountManagement.ContextOptions]::Negotiate)
            if (-not $r) { throw 'Passwort falsch oder Konto gesperrt' }
            'erfolgreich'
          } | Out-Null
          $pw = $null
        }
      }
    } else {
      Out-Line 'HINWEIS Mit -Benutzer <Name> werden auch Benutzer und Gruppenmitgliedschaft geprüft.' 'Yellow'
    }
    $script:ctx.Dispose()
  }
}

if ($Ausgabe) { [IO.File]::WriteAllLines($Ausgabe, [string[]]$lines, (New-Object Text.UTF8Encoding $true)) }
