# Code Style Settings — Decision Record

## Decision: No Code Style settings page for Crystal

Crystal intentionally does **not** have a Code Style settings page in Settings → Editor → Code Style.

## Rationale

Crystal's canonical formatter is `crystal tool format` (the Crystal compiler's built-in formatter), registered via `<formattingService>` in plugin.xml. It:

- Has **zero configuration** — always formats using the Crystal community style (2-space indent, specific spacing rules).
- **Ignores** IntelliJ's `CodeStyleSettings` entirely.
- Requires the Crystal compiler to be installed at the configured path.

A Code Style page with inert settings (e.g. Tab size, Indent size) would be **misleading** — users would configure options that have no effect on Ctrl+Alt+L formatting.

## What Code Style settings DO affect (without a native formatter)

The platform's `IndentOptions` (USE_TAB_CHARACTER, SMART_TABS, TAB_SIZE, INDENT_SIZE, CONTINUATION_INDENT_SIZE) are still read by the **Enter handler** (`CrystalEnterHandler`) for auto-indent on pressing Enter. These settings have effect in the `.editorconfig` export and some editor behaviors, but NOT on `crystal tool format`.

## Future considerations

If a native IntelliJ `FormattingModelBuilder` is ever added (one that reads `CodeStyleSettings` and implements Crystal formatting rules in-process), a Code Style page could be reintroduced. Until then, `crystal tool format` is the single source of formatting truth.

## Historical notes

A `langCodeStyleSettingsProvider` EP registration with `CrystalCodeStyleSettingsProvider` was added in an earlier version (v0.1.18 development) but was removed because the provider's settings had no effect on `crystal tool format`. The EP registration used the wrong attribute (`implementationClass=` instead of `implementation=`) which silently prevented the provider from loading — this bug was the initial motivation for investigation.
