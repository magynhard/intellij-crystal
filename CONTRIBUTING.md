# Contributing

Thanks for your interest in contributing to the Crystal Language Plugin!
This guide covers **how to file good issues** and **how code contributions
work**.

## Before You Open an Issue

Spend 2 minutes on these checks — it saves everyone time:

1. **Search existing issues** — your problem may already be reported or
   discussed. Use the search bar on the [Issues page](https://github.com/magynhard/intellij-crystal/issues).
2. **Update to the latest plugin version** — Settings → Plugins → Check for
   Updates. Many "bugs" are already fixed in the latest release.
3. **Reproduce with a minimal example** — strip your code down to the
   smallest snippet that still triggers the issue. This is required for all
   issue types.

## Choosing the Right Issue Type

We use three issue templates. Pick the one that fits — filing under the wrong
type slows down triage.

| Type | When to use | Examples |
|------|-------------|----------|
| **🐛 Bug Report** | Something doesn't work as expected — wrong behavior, parse error, crash, false-positive inspection, broken navigation. | "Go to Definition on `.new` shows 30 targets instead of 1" |
| **✨ Feature Request** | A Crystal construct isn't supported yet, or a new IDE feature is needed. The feature is *impossible* today, not just clunky. | "Support for `asm` blocks inside macros" / "Inlay hints for inferred types" |
| **🦥 UX Issue** | Something technically works but feels clunky, slow, or unintuitive. It's about the *workflow*, not a bug or missing feature. | "Running a single spec requires 5 clicks" / "Rename dialog has too many steps" |

**Not sure?** Use [Discussions](https://github.com/magynhard/intellij-crystal/discussions)
first. We'll help you figure out which type fits.

## How to Write a Good Issue

Every issue template requires three fields. They're mandatory for a reason:

### Current state

Show **what happens today** with a minimal, self-contained code example. For
bugs, this is the reproduction case. For features, this is the current
workaround (or "not possible"). For UX issues, this is the current
step-by-step workflow.

**Good:**
```crystal
record Config, host : String, port : Int32 = 80

Config.new(<caret>)
# → No parameter hints shown in the popup
```

**Bad:** "Completion doesn't work for records." (No example, no caret
position, no detail about what "doesn't work" means.)

### Desired state

Show **what should happen instead** with a concrete example. For bugs, this
is the correct behavior. For features, this is the desired behavior. For UX
issues, this is the ideal workflow.

**Good:**
```crystal
Config.new(<caret>)
# → Popup shows:
#   host : String
#   port : Int32 = 80
```

**Bad:** "It should work." (Work how? What's the expected output?)

### Edge cases and related constellations

List related syntax patterns, boundary cases, or situations where the same
problem or feature applies. This is the field people skip — **don't skip it**.
It helps us:

- Understand the full scope of the problem
- Design the fix/feature correctly the first time
- Avoid regressions where the fix works for the reported case but breaks for
  a slightly different constellation

**Good:**
- Also happens with `Outer::Inner.new` (nested class)
- Does NOT happen when the class has `def self.new` defined
- Happens with zero-arg `Foo.new` and with args
- Should this also work for `struct` definitions?

**Bad:** "N/A" or leaving it empty.

> **Why is this required?** Taking 5 minutes to fill out all three fields
> often reveals that the "bug" is actually a missing feature, or that the
> "feature request" is already possible via a workaround, or that the real
> pain point is somewhere else entirely. That insight is valuable — it
> saves back-and-forth and sometimes makes the issue unnecessary. We'd
> rather you reflect and close your own draft than we spend time triaging a
> vague report.

## What Happens After You Submit

1. **Triage** — New issues get the `needs-triage` label. A maintainer reviews
   the issue within a few days, assigns labels, and either accepts it or
   asks for more info (`needs-info` label).

2. **Needs info** — If current/expected examples are missing or the reproduction
   doesn't work, the issue gets `needs-info` and a comment asking for
   specifics. If no response within 2 weeks, the issue is closed.

3. **Discussion** — Accepted issues stay open for discussion. Anyone can
   contribute ideas or edge cases in the comments.

4. **Implementation** — When someone picks up the issue, it gets an
   `in-progress` label. For code contributions, see below.

## Code Contributions

Pull requests are welcome! Before starting work on a larger change:

1. **Open an issue first** — describe the problem and proposed approach.
   This prevents duplicate work and ensures the direction aligns with the
   project's architecture.
2. **Read [AGENTS.md](AGENTS.md)** — it contains the project's coding
   conventions, build commands, architecture rules, and critical design
   decisions. All code contributions must follow these rules.
3. **Build and test** — `./gradlew build` (compile + tests). Every
   implementation must have unit tests (see AGENTS.md → Testing Conventions).

### Build from source

```bash
git clone https://github.com/magynhard/intellij-crystal.git
cd intellij-crystal
./gradlew build          # compile + tests
./gradlew runIde         # launch a dev IDE with the plugin loaded
```

Requires JDK 21. See [README.md](README.md) for full setup instructions.

## Discussions vs. Issues

| | Issues | Discussions |
|---|---|---|
| **Use for** | Concrete, actionable reports with current/expected examples | Vague ideas, questions, "what if..." brainstorming, design discussion |
| **Template** | Required (bug / feature / UX) | Free-form |
| **Action** | Gets triaged, labeled, eventually implemented or closed | Stays open for discussion, may become an issue later |

**When in doubt, start with Discussions.** You can always turn a discussion
into an issue once the idea is concrete enough.
