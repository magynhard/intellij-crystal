# Crystal LSP Alternative — Standalone Implementation

## Key Finding

**Crystalline is not needed.** Almost all features can be implemented independently — better and with full control.

## What Crystalline Provides vs. What We (Already) Have

| Feature | Crystalline | Our Status | Needed? |
|---------|-------------|------------|---------|
| **Document Symbols** | ✅ | ✅ Already have Structure View | No |
| **Auto-Completion** | ✅ Compiler-based | ✅ StubIndex + Type Inference | No |
| **Formatting** | ✅ | ✅ `crystal tool format` | No |
| **Go to Definition** | ✅ | ✅ PSI + StubIndex | No |
| **Semantic Diagnostics** | ✅ Compiler-based | ✅ `crystal build --no-codegen` (for rename) | Extend |
| **Ameba Linting** | ✅ Integrated | ❌ New | Yes |
| **Hover Information** | ✅ Compiler-based | ⚠️ PSI-based possible | Yes |
| **Shard Indexing** | ✅ Via compiler | ❌ New (via `lib/`) | Yes |
| **Inlay Hints** | ✅ | ❌ New (PSI inference) | Optional |

## Implicit Types via PSI — What Works, What Doesn't

### Solvable via PSI (80% of cases)
| Pattern | Result |
|---------|--------|
| `x = Foo.new` | `Foo` |
| `x = "hello"` | `String` |
| `x = [1,2,3]` | `Array(Int32)` |
| `x = {a: 1}` | `NamedTuple(a: Int32)` |
| `x = 1` | `Int32` |
| `x = :sym` | `Symbol` |
| `def foo(x : String)` | `String` (annotated) |

### Requires Compiler (20% of cases)
| Pattern | Problem |
|---------|---------|
| `x = [1,2,3].map(&.to_s)` | Return type of `map` |
| Generics with type parameters | `Array(T)` → what is `T`? |
| Union types from control flow | `if cond then String else Int32 end` |
| Macros generating code | Unpredictable |

## Shard Indexing via `lib/` Directory

Crystal stores all shard dependencies in `lib/`:
```
my_project/
├── shard.yml
├── lib/
│   ├── http/          ← shard "http"
│   │   └── src/
│   │       └── http/
│   │           ├── client.cr
│   │           └── ...
│   └── JSON/          ← shard "JSON"
│       └── src/
│           └── json.cr
└── src/
    └── main.cr
```

**Approach:**
1. Parse `shard.yml` → list dependencies
2. Find `lib/<shard>/src/**/*.cr` files
3. Parse and add to StubIndex
4. Auto-completion works for `HTTP::Client`, `JSON::Any` etc.

**Trigger:** On project open + on `shards install`/`shards update`

## Concrete New Features

### 1. Ameba Integration (Quick Win, 1-2 days)
- Run `ameba --format json` as external process
- Display results as inspection warnings
- Quick-fixes for simple rules (~50+ rules)

### 2. Hover Information (Medium, 2-3 days)
- **PSI-based:** annotated types, method signatures
- **Extended:** simple type inference (`x = Foo.new` → `Foo`)
- Mouse over symbol → type + documentation

### 3. Shard Indexing (Medium, 2-3 days)
- Parse `shard.yml` (YAML parser)
- Add shard source files to StubIndex
- Auto-completion for shard types

### 4. Inlay Hints (Optional, 2-3 days)
- PSI-based type inference for variables
- Show implicit types as gray text

## Recommended Approach

### Phase 1: Quick Wins
1. Ameba integration as inspection
2. Extend compiler diagnostics (not just for rename)

### Phase 2: Core Features
3. Hover information (PSI-based)
4. Shard indexing via `lib/`

### Phase 3: Optional
5. Inlay hints
6. Extended type inference

## Dependencies

- **Ameba** (optional) — for linting
- **Crystal compiler** — already required
- **YAML parser** — for `shard.yml`
- **No Crystalline needed**
