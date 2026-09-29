# Minimal Todo

Schlanke, vollständig offline arbeitende Android-Todo-App (Kotlin, Jetpack Compose, Material 3, Room, DataStore).
Keine Internet-Berechtigung, kein Konto, keine Cloud, kein Tracking.

## Öffnen & Bauen

1. Ordner `MinimalTodo` in **Android Studio** (Ladybug oder neuer) über *File › Open* öffnen.
2. Gradle-Sync abwarten (JDK 17+; Android Studio bringt ein passendes JBR mit).
3. Starten: Run ▶ auf Emulator/Gerät (minSdk 26 / Android 8.0).

Kommandozeile:

```bash
./gradlew testDebugUnitTest        # JVM-Tests (Repository, Hierarchie, Export/Import, ViewModels)
./gradlew connectedDebugAndroidTest # Room-Tests auf Emulator/Gerät
./gradlew assembleRelease          # Release-APK mit R8/Minify + Resource-Shrinking
./gradlew bundleRelease            # AAB
```

> Der Release-Build ist zum Ausprobieren mit dem Debug-Schlüssel signiert.
> Für den Play Store in `app/build.gradle.kts` eine eigene `signingConfig` eintragen.

## Architektur

```
UI (Compose)  →  ViewModel (StateFlow)  →  TodoRepository  →  Room (todos) / DataStore (Einstellungen)
```

| Paket | Inhalt |
|---|---|
| `data/local` | `TodoEntity`, Enums, `Converters`, `TodoDao`, `AppDatabase` (+ Migrationsliste) |
| `data/repository` | `TodoRepository` – alle Schreibvorgänge, Transaktionen, Zyklenschutz, Löschen/Rückgängig, Duplizieren, Import |
| `data/preferences` | `SettingsRepository` (Interface) + DataStore-Implementierung |
| `data/backup` | Mini-JSON-Codec, versioniertes Backup-Format, `ImportPlanner` (ID-Konflikte, verwaiste Parents, Zyklen) |
| `util` | `TreeBuilder`/`TreeIndex` (Baum → sichtbare Zeilen), `Sorting`, `Hierarchy`, `TextExporter` (Teilen/TXT/Markdown), `DateUtils`, `DocumentIo` |
| `ui/*` | Screens (Home/Gruppe, Heute, Favoriten, Suche, Editor, Einstellungen, Willkommen) + Komponenten |

DI ohne Framework über `AppContainer` in `TodoApplication`.

## Wichtige Entscheidungen

- **Datenmodell** wie spezifiziert, ergänzt um `childSort` (Sortierung pro Gruppe), `collapsed` (Auf-/Zuklappzustand bleibt erhalten) und `searchText` (klein geschriebener Titel+Inhalt → Suche ist auch für Umlaute case-insensitiv; wird bei jedem `copy()` automatisch neu berechnet).
- `dueDate` = Epoch-Day (zeitzonenunabhängig), `dueTime` = Minuten seit Mitternacht, `repeatRule` für 1.1 reserviert.
- **Jeder Typ kann Kinder haben** – Gruppen enthalten Elemente, Aufgaben Unteraufgaben. Fortschritt „2/5“ zählt direkte Aufgaben-Kinder.
- **Zyklenschutz** in `Hierarchy.canMove`; der Verschieben-Dialog bietet den eigenen Unterbaum gar nicht erst an, das Repository prüft zusätzlich.
- **Sortierung**: Angeheftete immer oben; darunter Manuell / Alphabetisch / Offene zuerst (Standard) / Erledigte zuerst / Neueste / Älteste. Pro Gruppe einstellbar (Kontextmenü › „Sortierung des Inhalts“), Root über das ⋮-Menü bzw. Einstellungen.
- **Löschen** mit „Rückgängig“-Snackbar; bei Elementen mit Inhalt die Rückfrage *Abbrechen / Nur Gruppe löschen / Gruppe und Inhalte löschen*. „Nur Gruppe“ setzt die Kinder an die Stelle der Gruppe.
- **Unteraufgaben**: Sind alle erledigt, schlägt eine Snackbar vor, die übergeordnete Aufgabe zu erledigen – nie automatisch.
- **Export/Import** über `ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT`, keine Speicherberechtigung. JSON-Format: `{"format":"minimal-todo","version":1,"exportedAt":…,"items":[…]}`. Import „hinzufügen“ ist Standard, ID-Konflikte bekommen neue IDs, „ersetzen“ läuft in einer Transaktion.
- **Share-In**: `ACTION_SEND text/plain`. Einzeilig → Aufgabe (oder Text), mehrzeilig → Text (oder Aufgabe mit erster Zeile als Titel). Danach geht es zurück zur teilenden App.
- **Spracheingabe (Speech-to-Text)** über die System-Spracherkennung (`RecognizerIntent`, Offline bevorzugt). Die App selbst braucht dafür weder Mikrofon- noch Internet-Berechtigung. Mikrofon-Buttons gibt es in der Schnellerfassung, allen Editor-Feldern und der Suche; auf der Startseite öffnet der kleine Mikrofon-FAB direkt die Aufnahme und danach die Schnellerfassung mit dem erkannten Text (Enter → gespeichert). Ohne installierte Spracherkennung werden die Buttons ausgeblendet. Hinweis: Ob die Erkennung offline erfolgt, entscheidet die Spracherkennungs-App des Geräts (z. B. Google); für Offline-Nutzung das deutsche Offline-Sprachpaket installieren.
- **Datenschutz**: keine Berechtigungen im Manifest, `allowBackup=false` + Data-Extraction-Rules (keine System-Cloud-Sicherung).
- **Kleine APK**: keine Icon-Extended-Bibliothek (eigene Vektor-Icons), kein JSON-/DI-/Bild-Framework, nur Deutsch/Englisch-Ressourcen, R8 + Resource-Shrinking.
- **Performance**: gesamter Baum wird einmal pro DB-Änderung auf `Dispatchers.Default` in O(n) gebaut, nur aufgeklappte Äste werden sortiert; `LazyColumn` mit stabilen Keys und `contentType`. Test mit 10 000 Einträgen enthalten.
- **Wiederholungen** (1.1): Feld `repeatRule` ist vorhanden, UI dafür bewusst noch ausgeblendet.
- Drag & Drop ist nicht umgesetzt; die Reihenfolge ändert man über *Verschieben › Nach oben/unten* (Priorität: Funktion vor Animation).

## Erweiterbarkeit (nicht in 1.0)

Erinnerungen (WorkManager/AlarmManager auf `dueDate`/`dueTime`), Widgets (`observeDueUntil` liefert die Heute-Liste), Shortcuts, Anhänge (neue Tabelle + Migration in `Migrations.ALL`).

## Tests

- `app/src/test` – JVM: `TodoRepositoryTest`, `TreeBuilderTest`, `HierarchyTest`, `TextExporterTest`, `BackupTest`, `HomeViewModelTest`, `EditorViewModelTest` (mit In-Memory-Fake-DAO).
- `app/src/androidTest` – Room: `TodoDaoTest` (CRUD, Kinder/Root, Suche, Favoriten, Fälligkeiten, Filter, Löschen, 10 000 Einträge).
