# TODO — IntelliJ Crystal Plugin

## Strategy

Instead of integrating Crystalline as an external LSP (passive maintenance, high memory usage), we implement features independently:
- **Ameba** for linting (external process, fast)
- **Crystal compiler** (`crystal build --no-codegen`) for semantic diagnostics
- **PSI-based** type inference for hover and inlay hints (80% of cases)
- **Shard indexing** via `lib/` directory for cross-project auto-completion

See `.mimocode/plans/crystal-lsp-alternative.md` for detailed analysis.

## High Priority

- [ ] **Crystalline integration** — integrate the Crystal language server (crystalline) for diagnostics, ameba linting, and potentially enhanced code intelligence. This is the most impactful next step.

## Medium Priority

- [ ] **Crystal Shards support** — parse `shard.yml` for dependency completion and project structure awareness
- [ ] **Inlay hints** — show inferred types on variables (requires type inference improvements)

## Low Priority / Nice-to-Have

- [ ] **Scope-aware rename** — improve token-based rename with scope analysis (current compiler-verified rename works well enough)
- [ ] **Implement members** — generate stubs for abstract methods
- [ ] **New file templates** — create class, module, spec files
