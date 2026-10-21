<#
.SYNOPSIS
  Kalender-Server: liefert kalender/index.html aus und speichert Kalender + Benutzer zentral.

.DESCRIPTION
  Läuft mit Windows PowerShell 5.1 (Windows Server 2025 Core) und PowerShell 7.
  Keine Installation nötig. Daten liegen als JSON im Datenordner, tägliche Sicherungen in "backup".

  Rollen:  admin      – sieht/bearbeitet alles, verwaltet Benutzer, Personen, Kategorien, Feiertage
           groupadmin – verwaltet die ihm zugeordneten Gruppen (Personen, Einträge, Benutzerrechte)
           user       – je Person: keine / sehen (view) / bearbeiten (edit)
           display    – nur lesen (Monitor), Anmeldung über geheimen Anzeige-Link

  Anmeldung: mit dem Windows-/AD-Konto. Zugang hat, wer (auch verschachtelt) Mitglied der Gruppe
  -AdNutzerGruppe oder -AdAdminGruppe ist; Mitglieder der Admin-Gruppe sind Administrator.
  Lokal gibt es nur noch den Notzugang "admin" (und die Anzeige-Links). Mit -OhneAD wie früher
  nur lokale Konten.

.PARAMETER Port
  TCP-Port (Standard 8080).
.PARAMETER DataDir
  Datenordner (Standard: .\daten neben diesem Skript).
.PARAMETER AdNutzerGruppe
  AD-Gruppe, deren Mitglieder sich anmelden dürfen (Standard L_Kalender_Nutzer).
.PARAMETER AdAdminGruppe
  AD-Gruppe, deren Mitglieder Administratoren sind (Standard L_Kalender_Admins).
.PARAMETER OhneAD
  Keine AD-Anmeldung, nur lokale Konten.
.PARAMETER AdNachpruefungMinuten
  Wie oft bei laufender Sitzung geprüft wird, ob das AD-Konto noch berechtigt ist (Standard 15).
.PARAMETER ResetAdmin
  Setzt das Passwort des Benutzers "admin" neu (legt ihn ggf. an), schreibt es in
  ERSTES-ADMIN-PASSWORT.txt im Datenordner und beendet sich.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\kalender-server.ps1 -Port 8080
#>
param(
  [int]$Port = 8080,
  [string]$DataDir = (Join-Path $PSScriptRoot 'daten'),
  [string]$AdNutzerGruppe = 'L_Kalender_Nutzer',
  [string]$AdAdminGruppe = 'L_Kalender_Admins',
  [switch]$OhneAD,
  [switch]$ResetAdmin,
  [int]$AdNachpruefungMinuten = 15,
  [string]$AdTestDatei = ''   # nur für Tests: AD aus einer JSON-Datei nachbilden
)

$ErrorActionPreference = 'Stop'
# index.html liegt neben dem Skript (alles in einem Ordner) oder eine Ebene darüber (Unterordner "server")
$WebRoot      = $(if (Test-Path (Join-Path $PSScriptRoot 'index.html')) { $PSScriptRoot } else { Split-Path $PSScriptRoot -Parent })
$CalFile      = Join-Path $DataDir 'kalender.json'
$UsersFile    = Join-Path $DataDir 'benutzer.json'
$BackupDir    = Join-Path $DataDir 'backup'
$LogFile      = Join-Path $DataDir 'server.log'
$AdminPwFile  = Join-Path $DataDir 'ERSTES-ADMIN-PASSWORT.txt'
$Utf8         = New-Object System.Text.UTF8Encoding $false
$Regions      = @('NONE','DE','BW','BY','BE','BB','HB','HH','HE','MV','NI','NW','RP','SL','SN','ST','SH','TH')
$UserSessionHours = 12
$DisplaySessionDays = 365
$MinPasswordLength = 8
$UseAD = -not $OhneAD
$AdRecheckMinutes = $AdNachpruefungMinuten   # so oft wird bei laufender Sitzung geprüft, ob das AD-Konto noch berechtigt ist

New-Item -ItemType Directory -Force -Path $DataDir, $BackupDir | Out-Null

# ---------------------------------------------------------------- Hilfsfunktionen

function Write-Log([string]$msg) {
  $line = '{0:yyyy-MM-dd HH:mm:ss}  {1}' -f (Get-Date), $msg
  Write-Host $line
  try {
    if ((Test-Path $LogFile) -and (Get-Item $LogFile).Length -gt 5MB) { Move-Item $LogFile "$LogFile.1" -Force }
    [IO.File]::AppendAllText($LogFile, $line + "`r`n", $Utf8)
  } catch { }
}

# Fehler mit HTTP-Status: throw (New-HttpError 400 'Text')
function New-HttpError([int]$status, [string]$msg) { return "$status|$msg" }

function Get-Prop($o, [string]$name) {
  if ($null -eq $o) { return $null }
  if ($o -is [System.Collections.IDictionary]) { return $o[$name] }
  $p = $o.PSObject.Properties[$name]
  if ($p) { return $p.Value }
  return $null
}

function Read-JsonFile([string]$path) {
  if (-not (Test-Path $path)) { return $null }
  $text = [IO.File]::ReadAllText($path, [Text.Encoding]::UTF8)
  if (-not $text.Trim()) { return $null }
  return (ConvertFrom-Json $text)
}

# Atomar schreiben: erst Temp-Datei, dann ersetzen
function Write-JsonFile([string]$path, $obj) {
  $json = ConvertTo-Json -InputObject $obj -Depth 20
  $tmp = "$path.tmp"
  [IO.File]::WriteAllText($tmp, $json, $Utf8)
  Move-Item -Path $tmp -Destination $path -Force
}

function New-RandomHex([int]$bytes) {
  $b = New-Object byte[] $bytes
  $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
  $rng.GetBytes($b); $rng.Dispose()
  return (($b | ForEach-Object { $_.ToString('x2') }) -join '')
}

function New-RandomPassword {
  $chars = 'abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789'
  $b = New-Object byte[] 12
  $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
  $rng.GetBytes($b); $rng.Dispose()
  return (($b | ForEach-Object { $chars[$_ % $chars.Length] }) -join '')
}

function Get-PasswordHash([string]$password, [string]$saltHex, [int]$iterations) {
  $salt = New-Object byte[] ($saltHex.Length / 2)
  for ($i = 0; $i -lt $salt.Length; $i++) { $salt[$i] = [Convert]::ToByte($saltHex.Substring($i * 2, 2), 16) }
  $kdf = New-Object System.Security.Cryptography.Rfc2898DeriveBytes($password, $salt, $iterations, [System.Security.Cryptography.HashAlgorithmName]::SHA256)
  $hash = $kdf.GetBytes(32); $kdf.Dispose()
  return [Convert]::ToBase64String($hash)
}

function Set-UserPassword($user, [string]$password) {
  $user.salt = New-RandomHex 16
  $user.iterations = 120000
  $user.hash = Get-PasswordHash $password $user.salt $user.iterations
}

function Test-UserPassword($user, [string]$password) {
  if (-not $user.hash) { return $false }
  $calc = Get-PasswordHash $password $user.salt ([int]$user.iterations)
  # Vergleich ohne frühen Abbruch
  $a = [Text.Encoding]::ASCII.GetBytes($calc); $b = [Text.Encoding]::ASCII.GetBytes([string]$user.hash)
  if ($a.Length -ne $b.Length) { return $false }
  $diff = 0
  for ($i = 0; $i -lt $a.Length; $i++) { $diff = $diff -bor ($a[$i] -bxor $b[$i]) }
  return ($diff -eq 0)
}

function Test-Id($s)    { return ($s -is [string] -and $s -match '^[A-Za-z0-9_-]{1,40}$') }
function Test-Color($s) { return ($s -is [string] -and $s -match '^#[0-9a-fA-F]{6}$') }
function Test-Date($s) {
  if (-not ($s -is [string] -and $s -match '^\d{4}-\d{2}-\d{2}$')) { return $false }
  $d = [datetime]::MinValue
  return [datetime]::TryParseExact($s, 'yyyy-MM-dd', [Globalization.CultureInfo]::InvariantCulture, [Globalization.DateTimeStyles]::None, [ref]$d)
}
function Get-CleanText($s, [int]$max) {
  if ($null -eq $s) { return '' }
  $t = ([string]$s).Trim() -replace '[\x00-\x1f]', ''
  if ($t.Length -gt $max) { $t = $t.Substring(0, $max) }
  return $t
}

