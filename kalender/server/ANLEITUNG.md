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

### Updates – nur auf Befehl

Neue Versionen werden **nur eingespielt, wenn du es anstößt**. Es gibt zwei Wege:

**A) Server ohne Internet (Normalfall): vom Arbeitsplatz-PC aus**

`vom-pc-aktualisieren.ps1` einmal von GitHub auf den PC laden (z. B. nach `C:\Kalender-Update`), dann:

```powershell
powershell -ExecutionPolicy Bypass -File C:\Kalender-Update\vom-pc-aktualisieren.ps1 -Server GASWP003010
```

1. zeigt die neueste Version (Commit, Beschreibung, Autor, Datum, Link zu den Änderungen),
2. lädt die Dateien aus genau diesem Commit und prüft sie,
3. vergleicht mit dem Server, listet die Änderungen und fragt **„Jetzt aktualisieren? (J/N)“**,
4. kopiert sie nach `\\GASWP003010\C$\Kalender\neu` und spielt sie per PowerShell-Remoting ein
   (Sicherung nach `daten\backup\code-<Datum>`, Austausch, ggf. Server-Neustart).
   Klappt der Fernzugriff nicht, zeigt das Skript den einen Befehl, den du auf dem Server eingibst.

**Kommt PowerShell nicht ins Internet** (eigener DNS, Internet nur über Proxy im Browser), den Stand im
Browser holen: GitHub → Branch wählen → grüner Button **Code → Download ZIP**, dann:

```powershell
powershell -ExecutionPolicy Bypass -File C:\Kalender-Update\vom-pc-aktualisieren.ps1 -Server GASWP003010 -Zip "$env:USERPROFILE\Downloads\Battle-claude-nifty-curie-guwnkh.zip"
```

(Alternativ den Proxy angeben: `-Proxy http://proxy.firma.local:8080`.)

Voraussetzungen: Dein Windows-Konto ist Administrator auf dem Server
(sonst `-Credential (Get-Credential DOMÄNE\admin)` anhängen). Weitere Optionen: `-Version <Commit>`,
`-Pfad D:\Kalender`, `-Ja` (ohne Rückfrage).

**B) Server mit Internetzugang:** direkt auf dem Server

```powershell
powershell -ExecutionPolicy Bypass -File C:\Kalender\aktualisieren.ps1
```

(gleiche Anzeige und Rückfrage; Optionen `-Version`, `-Ja`, `-Proxy http://proxy:8080`).

In beiden Fällen bleiben die Kalenderdaten (`daten`) unverändert. Installierte Version:
`daten\installierte-version.txt`, Protokoll: `daten\update.log`. Eine neue Seite laden alle Browser
und der Monitor selbst nach.

## 3. Kalender einrichten (im Browser als admin)

1. **Einstellungen** → Personen benennen/anlegen, **Gruppen** bilden, Kategorien, Bundesland für Feiertage,
   **Schulferien** (iCal-Datei `.ics` des Bundeslands importieren oder von Hand eintragen). Jeder Benutzer blendet
   sie oben mit **🏖 Ferien** für sich ein. Jeder Benutzer kann außerdem **eigene Gruppen** anlegen, die nur er sieht.
   (Gruppen dienen nur der Auswahl; die Rechte gelten weiterhin je Person. Benutzer sehen in einer Gruppe
   nur die Mitglieder, für die sie freigegeben sind.)
2. Vorhandene Einträge aus der Einzelplatz-Version übernehmen: dort *Einstellungen → Exportieren*,
   hier *Einstellungen → Importieren*.
3. **Einstellungen → Benutzer verwalten**:
   - **Benutzer** anlegen (Name + Start-Passwort). Beim ersten Anmelden muss jeder sein eigenes Passwort setzen.
   - Je Person festlegen: **–** (nicht sichtbar), **sehen** oder **bearbeiten**.
   - **Administrator**: sieht und bearbeitet alles und verwaltet Benutzer.
   - **Gruppen-Admin**: Der Admin ordnet ihm in der Benutzerliste Gruppen zu („verwaltet: …“). Er sieht und
     bearbeitet alle Personen dieser Gruppen, legt dort über *Einstellungen → Meine Gruppen verwalten* neue
     Personen an (umbenennen, Farbe, aus der Gruppe nehmen), vergibt anderen Benutzern Rechte für diese
     Personen und legt selbst Benutzer/Anzeigen an (Passwort zurücksetzen/Löschen nur bei selbst angelegten).
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
| Update | `vom-pc-aktualisieren.ps1` am PC (siehe oben). Oder von Hand: neue `index.html` hineinkopieren genügt (Browser laden selbst neu); bei neuer `kalender-server.ps1` zusätzlich Server neu starten |
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
