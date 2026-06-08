---
title: "fix: Test-Runner-Navigation für alle Tests zuverlässig herstellen"
type: fix
status: active
date: 2026-06-08
---

# fix: Test-Runner-Navigation für alle Tests zuverlässig herstellen

## Overview

Wenn im Test-Runner-Baum auf einen Test geklickt wird, springt der IDE-Editor nur zum ersten Test ("works"), aber nicht zu nachfolgenden Tests ("works2", "sorks", etc.). Der Grund: Der statische Cache in `CrystalSpecFileIndexer` wird nie zwischen Runs geleert, was zu veralteten Indizes führt. Zusätzlich gibt es mehrere sekundäre Bugs, die die Zuverlässigkeit beeinträchtigen.

## Problem Frame

**User-Report:** Bei mehreren `it`-Blöcken in einem `describe`-Block oder bei mehreren `describe`-Blöcken funktioniert die Navigation (Doppelklick im Tree) nur für den allerersten Test. Alle weiteren Tests haben keinen Navigationstarget.

**Ursache:** `CrystalSpecFileIndexer` speichert den Index statisch in `companion object cache` und prüft nie, ob sich die Datei geändert hat. Beim zweiten Run wird der veraltete Index wiederverwendet.

## Requirements Trace

- R1. Klick auf jeden Test im Tree muss zur entsprechenden Quellzeile springen
- R2. Funktioniert auch bei Tests mit identischen Namen (z.B. zwei `it "works"` in verschiedenen Describe-Blöcken)
- R3. Funktioniert auch nach Datei-Änderungen zwischen Runs (Cache-Invaldierung)
- R4. Comment-Zeilen mit `describe`/`context`/`it` Mustern dürfen den Index nicht verfälschen
- R5. `it("...")` mit Klammern muss ebenfalls indiziert werden

## Scope Boundaries

- Keine Änderungen an der Crystal-Verbose-Output-Parsing-Logik (funktioniert korrekt)
- Keine Änderungen an `CrystalTestLocator` (funktioniert korrekt, solange die URL gesetzt ist)
- Keine Änderungen am Test-Runner-UI-Verhalten (IntelliJ-Plattform)

## Context & Research

### Relevant Code and Patterns

- `src/main/kotlin/de/magynhard/crystal/run/CrystalSpecFileIndexer.kt` — Statischer Cache, Index-Logik
- `src/main/kotlin/de/magynhard/crystal/run/CrystalTestEventsConverter.kt` — Parser für Crystal-Verbose-Output, URL-Zuordnung
- `src/main/kotlin/de/magynhard/crystal/run/CrystalTestLocator.kt` — Navigation zu Quellzeile
- `src/main/kotlin/de/magynhard/crystal/run/CrystalTestRunState.kt` — Orchestrator, ruft Indexer auf
- `src/test/kotlin/de/magynhard/crystal/run/CrystalTestEventsConverterTest.kt` — Bestehende Tests

### Key Data Flow

```
Source File → CrystalSpecFileIndexer.buildIndex() → Map<"fullTestName", TestLocation>
                                                            ↓
Crystal -v Output → Parser.parseRunningLine() → testLocations[fullName] → URL → TestNode.Test.url
                                                            ↓
User Clicks Test → CrystalTestLocator.getLocation(url) → PsiLocation(line)
```

## Key Technical Decisions

- **Cache-Invalidation durch File-Modification-Time:** Statt Cache komplett zu leeren (was bei großen Projekten mit vielen Spec-Dateien langsam wäre), wird die Datei-Änderungszeit (lastModified) mitgespeichert und bei jedem Zugriff geprüft. Nur bei Änderung wird neu indexiert.
- **Comment-Lines überspringen:** Zeilen die nach TrimStart mit `#` beginnen werden vom Indexer ignoriert.
- **Duplicate Test-Namen:** Der Index speichert eine Liste von `TestLocation` pro Key statt einzelne Location. Der Parser wählt die passende Location basierend auf Reihenfolge.

## Implementation Units

- [ ] **Unit 1: Cache-Invalidation in CrystalSpecFileIndexer**

**Goal:** Statischer Cache wird bei Datei-Änderungen automatisch aktualisiert

**Requirements:** R1, R3

**Dependencies:** None

**Files:**
- Modify: `src/main/kotlin/de/magynhard/crystal/run/CrystalSpecFileIndexer.kt`
- Test: `src/test/kotlin/de/magynhard/crystal/run/CrystalTestEventsConverterTest.kt`