# ---------------------------------------------------------------- Active Directory
# Ergebnis der Funktionen: @{ ok; sam; sid; display; user (darf rein); admin (Admin-Gruppe); error }

$script:AdMock = $null
if ($UseAD -and $AdTestDatei) { $script:AdMock = Get-Content $AdTestDatei -Raw -Encoding UTF8 | ConvertFrom-Json }
if ($UseAD -and -not $AdTestDatei) {
  try { Add-Type -AssemblyName System.DirectoryServices.AccountManagement }
  catch { Write-Host "AD-Unterstützung nicht verfügbar: $($_.Exception.Message)" }
}

# "DOMÄNE\name" → "name"
function Get-AdLoginName([string]$name) { if ($name -match '\\') { return ($name -split '\\')[-1] } return $name }

function Get-AdRolesFromNames($names) {
  $isAdmin = @($names) -contains $AdAdminGruppe
  return @{ user = ($isAdmin -or (@($names) -contains $AdNutzerGruppe)); admin = $isAdmin }
}

function Get-AdMockAccount([string]$by, [string]$value) {
  $script:AdMock = Get-Content $AdTestDatei -Raw -Encoding UTF8 | ConvertFrom-Json   # bei jedem Aufruf neu (Tests ändern sie)
  foreach ($a in @($script:AdMock.users)) {
    if (($by -eq 'sid' -and $a.sid -eq $value) -or ($by -eq 'name' -and ($a.sam -ieq $value -or $a.upn -ieq $value))) { return $a }
  }
  return $null
}

# Gruppenzugehörigkeit (inkl. verschachtelter Gruppen) eines UserPrincipal
function Get-AdMembership($ctx, $up) {
  $names = @()
  try {
    $names = @($up.GetAuthorizationGroups() | ForEach-Object { $_.SamAccountName })
  } catch {
    # Fallback, falls einzelne Gruppen nicht auflösbar sind: die beiden Gruppen direkt prüfen
    foreach ($g in @($AdNutzerGruppe, $AdAdminGruppe)) {
      try {
        $gp = [System.DirectoryServices.AccountManagement.GroupPrincipal]::FindByIdentity($ctx, $g)
        if ($gp -and @($gp.GetMembers($true) | Where-Object { $_.Sid -eq $up.Sid }).Count -gt 0) { $names += $g }
      } catch { }
    }
  }
  return Get-AdRolesFromNames $names
}

function ConvertTo-AdResult($up, $roles) {
  return @{ ok = $true; sam = [string]$up.SamAccountName; sid = [string]$up.Sid.Value; display = [string]$up.DisplayName
            user = $roles.user; admin = $roles.admin; enabled = ($up.Enabled -ne $false) }
}

# Benutzername + Passwort am AD prüfen
function Test-AdLogin([string]$name, [string]$pw) {
  $name = Get-AdLoginName $name
  if (-not $name -or -not $pw) { return @{ ok = $false } }   # leeres Passwort niemals annehmen
  if ($script:AdMock) {
    $a = Get-AdMockAccount 'name' $name
    if (-not $a -or $a.password -cne $pw) { return @{ ok = $false } }
    $r = Get-AdRolesFromNames $a.groups
    return @{ ok = $true; sam = $a.sam; sid = $a.sid; display = $a.display; user = $r.user; admin = $r.admin; enabled = ($a.enabled -ne $false) }
  }
  $ctx = New-Object System.DirectoryServices.AccountManagement.PrincipalContext([System.DirectoryServices.AccountManagement.ContextType]::Domain)
  try {
    if (-not $ctx.ValidateCredentials($name, $pw, [System.DirectoryServices.AccountManagement.ContextOptions]::Negotiate)) { return @{ ok = $false } }
    $up = [System.DirectoryServices.AccountManagement.UserPrincipal]::FindByIdentity($ctx, $name)
    if (-not $up) { return @{ ok = $false } }
    return ConvertTo-AdResult $up (Get-AdMembership $ctx $up)
  } finally { $ctx.Dispose() }
}

# Konto ohne Passwort nachschlagen (per SID oder Name) – für Nachprüfung und Vorab-Anlegen
function Find-AdAccount([string]$by, [string]$value) {
  if ($by -eq 'name') { $value = Get-AdLoginName $value }
  if ($script:AdMock) {
    $a = Get-AdMockAccount $by $value
    if (-not $a) { return @{ ok = $false } }
    $r = Get-AdRolesFromNames $a.groups
    return @{ ok = $true; sam = $a.sam; sid = $a.sid; display = $a.display; user = $r.user; admin = $r.admin; enabled = ($a.enabled -ne $false) }
  }
  $ctx = New-Object System.DirectoryServices.AccountManagement.PrincipalContext([System.DirectoryServices.AccountManagement.ContextType]::Domain)
  try {
    # per SID gezielt; per Name sucht FindByIdentity in Anmeldename, UPN usw.
    $up = $(if ($by -eq 'sid') { [System.DirectoryServices.AccountManagement.UserPrincipal]::FindByIdentity($ctx, [System.DirectoryServices.AccountManagement.IdentityType]::Sid, $value) }
            else { [System.DirectoryServices.AccountManagement.UserPrincipal]::FindByIdentity($ctx, $value) })
    if (-not $up) { return @{ ok = $false } }
    return ConvertTo-AdResult $up (Get-AdMembership $ctx $up)
  } finally { $ctx.Dispose() }
}

# ---------------------------------------------------------------- Kalenderdaten

$script:Persons = New-Object System.Collections.ArrayList
$script:Categories = New-Object System.Collections.ArrayList
$script:Groups = New-Object System.Collections.ArrayList
$script:School = New-Object System.Collections.ArrayList   # Schulferien: { id, name, from, to }
$script:Entries = New-Object System.Collections.ArrayList
$script:Region = 'DE'
$script:Version = 1

$DefaultCategories = @(
  [ordered]@{ id = 'c-urlaub';     name = 'Urlaub';     color = '#16a34a'; absent = $true },
  [ordered]@{ id = 'c-krank';      name = 'Krank';      color = '#dc2626'; absent = $true },
  [ordered]@{ id = 'c-schulung';   name = 'Schulung';   color = '#0891b2'; absent = $true },
  [ordered]@{ id = 'c-homeoffice'; name = 'Homeoffice'; color = '#9333ea'; absent = $false },
  [ordered]@{ id = 'c-termin';     name = 'Termin';     color = '#ca8a04'; absent = $false }
)

function Test-PersonExists([string]$id) { foreach ($p in $script:Persons) { if ($p.id -eq $id) { return $true } }; return $false }
function Test-CategoryExists([string]$id) { foreach ($c in $script:Categories) { if ($c.id -eq $id) { return $true } }; return $false }

# Prüft und vereinheitlicht einen Eintrag. Liefert ein [ordered]-Objekt oder wirft einen Fehler.
function ConvertTo-Entry($in, [string]$id) {
  if (-not (Test-Id $id)) { throw (New-HttpError 400 'Ungültige Eintrags-ID.') }
  $label = Get-CleanText (Get-Prop $in 'label') 60
  if (-not $label) { throw (New-HttpError 400 'Bitte eine Bezeichnung eingeben.') }
  $from = Get-Prop $in 'from'; $to = Get-Prop $in 'to'
  if (-not (Test-Date $from) -or -not (Test-Date $to)) { throw (New-HttpError 400 'Ungültiges Datum.') }
  if ([string]::CompareOrdinal($from, $to) -gt 0) { throw (New-HttpError 400 '"Bis" darf nicht vor "Von" liegen.') }
  $persons = @()
  foreach ($p in @(Get-Prop $in 'persons')) {
    if ((Test-Id $p) -and (Test-PersonExists $p) -and ($persons -notcontains $p)) { $persons += $p }
  }
  if ($persons.Count -eq 0) { throw (New-HttpError 400 'Bitte mindestens eine Person auswählen.') }
  $color = Get-Prop $in 'color'
  if (-not (Test-Color $color)) { $color = '#2563eb' }

  $e = [ordered]@{ id = $id; label = $label; from = $from; to = $to; persons = $persons; color = $color }
  $cat = Get-Prop $in 'category'
  if ((Test-Id $cat) -and (Test-CategoryExists $cat)) { $e.category = $cat }
  $half = Get-Prop $in 'half'
  if (($half -eq 'am' -or $half -eq 'pm') -and $from -eq $to) { $e.half = $half }
  $rep = Get-Prop $in 'repeat'
  if ($rep) {
    $freq = Get-Prop $rep 'freq'
    if ($freq -eq 'week' -or $freq -eq 'month' -or $freq -eq 'year') {
      $r = [ordered]@{ freq = $freq }
      if ($freq -eq 'week') { $r.interval = $(if ((Get-Prop $rep 'interval') -eq 2) { 2 } else { 1 }) }
      $until = Get-Prop $rep 'until'
      if ((Test-Date $until) -and [string]::CompareOrdinal($until, $from) -ge 0) { $r.until = $until }
      $e.repeat = $r
    }
  }
  return $e
}

