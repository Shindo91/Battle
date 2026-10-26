// UI texts and a German glossary for BitCraft names.
// The game data only ships English names, so German item names are built from this glossary
// (nouns carry their grammatical gender so adjectives can be inflected).
(function () {
  const UI = {
    de: {
      title: "BitCraft Crafting-Planer",
      subtitle: "Was willst du herstellen? Der Planer rechnet aus, was du farmen musst und welche Schritte an welcher Station anstehen.",
      searchLabel: "Gegenstand suchen",
      searchPlaceholder: "z. B. Ferralith Spitzhacke",
      noResults: "Nichts gefunden. Probier einen anderen Namen (Deutsch oder Englisch).",
      add: "Hinzufügen",
      wishlist: "Deine Craft-Liste",
      emptyList: "Noch nichts ausgewählt. Suche oben nach einem Gegenstand und füge ihn hinzu.",
      qty: "Menge",
      remove: "Entfernen",
      clearAll: "Liste leeren",
      example: "Beispiel laden",
      farmTitle: "Zu farmen",
      farmHint: "Rohstoffe für alle Gegenstände auf deiner Liste zusammen.",
      stepsTitle: "Crafting-Schritte",
      stepsHint: "In dieser Reihenfolge herstellen: Zwischenprodukte zuerst.",
      stationsTitle: "Benötigte Stationen",
      toolsTitle: "Benötigte Werkzeuge",
      sortOrder: "Reihenfolge",
      sortStation: "Nach Station",
      crafts: (n) => (n === 1 ? "1 Durchgang" : n + " Durchgänge"),
      yields: "ergibt",
      needs: "benötigt",
      tool: "Werkzeug",
      skill: "Skill",
      station: "Station",
      anyStation: "Ohne Station (Inventar)",
      recipe: "Rezept",
      recipeOption: (i) => "Rezept " + i,
      farmInstead: "Farmen / kaufen statt craften",
      craftInstead: "Selbst craften",
      sources: "Wo zu finden",
      noSource: "Keine bekannte Abbauquelle (Handel, Quest, Beute oder Kreaturen).",
      moreSources: (n) => "+" + n + " weitere",
      actions: "Aktionen",
      level: "Lv.",
      tier: "T",
      cycle: "Kreislauf erkannt, wird als Rohstoff behandelt.",
      expected: "Durchschnittswert (Zufallschance)",
      dataNote: "Spieldaten: BitCraftToolBox/BitCraft_GameData. Deutsche Namen sind übersetzt, der englische Originalname steht jeweils darunter.",
      total: "gesamt",
      language: "Sprache",
      byproduct: "Nebenprodukt",
    },
    en: {
      title: "BitCraft Crafting Planner",
      subtitle: "What do you want to make? The planner works out what to farm and which steps to do at which station.",
      searchLabel: "Search item",
      searchPlaceholder: "e.g. Ferralith Pickaxe",
      noResults: "Nothing found. Try another name (English or German).",
      add: "Add",
      wishlist: "Your craft list",
      emptyList: "Nothing selected yet. Search for an item above and add it.",
      qty: "Qty",
      remove: "Remove",
      clearAll: "Clear list",
      example: "Load example",
      farmTitle: "To farm",
      farmHint: "Raw materials for everything on your list combined.",
      stepsTitle: "Crafting steps",
      stepsHint: "Craft in this order: intermediates first.",
      stationsTitle: "Stations needed",
      toolsTitle: "Tools needed",
      sortOrder: "In order",
      sortStation: "By station",
      crafts: (n) => (n === 1 ? "1 craft" : n + " crafts"),
      yields: "yields",
      needs: "needs",
      tool: "Tool",
      skill: "Skill",
      station: "Station",
      anyStation: "No station (inventory)",
      recipe: "Recipe",
      recipeOption: (i) => "Recipe " + i,
      farmInstead: "Farm / buy instead of crafting",
      craftInstead: "Craft it myself",
      sources: "Where to find",
      noSource: "No known gathering source (trade, quest, loot or creatures).",
      moreSources: (n) => "+" + n + " more",
      actions: "actions",
      level: "Lv.",
      tier: "T",
      cycle: "Cycle detected, treated as a raw material.",
      expected: "Average (random chance)",
      dataNote: "Game data: BitCraftToolBox/BitCraft_GameData.",
      total: "total",
      language: "Language",
      byproduct: "Byproduct",
    },
  };

  const BUILDINGS = {
    "Workbench": "Werkbank", "Fire": "Feuer", "Oven": "Ofen", "Cooking Station": "Kochstation",
    "Loom": "Webstuhl", "Tanning Tub": "Gerbbottich", "Leatherworking Station": "Lederverarbeitungsstation",
    "Tailoring Station": "Schneiderstation", "Carpentry Station": "Zimmereistation", "Kiln": "Brennofen",
    "Masonry Station": "Steinmetzstation", "Grinder": "Mühle", "Smelter": "Schmelzofen",
    "Smithing Station": "Schmiedestation", "Well": "Brunnen", "Compost Bin": "Komposter",
    "Farming Station": "Landwirtschaftsstation", "Farming Field": "Feld", "Fishing Station": "Fischereistation",
    "Meat Smoker": "Fleischräucherei", "Forestry Station": "Forststation", "Drying Frame": "Trockengestell",
    "Timber Pile": "Holzstapel", "Scholar Station": "Gelehrtenstation", "Brazier": "Feuerschale",
    "Garden Pot": "Gartentopf", "Ancient Workbench": "Uralte Werkbank", "Inventory": "Inventar",
    "Cargo": "Fracht", "Mining Station": "Bergbaustation", "Hunting Station": "Jagdstation",
    "Foraging Station": "Sammelstation", "Taming Workstation": "Zähmungsstation", "Scrap Bench": "Schrottbank",
    "Cask": "Fass", "Apiary": "Bienenhaus", "Orchard": "Obstgarten", "Maple Tree Orchard": "Ahornplantage",
    "Animal Hutch": "Tierstall", "Construction Workstation": "Baustation", "Sailing Workstation": "Segelstation",
    "Heart Workshop": "Herzwerkstatt", "Empire Foundry": "Imperiumsgießerei", "Town Hall": "Rathaus",
    "Storage": "Lager", "Cargo Stockpile": "Frachtlager", "Trading Post": "Handelsposten",
  };

  const TOOLS = {
    "Axe": "Axt", "Saw": "Säge", "Chisel": "Meißel", "Pickaxe": "Spitzhacke", "Hammer": "Hammer",
    "Knife": "Messer", "Bow": "Bogen", "Scissors": "Schere", "Hoe": "Hacke", "Rod": "Angel", "Pot": "Topf",
    "Machete": "Machete", "Quill": "Schreibfeder", "Mallet": "Holzhammer", "Hexite Tuner": "Hexit-Stimmer",
    "Combat Weapon": "Kampfwaffe",
  };

  const SKILLS = {
    "Forestry": "Forstwirtschaft", "Carpentry": "Zimmerei", "Masonry": "Steinmetzerei", "Mining": "Bergbau",
    "Smithing": "Schmieden", "Scholar": "Gelehrsamkeit", "Leatherworking": "Lederverarbeitung",
    "Hunting": "Jagd", "Tailoring": "Schneiderei", "Farming": "Landwirtschaft", "Fishing": "Fischerei",
    "Cooking": "Kochen", "Foraging": "Sammeln", "Construction": "Bauwesen", "Taming": "Zähmen",
    "Slayer": "Jäger", "Merchanting": "Handel", "Sailing": "Segeln", "Hexite Gathering": "Hexit-Sammeln",
  };

  const VERBS = {
    "Assemble": "Zusammenbauen", "Bake": "Backen", "Boil": "Kochen", "Braid": "Flechten", "Breed": "Züchten",
    "Burn": "Verbrennen", "Carbonate": "Karbonisieren", "Carve": "Schnitzen", "Chop": "Hacken",
    "Clean": "Reinigen", "Collect": "Sammeln", "Cook": "Kochen", "Craft": "Herstellen", "Crush": "Zerkleinern",
    "Cut": "Schneiden", "Enrich": "Anreichern", "Extract": "Extrahieren", "Fill": "Füllen", "Fire": "Brennen",
    "Forge": "Schmieden", "Graft": "Veredeln", "Grind": "Mahlen", "Grow": "Anbauen", "Harvest": "Ernten",
    "Husk": "Entspelzen", "Identify": "Identifizieren", "Macerate": "Einweichen", "Make": "Machen",
    "Mix": "Mischen", "Mold": "Formen", "Pot": "Eintopfen", "Prepare": "Zubereiten", "Press": "Pressen",
    "Process": "Verarbeiten", "Refine": "Veredeln", "Research": "Erforschen", "Revert": "Zurückwandeln",
    "Salvage": "Bergen", "Saw": "Sägen", "Shape": "Formen", "Smash": "Zerschlagen", "Smelt": "Schmelzen",
    "Split": "Spalten", "Stitch": "Nähen", "Tan": "Gerben", "Treat": "Behandeln", "Weave": "Weben",
    // gathering verbs
    "Absorb": "Absorbieren", "Catch": "Fangen", "Challenge": "Herausfordern", "Chip": "Abschlagen",
    "Chisel": "Meißeln", "Collect Fruit": "Früchte sammeln", "Deposit": "Abgeben", "Destroy": "Zerstören",
    "Dig": "Graben", "Dig Up": "Ausgraben", "Fish": "Angeln", "Gather": "Sammeln", "Loot": "Plündern",
    "Mine": "Abbauen", "Net Fish": "Netzfischen", "Open": "Öffnen", "Read": "Lesen", "Reforge": "Umschmieden",
    "Repair": "Reparieren", "Rescue": "Retten", "Sew": "Nähen", "Store": "Lagern", "Take": "Nehmen",
    "Tame": "Zähmen", "Hunt": "Jagen", "Fight": "Bekämpfen", "Transcribe": "Abschreiben", "Unlock": "Aufschließen", "Use Sawmill": "Sägewerk nutzen",
  };

  // Nouns: english -> [german, gender (m/f/n/p), optional form used as first part of a compound]
  const NOUNS = {
    "wood log": ["Holzstamm", "m"], "spool of thread": ["Garnspule", "f"], "plant fiber": ["Pflanzenfaser", "f"],
    "tree bark": ["Baumrinde", "f"], "ore piece": ["Erzstück", "n"], "ore chunk": ["Erzbrocken", "m"],
    "ore concentrate": ["Erzkonzentrat", "n"], "glass vial": ["Glasphiole", "f"], "water bucket": ["Wassereimer", "m"],
    "fish oil": ["Fischöl", "n"], "leather treatment": ["Lederbehandlung", "f"], "cloth strip": ["Stoffstreifen", "m"],
    "room divider": ["Raumteiler", "m"], "hay bale": ["Heuballen", "m"], "reading glasses": ["Lesebrille", "f"],
    "leather": ["Leder", "n"], "belt": ["Gürtel", "m"], "boots": ["Stiefel", "p"], "boot": ["Stiefel", "m"],
    "gloves": ["Handschuhe", "p", "Handschuh"], "helm": ["Helm", "m"], "helmet": ["Helm", "m"],
    "bracers": ["Armschienen", "p", "Armschienen"], "cap": ["Kappe", "f", "Kappen"], "shirt": ["Hemd", "n"],
    "legguards": ["Beinschützer", "p"], "armor": ["Rüstung", "f", "Rüstungs"], "ring": ["Ring", "m"],
    "pie": ["Pastete", "f", "Pasteten"], "berry": ["Beere", "f", "Beeren"], "berries": ["Beeren", "p"],
    "research": ["Forschung", "f", "Forschungs"], "plant": ["Pflanze", "f", "Pflanzen"], "cloth": ["Stoff", "m"],
    "amulet": ["Amulett", "n"], "bulb": ["Knolle", "f", "Knollen"], "bulbs": ["Knollen", "p"],
    "ocean": ["Ozean", "m"], "wood": ["Holz", "n"], "knife": ["Messer", "n"], "rod": ["Angel", "f"],
    "saw": ["Säge", "f", "Säge"], "hoe": ["Hacke", "f"], "bow": ["Bogen", "m"], "shortsword": ["Kurzschwert", "n"],
    "chisel": ["Meißel", "m"], "shield": ["Schild", "m"], "axe": ["Axt", "f"], "pickaxe": ["Spitzhacke", "f"],
    "hammer": ["Hammer", "m"], "machete": ["Machete", "f"], "quill": ["Schreibfeder", "f"], "scissors": ["Schere", "f"],
    "daggers": ["Dolche", "p", "Dolch"], "seeds": ["Samen", "p"], "seed": ["Samen", "m"], "shoes": ["Schuhe", "p", "Schuh"],
    "shorts": ["Shorts", "p"], "mace": ["Streitkolben", "m"], "spear": ["Speer", "m"], "leggings": ["Leggings", "p"],
    "crossbow": ["Armbrust", "f"], "claymore": ["Zweihänder", "m"], "fragment": ["Fragment", "n"],
    "fragments": ["Fragmente", "p"], "brick": ["Ziegel", "m"], "sapphire": ["Saphir", "m"], "ruby": ["Rubin", "m"],
    "emerald": ["Smaragd", "m"], "diamond": ["Diamant", "m"], "sticks": ["Stöcke", "p"], "stick": ["Stock", "m"],
    "sandwich": ["Sandwich", "n"], "stone": ["Stein", "m"], "ore": ["Erz", "n"], "rolls": ["Brötchen", "p"],
    "roll": ["Rolle", "f"], "pelt": ["Fell", "n"], "elixir": ["Elixier", "n"], "fish": ["Fisch", "m"],
    "meat": ["Fleisch", "n"], "mushroom": ["Pilz", "m"], "recipe": ["Rezept", "n"], "plank": ["Planke", "f", "Planken"],
    "trap": ["Falle", "f", "Fallen"], "ingot": ["Barren", "m"], "shell": ["Muschel", "f", "Muschel"],
    "shells": ["Muscheln", "p"], "metal": ["Metall", "n"], "fang": ["Reißzahn", "m"], "pot": ["Topf", "m"],
    "glass": ["Glas", "n"], "potion": ["Trank", "m"], "filet": ["Filet", "n"], "rice": ["Reis", "m"],
    "scale": ["Schuppe", "f", "Schuppen"], "mastery": ["Meisterschaft", "f"], "oil": ["Öl", "n"],
    "solvent": ["Lösungsmittel", "n"], "tree": ["Baum", "m"], "lobster": ["Hummer", "m"], "food": ["Futter", "n"],
    "cottage": ["Hütte", "f"], "knowledge": ["Wissen", "n"], "bird": ["Vogel", "m"], "energy": ["Energie", "f"],
    "gem": ["Edelstein", "m"], "table": ["Tisch", "m"], "flint": ["Feuerstein", "m"], "flower": ["Blume", "f", "Blumen"],
    "concentrate": ["Konzentrat", "n"], "heart": ["Herz", "n"], "goat": ["Ziege", "f", "Ziegen"], "study": ["Studie", "f"],
    "tea": ["Tee", "m"], "wicker": ["Korbgeflecht", "n"], "peach": ["Pfirsich", "m"], "timber": ["Bauholz", "n"],
    "slab": ["Platte", "f", "Platten"], "bark": ["Rinde", "f", "Rinden"], "lump": ["Klumpen", "m"],
    "chemical": ["Chemikalie", "f"], "river": ["Fluss", "m"], "stinger": ["Stachel", "m"], "chum": ["Fischköder", "m"],
    "journal": ["Tagebuch", "n"], "slime": ["Schleim", "m"], "parchment": ["Pergament", "n"], "sand": ["Sand", "m"],
    "ink": ["Tinte", "f", "Tinten"], "net": ["Netz", "n"], "trunk": ["Stamm", "m"], "frames": ["Rahmen", "p"],
    "frame": ["Rahmen", "m"], "sheeting": ["Bahn", "f"], "roots": ["Wurzeln", "p"], "root": ["Wurzel", "f"],
    "geode": ["Geode", "f"], "tarp": ["Plane", "f"], "log": ["Stamm", "m"], "hair": ["Haar", "n"],
    "catalyst": ["Katalysator", "m"], "pebbles": ["Kiesel", "p"], "bandage": ["Verband", "m"], "piece": ["Stück", "n"],
    "bait": ["Köder", "m"], "nails": ["Nägel", "p", "Nagel"], "tannin": ["Gerbstoff", "m"], "polish": ["Politur", "f"],
    "straw": ["Stroh", "n"], "muck": ["Schlamm", "m"], "rope": ["Seil", "n"], "thread": ["Faden", "m"],
    "spool": ["Spule", "f"], "fertilizer": ["Dünger", "m"], "filament": ["Filament", "n"], "seedling": ["Setzling", "m"],
    "waste": ["Abfall", "m"], "dough": ["Teig", "m"], "crop": ["Ernte", "f"], "pigment": ["Pigment", "n"],
    "fiber": ["Faser", "f"], "clay": ["Ton", "m"], "bread": ["Brot", "n"], "pumpkin": ["Kürbis", "m"],
    "pumpkins": ["Kürbisse", "p"], "amber": ["Bernstein", "m"], "resin": ["Harz", "n"], "skewer": ["Spieß", "m"],
    "baitfish": ["Köderfisch", "m"], "textile": ["Textil", "n"], "firesand": ["Feuersand", "m"], "lure": ["Köder", "m"],
    "carvings": ["Schnitzereien", "p"], "codex": ["Kodex", "m"], "crab": ["Krabbe", "f", "Krabben"],
    "mix": ["Mischung", "f"], "vial": ["Phiole", "f"], "treatment": ["Behandlung", "f"], "strip": ["Streifen", "m"],
    "stamina": ["Ausdauer", "f"], "sapling": ["Setzling", "m"], "chair": ["Stuhl", "m"], "fur": ["Pelz", "m"],
    "cargo": ["Fracht", "f"], "pizza": ["Pizza", "f"], "key": ["Schlüssel", "m"], "bobber": ["Schwimmer", "m"],
    "rug": ["Teppich", "m"], "tube": ["Röhre", "f"], "fence": ["Zaun", "m"], "bin": ["Behälter", "m"],
    "mask": ["Maske", "f"], "skiff": ["Boot", "n"], "storage": ["Lager", "n"], "crystal": ["Kristall", "m"],
    "blueprint": ["Bauplan", "m"], "diagrams": ["Diagramme", "p"], "feather": ["Feder", "f"], "walls": ["Mauern", "p"],
    "chitin": ["Chitin", "n"], "weapon": ["Waffe", "f", "Waffen"], "charcoal": ["Holzkohle", "f"],
    "straps": ["Riemen", "p"], "hourglass": ["Sanduhr", "f"], "carpet": ["Teppich", "m"], "pile": ["Haufen", "m"],
    "rock": ["Fels", "m"], "post": ["Pfosten", "m"], "fruit": ["Frucht", "f"], "note": ["Notiz", "f"],
    "notes": ["Notizen", "p"], "hook": ["Haken", "m"], "charm": ["Talisman", "m"], "bucket": ["Eimer", "m"],
    "couch": ["Couch", "f"], "tiara": ["Tiara", "f"], "garden": ["Garten", "m"], "plot": ["Beet", "n"],
    "desk": ["Schreibtisch", "m"], "web": ["Netz", "n"], "bed": ["Bett", "n"], "egg": ["Ei", "n"],
    "capacitor": ["Kondensator", "m"], "kit": ["Set", "n"], "water": ["Wasser", "n"], "brazier": ["Feuerschale", "f"],
    "lamp": ["Lampe", "f"], "hat": ["Hut", "m"], "floor": ["Boden", "m"], "cart": ["Karren", "m"], "hull": ["Rumpf", "m"],
    "shelf": ["Regal", "n"], "wheel": ["Rad", "n"], "nugget": ["Nugget", "n"], "bones": ["Knochen", "p"],
    "bone": ["Knochen", "m"], "sugar": ["Zucker", "m"], "grain": ["Getreide", "n"], "sap": ["Saft", "m"],
    "salt": ["Salz", "n"], "pearl": ["Perle", "f", "Perlen"], "sail": ["Segel", "n"], "map": ["Karte", "f"],
    "hay": ["Heu", "n"], "bale": ["Ballen", "m"], "spices": ["Gewürze", "p"], "seaweed": ["Seetang", "m"],
    "kelp": ["Seetang", "m"], "compass": ["Kompass", "m"], "gift": ["Geschenk", "n"], "lantern": ["Laterne", "f"],
    "arrows": ["Pfeile", "p", "Pfeil"], "jam": ["Marmelade", "f"], "bench": ["Bank", "f"], "treasure": ["Schatz", "m"],
    "schematic": ["Schaltplan", "m"], "quiver": ["Köcher", "m"], "core": ["Kern", "m"], "deed": ["Urkunde", "f"],
    "animal": ["Tier", "n"], "building": ["Gebäude", "n"], "honey": ["Honig", "m"], "book": ["Buch", "n"],
    "sandpaper": ["Schleifpapier", "n"], "carrot": ["Karotte", "f", "Karotten"], "tool": ["Werkzeug", "n"],
    "bundle": ["Bündel", "n"], "coin": ["Münze", "f"], "battery": ["Batterie", "f"], "coffee": ["Kaffee", "m"],
    "cocoa": ["Kakao", "m"], "chocolate": ["Schokolade", "f", "Schokoladen"], "hammock": ["Hängematte", "f"],
    "anchor": ["Anker", "m"], "hide": ["Haut", "f"], "smoker": ["Räucherofen", "m"], "obsidian": ["Obsidian", "m"],
    "block": ["Block", "m"], "glue": ["Leim", "m"], "pitch": ["Pech", "n"], "essence": ["Essenz", "f"],
    "cocoon": ["Kokon", "m"], "relic": ["Relikt", "n"], "tablet": ["Tafel", "f"], "mallet": ["Holzhammer", "m"],
    "platform": ["Plattform", "f"], "campfire": ["Lagerfeuer", "n"], "dish": ["Gericht", "n"],
    "banana": ["Banane", "f", "Bananen"], "coconut": ["Kokosnuss", "f"], "pineapple": ["Ananas", "f"],
    "popsicle": ["Eis am Stiel", "n"], "curry": ["Curry", "n"], "vegetable": ["Gemüse", "n"],
    "sunflower": ["Sonnenblume", "f", "Sonnenblumen"], "blossom": ["Blüte", "f", "Blüten"], "imprint": ["Abdruck", "m"],
    "oyster": ["Auster", "f", "Austern"], "ash": ["Asche", "f"], "feast": ["Festmahl", "n"], "room": ["Raum", "m"],
    "planter": ["Pflanzkübel", "m"], "fountain": ["Brunnen", "m"], "wedge": ["Keil", "m"], "line": ["Leine", "f"],
    "ears": ["Ähren", "p"], "eye": ["Auge", "n"], "ice": ["Eis", "n"], "snow": ["Schnee", "m"],
    "mittens": ["Fäustlinge", "p"], "glasses": ["Brille", "f"], "silk": ["Seide", "f", "Seiden"], "sift": ["Sieb", "n"],
    "item": ["Gegenstand", "m"], "appearance": ["Aussehen", "n"], "sentinel": ["Wächter", "m"], "pirate": ["Pirat", "m", "Piraten"],
    "king": ["König", "m"], "crafting": ["Handwerk", "n"], "gathering": ["Sammeln", "n"], "fishing": ["Angeln", "n", "Angel"],
    "hunting": ["Jagd", "f", "Jagd"], "mining": ["Bergbau", "m"], "foraging": ["Sammeln", "n"], "farming": ["Ackerbau", "m"],
    "cooking": ["Kochen", "n", "Koch"], "carpentry": ["Zimmerei", "f"], "masonry": ["Mauerwerk", "n"], "skill": ["Fertigkeit", "f"],
    "training": ["Training", "n"], "outpost": ["Außenposten", "m"], "cobbler": ["Auflauf", "m"],
    "documents": ["Dokumente", "p"], "materials": ["Materialien", "p"], "jungle": ["Dschungel", "m"],
    "desert": ["Wüste", "f"], "tundra": ["Tundra", "f"], "plains": ["Ebene", "f"], "swamp": ["Sumpf", "m"],
    "lake": ["See", "m"], "survey": ["Vermessung", "f"], "hold": ["Laderaum", "m"], "power": ["Kraft", "f"],
    "dough ball": ["Teigkugel", "f"], "starfish": ["Seestern", "m"], "conch": ["Muschelhorn", "n"],
  };

  // Adjectives: english -> german stem; endings follow the head noun (no article: -er/-e/-es/-e).
  const ADJ = {
    "high quality": "hochwertig", "rough": "grob", "simple": "einfach", "sturdy": "robust", "fine": "fein",
    "exquisite": "exquisit", "peerless": "unvergleichlich", "ornate": "verziert", "pristine": "unberührt",
    "flawless": "makellos", "magnificent": "prächtig", "deluxe": "luxuriös", "woven": "gewebt",
    "refined": "veredelt", "raw": "roh", "mashed": "zerstampft", "infused": "durchtränkt", "uncut": "ungeschliffen",
    "ground": "gemahlen", "stuffed": "gefüllt", "ambrosial": "ambrosisch", "plain": "schlicht", "zesty": "würzig",
    "savory": "herzhaft", "succulent": "saftig", "delectable": "köstlich", "flavorful": "aromatisch",
    "scrumptious": "lecker", "delicious": "delikat", "ancient": "uralt", "roasted": "geröstet",
    "tropical": "tropisch", "enormous": "riesig", "unfired": "ungebrannt", "auric": "aurisch", "large": "groß",
    "small": "klein", "cozy": "gemütlich", "talented": "talentiert", "crystalized": "kristallisiert",
    "skillful": "geschickt", "cooked": "gekocht", "hardened": "gehärtet", "advanced": "fortgeschritten",
    "domesticated": "domestiziert", "proficient": "kundig", "average": "durchschnittlich", "tiny": "winzig",
    "unidentified": "unidentifiziert", "potted": "eingetopft", "encrusted": "verkrustet", "crushed": "zerkleinert",
    "comprehensive": "umfassend", "essential": "essenziell", "massive": "massiv", "citric": "zitrisch",
    "cleaned": "gereinigt", "tanned": "gegerbt", "stripped": "entrindet", "molten": "geschmolzen",
    "healing": "heilend", "energizing": "belebend", "reckless": "rücksichtslos", "skewered": "aufgespießt",
    "vibrant": "lebhaft", "hot": "heiß", "divine": "göttlich", "mossy": "moosig", "abyssal": "abyssisch",
    "murky": "trüb", "briny": "salzig", "muddy": "schlammig", "azure": "azurblau", "rocky": "felsig",
    "chilling": "kühlend", "blue": "blau", "white": "weiß", "red": "rot", "green": "grün", "yellow": "gelb",
    "brown": "braun", "gray": "grau", "magnetic": "magnetisch", "carved": "geschnitzt", "charged": "geladen",
    "calcic": "kalkhaltig", "expanded": "erweitert", "verdant": "üppig", "wild": "wild", "broken": "zerbrochen",
    "lucky": "glücklich", "pure": "rein", "lost": "verloren", "old": "alt", "smooth": "glatt", "round": "rund",
    "circular": "kreisförmig", "depleted": "erschöpft", "reinforced": "verstärkt", "heated": "erhitzt",
    "tattered": "zerfetzt", "salvaged": "geborgen", "wooden": "hölzern", "knitted": "gestrickt", "rich": "reich",
    "elder": "alt", "giant": "riesig", "empty": "leer", "preserved": "konserviert", "makeshift": "behelfsmäßig",
    "mysterious": "mysteriös", "forgotten": "vergessen", "thick": "dick", "medium": "mittelgroß", "long": "lang",
    "silken": "seiden", "colorful": "bunt", "striped": "gestreift", "dotted": "gepunktet",
    "bioluminescent": "biolumineszent", "conductive": "leitfähig", "brittle": "spröd", "perfect": "perfekt",
    "injured": "verletzt", "smelly": "stinkend", "pungent": "stechend", "cooling": "kühlend",
    "windswept": "windgepeitscht", "fall": "herbstlich", "winter": "winterlich", "tall": "hoch",
    "big": "groß", "dwarf": "zwergen", "light": "leicht", "uneven": "uneben", "workable": "bearbeitbar",
    "overheated": "überhitzt", "faint": "schwach", "basic": "einfach", "chemical": "chemisch",
    "dried": "getrocknet", "smoked": "geräuchert", "fried": "frittiert", "baked": "gebacken", "boiled": "gekocht",
    "grilled": "gegrillt", "sliced": "geschnitten", "pickled": "eingelegt",
  };

  // First parts of compounds ("Plated Armor" -> "Plattenrüstung", "Forester's Axe" -> "Försteraxt").
  const PREFIX = {
    "plated": "Platten", "duelist": "Duellanten", "forester's": "Förster", "woodcrafter's": "Holzhandwerker",
    "automata's": "Automaten", "beginner's": "Anfänger", "potter's": "Töpfer", "novice's": "Novizen",
    "adventurer's": "Abenteurer", "stonesmith's": "Steinmetz", "pirate's": "Piraten", "landweaver's": "Landweber",
    "futuremaker's": "Zukunftsmacher", "sentinel's": "Wächter", "mason's": "Steinmetz", "miner's": "Bergmanns",
    "survivalist's": "Überlebens", "survalist's": "Überlebens", "hunter's": "Jäger", "farmer's": "Bauern",
    "smith's": "Schmiede", "fisher's": "Fischer", "leatherworker's": "Lederer", "carpenter's": "Zimmermanns",
    "tailor's": "Schneider", "scholar's": "Gelehrten", "forager's": "Sammler", "blacksmith's": "Schmiede",
    "librarian's": "Bibliothekars", "traveler's": "Reisenden", "surveyor's": "Landvermesser", "clothmaker's": "Tuchmacher",
    "starseeker's": "Sternsucher", "swashbuckler's": "Draufgänger", "windrider's": "Windreiter", "midwinter's": "Mittwinter",
    "outdoor": "Außen", "animal": "Tier",
  };

  const SMALL = { "and": "und", "of": "aus", "the": "", "a": "", "on": "auf" };

  const ENDINGS = { m: "er", f: "e", n: "es", p: "e" };

  function inflect(stem, gender) {
    if (/(lila|rosa|orange)$/.test(stem)) return cap(stem);
    let ending = ENDINGS[gender] || "e";
    if (stem.endsWith("e")) ending = ending.slice(1) || "";
    return cap(stem + ending);
  }

  function cap(s) {
    return s ? s.charAt(0).toUpperCase() + s.slice(1) : s;
  }

  const cache = new Map();

  // Translates an English item name into German, keeping proper names (materials, creatures) as they are.
  function translateItemName(name) {
    if (cache.has(name)) return cache.get(name);
    const words = name.split(/\s+/).filter(Boolean);
    const tokens = [];
    for (let i = 0; i < words.length; ) {
      let matched = false;
      for (let len = Math.min(3, words.length - i); len >= 1 && !matched; len--) {
        const phrase = words.slice(i, i + len).join(" ").toLowerCase();
        if (len > 1 && NOUNS[phrase]) {
          tokens.push({ type: "N", de: NOUNS[phrase][0], g: NOUNS[phrase][1], comp: NOUNS[phrase][2] });
        } else if (ADJ[phrase] && (len > 1 || i < words.length - 1)) {
          tokens.push({ type: "A", stem: ADJ[phrase] });
        } else if (len === 1 && PREFIX[phrase] && i < words.length - 1) {
          tokens.push({ type: "P", de: PREFIX[phrase] });
        } else if (len === 1 && NOUNS[phrase]) {
          tokens.push({ type: "N", de: NOUNS[phrase][0], g: NOUNS[phrase][1], comp: NOUNS[phrase][2] });
        } else if (len === 1 && phrase in SMALL) {
          tokens.push({ type: "S", de: SMALL[phrase] });
        } else if (len === 1 && /[:,]$/.test(words[i])) {
          const word = words[i].slice(0, -1);
          const noun = NOUNS[word.toLowerCase()];
          tokens.push({ type: "S", de: (noun ? noun[0] : word) + words[i].slice(-1) });
        } else if (len === 1) {
          tokens.push({ type: "X", de: words[i].replace(/'s$/, "") });
        } else {
          continue;
        }
        i += len;
        matched = true;
      }
    }
    // Gender of the head noun (last noun) drives adjective endings.
    let gender = "n";
    for (let i = tokens.length - 1; i >= 0; i--) {
      if (tokens[i].type === "N") { gender = tokens[i].g; break; }
    }
    const out = [];
    let compound = "";
    let lastWasProper = false;
    const flush = () => { if (compound) out.push(compound); compound = ""; lastWasProper = false; };
    tokens.forEach((t, idx) => {
      const next = tokens[idx + 1];
      const continues = next && (next.type === "N" || next.type === "P");
      if (t.type === "A") {
        flush();
        const adj = inflect(t.stem, gender);
        out.push(out.length && !out[out.length - 1].endsWith(":") ? adj.toLowerCase() : adj);
      } else if (t.type === "S") {
        flush();
        if (t.de) out.push(t.de);
      } else if (t.type === "X") {
        if (compound && !lastWasProper) flush();
        if (continues) {
          compound = (compound ? compound + " " : "") + t.de + "-";
          lastWasProper = true;
        } else {
          flush();
          out.push(t.de);
        }
      } else {
        // noun or compound prefix
        const part = t.type === "N" ? (continues ? (t.comp || t.de) : t.de) : t.de;
        if (!compound) compound = part;
        else if (compound.endsWith("-")) compound += part;
        else compound += part.charAt(0).toLowerCase() + part.slice(1);
        lastWasProper = false;
        if (!continues) flush();
      }
    });
    flush();
    const result = out.join(" ").replace(/\s+/g, " ").trim() || name;
    cache.set(name, result);
    return result;
  }

  window.I18N = {
    UI,
    translateItemName,
    building: (name, lang) => (lang === "de" ? BUILDINGS[name] || name : name),
    tool: (name, lang) => (lang === "de" ? TOOLS[name] || name : name),
    skill: (name, lang) => (lang === "de" ? SKILLS[name] || name : name),
    verb: (name, lang) => (lang === "de" ? VERBS[name] || name : name),
  };
})();
