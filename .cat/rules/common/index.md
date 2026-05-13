---
mainAgent: false
subAgents: []
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# CAT Rules Index

Rules in this directory are injected by CAT hooks based on audience frontmatter properties. Each file
can declare who receives it using `mainAgent`, `subAgents`, and `paths` frontmatter:

```yaml
---
paths: ["*.java"]      # Only inject when operating on matching files (default: always)
---
```

All properties have defaults and can be omitted: `mainAgent` defaults to `true`, `subAgents` defaults to
all, `paths` defaults to always inject.
See `plugin/concepts/rules-audience.md` for full documentation of the two-tier rules system.

## Rules in This Directory

| Rule File | Audience | Paths | Purpose |
|-----------|----------|-------|---------|
| [backwards-compatibility.md](backwards-compatibility.md) | all agents | always | Require migrations instead of legacy fallbacks |
| [cli-output-format.md](cli-output-format.md) | all agents | `client/**`, `plugin/**` | Choose CLI output by consumer |
| [data-structures.md](data-structures.md) | all agents | `client/**`, `plugin/**` | Standardize CAT-owned structured data |
| [hooks.md](hooks.md) | main agent only | always | Runtime-neutral hook guidance |
| [convention-locations.md](convention-locations.md) | main agent only | always | Where to put end-user vs plugin-dev conventions |
| [bug-workaround.md](bug-workaround.md) | all agents | `plugin/**`, `client/**` | Standard comment format for external bug workarounds |
| [documentation-style.md](documentation-style.md) | all agents | `*.md` | Documentation wording and line-wrapping conventions |
| [edit-planning.md](edit-planning.md) | all agents | always | Plan independent edits together |
| [error-handling.md](error-handling.md) | all agents | `client/**`, `plugin/**` | Require meaningful fail-fast errors |
| [index-schema.md](index-schema.md) | all agents | `index.json`, `**/index.json` | Required schema for issue `index.json` files |
| [jackson.md](jackson.md) | all agents | `*.java` | Use shared Jackson JsonMapper |
| [license-header.md](license-header.md) | all agents | always | Apply CAT license headers |
| [language-requirements.md](language-requirements.md) | all agents | `plugin/**`, `client/**` | Use supported project languages |
| [llm-to-java.md](llm-to-java.md) | all agents | `plugin/**`, `client/**` | Extract deterministic skill logic into Java |
| [java.md](java.md) | all agents | `*.java` | Java build, style, and testing conventions |
| [multi-instance-safety.md](multi-instance-safety.md) | all agents | `plugin/**`, `client/**` | Isolate concurrent work |
| [naming-conventions.md](naming-conventions.md) | all agents | `client/**`, `plugin/**` | Standardize identifier casing |
| [pre-existing-problems.md](pre-existing-problems.md) | all agents | always | Fix pre-existing problems when they violate issue goals |
| [plugin-development.md](plugin-development.md) | all agents | `plugin/**`, `client/**` | Edit source worktrees, not installed caches |
| [plugin-file-references.md](plugin-file-references.md) | all agents | `client/plugin/**` | Keep plugin references deployable |
| [report-problems.md](report-problems.md) | all agents | always | Report stale references |
| [requirements-api.md](requirements-api.md) | all agents | `client/**` | Use requirements.java |
| [skill-loading.md](skill-loading.md) | all agents | `plugin/skill-sources/**`, `plugin/agents/**` | Skill loading model and marker-file rules |
| [shell-efficiency.md](shell-efficiency.md) | all agents | `*.sh` | Improve shell calls safely |
| [scope-passing.md](scope-passing.md) | all agents | `*.java` | Pass scope objects directly |
| [skills.md](skills.md) | all agents | `client/plugin/skills/**` | Keep skill instructions in first-use.md |
| [skill-step-numbering.md](skill-step-numbering.md) | all agents | `plugin/skill-sources/**`, `plugin/agents/**` | Enforce sequential 1-based skill step numbering |
| [testing-conventions.md](testing-conventions.md) | all agents | `client/**` | Keep tests isolated and meaningful |
| [terminology.md](terminology.md) | all agents | always | Keep config terminology identical across code/docs |