function ConvertTo-PersonList($list) {
  $out = New-Object System.Collections.ArrayList
  $seen = @{}
  foreach ($p in @($list)) {
    $id = Get-Prop $p 'id'
    if (-not (Test-Id $id) -or $seen.ContainsKey($id)) { continue }
    $name = Get-CleanText (Get-Prop $p 'name') 30
    if (-not $name) { $name = 'Person ' + ($out.Count + 1) }
    $color = Get-Prop $p 'color'; if (-not (Test-Color $color)) { $color = '#2563eb' }
    $seen[$id] = $true
    [void]$out.Add([ordered]@{ id = $id; name = $name; color = $color })
  }
  if ($out.Count -eq 0) { throw (New-HttpError 400 'Mindestens eine Person ist nötig.') }
  if ($out.Count -gt 50) { throw (New-HttpError 400 'Höchstens 50 Personen.') }
  return ,$out
}

function ConvertTo-CategoryList($list) {
  $out = New-Object System.Collections.ArrayList
  $seen = @{}
  foreach ($c in @($list)) {
    $id = Get-Prop $c 'id'
    if (-not (Test-Id $id) -or $seen.ContainsKey($id)) { continue }
    $name = Get-CleanText (Get-Prop $c 'name') 30
    if (-not $name) { continue }
    $color = Get-Prop $c 'color'; if (-not (Test-Color $color)) { $color = '#2563eb' }
    $seen[$id] = $true
    [void]$out.Add([ordered]@{ id = $id; name = $name; color = $color; absent = [bool](Get-Prop $c 'absent') })
  }
  if ($out.Count -gt 50) { throw (New-HttpError 400 'Höchstens 50 Kategorien.') }
  return ,$out
}

# Gruppen von Personen (nur zur Auswahl/Anzeige; Rechte gelten weiter je Person)
function ConvertTo-GroupList($list) {
  $out = New-Object System.Collections.ArrayList
  $seen = @{}
  foreach ($g in @($list)) {
    $id = Get-Prop $g 'id'
    if (-not (Test-Id $id) -or $seen.ContainsKey($id)) { continue }
    $name = Get-CleanText (Get-Prop $g 'name') 30
    if (-not $name) { continue }
    $members = @()
    foreach ($m in @(Get-Prop $g 'members')) {
      if ((Test-Id $m) -and (Test-PersonExists $m) -and ($members -notcontains $m)) { $members += $m }
    }
    $seen[$id] = $true
    [void]$out.Add([ordered]@{ id = $id; name = $name; members = $members })
  }
  if ($out.Count -gt 50) { throw (New-HttpError 400 'Höchstens 50 Gruppen.') }
  return ,$out
}

# Schulferien (vom Admin gepflegt bzw. aus iCal importiert)
function ConvertTo-SchoolList($list) {
  $out = New-Object System.Collections.ArrayList
  foreach ($h in @($list)) {
    if ($null -eq $h) { continue }
    $name = Get-CleanText (Get-Prop $h 'name') 40
    $from = Get-Prop $h 'from'; $to = Get-Prop $h 'to'
    if (-not $name -or -not (Test-Date $from) -or -not (Test-Date $to) -or [string]::CompareOrdinal($from, $to) -gt 0) { continue }
    $id = Get-Prop $h 'id'; if (-not (Test-Id $id)) { $id = 's' + (New-RandomHex 6) }
    [void]$out.Add([ordered]@{ id = $id; name = $name; from = $from; to = $to })
  }
  if ($out.Count -gt 500) { throw (New-HttpError 400 'Höchstens 500 Ferien-Einträge.') }
  $sorted = New-Object System.Collections.ArrayList
  foreach ($x in @($out | Sort-Object { $_.from })) { [void]$sorted.Add($x) }
  return ,$sorted
}

function Import-CalendarData($data, [ref]$skipped) {
  $script:Persons = ConvertTo-PersonList (Get-Prop $data 'persons')
  $cats = Get-Prop $data 'categories'
  $script:Categories = $(if ($null -eq $cats) { ConvertTo-CategoryList $DefaultCategories } else { ConvertTo-CategoryList $cats })
  $r = Get-Prop $data 'holidayRegion'
  $script:Region = $(if ($Regions -contains $r) { $r } else { 'DE' })
  $script:Groups = ConvertTo-GroupList (Get-Prop $data 'groups')
  $script:School = ConvertTo-SchoolList (Get-Prop $data 'schoolHolidays')
  $script:Entries = New-Object System.Collections.ArrayList
  $ids = @{}
  $skip = 0
  foreach ($e in @(Get-Prop $data 'entries')) {
    if ($null -eq $e) { continue }
    $id = Get-Prop $e 'id'
    if (-not (Test-Id $id) -or $ids.ContainsKey($id)) { $id = 'e' + (New-RandomHex 6) }
    try { [void]$script:Entries.Add((ConvertTo-Entry $e $id)); $ids[$id] = $true } catch { $skip++ }
  }
  if ($skipped) { $skipped.Value = $skip }
}

function Save-Calendar {
  $script:Version++
  $today = Get-Date -Format 'yyyy-MM-dd'
  $bk = Join-Path $BackupDir "kalender-$today.json"
  if ((Test-Path $CalFile) -and -not (Test-Path $bk)) {
    Copy-Item $CalFile $bk
    if (Test-Path $UsersFile) { Copy-Item $UsersFile (Join-Path $BackupDir "benutzer-$today.json") -Force }
    Get-ChildItem $BackupDir -Filter '*.json' | Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-60) } | Remove-Item -Force
  }
  Write-JsonFile $CalFile ([ordered]@{
    version = $script:Version; holidayRegion = $script:Region
    persons = $script:Persons; categories = $script:Categories; groups = $script:Groups
    schoolHolidays = $script:School; entries = $script:Entries
  })
}

# ---------------------------------------------------------------- Benutzer

$script:Users = New-Object System.Collections.ArrayList

function ConvertTo-UserRecord($u) {
  $perms = [ordered]@{}
  $p = Get-Prop $u 'perms'
  if ($p) {
    $names = $(if ($p -is [System.Collections.IDictionary]) { @($p.Keys) } else { @($p.PSObject.Properties | ForEach-Object { $_.Name }) })
    foreach ($k in $names) { $v = Get-Prop $p $k; if ($v -eq 'view' -or $v -eq 'edit') { $perms[$k] = $v } }
  }
  return [ordered]@{
    id = [string](Get-Prop $u 'id'); name = [string](Get-Prop $u 'name'); role = [string](Get-Prop $u 'role')
    salt = Get-Prop $u 'salt'; hash = Get-Prop $u 'hash'; iterations = Get-Prop $u 'iterations'
    mustChange = [bool](Get-Prop $u 'mustChange'); key = Get-Prop $u 'key'; perms = $perms
    adminGroups = @(@(Get-Prop $u 'adminGroups') | Where-Object { Test-Id $_ })   # nur Gruppen-Admin
    myGroups = @(@(Get-Prop $u 'myGroups') | Where-Object { $_ })               # persönliche Gruppen
    createdBy = Get-Prop $u 'createdBy'
    source = $(if ((Get-Prop $u 'source') -eq 'ad') { 'ad' } else { 'local' }); sid = Get-Prop $u 'sid'
  }
}

