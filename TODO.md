# TODO — IntelliJ Crystal Plugin

## Parser-Erweiterungen (BNF-Grammatik)

Die aktuelle Grammatik deckt die häufigsten Konstrukte ab. Folgende Erweiterungen stehen aus:

- [ ] Generics vollständig (`Array(T)`, `forall T`, Constraints)
- [ ] Macro-Body-Parsing (`{% %}`, `{{ }}`, `{% for %}`)
- [ ] Union-Types als Typ-Annotation (`Int32 | String`)
- [ ] Proc/Lambda-Typen (`-> Int32`, `Proc(Int32, String)`)
- [ ] Proc-Literale (`->{ }`, `->(x) { }`)
- [ ] Pattern Matching (Crystal 1.x `case...in`)
- [ ] Multi-Assignment (`a, b = 1, 2`)
- [ ] Splat-Parameter (`*args`, `**kwargs`)
- [ ] Annotation-Bodies (`@[JSON::Field(key: "x")]`)
- [ ] `asm` Blöcke
- [ ] Named Tuples (`{name: "foo", age: 42}`)
- [ ] `select` Statement (Concurrency)
- [ ] Heredocs als Ausdrücke im Parser (Lexer hat sie schon)
- [ ] Bessere Operator-Precedence (Pratt-Parsing oder Precedence-Climbing)
- [ ] Type Restrictions bei Parametern (`def foo(x : Int32)`)
- [ ] Default-Werte bei Parametern (vollständige Ausdrücke)
- [ ] Visibility Modifiers als Modifier am PSI-Knoten
- [ ] `with...yield` Blöcke
- [ ] `pointerof`, `offsetof` als Ausdrücke
- [ ] String-Interpolation als verschachtelte Ausdrücke im Parser
- [ ] Suffix-if/unless/while (`expr if condition`)
- [ ] Ternary Operator (bereits teilweise: `? :` in expression)

## IDE-Features (benötigen Parser)

- [ ] Parameter Info (Ctrl+P) — Methodenaufruf erkennen, Deklaration finden
- [ ] Bessere Structure View — PSI-basiert statt token-basiert
- [ ] Reference Resolution — Variablen/Methoden zu Deklaration auflösen
- [ ] Code Completion — kontextabhängige Vorschläge
- [ ] Type Inference (basic) — Variablentyp aus Zuweisung ableiten
- [ ] Inline Rename — scope-aware statt rein textbasiert
- [ ] Semantic Highlighting — Variablen vs. Methoden vs. Typen farblich unterscheiden
- [ ] Inlay Hints — Typ-Hinweise bei Variablen
- [ ] Quick Documentation — Kommentar über def anzeigen
- [ ] Implement Members — abstrakte Methoden implementieren

## IDE-Features (unabhängig vom Parser)

- [ ] Ameba Integration (Crystal Linter) — Inspections aus `ameba` anzeigen
- [ ] Crystal Shards Support — `shard.yml` Parsing, Dependency Completion
- [ ] Test Runner — `crystal spec` mit IntelliJ Test UI verbinden
- [ ] Debugger Integration — GDB/LLDB für Crystal Binaries
- [ ] Project SDK — Crystal-Version erkennen und konfigurieren
- [ ] New File Templates — Klasse, Modul, Spec erstellen
- [ ] Spell Checking in Strings/Kommentaren
- [ ] Markdown-Rendering für Doc-Kommentare

## Infrastruktur

- [ ] Mehr Lexer-Tests (Edge-Cases: nested interpolation, regex vs. division)
- [ ] Parser-Tests (Gold-File basiert)
- [ ] CI/CD Pipeline (GitHub Actions)
- [ ] Plugin Marketplace Veröffentlichung
- [ ] Plugin-Icon für Marketplace
- [ ] Changelog automatisieren
