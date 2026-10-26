# BitCraft Crafting-Planer

Web-App für [BitCraft Online](https://bitcraftonline.com): Gegenstände auswählen (z. B. *Ferralith-Spitzhacke*),
und der Planer zeigt

- **Zu farmen**: alle Rohstoffe für die ganze Liste zusammengerechnet, mit Fundorten (Ressource, Werkzeug, Skill-Level),
- **Crafting-Schritte** in der richtigen Reihenfolge, mit Station + Tier, Werkzeug und Skill-Level
  (umschaltbar auf „nach Station gruppiert“),
- **benötigte Stationen und Werkzeuge** als Übersicht.

Mehrere Gegenstände mit Mengen sind möglich; gemeinsame Zwischenprodukte werden zusammengefasst.
Pro Zwischenprodukt lässt sich ein anderes Rezept wählen oder „farmen / kaufen statt craften“ einstellen.
Oben rechts schaltet man zwischen **Deutsch und Englisch** um. Die Suche versteht beide Sprachen und verzeiht Tippfehler
(„ferrolith spitzhacke“ findet *Ferralith Pickaxe*).

## Starten

Keine Installation nötig: `index.html` im Browser öffnen. Liste, Sprache und Rezeptwahl werden im Browser gespeichert.

## Dateien

| Datei | Inhalt |
|---|---|
| `index.html`, `style.css` | Oberfläche |
| `app.js` | Suche, Liste, Darstellung der Ergebnisse |
| `planner.js` | Rechenlogik: Rezeptbaum auflösen, Mengen und Reihenfolge berechnen |
| `i18n.js` | Texte DE/EN und Glossar für deutsche Item-, Stations-, Werkzeug- und Skillnamen |
| `data.js` | Aus den Spieldaten erzeugt (nicht von Hand bearbeiten) |
| `tools/build_data.py` | Erzeugt `data.js` |

## Spieldaten aktualisieren

Die Daten stammen aus [BitCraftToolBox/BitCraft_GameData](https://github.com/BitCraftToolBox/BitCraft_GameData)
(Branch `cereal/cs`). Nach einem Spiel-Update:

```sh
git clone --depth 1 -b cereal/cs https://github.com/BitCraftToolBox/BitCraft_GameData /tmp/gamedata
python3 tools/build_data.py /tmp/gamedata/static data.js
```

## Hinweise

- Das Spiel liefert nur englische Namen. Deutsche Namen werden über das Glossar in `i18n.js` gebildet;
  der englische Originalname steht immer darunter, damit man ihn im Spiel wiederfindet.
- Seltenheitsvarianten (Common/Uncommon …) eines Gegenstands werden als ein Gegenstand behandelt.
- Zufallsausbeuten (z. B. 95 % normale / 5 % hochwertige Planke) fließen als Durchschnitt ein;
  solche Mengen sind mit „≈“ markiert.