function Save-Users { Write-JsonFile $UsersFile ([ordered]@{ users = $script:Users }) }

function Find-UserById([string]$id) { foreach ($u in $script:Users) { if ($u.id -eq $id) { return $u } }; return $null }
function Find-UserByName([string]$name) { foreach ($u in $script:Users) { if ($u.name -ieq $name) { return $u } }; return $null }
function Get-AdminCount { return @($script:Users | Where-Object { $_.role -eq 'admin' }).Count }

function Reset-AdminPassword {
  $admin = Find-UserByName 'admin'
  if (-not $admin) {
    $admin = [ordered]@{ id = 'u' + (New-RandomHex 6); name = 'admin'; role = 'admin'; salt = $null; hash = $null
                         iterations = $null; mustChange = $true; key = $null; perms = [ordered]@{}
                         adminGroups = @(); myGroups = @(); createdBy = $null; source = 'local'; sid = $null }
    [void]$script:Users.Add($admin)
  }
  $pw = New-RandomPassword
  Set-UserPassword $admin $pw
  $admin.role = 'admin'
  $admin.mustChange = $true
  Save-Users
  [IO.File]::WriteAllText($AdminPwFile, "Benutzer: admin`r`nPasswort: $pw`r`n`r`nBeim ersten Anmelden muss das Passwort geändert werden.`r`nDiese Datei wird danach automatisch gelöscht.`r`n", $Utf8)
  Write-Log "Admin-Passwort neu gesetzt. Siehe $AdminPwFile"
}

# Personen, die ein Gruppen-Admin verwaltet: alle Mitglieder seiner zugeordneten Gruppen
function Get-ManagedIds($user) {
  if ($user.role -ne 'groupadmin') { return @() }
  $ids = @()
  foreach ($g in $script:Groups) { if ($user.adminGroups -contains $g.id) { $ids += @($g.members) } }
  return @($ids | Select-Object -Unique)
}

# Recht einer Person für einen Benutzer: 'edit' | 'view' | 'none'
function Get-Perm($user, [string]$personId) {
  if ($user.role -eq 'admin') { return 'edit' }
  if ($user.role -eq 'groupadmin' -and (Get-ManagedIds $user) -contains $personId) { return 'edit' }
  $v = $user.perms[$personId]
  if ($user.role -eq 'display' -and $v) { return 'view' }
  if ($v) { return $v }
  return 'none'
}
function Test-CanEditEntry($user, $entry) {
  foreach ($p in @($entry.persons)) { if ((Get-Perm $user $p) -ne 'edit') { return $false } }
  return $true
}

function Get-PublicUser($u) {
  $o = [ordered]@{ id = $u.id; name = $u.name; role = $u.role; mustChange = [bool]$u.mustChange; perms = $u.perms }
  if ($u.role -eq 'display') { $o.key = $u.key }
  if ($u.role -eq 'groupadmin') { $o.adminGroups = @($u.adminGroups) }
  $o.source = $u.source
  return $o
}

function Remove-PersonReferences([string[]]$removed) {
  if ($removed.Count -eq 0) { return }
  $keep = New-Object System.Collections.ArrayList
  foreach ($e in $script:Entries) {
    $e.persons = @($e.persons | Where-Object { $removed -notcontains $_ })
    if ($e.persons.Count -gt 0) { [void]$keep.Add($e) }
  }
  $script:Entries = $keep
  foreach ($u in $script:Users) { foreach ($r in $removed) { if ($u.perms.Contains($r)) { $u.perms.Remove($r) } } }
  Save-Users
}

# ---------------------------------------------------------------- Laden / Erststart

$calData = Read-JsonFile $CalFile
if ($calData) {
  Import-CalendarData $calData $null
  $v = Get-Prop $calData 'version'; if ($v) { $script:Version = [int]$v }
} else {
  $script:Persons = ConvertTo-PersonList @(
    @{ id = 'p1'; name = 'Person 1'; color = '#2563eb' }, @{ id = 'p2'; name = 'Person 2'; color = '#16a34a' },
    @{ id = 'p3'; name = 'Person 3'; color = '#dc2626' })
  $script:Categories = ConvertTo-CategoryList $DefaultCategories
  Save-Calendar
}

$usersData = Read-JsonFile $UsersFile
if ($usersData) { foreach ($u in @(Get-Prop $usersData 'users')) { if ($u) { [void]$script:Users.Add((ConvertTo-UserRecord $u)) } } }
if ($ResetAdmin -or $script:Users.Count -eq 0) {
  Reset-AdminPassword
  if ($ResetAdmin) { return }
}

# ---------------------------------------------------------------- Sitzungen

$script:Sessions = @{}   # token -> @{ userId; expires; display }
$script:Fails = @{}      # login-name -> @{ n = Fehlversuche; until }

function New-Session($user) {
  $token = New-RandomHex 32
  $isDisplay = $user.role -eq 'display'
  $exp = $(if ($isDisplay) { (Get-Date).AddDays($DisplaySessionDays) } else { (Get-Date).AddHours($UserSessionHours) })
  $script:Sessions[$token] = @{ userId = $user.id; expires = $exp; display = $isDisplay; checked = (Get-Date) }
  $maxAge = $(if ($isDisplay) { "; Max-Age=$($DisplaySessionDays * 86400)" } else { '' })
  return "kal=$token; Path=/; HttpOnly; SameSite=Strict$maxAge"
}

# Rolle eines AD-Kontos an die Gruppen anpassen: Admin-Gruppe → admin; sonst bleibt user/groupadmin
function Update-AdRole($u, $a) {
  $old = $u.role
  if ($a.admin) { $u.role = 'admin' }
  elseif ($u.role -eq 'admin') { $u.role = 'user' }
  if ($u.role -ne $old) { Save-Users; Write-Log "Rolle von $($u.name) laut AD: $old → $($u.role)" }
}

function Remove-UserSessions([string]$userId, [string]$exceptToken) {
  foreach ($t in @($script:Sessions.Keys)) {
    if ($script:Sessions[$t].userId -eq $userId -and $t -ne $exceptToken) { $script:Sessions.Remove($t) }
  }
}

function Get-SessionUser($req, [ref]$tokenOut) {
  $c = $req.Cookies['kal']
  if (-not $c) { return $null }
  $s = $script:Sessions[$c.Value]
  if (-not $s) { return $null }
  if ($s.expires -lt (Get-Date)) { $script:Sessions.Remove($c.Value); return $null }
  $u = Find-UserById $s.userId
  if (-not $u) { $script:Sessions.Remove($c.Value); return $null }
  # AD-Konten: alle paar Minuten prüfen, ob sie noch existieren, aktiv und in der Gruppe sind
  if ($UseAD -and $u.source -eq 'ad' -and $s.checked -lt (Get-Date).AddMinutes(-$AdRecheckMinutes)) {
    try {
      $a = Find-AdAccount 'sid' $u.sid
      $s.checked = Get-Date
      if (-not $a.ok -or -not $a.enabled -or -not $a.user) {
        Write-Log "Sitzung beendet (AD-Konto nicht mehr berechtigt): $($u.name)"
        $script:Sessions.Remove($c.Value); return $null
      }
      Update-AdRole $u $a
    } catch { Write-Log "AD-Nachprüfung für $($u.name) nicht möglich: $($_.Exception.Message)" }   # bei AD-Störung Sitzung behalten
  }
  if (-not $s.display) { $s.expires = (Get-Date).AddHours($UserSessionHours) }   # gleitend verlängern
  if ($tokenOut) { $tokenOut.Value = $c.Value }
  return $u
}

# ---------------------------------------------------------------- HTTP

function Send-Response($ctx, [int]$status, [string]$contentType, [byte[]]$bytes, [string]$cookie) {
  $res = $ctx.Response
  $res.StatusCode = $status
  $res.ContentType = $contentType
  $res.Headers.Add('Cache-Control', 'no-store')
  $res.Headers.Add('X-Content-Type-Options', 'nosniff')
  $res.Headers.Add('X-Frame-Options', 'DENY')
  if ($cookie) { $res.AppendHeader('Set-Cookie', $cookie) }
  $res.ContentLength64 = $bytes.Length
  $res.OutputStream.Write($bytes, 0, $bytes.Length)
  $res.OutputStream.Close()
}