**Approach:**
1. Erweitere `TestLocation` um `lastModified: Long`
2. Erstelle eine interne Data-Class `IndexedFile(val locations: Map<String, TestLocation>, val lastModified: Long)`
3. In `getTestLocations()`: Vor Cache-Rückgabe prüfen ob `File(filePath).lastModified() > cached.lastModified`
4. Bei Änderung: Neu indexieren und Cache aktualisieren
5. Ähnliche Logik für `getTestLocationsForDirectory()` — aber hier reicht es, den gesamten Directory-Cache zu leeren wenn eine Datei geändert wurde

**Test scenarios:**
- Happy path: Datei wird indiziert, Cache gibt korrekte Ergebnisse zurück
- Edge case: Datei wird nach erstem Run geändert (neues `it`-Block hinzugefügt), zweiter Run liefert aktualisierten Index
- Edge case: Datei wird zwischen mehreren Runs mehrfach geändert
- Integration: Kombination aus Cache + Parser — vollständiger Round-Trip von Index zu URL

**Verification:**
- Neuer Test `testIndexer_cacheInvalidationOnFileChange` erstellen
- Vorhandene Tests weiterhin bestehen

---

- [ ] **Unit 2: Comment-Lines im Indexer überspringen**

**Goal:** Zeilen die mit `#` beginnen werden ignoriert

**Requirements:** R4

**Dependencies:** None

**Files:**
- Modify: `src/main/kotlin/de/magynhard/crystal/run/CrystalSpecFileIndexer.kt`
- Test: `src/test/kotlin/de/magynhard/crystal/run/CrystalTestEventsConverterTest.kt`

**Approach:**
In der `while`-Schleife von `buildIndex()`, nach `val trimmed = line.trimStart()`, prüfen ob `trimmed.startsWith("#")` — wenn ja, `i++` und `continue`.

**Test scenarios:**
- Happy path: Comment `# describe "fake" do` wird ignoriert
- Happy path: Comment `# it "fake" do` wird ignoriert
- Edge case: Comment mit `it` mitten im Text `# some it "thing"` wird ignoriert
- Edge case: Echte `describe`/`it` Blöcke nach Comments werden korrekt indiziert

**Verification:**
- Neuer Test `testIndexer_skipsComments` mit Comment-Lines die describe/it Muster enthalten

---

- [ ] **Unit 3: `it(...)` mit Klammern unterstützen**

**Goal:** `it("works") do` wird korrekt indiziert

**Requirements:** R5

**Dependencies:** None

**Files:**
- Modify: `src/main/kotlin/de/magynhard/crystal/run/CrystalSpecFileIndexer.kt`
- Test: `src/test/kotlin/de/magynhard/crystal/run/CrystalTestEventsConverterTest.kt`

**Approach:**
Erweitere die `it`-Regex von `it\s+["'](.+?)["']` zu `it\s*[(\s]["'](.+?)["']\s*[)]?` — oder einfacher: zwei Alternative Regexes testen (`it "..."` und `it("...")`).

**Test scenarios:**
- Happy path: `it("works") do` wird als Test erkannt
- Happy path: `it ("works") do` (mit Leerzeichen) wird erkannt
- Edge case: Gemischte Styles in derselben Datei

**Verification:**
- Neuer Test `testIndexer_parenthesizedItBlock`

---

- [ ] **Unit 4: Duplicate Test-Namen-handling**

**Goal:** Zwei Tests mit gleichem Namen (in verschiedenen Describe-Blöcken) haben beide Navigation

**Requirements:** R2

**Dependencies:** Unit 1

**Files:**
- Modify: `src/main/kotlin/de/magynhard/crystal/run/CrystalSpecFileIndexer.kt`
- Modify: `src/main/kotlin/de/magynhard/crystal/run/CrystalTestEventsConverter.kt`
- Test: `src/test/kotlin/de/magynhard/crystal/run/CrystalTestEventsConverterTest.kt`

**Approach:**
1. Ändere `Map<String, TestLocation>` zu `Map<String, MutableList<TestLocation>>`
2. Bei Indexer: Bei neuem Test mit gleichem Key → an Liste anhängen (nicht überschreiben)
3. Bei Parser: Verwende `removeFirst()` oder Index-basierte Zuordnung, um pro Test die passende Location zuzuordnen
4. Alternative (einfacher): Verwende `fullName + ":" + line` als eindeutigen Key und ordne im Parser per Reihenfolge zu

