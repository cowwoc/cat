---
paths: ["plugin/**"]
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Plugin File References Convention

Files deployed to end-user machines (under `plugin/`) must only reference other deployed paths. They must never
reference source-only paths that exist only in the developer's repository and are not shipped to end users.

## Definitions

**Deployed file** — Any file under `plugin/`. These files are packaged and installed on end-user machines via the
plugin distribution. End users have access to them at runtime.

**Source-only path** — Any path outside `plugin/` that exists only in the developer's repository. Common examples:

- `.claude/rules/` — Developer-facing conventions and rules (not shipped to end users)
- `.cat/rules/` — CAT system rules (not shipped to end users)
- `.cat/issues/` — Issue tracking artifacts (not shipped to end users)
- `client/src/` — Java source files (compiled to binaries; source not shipped)
- `docs/` — Project documentation (not shipped to end users)

## Rule

**Plugin files must not reference source-only paths.** A reference includes:

- A path in a `See <path>` instruction or note
- A path in a `See also` cross-reference
- A directive that reads a source-only file at runtime:

  ```
  !`cat .claude/rules/foo.md`
  ```
- An agent instruction like "Read `.claude/rules/foo.md` for details"

## Compliant Examples

**A plugin skill referencing another plugin file:**

```markdown
# my-skill/first-use.md
For enforcement rules, see `plugin/rules/my-rules.md`.
```

**A plugin hook referencing a plugin concept:**

```markdown
See `plugin/concepts/worktree-isolation.md` for context.
```

## Non-Compliant Examples

**A plugin skill referencing a developer-only rule file:**

```markdown
# my-skill/first-use.md
For enforcement rules, see `.claude/rules/common.md`.   ← WRONG: .claude/rules/ is not shipped
```

**A plugin agent referencing a CAT system rule:**

```markdown
For hook registration details, see `.cat/rules/hooks.md`.   ← WRONG: .cat/rules/ is not shipped
```

## Where Rules Belong

| Rule audience | Correct location |
|---------------|------------------|
| End users (shipped to their machines) | `plugin/rules/` |
| Plugin developers only (not shipped) | `.claude/rules/` |
| CAT system internals (not shipped) | `.cat/rules/` |

When a plugin file needs to document a convention that end users must follow, add the rule to `plugin/rules/` and
reference it from there. Do not point plugin files at `.claude/rules/` or `.cat/rules/`.
