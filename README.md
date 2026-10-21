# Trainer Battle

Android-Spiel (Kotlin + Jetpack Compose): Trainer lassen ihre Kreaturen in rundenbasierten Kämpfen
gegeneinander antreten, sammeln Erfahrung, leveln auf und entwickeln ihre Kreaturen weiter.
Monetarisiert über **AdMob** (Belohnungsvideos, Interstitials, Banner) und **Google Play Billing**
(Edelstein-Pakete, „Werbung entfernen“).

## Spielinhalte

| Bereich | Inhalt |
|---|---|
| Kreaturen | 17 eigene Kreaturen in 7 Typen (Normal, Feuer, Wasser, Natur, Blitz, Erde, Luft), 7 Entwicklungslinien, seltene/epische Kreaturen |
| Kampf | Rundenbasiert, Typ-Effektivität, STAB-Bonus, Volltreffer, Genauigkeit, Statuswerte-Stufen, Heilung, Wechseln, Aufgeben |
| Leveln | EP-Kurve bis Lv. 100, neue Attacken beim Levelaufstieg, Entwicklungen, EP-Teiler für Teammitglieder auf der Bank |
| Kampagne | 12 Trainer mit steigender Schwierigkeit (KI wird schlauer), Arena-Leiter und Champion |
| Arena | Endlos generierte Rivalen, Wertungssystem mit Rängen Bronze bis Diamant |
| Wirtschaft | Münzen, Edelsteine, Energie (1 pro Kampf, Regeneration alle 12 Min.), tägliche Login-Belohnung mit Serie |
| Shop | Levelbonbon, Kreatur-Ei, Seltenes Ei, Energie auffüllen, Münzbeutel |

## Monetarisierung

| Quelle | Wo | Umsetzung |
|---|---|---|
| Belohnungsvideo | Münzen nach Sieg verdoppeln, +3 Energie | `AdsManager.showRewarded` |
| Interstitial | Nach jedem 3. Kampf (nicht bei „Werbefrei“) | `AdsManager.onBattleFinished` |
| Banner | Startbildschirm (nicht bei „Werbefrei“) | `MainActivity.BannerAd` |
| Edelsteine | `gems_small` (100), `gems_medium` (550), `gems_large` (1200) | Verbrauchbare In-App-Produkte |
| Werbefrei | `remove_ads` | Nicht verbrauchbares In-App-Produkt |

DSGVO-Einwilligung wird über Googles User Messaging Platform (UMP) eingeholt. Der Startbildschirm
zeigt bei Bedarf den Button „Datenschutz-Einstellungen“.

## Projektstruktur

```
core/   Reine Kotlin-Spiellogik (Kampf, Leveln, Wirtschaft) – ohne Android, mit Unit-Tests
app/    Android-App: Compose-UI, Speicherstand, AdMob, Play Billing
```

Die gesamte Spiellogik liegt in `core` und ist unabhängig testbar. Später kann ein Server
(für Online-PvP) dieselbe Logik wiederverwenden.

## Bauen

- **Android Studio** (empfohlen): Projekt öffnen, `app` starten.
- **Kommandozeile**: `./gradlew :core:test :app:assembleDebug`
- **CI**: GitHub Actions baut bei jedem Push die Debug-APK (unter *Actions → Artifacts*).

Ohne Android SDK (keine `local.properties`, kein `ANDROID_HOME`) wird nur `core` gebaut.

## Checkliste bis zum Play Store

1. **AdMob-Konto** anlegen, App registrieren, eigene IDs in `app/build.gradle.kts` (Release-Block) eintragen.
   Die aktuellen IDs sind Googles offizielle Test-IDs.
2. **Play Console** (einmalig 25 $): App anlegen, In-App-Produkte `gems_small`, `gems_medium`,
   `gems_large` und `remove_ads` mit genau diesen IDs erstellen.
3. **Signierschlüssel** erzeugen und `keystore.properties` im Projektstamm anlegen (nicht committen):
   ```
   storeFile=release.jks
   storePassword=…
   keyAlias=…
   keyPassword=…
   ```
   Dann `./gradlew :app:bundleRelease` und die `.aab` hochladen.
