# Parameter Info

Parameter Info (`Ctrl+P`) displays Crystal method signatures for parenthesized calls, bare calls, DOT-calls, class methods, constructors, and record macros.

## Architecture

`CrystalParameterInfoHandler` is the IntelliJ `ParameterInfoHandler` lifecycle coordinator and compatibility facade. It owns the synthetic `CrystalParameterInfoAnchor`, resolves indexed method definitions, prepares displayed signatures, and delegates call analysis to package-internal helpers:

- `CrystalParameterCallLocator` discovers the argument owner without changing the established lookup order: a preliminary bare/DOT-call scan that can select an inner DOT-call before an outer call's arguments, direct PSI, adjacent PSI, broken parenthesized PSI, bare-call recovery, then the closing-parenthesis edge case.
- `CrystalBareCallParameterLocator` performs bounded backwards token scans, detects bare method names, and recovers synthetic anchors for incomplete calls.
- `CrystalDotCallParameterLocator` extracts method and receiver names from call PSI and resolves the class name preceding `.new`.
- `CrystalParameterIndexUtil` computes the active argument from top-level commas.

The handler continues to expose `findArgsHolder`, `scanBackwardsForBareCall`, `computeCurrentParameterIndex`, and `findReceiverNameFromSiblings` for existing callers and tests. These methods delegate to the helpers. `findMethodNameForArgs` remains in the handler because it coordinates synthetic anchors, regular call expressions, bare call expressions, sibling recovery, and malformed PSI.

## Parameter Index

The active parameter index is the number of top-level commas before the caret. Commas nested inside parentheses, arrays, hashes, named tuples, or nested method calls do not advance the outer parameter index. The same rule applies to regular PSI argument owners, bare-call anchors, and synthetic anchors created for incomplete parenthesized calls.

## DOT-Calls

For argument owners represented by `CrystalCallArgs` or `CrystalBareArgumentList`, method-name recovery walks preceding siblings to find the method token. Bare DOT-calls without arguments use backwards leaf scanning. `extractIdentifierFromCallExpression` retains its existing call-expression role; it is not a DOT-call recovery mechanism.

Constant receivers constrain indexed method candidates to definitions enclosed by the same class or module name. Constructor calls resolve `.new` through the receiver class and display `initialize` or record parameters when available.
