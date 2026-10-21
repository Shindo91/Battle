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

### Automatische Updates aus GitHub (empfohlen)

Statt neue Versionen von Hand zu kopieren, holt sich der Server sie selbst aus dem Repository
(`https://github.com/Shindo91/Battle`, öffentlich – keine Zugangsdaten nötig, kein Git auf dem Server):

```powershell
powershell -ExecutionPolicy Bypass -File C:\Kalender\installieren.ps1 -Port 8080 -AutoUpdate -Branch main
```

- Legt zusätzlich die Aufgabe **„Kalender-Update“** an: täglich um 03:00 (`-UpdateUhrzeit 05:30` zum Ändern).
- **Sofort aktualisieren:** `Start-ScheduledTask Kalender-Update` – oder direkt
  `powershell -ExecutionPolicy Bypass -File C:\Kalender\aktualisieren.ps1 -Branch main`
- Ablauf: alle Dateien laden → prüfen (vollständig, PowerShell-Syntax fehlerfrei) → nur bei Erfolg ersetzen.
  Alte Versionen landen in `daten\backup\code-<Datum>`. Hat sich das Server-Skript geändert, startet der
  Server neu; eine neue Seite laden alle Browser und der Monitor innerhalb von ~20 Sekunden von selbst.
- Die Kalenderdaten (`daten`) werden dabei nie verändert. Protokoll: `daten\update.log`.
- Der Server braucht Zugriff auf `https://raw.githubusercontent.com`. Bei einem Proxy: `-Proxy http://proxy:8080`.
- Solange die Änderungen noch nicht im Branch `main` sind, den Arbeits-Branch angeben, z. B.
  `-Branch claude/nifty-curie-guwnkh`.
- **Wichtig:** Damit läuft Code aus dem Repository auf dem Server (als SYSTEM). Schreibrechte auf das
  Repository daher nur vertrauenswürdigen Personen geben; `main` am besten nur über geprüfte Pull Requests ändern.

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
| Update | automatisch (siehe oben) oder `Start-ScheduledTask Kalender-Update`. Von Hand: neue `index.html` hineinkopieren genügt (Browser laden selbst neu); bei neuer `kalender-server.ps1` zusätzlich Server neu starten |
| Update zurücknehmen | Dateien aus `daten\backup\code-<Datum>` zurückkopieren, Server neu starten |
| Admin-Passwort vergessen | `powershell -ExecutionPolicy Bypass -File C:\Kalender\kalender-server.ps1 -ResetAdmin` → neues Passwort in `ERSTES-ADMIN-PASSWORT.txt` |
| Anderer Port | `installieren.ps1 -Port 8090` erneut ausführen |
| Entfernen | `installieren.ps1 -Entfernen` (Daten bleiben erhalten; entfernt auch die Update-Aufgabe) |

## 6. Sicherheit

- Passwörter werden nur als Hash gespeichert (PBKDF2-SHA256, 120 000 Runden, mit Salt).
- Nach 5 falschen Passwörtern ist die Anmeldung für diesen Namen 1 Minute gesperrt.
- Anmeldungen laufen nach 12 Stunden ohne Nutzung ab; der Anzeige-Zugang bleibt angemeldet.
- Alle Rechte werden **auf dem Server** geprüft, nicht nur im Browser.
- Die Verbindung ist **HTTP** (unverschlüsselt) – im internen Netz üblich, aber Passwörter gehen im
  Klartext über das Netzwerk. Für HTTPS kann ein Zertifikat an den Port gebunden werden
  (`netsh http add sslcert …`) – bei Bedarf passe ich den Server dafür an.
- Der Ordner `daten` enthält die Passwort-Hashes: Zugriff nur für Administratoren.