4. **Datenschutzerklärung** (Pflicht bei Werbung und Käufen) online stellen und in der Play Console verlinken.
5. **Formular zur Datensicherheit** und **Einstufung des Inhalts (IARC)** ausfüllen. Bei Lootbox-artigen
   Eiern die Wahrscheinlichkeiten angeben (Seltenes Ei: 15 % episch, 85 % selten).
6. **Grafiken**: App-Icon (512×512), Feature-Grafik (1024×500) und Screenshots. Die Kreaturen sind
   aktuell Emoji-Platzhalter.
7. Neue private Entwicklerkonten müssen vor der Veröffentlichung einen **geschlossenen Test** mit
   mindestens 12 Testern über 14 Tage durchführen.

## Nächste Schritte

- **Online-PvP**: Backend (z. B. Firebase oder ein kleiner Kotlin-Server mit `core`) für echte Kämpfe
  zwischen Spielern und Ranglisten. Die Arena simuliert das bisher mit generierten Rivalen.
- **Serverseitige Kaufprüfung**: Käufe vor der Gutschrift auf einem Server prüfen (siehe TODO in `BillingManager`).
- **Cloud-Speicherstand** (z. B. Play Games Services), damit Fortschritt beim Gerätewechsel erhalten bleibt.
- Echte Grafiken und Animationen, Sound, mehr Kreaturen und Attacken, Events, Battle Pass.

---

# Jahreskalender (`kalender/`)

Eigenständiger Jahreskalender für einen Monitor – unabhängig vom Spiel. Einfach
`kalender/index.html` im Browser öffnen (z. B. Chrome/Edge im Vollbild mit `F11` bzw. dem ⛶-Button).
Keine Installation, kein Server.

- **Jahresansicht** auf einem Bildschirm: 12 Monate untereinander, je Monat eine Zeile mit Wochentag
  und Datum (z. B. „Do 01.01.“) und darunter eine Zeile pro Person. Wochenenden grau, heutiger Tag rot.
- **Ansichten** oben links umschalten: **Woche**, **Monat**, **Quartal**, **Halbjahr**, **Jahr** (auch per
  Taste `W`/`M`/`Q`/`H`/`J`, blättern mit ◀ ▶ oder den Pfeiltasten). Klick auf einen Monatsnamen öffnet die
  Monatsansicht, Klick auf einen Wochentag die Wochenansicht. Die gewählte Ansicht bleibt gespeichert.
- **Einträge** mit Bezeichnung, Zeitraum *von – bis*, Farbe (Palette oder eigene Farbe) und
  einer oder mehreren Personen. Schnellauswahl *1–4 Wochen* setzt das Bis-Datum automatisch.
  Anlegen über „+ Eintrag“, Klick auf einen Tag oder **mit der Maus über mehrere Tage ziehen**
  (Von/Bis und Person werden übernommen);
  Klick auf einen Balken zum Bearbeiten/Löschen. Überlappende Einträge werden gestapelt.
- **Feiertage** (Deutschland) werden rosa markiert und mit Namen beschriftet (in Jahr/Halbjahr/Quartal in der Tagesspalte, in Monats- und
  Wochenansicht unter dem Datum). Bundesland in den Einstellungen wählbar; wird für jedes Jahr automatisch berechnet.
- **Personen**: beliebig viele (Standard 3). In den Einstellungen hinzufügen, umbenennen, Farbe ändern,
  entfernen oder per Häkchen ausblenden. Schnell aus-/einblenden durch Klick auf den Namen oben in der Kopfzeile.
- **Hell/Dunkel**: Button in der Kopfzeile schaltet zwischen *Auto* (wie System), *Hell* und *Dunkel*.
- **Einstellungen**: Personen, Bundesland für Feiertage, Export/Import der Daten als JSON.
- Daten werden im Browser (`localStorage`) gespeichert – per Export/Import auf einen anderen
  Rechner übertragbar.
