---
mainAgent: false
subAgents: []
---
# CAT Rules Index

Rules in this directory are injected by CAT hooks based on audience frontmatter properties. Each file
can declare who receives it using `mainAgent` and `subAgents` frontmatter:

```yaml
---
subAgents: []      # Do not inject into subagents
---
```

These properties have defaults and can be omitted: `mainAgent` defaults to `true`, and `subAgents` defaults to all.
See `plugin/concepts/rules-audience.md` for full documentation of the two-tier rules system.

Always-on shared rules live in `.cat/rules/common/`.

Path-specific shared rules live as `paths`-restricted files in `.cat/rules/common/`. Claude loads them through native
path matching. Codex main SessionStart writes path-scoped bodies into the CAT plugin data directory and injects
lazy-loading stubs that point there. Codex subagents reuse the main-agent manifest and body files instead of
regenerating them.

## Always-On Rules

| Rule File | Audience | Paths | Purpose |
|-----------|----------|-------|---------|
| [backwards-compatibility.md](backwards-compatibility.md) | all agents | always | Require migrations instead of legacy fallbacks |
| [caveman-guard.md](caveman-guard.md) | main agent only | always | External caveman drift guard consumed by SessionStart hook |
| [commit-types.md](commit-types.md) | main agent only | always | Enforce commit type selection and commit grouping rules |
| [convention-locations.md](convention-locations.md) | main agent only | always | Where to put end-user vs plugin-dev conventions |
| [issue-workflow.md](issue-workflow.md) | main agent only | always | Require issue workflow for plugin/client changes |
| [dependency-boundaries.md](dependency-boundaries.md) | main agent only | always | Keep dependencies and test seams owned by the relevant behavior |
| [edit-planning.md](edit-planning.md) | all agents | always | Plan independent edits together |
| [pre-existing-problems.md](pre-existing-problems.md) | all agents | always | Fix pre-existing problems when they violate issue goals |
| [report-problems.md](report-problems.md) | all agents | always | Report stale references |
| [runtime-duplication.md](runtime-duplication.md) | all agents | always | Keep engine-specific guidance in engine directories |
| [testing-requirements.md](testing-requirements.md) | main agent only | always | Enforce TDD and full verify before review |
| [testing-scope.md](testing-scope.md) | main agent only | always | Behavior-first boundaries for Bats, SPRT, and TestNG |
| [terminology.md](terminology.md) | all agents | always | Keep config terminology identical across code/docs |

## Path-Restricted Common Rules

| Rule File | Audience | Paths | Purpose |
|-----------|----------|-------|---------|
| [bug-workaround.md](bug-workaround.md) | all agents | `client/**` | Standard comment format for external bug workarounds |
| [cli-output-format.md](cli-output-format.md) | all agents | `client/**`, `plugin/**` | Choose CLI output by consumer |
| [data-structures.md](data-structures.md) | all agents | `client/**`, `plugin/**` | Standardize CAT-owned structured data |
| [documentation-style.md](documentation-style.md) | all agents | `*.md`, `*.java` | Documentation wording and line-wrapping conventions |
| [error-handling.md](error-handling.md) | all agents | `client/**`, `plugin/**` | Require meaningful fail-fast errors |
| [hooks.md](hooks.md) | main agent only | `client/plugin/hooks/**`, `client/**/hook/**`, `client/**/hooks/**`, `.claude/settings.json`, `.codex/**` | Engine-neutral hook guidance |
| [index-schema.md](index-schema.md) | all agents | `index.json`, `**/index.json` | Required schema for issue `index.json` files |
| [jackson.md](jackson.md) | all agents | `*.java` | Use shared Jackson JsonMapper |
| [java.md](java.md) | all agents | `*.java` | Java build, style, and testing conventions |
| [language-requirements.md](language-requirements.md) | all agents | `plugin/**`, `client/**` | Use supported project languages |
| [license-header.md](license-header.md) | all agents | `client/**`, `plugin/**`, `*.java`, `*.sh`, `*.md`, `*.toml` | Apply CAT license headers |
| [llm-to-java.md](llm-to-java.md) | all agents | `client/**` | Extract deterministic skill logic into Java |
| [multi-instance-safety.md](multi-instance-safety.md) | all agents | `plugin/**`, `client/**` | Isolate concurrent work |
| [naming-conventions.md](naming-conventions.md) | all agents | `client/**`, `plugin/**` | Standardize identifier casing |
| [plugin-development.md](plugin-development.md) | all agents | `plugin/**`, `client/**` | Edit source worktrees, not installed caches |
| [plugin-file-references.md](plugin-file-references.md) | all agents | `client/plugin/**` | Keep plugin references deployable |
| [requirements-api.md](requirements-api.md) | all agents | `client/**` | Use requirements.java |
| [scope-passing.md](scope-passing.md) | all agents | `*.java` | Pass scope objects directly |
| [shell-efficiency.md](shell-efficiency.md) | all agents | `*.sh` | Improve shell calls safely |
| [skill-loading.md](skill-loading.md) | all agents | `client/plugin/skill-sources/**`, `client/plugin/agents/**` | Skill loading model and marker-file rules |
| [skill-step-numbering.md](skill-step-numbering.md) | all agents | `client/plugin/skill-sources/**`, `client/plugin/agents/**` | Enforce sequential 1-based skill step numbering |
| [skills.md](skills.md) | all agents | `client/plugin/skills/**` | Keep skill instructions in first-use.md |
| [testing-conventions.md](testing-conventions.md) | all agents | `client/**` | Keep tests isolated and meaningful |
