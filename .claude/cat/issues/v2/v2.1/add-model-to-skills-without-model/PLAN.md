# Plan: add-model-to-skills-without-model

## Goal
Add explicit `model:` frontmatter to all 44 skill SKILL.md files that currently lack a model specification, using haiku
for mechanical/procedural skills and sonnet for skills requiring reasoning or judgment.

## Satisfies
None - infrastructure improvement for cost optimization and consistent behavior

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Wrong model assignment could degrade skill quality (too weak) or waste tokens (too strong)
- **Mitigation:** Model assignments reviewed against skill complexity; git skills with conflict handling use sonnet

## Model Assignments

### haiku (22 skills) — Mechanical, procedural, templated, display rendering

| Skill | Rationale |
|-------|-----------|
| `batch-read` | Read multiple files — mechanical |
| `collect-results` | Collects subagent outputs — structured |
| `extract-investigation-context` | Extracts context from JSONL — mechanical |
| `feedback` | Files GitHub issues — templated |
| `format-documentation` | Line wrapping/formatting — mechanical |
| `get-history` | Reads and presents session logs |
| `get-session-id` | Returns a single variable value |
| `git-amend` | Safety checks + amend — procedural validation only |
| `git-commit` | Commit message guidance — procedural |
| `grep-and-read` | Search + read shortcut — mechanical |
| `load-skill` | Loads and returns skill content |
| `merge-subagent` | Branch merge — procedural |
| `monitor-subagents` | Status checks — structured |
| `register-hook` | Hook setup — templated |
| `safe-rm` | Directory safety — procedural |
| `stakeholder-concern-box` | Renders a display box |
| `stakeholder-review-box` | Renders a display box |
| `stakeholder-selection-box` | Renders a display box |
| `validate-git-safety` | Pre-flight checks — procedural |
| `work-complete` | Generates summary box |
| `work-merge` | Squash + merge — follows script |
| `write-and-commit` | Create + commit — mechanical |

### sonnet (22 skills) — Reasoning, analysis, judgment, conflict resolution

| Skill | Rationale |
|-------|-----------|
| `add` | Issue creation requires understanding scope/requirements |
| `compare-docs` | Semantic equivalence analysis |
| `decompose-issue` | Breaking down issues requires architectural judgment |
| `delegate` | Orchestration decisions |
| `empirical-test` | Controlled experiments require careful reasoning |
| `git-merge-linear` | Merge conflicts require judgment |
| `git-rebase` | Conflict resolution, backup/recovery decisions |
| `git-rewrite-history` | History rewriting — high risk |
| `git-squash` | Squash failures need recovery reasoning |
| `init` | Project structure decisions |
| `learn` | Root cause analysis requires reasoning |
| `optimize-execution` | Session analysis requires deep reasoning |
| `research` | Technical investigation requires deep reasoning |
| `run-retrospective` | Pattern analysis across learnings |
| `safe-remove-code` | Safe code removal requires understanding |
| `shrink-doc` | Compression with semantic preservation |
| `skill-builder` | Backward reasoning for skill design |
| `stakeholder-review` | Multi-perspective code review |
| `tdd-implementation` | TDD requires code understanding |
| `work` | Full orchestration of issue lifecycle |
| `work-prepare` | Issue selection with filter interpretation |
| `work-with-issue` | Phase orchestration with judgment calls |

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Steps
1. For each skill in the haiku list, add `model: haiku` to the SKILL.md frontmatter
2. For each skill in the sonnet list, add `model: sonnet` to the SKILL.md frontmatter
3. Run `mvn -f client/pom.xml verify` to ensure no build regressions

## Post-conditions
- [ ] All 44 skills have `model:` specified in frontmatter
- [ ] No skill SKILL.md file lacks a model specification
- [ ] haiku assigned to 22 mechanical/procedural skills
- [ ] sonnet assigned to 22 reasoning/judgment skills
- [ ] Build passes with no regressions
