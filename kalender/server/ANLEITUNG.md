# Kalender im Intranet – Einrichtung

Der Kalender läuft als kleiner Webserver auf einem Windows Server (auch **Server Core** ohne GUI).
Bearbeitet wird im Browser am Arbeitsplatz, angezeigt am Monitor über einen ThinPro-Client.

```
Windows-Arbeitsplätze (Edge/Chrome) ──┐
                                      ├──▶  Windows Server: kalender-server.ps1  ──▶  Ordner "daten"
ThinPro-Client am Monitor (Kiosk) ────┘     (liefert Seite, prüft Rechte, speichert)     + tägliche Backups
```

Voraussetzungen: Windows Server 2016 oder neuer (getestet für 2025 Core), Windows PowerShell 5.1
(vorinstalliert). Es muss **nichts installiert** werden.

---

## 1. Dateien auf den Server kopieren

Alle drei Dateien in **einen Ordner**, z. B. `C:\Kalender`:

```
C:\Kalender\index.html
C:\Kalender\kalender-server.ps1
C:\Kalender\installieren.ps1
C:\Kalender\aktualisieren.ps1
```

(Alternativ dürfen die beiden Skripte auch in einem Unterordner `server` liegen. Die Daten landen
immer im Ordner `daten` neben den Skripten.)

Vom Arbeitsplatz aus z. B. über die Admin-Freigabe `\\SERVERNAME\c$\Kalender`.

## 2. Einrichten (auf dem Server, PowerShell als Administrator)

```powershell
powershell -ExecutionPolicy Bypass -File C:\Kalender\installieren.ps1 -Port 8080
```

Das Skript
- legt die geplante Aufgabe **„Kalender-Server“** an (Start beim Hochfahren als SYSTEM, Neustart bei Absturz),
- öffnet den Port in der **Windows-Firewall** (Profile Domäne/Privat),
- startet den Server und zeigt das **erste Admin-Passwort** an.

Das Passwort steht auch in `C:\Kalender\daten\ERSTES-ADMIN-PASSWORT.txt`. Die Datei wird
gelöscht, sobald der Admin sein Passwort geändert hat.

Test vom Arbeitsplatz: `http://SERVERNAME:8080/` öffnen → mit `admin` und dem Passwort anmelden →
eigenes Passwort vergeben.

### Updates aus GitHub – nur auf Befehl

Neue Versionen holt sich der Server **nur, wenn du es anstößt** – nie automatisch:

```powershell
powershell -ExecutionPolicy Bypass -File C:\Kalender\aktualisieren.ps1
```

Das Skript
1. zeigt die neueste Version des Branches (Commit, Beschreibung, Autor, Datum, Link zu den Änderungen),
2. lädt alle Dateien genau aus diesem Commit und prüft sie (vollständig, PowerShell-Syntax fehlerfrei),
3. listet, welche Dateien sich ändern würden, und fragt **„Jetzt aktualisieren? (J/N)“**,
4. sichert die alten Dateien nach `daten\backup\code-<Datum>`, ersetzt sie und startet den Server neu,
   falls sich das Server-Skript geändert hat. Eine neue Seite laden alle Browser und der Monitor selbst nach.

| Wunsch | Befehl |
|---|---|
| Bestimmte Version (Commit) einspielen | `aktualisieren.ps1 -Version 3d93795` |
| Ohne Rückfrage | `aktualisieren.ps1 -Ja` |
| Anderer Branch | `aktualisieren.ps1 -Branch main` |
| Über Proxy | `aktualisieren.ps1 -Proxy http://proxy:8080` |

Die Kalenderdaten (`daten`) werden nie verändert. Die installierte Version steht in
`daten\installierte-version.txt`, das Protokoll in `daten\update.log`. Der Server braucht dafür
Zugriff auf `https://api.github.com` und `https://raw.githubusercontent.com`.

## 3. Kalender einrichten (im Browser als admin)

1. **Einstellungen** → Personen benennen/anlegen, **Gruppen** bilden, Kategorien, Bundesland für Feiertage.
   (Gruppen dienen nur der Auswahl; die Rechte gelten weiterhin je Person. Benutzer sehen in einer Gruppe
   nur die Mitglieder, für die sie freigegeben sind.)
2. Vorhandene Einträge aus der Einzelplatz-Version übernehmen: dort *Einstellungen → Exportieren*,
   hier *Einstellungen → Importieren*.
3. **Einstellungen → Benutzer verwalten**:
   - **Benutzer** anlegen (Name + Start-Passwort). Beim ersten Anmelden muss jeder sein eigenes Passwort setzen.
   - Je Person festlegen: **–** (nicht sichtbar), **sehen** oder **bearbeiten**.
   - **Administrator**: sieht und bearbeitet alles und verwaltet Benutzer.
   - **Anzeige (Monitor)** anlegen, Personen auf „sehen“ stellen und den **Link** kopieren.
   - **Passwort zurücksetzen**: neues Start-Passwort vergeben – der Benutzer muss es beim nächsten
     Anmelden ändern, bestehende Anmeldungen werden beendet.