**Test scenarios:**
- Happy path: Zwei `it "works"` in verschiedenen Describe-Blöcken — beide haben Navigation
- Happy path: Drei identische Test-Namen in verschiedenen Describe-Blöcken
- Edge case: Gleicher Test-Name im selben Describe-Block (Crystal erlaubt das)

**Verification:**
- Neuer Test `testIndexer_duplicateTestNames`
- Neuer Round-Trip-Test der Navigation für duplicate Names

---

- [ ] **Unit 5: CrystalTestLocator unit testen**

**Goal:** Navigation von URL zu PsiLocation wird durch Tests abgedeckt

**Requirements:** R1

**Dependencies:** None

**Files:**
- Test: `src/test/kotlin/de/magynhard/crystal/run/CrystalTestEventsConverterTest.kt`

**Approach:**
Da `CrystalTestLocator.getLocation()` eine IDE-Abhängigkeit (Project, PsiManager, etc.) hat, kann es nicht als reiner Unit-Test getestet werden. Stattdessen:
1. Teste die URL-Parsing-Logik separat (File-Path und Line-Number Extraktion)
2. Erstelle einen Integration-Test mit `BasePlatformTestCase` falls möglich

**Test scenarios:**
- Happy path: URL `crystal_spec://path/to/file.cr:42` wird korrekt geparst
- Edge case: Dateipfad mit mehreren Doppelpunkten (z.B. Windows-Pfade)
- Edge case: Ungültige URL (kein Doppelpunkt)

**Verification:**
- Neuer Test `testLocator_urlParsing`

---

- [ ] **Unit 6: Cache vor jedem Run leeren (Sicherheitsnetz)**

**Goal:** Zusätzlich zur File-Modification-Time-Prüfung wird der Cache vor jedem Test-Run komplett geleert

**Requirements:** R1, R3

**Dependencies:** Unit 1

**Files:**
- Modify: `src/main/kotlin/de/magynhard/crystal/run/CrystalTestRunState.kt`

**Approach:**
In `CrystalTestRunState.execute()`, vor dem Aufruf von `CrystalSpecFileIndexer.getTestLocations()` oder `getTestLocationsForDirectory()`, `CrystalSpecFileIndexer.clearCache()` aufrufen.

Das ist ein Sicherheitsnetz falls die Modification-Time-Prüung in Unit 1 mal versagt.

**Test scenarios:**
- Test expectation: none -- reine Infrastruktur-Änderung, wird durch Integrationstests abgedeckt

**Verification:**
- Manueller Test: Datei ändern → Run → neue Tests werden im Tree angezeigt mit Navigation

## System-Wide Impact

- **Cache-Lebensdauer:** Der statische Cache lebt in der `companion object` von `CrystalSpecFileIndexer`. Er wird nur bei Klassen-Löschung (IDE-Restart) geleert. Mit der Modification-Time-Prüfung wird er bei jeder Nutzung aktualisiert.
- **Performance:** Die Modification-Time-Prüfung ist ein schneller `File.lastModified()` Aufruf — kein messbarer Overhead.
- **Abwärtskompatibilität:** Alle Änderungen sind intern. Die öffentliche API (`getTestLocations`, `getTestLocationsForDirectory`) bleibt unverändert.

## Risks & Dependencies

| Risk | Mitigation |
|------|------------|
| File-Modification-Time-Prüfung kann bei Remote-Filesystems (NFS, etc.) unzuverlässig sein | Unit 6 als Sicherheitsnetz: Cache vor jedem Run leeren |
| `it(...)` Regex könnte bestehende `it "..."` matches brechen | Test-first: Alle vorhandenen Tests müssen weiterhin bestehen |
| Duplicate-Test-Namen-Handling könnte bestehende fullName-Konstruktion ändern | Nur intern: Parser-Logik für URL-Zuordnung wird angepasst, fullName bleibt gleich |

## Documentation / Operational Notes

- `TODO.md` Zeile 108 (Test runner) muss um "navigation works for all tests including duplicates" ergänzt werden
- Keine Änderungen an plugin.xml nötig
- Keine Änderungen an BNF/Flex nötig

## Sources & References

- **Origin:** User-Bugreport (direkt im Chat)
- **Related code:** `src/main/kotlin/de/magynhard/crystal/run/` (gesamter Run-Ordner)
- **Related tests:** `src/test/kotlin/de/magynhard/crystal/run/CrystalTestEventsConverterTest.kt`
