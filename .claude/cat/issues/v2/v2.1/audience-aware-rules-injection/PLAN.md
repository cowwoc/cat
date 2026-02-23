# Plan: Audience-Aware Rules Injection

## Goal
Control which rules/conventions get injected into main agent vs subagent context, reducing token waste from
orchestration rules being sent to implementation subagents.

## Background

### Native Claude Code behavior
`.claude/rules/` and `CLAUDE.md` are loaded by Claude Code's native `q4()` function for **both** the main agent and
all subagents. Claude Code natively supports a `paths` frontmatter property in `.claude/rules/` files:

```yaml
---
paths:
  - "*.java"
  - "src/main/**"
---
```

- **Unconditional rules**: No `paths` frontmatter — always loaded into context
- **Conditional rules**: Have `paths` frontmatter — only loaded when the agent operates on a file matching those globs
- Globs use `.gitignore`-style syntax (via the `ignore` library)

This native `paths` support controls **when** content loads (based on file being edited), but does NOT control **who**
receives it (main agent vs subagent). Both audiences receive the same content.

### Current CAT state
`.claude/cat/conventions/` holds on-demand coding standards (java.md, state-schema.md) that are loaded explicitly by
the agent when editing certain file types. These files currently use a `paths` frontmatter property and a
`stakeholders` property, but since they're not in `.claude/rules/`, Claude Code doesn't process the `paths` property
natively.

### Two-tier strategy

| Location | Loaded by | Purpose |
|----------|-----------|---------|
| `.claude/rules/` | Claude Code (native) | Content for **all** agents (main + every subagent) |
| `.claude/cat/rules/` | CAT hooks | Content that needs audience filtering |

**Principle:** `.claude/rules/` is only for content that should reach every agent unconditionally. Everything else
lives in `.claude/cat/rules/` where CAT controls who receives it.

### Frontmatter properties

Files in `.claude/cat/rules/` support these optional frontmatter properties:

```yaml
---
mainAgent: true              # Inject into main agent context (default: true)
subAgents: [all]             # Which subagent types receive this (default: [all])
paths: ["*.java"]            # Only inject when operating on matching files (default: always)
stakeholders: [design]       # Stakeholder tags for review workflows
---
```

`subAgents` values:
- `[all]` — inject into all subagents (default)
- `[]` — do not inject into any subagent
- `["cat:work-execute", "Explore"]` — inject only into specific subagent types

`mainAgent` values:
- `true` — inject into main agent (default)
- `false` — do not inject into main agent

`paths` values:
- Omitted — always inject (default)
- `["*.java", "*.xml"]` — only inject when operating on files matching these globs

CAT hooks implement `paths` filtering independently of Claude Code's native support, since files in `.claude/cat/rules/`
are not processed by Claude Code's native loader.

## Approach

### Phase 1: Audit content audience
Review all content in `.claude/rules/`, `CLAUDE.md`, and `.claude/cat/conventions/` to classify each section:
- **All agents, unconditional**: Content that every agent always needs → stays in `.claude/rules/`
- **mainAgent-only**: Orchestration rules → `.claude/cat/rules/` with `subAgents: []`
- **Both, conditional**: Coding conventions → `.claude/cat/rules/` with `paths` frontmatter
- **Subagent-only**: Rules only for subagents → `.claude/cat/rules/` with `mainAgent: false`

### Phase 2: Rename and reorganize
- Rename `.claude/cat/conventions/` to `.claude/cat/rules/`
- Move content out of `.claude/rules/` that doesn't belong to all agents
- Update all references across the codebase

### Phase 3: Implement hook-based injection
Modify `SessionStartHook` and `SubagentStartHook` to:
1. Discover files in `.claude/cat/rules/`
2. Parse frontmatter for `mainAgent`, `subAgents`, and `paths` properties
3. Filter based on current context (main agent vs subagent type, file being operated on)
4. Inject matching content via `additionalContext`

### Phase 4: Move content to appropriate locations
- Move orchestration-specific content from `.claude/rules/` into `.claude/cat/rules/` with `subAgents: []`
- Move coding conventions from `.claude/cat/conventions/` into `.claude/cat/rules/` with appropriate `paths` frontmatter
- Leave only truly universal content in `.claude/rules/`

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** Moving content out of `.claude/rules/` means it's no longer loaded natively by Claude Code; it depends
  on CAT hooks working correctly. If hooks fail, the main agent loses orchestration rules.
- **Mitigation:** Keep only critical safety rules that must never be lost in `.claude/rules/`. Orchestration content is
  non-critical — if hooks fail, the agent still functions, just without CAT workflow guidance.

## Files to Modify
- `.claude/cat/conventions/` — rename to `.claude/cat/rules/`
- `.claude/cat/conventions/INDEX.md` — update paths
- `.claude/cat/conventions/java.md` — move to `.claude/cat/rules/java.md`
- `.claude/cat/conventions/state-schema.md` — move to `.claude/cat/rules/state-schema.md`
- `client/src/main/java/io/github/cowwoc/cat/hooks/SessionStartHook.java` — add rules injection
- `client/src/main/java/io/github/cowwoc/cat/hooks/SubagentStartHook.java` — add rules injection with `paths` filtering
- `plugin/skills/` — update references from conventions to rules
- `CLAUDE.md` — update references and language conventions table
- `.claude/rules/common.md` — extract non-universal sections into `.claude/cat/rules/`
- `.claude/rules/hooks.md` — extract non-universal sections into `.claude/cat/rules/`

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Steps
1. **Audit content**: Review `.claude/rules/common.md`, `.claude/rules/hooks.md`, and `CLAUDE.md` to classify each
   section by audience
2. **Rename directory**: Rename `.claude/cat/conventions/` to `.claude/cat/rules/`, update all references
3. **Implement frontmatter parsing**: Implement YAML frontmatter parser for `mainAgent`, `subAgents`, and `paths`
   properties in Java
4. **Update SessionStartHook**: Discover and inject `.claude/cat/rules/` files filtered by `mainAgent: true` and
   `paths` matching
5. **Update SubagentStartHook**: Discover and inject `.claude/cat/rules/` files filtered by matching `subAgents` type
   and `paths` matching
6. **Add audience frontmatter**: Add appropriate frontmatter to all files in `.claude/cat/rules/`
7. **Move content**: Move non-universal sections from `.claude/rules/` into `.claude/cat/rules/` with appropriate
   audience frontmatter; move conventions from old directory
8. **Update CLAUDE.md**: Update convention file references and language conventions table
9. **Write user-facing documentation**: Document the two-tier rules system in a user-facing doc (e.g.,
   `plugin/concepts/rules-audience.md`), explaining when to use `.claude/rules/` vs `.claude/cat/rules/`, and the
   `mainAgent`, `subAgents`, and `paths` frontmatter properties
10. **Write tests**: Test frontmatter parsing, audience filtering, `paths` matching, and injection for both hooks

## Post-conditions
- [ ] `.claude/cat/conventions/` no longer exists; replaced by `.claude/cat/rules/`
- [ ] `.claude/rules/` contains only content intended for all agents unconditionally
- [ ] All files in `.claude/cat/rules/` have appropriate `mainAgent`/`subAgents` frontmatter
- [ ] SessionStartHook injects CAT rules where `mainAgent: true` (and `paths` match)
- [ ] SubagentStartHook injects CAT rules matching the subagent's type (and `paths` match)
- [ ] Orchestration-only content no longer reaches subagents
- [ ] All tests pass
- [ ] No references to `.claude/cat/conventions/` remain in codebase
- [ ] User-facing documentation explains the two-tier rules system with examples