Regeln für Einträge: Ein Eintrag mit mehreren Personen kann nur geändert werden, wer bei **allen**
diesen Personen „bearbeiten“ darf. Personen, die jemand nicht sehen darf, werden ihm auch bei
gemeinsamen Einträgen nicht angezeigt.

## 4. ThinPro-Client am Monitor

(Bezeichnungen je nach ThinPro-Version leicht unterschiedlich.)

1. **Verbindung anlegen**: Typ *Web Browser*, URL = **Anzeige-Link** aus der Benutzerverwaltung
   (`http://SERVERNAME:8080/?key=…`).
2. Optionen: **Kiosk-/Vollbildmodus**, **automatisch beim Start verbinden**, **bei Beenden neu verbinden**.
3. **Energieverwaltung**: Bildschirm ausschalten = *nie*, Bildschirmschoner *aus*.

Der Monitor meldet sich über den Link selbst an (auch nach Neustarts), aktualisiert sich alle
20 Sekunden und springt nach 5 Minuten ohne Bedienung zurück zur Jahresansicht mit „Heute“
(einstellbar in den Einstellungen am Monitor-Browser). Bei Verbindungsproblemen erscheint unten
„Keine Verbindung zum Server“, der letzte Stand bleibt sichtbar.

Link neu erzeugen (z. B. wenn er weitergegeben wurde): *Benutzer verwalten → Neuer Link* – der alte
Link funktioniert dann sofort nicht mehr.

## 5. Betrieb

| Aufgabe | Befehl / Ort |
|---|---|
| Status | `Get-ScheduledTask Kalender-Server` |
| Neu starten | `Stop-ScheduledTask Kalender-Server; Start-ScheduledTask Kalender-Server` |
| Protokoll | `C:\Kalender\daten\server.log` |
| Daten | `C:\Kalender\daten\kalender.json`, `benutzer.json` |
| Sicherungen | `C:\Kalender\daten\backup\` – täglich, 60 Tage aufbewahrt. Den Ordner `daten` zusätzlich in die normale Server-Sicherung aufnehmen. |
| Update | `aktualisieren.ps1` (siehe oben). Oder von Hand: neue `index.html` hineinkopieren genügt (Browser laden selbst neu); bei neuer `kalender-server.ps1` zusätzlich Server neu starten |
| Update zurücknehmen | Dateien aus `daten\backup\code-<Datum>` zurückkopieren, Server neu starten |
| Admin-Passwort vergessen | `powershell -ExecutionPolicy Bypass -File C:\Kalender\kalender-server.ps1 -ResetAdmin` → neues Passwort in `ERSTES-ADMIN-PASSWORT.txt` |
| Anderer Port | `installieren.ps1 -Port 8090` erneut ausführen |
| Entfernen | `installieren.ps1 -Entfernen` (Daten bleiben erhalten) |

## 6. Sicherheit

- Passwörter werden nur als Hash gespeichert (PBKDF2-SHA256, 120 000 Runden, mit Salt).
- Nach 5 falschen Passwörtern ist die Anmeldung für diesen Namen 1 Minute gesperrt.
- Anmeldungen laufen nach 12 Stunden ohne Nutzung ab; der Anzeige-Zugang bleibt angemeldet.
- Alle Rechte werden **auf dem Server** geprüft, nicht nur im Browser.
- Die Verbindung ist **HTTP** (unverschlüsselt) – im internen Netz üblich, aber Passwörter gehen im
  Klartext über das Netzwerk. Für HTTPS kann ein Zertifikat an den Port gebunden werden
  (`netsh http add sslcert …`) – bei Bedarf passe ich den Server dafür an.
- Der Ordner `daten` enthält die Passwort-Hashes: Zugriff nur für Administratoren.

## 7. Repository schützen

Mit `aktualisieren.ps1` läuft Code aus dem Repository auf dem Server. Deshalb:

1. **Wer darf schreiben?** GitHub → Repository → *Settings → Collaborators*: nur Personen, denen du
   vertraust. Alle anderen können das öffentliche Repository nur lesen bzw. Vorschläge (Pull Requests)
   machen, die du selbst annehmen musst.
2. **Konto absichern:** *Settings → Password and authentication* → **Zwei-Faktor-Authentifizierung**
   einschalten. Ungenutzte Zugriffstokens (*Developer settings*) und App-Zugriffe
   (*Applications*) prüfen und entfernen.
3. **Branch schützen:** *Settings → Rules → Rulesets → New branch ruleset* für den Branch, aus dem
   aktualisiert wird: „Restrict deletions“, „Block force pushes“, „Require a pull request before merging“.
   Dann kann niemand – auch keine App – unbemerkt direkt darauf schreiben.
4. **Vor jedem Update prüfen:** `aktualisieren.ps1` zeigt Commit, Autor und den Link zu den Änderungen,
   bevor etwas ersetzt wird. Wer ganz sicher gehen will, spielt mit `-Version <Commit>` gezielt eine
   geprüfte Version ein.
5. Im Repository liegen **keine Daten oder Passwörter** – die bleiben im Ordner `daten` auf dem Server.
