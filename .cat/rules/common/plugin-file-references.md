---
paths: ["client/plugin/**"]
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Plugin File References Convention

Files deployed to end-user machines (under `client/plugin/`) must only reference other deployed paths. They must never
reference source-only paths that exist only in the developer's repository and are not shipped to end users.

## Definitions

**Deployed file** — Any file under `client/plugin/`. These files are packaged and installed on end-user machines via
the plugin distribution. End users have access to them at runtime.

**Source-only path** — Any path outside `client/plugin/` that exists only in the developer's repository. Common
examples:

- Runtime-specific project rule directories — developer-facing conventions and rules (not shipped to end users)
- `.cat/issues/` — Issue tracking artifacts (not shipped to end users)
- `client/src/` — Java source files (compiled to binaries; source not shipped)
- `docs/` — Project documentation (not shipped to end users)

## Rule

**Plugin files must not reference source-only paths.** A reference includes:

- A path in a `See <path>` instruction or note
- A path in a `See also` cross-reference
- A directive that reads a source-only file at runtime:

  ```
  !`cat .cat/rules/common/foo.md`
  ```
- An agent instruction like "Read `.cat/rules/common/foo.md` for details"

## Compliant Examples

**A plugin skill referencing another plugin file:**

```markdown
# my-skill/first-use.md
For enforcement rules, see `client/plugin/rules/common/my-rules.md`.
```

**A plugin hook referencing a plugin concept:**

```markdown
See `client/plugin/concepts/worktree-isolation.md` for context.
```

## Non-Compliant Examples

**A plugin skill referencing a developer-only rule file:**

```markdown
# my-skill/first-use.md
For enforcement rules, see `.cat/rules/common/foo.md`.   ← WRONG: project-local rules are not shipped
```

**A plugin agent referencing a project-local CAT rule path:**

```markdown
For hook registration details, see `.cat/rules/common/hooks.md`.   ← WRONG: .cat/rules/ is not shipped
```

## Where Rules Belong

| Rule audience | Correct location |
|---------------|------------------|
| End users (shipped to their machines) | `client/plugin/rules/common/` |
| Plugin developers only (not shipped) | `.cat/rules/common/` or runtime-specific project rule directories |
| Runtime-specific shipped rules | `client/plugin/rules/<runtime>/` |

When a plugin file needs to document a convention that end users must follow across runtimes, add the rule to
`client/plugin/rules/common/` and reference it from there. Do not point plugin files at project-local rule paths.