function Send-Json($ctx, [int]$status, $obj, [string]$cookie) {
  $json = $(if ($null -eq $obj) { '{}' } else { ConvertTo-Json -InputObject $obj -Depth 20 -Compress })
  Send-Response $ctx $status 'application/json; charset=utf-8' $Utf8.GetBytes($json) $cookie
}

function Read-Body($req) {
  if (-not $req.HasEntityBody) { return $null }
  if ($req.ContentLength64 -gt 10MB) { throw (New-HttpError 413 'Anfrage zu groß.') }
  $sr = New-Object IO.StreamReader($req.InputStream, [Text.Encoding]::UTF8)
  $text = $sr.ReadToEnd(); $sr.Close()
  if (-not $text.Trim()) { return $null }
  try { return (ConvertFrom-Json $text) } catch { throw (New-HttpError 400 'Ungültige Daten.') }
}

# Stand der Seite (index.html): ändert er sich, laden die Browser die Seite neu
function Get-AppVersion { return [string](Get-Item (Join-Path $WebRoot 'index.html')).LastWriteTimeUtc.Ticks }

# Kalenderdaten, gefiltert auf das, was der Benutzer sehen darf
function Get-CalendarView($user) {
  $perm = @{}                                        # Recht je Person nur einmal berechnen
  foreach ($p in $script:Persons) { $perm[$p.id] = Get-Perm $user $p.id }
  $persons = New-Object System.Collections.ArrayList
  foreach ($p in $script:Persons) {
    if ($perm[$p.id] -ne 'none') { [void]$persons.Add([ordered]@{ id = $p.id; name = $p.name; color = $p.color; canEdit = ($perm[$p.id] -eq 'edit') }) }
  }
  $entries = New-Object System.Collections.ArrayList
  foreach ($e in $script:Entries) {
    $vis = @($e.persons | Where-Object { $perm[$_] -and $perm[$_] -ne 'none' })
    if ($vis.Count -eq 0) { continue }
    $o = [ordered]@{}
    foreach ($k in $e.Keys) { $o[$k] = $e[$k] }
    $o.persons = $vis                                # unsichtbare Personen nicht verraten
    if (@($e.persons | Where-Object { $perm[$_] -ne 'edit' }).Count -gt 0) { $o._ro = $true }
    [void]$entries.Add($o)
  }
  $visible = { param($ids) @(@($ids) | Where-Object { $perm[$_] -and $perm[$_] -ne 'none' }) }
  $groups = New-Object System.Collections.ArrayList
  foreach ($g in $script:Groups) {
    $m = @(& $visible $g.members)   # @(): auch ein einzelnes Mitglied bleibt eine Liste
    $managed = $user.role -eq 'admin' -or ($user.role -eq 'groupadmin' -and $user.adminGroups -contains $g.id)
    # eigene Gruppen sieht der Gruppen-Admin auch leer (um Personen anzulegen)
    if ($m.Count -gt 0 -or ($managed -and $user.role -eq 'groupadmin')) {
      [void]$groups.Add([ordered]@{ id = $g.id; name = $g.name; members = $m; managed = [bool]$managed })
    }
  }
  $mine = New-Object System.Collections.ArrayList
  foreach ($g in @($user.myGroups)) {
    $m = @(& $visible (Get-Prop $g 'members'))
    [void]$mine.Add([ordered]@{ id = (Get-Prop $g 'id'); name = (Get-Prop $g 'name'); members = $m })
  }
  return [ordered]@{ version = $script:Version; app = (Get-AppVersion); holidayRegion = $script:Region; persons = $persons
                     categories = $script:Categories; groups = $groups; myGroups = $mine
                     schoolHolidays = $script:School; entries = $entries }
}

function Find-EntryIndex([string]$id) {
  for ($i = 0; $i -lt $script:Entries.Count; $i++) { if ($script:Entries[$i].id -eq $id) { return $i } }
  return -1
}

function Assert-Admin($user) { if ($user.role -ne 'admin') { throw (New-HttpError 403 'Nur für Administratoren.') } }
function Assert-Editor($user) { if ($user.role -eq 'display') { throw (New-HttpError 403 'Die Anzeige darf nichts ändern.') } }

function Set-UserPerms($u, $perms) {
  $clean = [ordered]@{}
  if ($perms) {
    foreach ($p in $script:Persons) {
      $v = Get-Prop $perms $p.id
      if ($v -eq 'edit' -and $u.role -eq 'display') { $v = 'view' }   # Anzeige darf nie bearbeiten
      if ($v -eq 'view' -or $v -eq 'edit') { $clean[$p.id] = $v }
    }
  }
  $u.perms = $clean
}

function Assert-UserManager($user) {
  if ($user.role -ne 'admin' -and $user.role -ne 'groupadmin') { throw (New-HttpError 403 'Keine Berechtigung.') }
}
# Darf ein Gruppen-Admin diesen Zugang verwalten (Passwort, Link, Löschen)? Nur selbst angelegte Benutzer/Anzeigen.
function Test-OwnsUser($user, $target) {
  return ($user.role -eq 'admin') -or ($target.createdBy -eq $user.id -and ($target.role -eq 'user' -or $target.role -eq 'display'))
}
function Set-AdminGroups($u, $ids) {
  $valid = @($script:Groups | ForEach-Object { $_.id })
  $u.adminGroups = @(@($ids) | Where-Object { $valid -contains $_ } | Select-Object -Unique)
}
# Gruppen-Admin: nur Rechte für seine Personen ändern, alle anderen Rechte des Benutzers bleiben
function Merge-ManagedPerms($user, $target, $perms) {
  $managed = Get-ManagedIds $user
  $tmp = [ordered]@{ role = $target.role; perms = $null }
  Set-UserPerms $tmp $perms
  $merged = [ordered]@{}
  foreach ($k in @($target.perms.Keys)) { if ($managed -notcontains $k) { $merged[$k] = $target.perms[$k] } }
  foreach ($k in @($tmp.perms.Keys)) { if ($managed -contains $k) { $merged[$k] = $tmp.perms[$k] } }
  $target.perms = $merged
}
function Get-UserForManager($user, $u) {
  $o = Get-PublicUser $u
  if ($user.role -eq 'groupadmin') {
    $managed = Get-ManagedIds $user
    $p = [ordered]@{}; foreach ($k in @($u.perms.Keys)) { if ($managed -contains $k) { $p[$k] = $u.perms[$k] } }
    $o.perms = $p
  }
  $o.managed = [bool](Test-OwnsUser $user $u)
  return $o
}

function Test-UserName([string]$name) { return ($name -match '^[\p{L}\p{N}._ -]{1,30}$') }

