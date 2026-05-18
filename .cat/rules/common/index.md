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

Path-specific shared rule bodies live in `.cat/rules/include/`. Runtime directories decide how to expose them:
- `.cat/rules/claude/` uses `paths` frontmatter plus `cat:include` stubs so Claude's CAT hook can load the bodies on
  demand.
- `.cat/rules/codex/` uses always-loaded stubs that declare `paths` and `include`, then apply
  [rule-loading.md](../codex/rule-loading.md), because Codex does not natively load `paths` rules on demand.

## Always-On Rules

| Rule File | Audience | Paths | Purpose |
|-----------|----------|-------|---------|
| [backwards-compatibility.md](backwards-compatibility.md) | all agents | always | Require migrations instead of legacy fallbacks |
| [commit-types.md](commit-types.md) | main agent only | always | Enforce commit type selection and commit grouping rules |
| [convention-locations.md](convention-locations.md) | main agent only | always | Where to put end-user vs plugin-dev conventions |
| [issue-workflow.md](issue-workflow.md) | main agent only | always | Require issue workflow for plugin/client changes |
| [approval-gate.md](approval-gate.md) | main agent only | always | Re-squash commits before every approval gate |
| [dependency-boundaries.md](dependency-boundaries.md) | main agent only | always | Keep dependencies and test seams owned by the relevant behavior |
| [edit-planning.md](edit-planning.md) | all agents | always | Plan independent edits together |
| [hooks.md](hooks.md) | main agent only | always | Runtime-neutral hook guidance |
| [license-header.md](license-header.md) | all agents | always | Apply CAT license headers |
| [pre-existing-problems.md](pre-existing-problems.md) | all agents | always | Fix pre-existing problems when they violate issue goals |
| [report-problems.md](report-problems.md) | all agents | always | Report stale references |
| [runtime-duplication.md](runtime-duplication.md) | all agents | always | Keep runtime-specific guidance in runtime directories |
| [testing-requirements.md](testing-requirements.md) | main agent only | always | Enforce TDD and full verify before review |
| [testing-scope.md](testing-scope.md) | main agent only | always | Behavior-first boundaries for Bats, SPRT, and TestNG |
| [terminology.md](terminology.md) | all agents | always | Keep config terminology identical across code/docs |

## Included Files

| Rule File | Paths | Purpose |
|-----------|-------|---------|
| [bug-workaround.md](../include/bug-workaround.md) | `plugin/**`, `client/**` | Standard comment format for external bug workarounds |
| [cli-output-format.md](../include/cli-output-format.md) | `client/**`, `plugin/**` | Choose CLI output by consumer |
| [data-structures.md](../include/data-structures.md) | `client/**`, `plugin/**` | Standardize CAT-owned structured data |
| [documentation-style.md](../include/documentation-style.md) | `*.md` | Documentation wording and line-wrapping conventions |
| [error-handling.md](../include/error-handling.md) | `client/**`, `plugin/**` | Require meaningful fail-fast errors |
| [index-schema.md](../include/index-schema.md) | `index.json`, `**/index.json` | Required schema for issue `index.json` files |
| [jackson.md](../include/jackson.md) | `*.java` | Use shared Jackson JsonMapper |
| [java.md](../include/java.md) | `*.java` | Java build, style, and testing conventions |
| [language-requirements.md](../include/language-requirements.md) | `plugin/**`, `client/**` | Use supported project languages |
| [llm-to-java.md](../include/llm-to-java.md) | `plugin/**`, `client/**` | Extract deterministic skill logic into Java |
| [multi-instance-safety.md](../include/multi-instance-safety.md) | `plugin/**`, `client/**` | Isolate concurrent work |
| [naming-conventions.md](../include/naming-conventions.md) | `client/**`, `plugin/**` | Standardize identifier casing |
| [plugin-development.md](../include/plugin-development.md) | `plugin/**`, `client/**` | Edit source worktrees, not installed caches |
| [plugin-file-references.md](../include/plugin-file-references.md) | `client/plugin/**` | Keep plugin references deployable |
| [requirements-api.md](../include/requirements-api.md) | `client/**` | Use requirements.java |
| [scope-passing.md](../include/scope-passing.md) | `*.java` | Pass scope objects directly |
| [shell-efficiency.md](../include/shell-efficiency.md) | `*.sh` | Improve shell calls safely |
| [skill-loading.md](../include/skill-loading.md) | `plugin/skill-sources/**`, `plugin/agents/**` | Skill loading model and marker-file rules |
| [skill-step-numbering.md](../include/skill-step-numbering.md) | `plugin/skill-sources/**`, `plugin/agents/**` | Enforce sequential 1-based skill step numbering |
| [skills.md](../include/skills.md) | `client/plugin/skills/**` | Keep skill instructions in first-use.md |
| [testing-conventions.md](../include/testing-conventions.md) | `client/**` | Keep tests isolated and meaningful |