function Invoke-Request($ctx) {
  $req = $ctx.Request
  $path = $req.Url.AbsolutePath
  $method = $req.HttpMethod

  # ---- Seite
  if ($method -eq 'GET' -and ($path -eq '/' -or $path -eq '/index.html')) {
    $bytes = [IO.File]::ReadAllBytes((Join-Path $WebRoot 'index.html'))
    Send-Response $ctx 200 'text/html; charset=utf-8' $bytes $null
    return
  }
  if (-not $path.StartsWith('/api/')) { Send-Json $ctx 404 @{ error = 'Nicht gefunden.' } $null; return }

  # Schutz gegen Anfragen von fremden Seiten (CSRF): Änderungen nur mit JSON-Header
  if ($method -ne 'GET' -and $req.ContentType -notlike 'application/json*') {
    throw (New-HttpError 415 'Erwartet JSON.')
  }

  # ---- Anmeldung (ohne Sitzung)
  if ($path -eq '/api/login' -and $method -eq 'POST') {
    $body = Read-Body $req
    $key = Get-Prop $body 'key'
    if ($key) {
      $u = $null
      foreach ($x in $script:Users) { if ($x.role -eq 'display' -and $x.key -and $x.key -ceq [string]$key) { $u = $x } }
      if (-not $u) { Start-Sleep -Milliseconds 500; throw (New-HttpError 401 'Anzeige-Link ungültig.') }
      Send-Json $ctx 200 @{ user = (Get-PublicUser $u) } (New-Session $u)
      return
    }
    $name = Get-CleanText (Get-Prop $body 'name') 60
    $pw = [string](Get-Prop $body 'password')
    $fk = $name.ToLowerInvariant()
    $f = $script:Fails[$fk]
    if ($f -and $f.until -gt (Get-Date)) { throw (New-HttpError 429 'Zu viele Fehlversuche. Bitte 1 Minute warten.') }
    $fail = {
      param([string]$why, [int]$status, [string]$msg)
      if (-not $f) { $f = @{ n = 0; until = (Get-Date) }; $script:Fails[$fk] = $f }
      $f.n++
      if ($f.n -ge 5) { $f.until = (Get-Date).AddMinutes(1); $f.n = 0 }
      Write-Log "Anmeldung fehlgeschlagen: $name ($($req.RemoteEndPoint.Address)) – $why"
      throw (New-HttpError $status $msg)
    }
    $local = Find-UserByName $name
    # Lokale Anmeldung: mit AD nur noch der Notzugang "admin"; ohne AD alle lokalen Konten
    $localAllowed = $local -and $local.source -ne 'ad' -and $local.role -ne 'display' -and $local.hash -and
                    (-not $UseAD -or $local.name -ieq 'admin')
    if ($localAllowed) {
      if (-not (Test-UserPassword $local $pw)) { & $fail 'Passwort falsch' 401 'Benutzername oder Passwort falsch.' }
      $u = $local
    } elseif (-not $UseAD) {
      & $fail 'unbekannt' 401 'Benutzername oder Passwort falsch.'
    } else {
      try { $a = Test-AdLogin $name $pw }
      catch {
        Write-Log "AD nicht erreichbar bei Anmeldung von ${name}: $($_.Exception.Message)"
        throw (New-HttpError 503 'Die Windows-Anmeldung (Active Directory) ist gerade nicht erreichbar. Bitte später erneut versuchen.')
      }
      if (-not $a.ok) { & $fail 'AD: Name/Passwort falsch' 401 'Benutzername oder Passwort falsch.' }
      if (-not $a.enabled) { & $fail 'AD-Konto gesperrt' 403 'Dieses Windows-Konto ist gesperrt.' }
      if (-not $a.user) { & $fail "nicht in $AdNutzerGruppe/$AdAdminGruppe" 403 "Kein Zugriff: Das Konto ist nicht in der Gruppe $AdNutzerGruppe. Bitte bei der IT freischalten lassen." }
      # Kalender-Konto zum AD-Konto: per SID, sonst ein gleichnamiges altes lokales Konto übernehmen, sonst neu
      $u = $script:Users | Where-Object { $_.source -eq 'ad' -and $_.sid -eq $a.sid } | Select-Object -First 1
      if (-not $u) {
        $u = $script:Users | Where-Object { $_.name -ieq $a.sam -and $_.name -ine 'admin' -and $_.role -ne 'display' } | Select-Object -First 1
        if ($u) { Write-Log "Lokales Konto $($u.name) wird mit dem AD-Konto verknüpft." }
        else {
          $u = [ordered]@{ id = 'u' + (New-RandomHex 6); name = $a.sam; role = 'user'; salt = $null; hash = $null; iterations = $null
                           mustChange = $false; key = $null; perms = [ordered]@{}; adminGroups = @(); myGroups = @(); createdBy = $null
                           source = 'ad'; sid = $a.sid }
          [void]$script:Users.Add($u)
          Write-Log "Neues AD-Konto im Kalender: $($a.sam)"
        }
      }
      $u.source = 'ad'; $u.sid = $a.sid; $u.name = $a.sam
      $u.salt = $null; $u.hash = $null; $u.iterations = $null; $u.mustChange = $false
      Update-AdRole $u $a
      Save-Users
    }
    $script:Fails.Remove($fk)
    Write-Log "Anmeldung: $($u.name)$(if ($u.source -eq 'ad') { ' (AD)' })"
    Send-Json $ctx 200 @{ user = (Get-PublicUser $u) } (New-Session $u)
    return
  }
  # Anmeldeart für das Anmeldefenster (ohne Sitzung abrufbar)
  if ($path -eq '/api/info' -and $method -eq 'GET') {
    Send-Json $ctx 200 @{ ad = $UseAD; userGroup = $AdNutzerGruppe; adminGroup = $AdAdminGruppe } $null
    return
  }

  $token = $null
  $user = Get-SessionUser $req ([ref]$token)
  if (-not $user) { throw (New-HttpError 401 'Bitte anmelden.') }

  if ($path -eq '/api/logout' -and $method -eq 'POST') {
    $script:Sessions.Remove($token)
    Send-Json $ctx 200 @{ ok = $true } 'kal=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0'
    return
  }
  if ($path -eq '/api/me' -and $method -eq 'GET') { Send-Json $ctx 200 @{ user = (Get-PublicUser $user) } $null; return }

  if ($path -eq '/api/password' -and $method -eq 'POST') {
    Assert-Editor $user
    if ($user.source -eq 'ad') { throw (New-HttpError 400 'Das Passwort deines Windows-Kontos änderst du wie gewohnt in Windows (Strg+Alt+Entf).') }
    $body = Read-Body $req
    $old = [string](Get-Prop $body 'old'); $new = [string](Get-Prop $body 'new')
    if (-not (Test-UserPassword $user $old)) { throw (New-HttpError 400 'Das bisherige Passwort stimmt nicht.') }
    if ($new.Length -lt $MinPasswordLength) { throw (New-HttpError 400 "Das neue Passwort muss mindestens $MinPasswordLength Zeichen haben.") }
    if ($new -ceq $old) { throw (New-HttpError 400 'Das neue Passwort muss sich vom bisherigen unterscheiden.') }
    Set-UserPassword $user $new
    $user.mustChange = $false
    Save-Users
    Remove-UserSessions $user.id $token
    if ($user.name -ieq 'admin' -and (Test-Path $AdminPwFile)) { Remove-Item $AdminPwFile -Force }
    Write-Log "Passwort geändert: $($user.name)"
    Send-Json $ctx 200 @{ user = (Get-PublicUser $user) } $null
    return
  }

  if ($user.mustChange) { throw (New-HttpError 403 'Bitte zuerst das Passwort ändern.') }

  # ---- Kalender lesen
  if ($path -eq '/api/data' -and $method -eq 'GET') {
    $since = $req.QueryString['v']
    if ($since -and $since -eq [string]$script:Version) { Send-Json $ctx 200 @{ unchanged = $true; version = $script:Version; app = (Get-AppVersion) } $null; return }
    Send-Json $ctx 200 (Get-CalendarView $user) $null
    return
  }

  # ---- Einträge
  if ($path -eq '/api/entries' -and $method -eq 'POST') {
    Assert-Editor $user
    $body = Read-Body $req
    $id = Get-Prop $body 'id'
    if (-not (Test-Id $id)) { $id = 'e' + (New-RandomHex 6) }
    if ((Find-EntryIndex $id) -ge 0) { throw (New-HttpError 409 'Eintrag existiert bereits.') }
    $e = ConvertTo-Entry $body $id
    if (-not (Test-CanEditEntry $user $e)) { throw (New-HttpError 403 'Keine Berechtigung für diese Person(en).') }
    [void]$script:Entries.Add($e)
    Save-Calendar
    Send-Json $ctx 200 @{ entry = $e; version = $script:Version } $null
    return
  }
  if ($path -match '^/api/entries/([A-Za-z0-9_-]{1,40})$') {
    Assert-Editor $user
    $id = $Matches[1]
    $i = Find-EntryIndex $id
    if ($i -lt 0) { throw (New-HttpError 404 'Eintrag nicht gefunden (evtl. inzwischen gelöscht).') }
    if (-not (Test-CanEditEntry $user $script:Entries[$i])) { throw (New-HttpError 403 'Keine Berechtigung für diesen Eintrag.') }
    if ($method -eq 'PUT') {
      $e = ConvertTo-Entry (Read-Body $req) $id
      if (-not (Test-CanEditEntry $user $e)) { throw (New-HttpError 403 'Keine Berechtigung für diese Person(en).') }
      $script:Entries[$i] = $e
      Save-Calendar
      Send-Json $ctx 200 @{ entry = $e; version = $script:Version } $null
      return
    }
    if ($method -eq 'DELETE') {
      $script:Entries.RemoveAt($i)
      Save-Calendar
      Send-Json $ctx 200 @{ ok = $true; version = $script:Version } $null
      return
    }
  }

  # ---- Admin: Personen, Kategorien, Feiertage
  if ($path -eq '/api/settings' -and $method -eq 'PUT') {
    Assert-Admin $user
    $body = Read-Body $req
    $persons = ConvertTo-PersonList (Get-Prop $body 'persons')
    $cats = ConvertTo-CategoryList (Get-Prop $body 'categories')
    $region = Get-Prop $body 'holidayRegion'
    if ($Regions -notcontains $region) { $region = $script:Region }
    $newIds = @($persons | ForEach-Object { $_.id })
    $removed = @($script:Persons | Where-Object { $newIds -notcontains $_.id } | ForEach-Object { $_.id })
    # Kategorie-Farbe geändert → Einträge mit der alten Farbe mitziehen; gelöschte Kategorien lösen
    $oldCats = @{}; foreach ($c in $script:Categories) { $oldCats[$c.id] = $c }
    $newCats = @{}; foreach ($c in $cats) { $newCats[$c.id] = $c }
    foreach ($e in $script:Entries) {
      if (-not $e.Contains('category')) { continue }
      $nc = $newCats[$e.category]; $oc = $oldCats[$e.category]
      if (-not $nc) { $e.Remove('category'); continue }
      if ($oc -and $oc.color -ne $nc.color -and $e.color -eq $oc.color) { $e.color = $nc.color }
    }
    $script:Persons = $persons
    $script:Categories = $cats
    $script:Region = $region
    $script:Groups = ConvertTo-GroupList (Get-Prop $body 'groups')   # nach den Personen prüfen
    $script:School = ConvertTo-SchoolList (Get-Prop $body 'schoolHolidays')
    foreach ($u in $script:Users) { if ($u.role -eq 'groupadmin') { Set-AdminGroups $u $u.adminGroups } }
    Remove-PersonReferences $removed
    Save-Calendar
    Write-Log "Einstellungen geändert von $($user.name)"
    Send-Json $ctx 200 @{ ok = $true; version = $script:Version } $null
    return
  }
  if ($path -eq '/api/export' -and $method -eq 'GET') {
    Assert-Admin $user
    Send-Json $ctx 200 ([ordered]@{ holidayRegion = $script:Region; persons = $script:Persons; categories = $script:Categories
                                    groups = $script:Groups; schoolHolidays = $script:School; entries = $script:Entries }) $null
    return
  }
  if ($path -eq '/api/import' -and $method -eq 'POST') {
    Assert-Admin $user
    $body = Read-Body $req
    $oldIds = @($script:Persons | ForEach-Object { $_.id })
    $skipped = 0
    Import-CalendarData $body ([ref]$skipped)
    $newIds = @($script:Persons | ForEach-Object { $_.id })
    Remove-PersonReferences @($oldIds | Where-Object { $newIds -notcontains $_ })
    Save-Calendar
    Write-Log "Import von $($user.name): $($script:Entries.Count) Einträge, $skipped übersprungen"
    Send-Json $ctx 200 @{ ok = $true; entries = $script:Entries.Count; skipped = $skipped; version = $script:Version } $null
    return
  }

  # ---- Persönliche Gruppen (sieht nur der Benutzer selbst)
  if ($path -eq '/api/mygroups' -and $method -eq 'PUT') {
    Assert-Editor $user
    $list = ConvertTo-GroupList (Get-Prop (Read-Body $req) 'groups')
    foreach ($g in $list) { $g.members = @($g.members | Where-Object { (Get-Perm $user $_) -ne 'none' }) }
    $user.myGroups = @($list)
    Save-Users
    $script:Version++   # damit andere geöffnete Fenster desselben Benutzers nachladen
    Send-Json $ctx 200 @{ ok = $true; version = $script:Version } $null
    return
  }

  # ---- Personen anlegen/ändern (Admin, Gruppen-Admin in seinen Gruppen)
  if ($path -eq '/api/persons' -and $method -eq 'POST') {
    Assert-UserManager $user
    $body = Read-Body $req
    $gid = Get-Prop $body 'groupId'
    $group = $script:Groups | Where-Object { $_.id -eq $gid } | Select-Object -First 1
    if ($user.role -eq 'groupadmin' -and (-not $group -or $user.adminGroups -notcontains $gid)) { throw (New-HttpError 403 'Nur in deinen eigenen Gruppen.') }
    if ($script:Persons.Count -ge 50) { throw (New-HttpError 400 'Höchstens 50 Personen.') }
    $name = Get-CleanText (Get-Prop $body 'name') 30
    if (-not $name) { throw (New-HttpError 400 'Bitte einen Namen eingeben.') }
    $color = Get-Prop $body 'color'; if (-not (Test-Color $color)) { $color = '#2563eb' }
    $p = [ordered]@{ id = 'p' + (New-RandomHex 6); name = $name; color = $color }
    [void]$script:Persons.Add($p)
    if ($group) { $group.members = @($group.members) + $p.id }
    Save-Calendar
    Write-Log "Person angelegt: $name (Gruppe $gid) von $($user.name)"
    Send-Json $ctx 200 @{ person = $p; version = $script:Version } $null
    return
  }
  if ($path -match '^/api/persons/([A-Za-z0-9_-]{1,40})$' -and $method -eq 'PUT') {
    Assert-UserManager $user
    $p = $script:Persons | Where-Object { $_.id -eq $Matches[1] } | Select-Object -First 1
    if (-not $p) { throw (New-HttpError 404 'Person nicht gefunden.') }
    if ((Get-Perm $user $p.id) -ne 'edit' -or ($user.role -eq 'groupadmin' -and (Get-ManagedIds $user) -notcontains $p.id)) {
      throw (New-HttpError 403 'Nur Personen deiner Gruppen.')
    }
    $body = Read-Body $req
    $name = Get-CleanText (Get-Prop $body 'name') 30
    if ($name) { $p.name = $name }
    $color = Get-Prop $body 'color'; if (Test-Color $color) { $p.color = $color }
    Save-Calendar
    Send-Json $ctx 200 @{ person = $p; version = $script:Version } $null
    return
  }
  if ($path -match '^/api/groups/([A-Za-z0-9_-]{1,40})/remove$' -and $method -eq 'POST') {
    Assert-UserManager $user
    $gid = $Matches[1]
    $group = $script:Groups | Where-Object { $_.id -eq $gid } | Select-Object -First 1
    if (-not $group) { throw (New-HttpError 404 'Gruppe nicht gefunden.') }
    if ($user.role -eq 'groupadmin' -and $user.adminGroups -notcontains $gid) { throw (New-HttpError 403 'Nur in deinen eigenen Gruppen.') }
    $pid2 = Get-Prop (Read-Body $req) 'personId'
    $group.members = @($group.members | Where-Object { $_ -ne $pid2 })
    Save-Calendar
    Send-Json $ctx 200 @{ ok = $true; version = $script:Version } $null
    return
  }

  # ---- Benutzerverwaltung (Admin; Gruppen-Admin eingeschränkt)
  if ($path -eq '/api/users' -and $method -eq 'GET') {
    Assert-UserManager $user
    $list = @($script:Users | Where-Object { $user.role -eq 'admin' -or (($_.role -eq 'user' -or $_.role -eq 'display') -and $_.id -ne $user.id) })
    Send-Json $ctx 200 @{ users = @($list | ForEach-Object { Get-UserForManager $user $_ }) } $null
    return
  }
  if ($path -eq '/api/users' -and $method -eq 'POST') {
    Assert-UserManager $user
    $body = Read-Body $req
    $name = Get-CleanText (Get-Prop $body 'name') 60
    $adAccount = $null
    if ($UseAD -and (Get-Prop $body 'role') -ne 'display') {
      # AD-Konto vorab eintragen, damit Rechte schon vor der ersten Anmeldung vergeben werden können
      try { $adAccount = Find-AdAccount 'name' $name } catch { throw (New-HttpError 503 'Active Directory nicht erreichbar.') }
      if (-not $adAccount.ok) { throw (New-HttpError 400 "Im Active Directory gibt es kein Konto '$name'.") }
      if ($script:Users | Where-Object { $_.source -eq 'ad' -and $_.sid -eq $adAccount.sid }) { throw (New-HttpError 400 'Dieses AD-Konto ist bereits eingetragen.') }
      $name = $adAccount.sam
    }
    if (-not (Test-UserName $name)) { throw (New-HttpError 400 'Benutzername: 1–30 Zeichen (Buchstaben, Ziffern, Punkt, Leerzeichen, - und _).') }
    if (Find-UserByName $name) { throw (New-HttpError 400 'Diesen Benutzernamen gibt es schon.') }
    $role = Get-Prop $body 'role'
    $allowed = $(if ($user.role -eq 'admin') { @('admin', 'groupadmin', 'user', 'display') } else { @('user', 'display') })
    if ($allowed -notcontains $role) { $role = 'user' }
    $u = [ordered]@{ id = 'u' + (New-RandomHex 6); name = $name; role = $role; salt = $null; hash = $null
                     iterations = $null; mustChange = $false; key = $null; perms = [ordered]@{}
                     adminGroups = @(); myGroups = @(); createdBy = $user.id; source = 'local'; sid = $null }
    if ($adAccount) {
      $u.source = 'ad'; $u.sid = $adAccount.sid
      if ($role -eq 'admin') { $role = 'user' }            # Admin kommt aus der AD-Gruppe
      if ($adAccount.admin) { $role = 'admin' }
      $u.role = $role
    } elseif ($role -eq 'display') {
      $u.key = New-RandomHex 24
    } else {
      $pw = [string](Get-Prop $body 'password')
      if ($pw.Length -lt $MinPasswordLength) { throw (New-HttpError 400 "Das Passwort muss mindestens $MinPasswordLength Zeichen haben.") }
      Set-UserPassword $u $pw
      $u.mustChange = $true
    }
    if ($user.role -eq 'groupadmin') { Merge-ManagedPerms $user $u (Get-Prop $body 'perms') }
    else { Set-UserPerms $u (Get-Prop $body 'perms') }
    if ($role -eq 'groupadmin') { Set-AdminGroups $u (Get-Prop $body 'adminGroups') }
    [void]$script:Users.Add($u)
    Save-Users
    Write-Log "Benutzer angelegt: $name ($role$(if ($adAccount) { ', AD' })) von $($user.name)"
    Send-Json $ctx 200 @{ user = (Get-UserForManager $user $u) } $null
    return
  }
  if ($path -match '^/api/users/([A-Za-z0-9_-]{1,40})(/password|/key)?$') {
    Assert-UserManager $user
    $target = Find-UserById $Matches[1]
    $action = $Matches[2]
    if (-not $target) { throw (New-HttpError 404 'Benutzer nicht gefunden.') }
    if ($user.role -eq 'groupadmin' -and (($target.role -ne 'user' -and $target.role -ne 'display') -or $target.id -eq $user.id)) {
      throw (New-HttpError 403 'Diesen Zugang darfst du nicht ändern.')
    }
    $body = Read-Body $req
    if (-not $action -and $method -eq 'PUT') {
      if ($user.role -eq 'groupadmin') {
        Merge-ManagedPerms $user $target (Get-Prop $body 'perms')     # nur Rechte für eigene Personen
      } else {
        $role = Get-Prop $body 'role'
        if ($role -and $role -ne $target.role) {
          if (@('admin', 'groupadmin', 'user') -notcontains $role -or $target.role -eq 'display') { throw (New-HttpError 400 'Diese Rollenänderung ist nicht möglich.') }
          if ($target.source -eq 'ad' -and ($role -eq 'admin' -or $target.role -eq 'admin')) {
            throw (New-HttpError 400 "Administrator wird man über die AD-Gruppe $AdAdminGruppe.")
          }
          if ($target.role -eq 'admin' -and (Get-AdminCount) -le 1) { throw (New-HttpError 400 'Es muss mindestens einen Administrator geben.') }
          $target.role = $role
        }
        Set-UserPerms $target (Get-Prop $body 'perms')
        if ($target.role -eq 'groupadmin') { Set-AdminGroups $target (Get-Prop $body 'adminGroups') } else { $target.adminGroups = @() }
      }
      Save-Users
      Send-Json $ctx 200 @{ user = (Get-UserForManager $user $target) } $null
      return
    }
    if (-not (Test-OwnsUser $user $target)) { throw (New-HttpError 403 'Nur selbst angelegte Zugänge.') }
    if ($action -eq '/password' -and $method -eq 'POST') {
      if ($target.role -eq 'display') { throw (New-HttpError 400 'Die Anzeige hat kein Passwort.') }
      if ($target.source -eq 'ad') { throw (New-HttpError 400 'AD-Passwörter werden im Active Directory zurückgesetzt.') }
      $pw = [string](Get-Prop $body 'password')
      if ($pw.Length -lt $MinPasswordLength) { throw (New-HttpError 400 "Das Passwort muss mindestens $MinPasswordLength Zeichen haben.") }
      Set-UserPassword $target $pw
      $target.mustChange = $true
      Save-Users
      Remove-UserSessions $target.id $(if ($target.id -eq $user.id) { $token } else { '' })
      Write-Log "Passwort zurückgesetzt: $($target.name) von $($user.name)"
      Send-Json $ctx 200 @{ user = (Get-UserForManager $user $target) } $null
      return
    }
    if ($action -eq '/key' -and $method -eq 'POST') {
      if ($target.role -ne 'display') { throw (New-HttpError 400 'Nur Anzeige-Zugänge haben einen Link.') }
      $target.key = New-RandomHex 24
      Save-Users
      Remove-UserSessions $target.id ''
      Send-Json $ctx 200 @{ user = (Get-UserForManager $user $target) } $null
      return
    }
    if (-not $action -and $method -eq 'DELETE') {
      if ($target.id -eq $user.id) { throw (New-HttpError 400 'Den eigenen Zugang kann man nicht löschen.') }
      if ($target.role -eq 'admin' -and (Get-AdminCount) -le 1) { throw (New-HttpError 400 'Es muss mindestens einen Administrator geben.') }
      $script:Users.Remove($target)
      Remove-UserSessions $target.id ''
      Save-Users
      Write-Log "Benutzer gelöscht: $($target.name) von $($user.name)"
      Send-Json $ctx 200 @{ ok = $true } $null
      return
    }
  }

  throw (New-HttpError 404 'Nicht gefunden.')
}

# ---------------------------------------------------------------- Start

$listener = New-Object System.Net.HttpListener
$listener.Prefixes.Add("http://+:$Port/")
try {
  $listener.Start()
} catch {
  Write-Log "Server konnte Port $Port nicht öffnen: $($_.Exception.Message)"
  Write-Log 'Tipp: als Administrator bzw. SYSTEM starten oder Port prüfen.'
  exit 1
}
Write-Log "Kalender-Server läuft: http://$([Environment]::MachineName):$Port/  (Daten: $DataDir)"

try {
  while ($listener.IsListening) {
    $task = $listener.GetContextAsync()
    while (-not $task.AsyncWaitHandle.WaitOne(500)) { }   # so bleibt Strg+C wirksam
    $ctx = $task.GetAwaiter().GetResult()
    try {
      Invoke-Request $ctx
    } catch {
      $msg = $_.Exception.Message
      $status = 500
      if ($msg -match '^(\d{3})\|(.*)$') { $status = [int]$Matches[1]; $msg = $Matches[2] }
      else { Write-Log "Fehler bei $($ctx.Request.HttpMethod) $($ctx.Request.Url.AbsolutePath): $msg" ; $msg = 'Interner Fehler.' }
      try { Send-Json $ctx $status @{ error = $msg } $null } catch { }
    } finally {
      try { $ctx.Response.Close() } catch { }
    }
  }
} finally {
  $listener.Stop()
  $listener.Close()
}
