<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Skill Loading Reference

How to register, invoke, and reference skills for main agents and subagents in Claude Code.

## Skill Types

| Type | Location | Name Format | Example |
|------|----------|-------------|---------|
| Plugin skill | `plugin/skills/{name}/` | `cat:{name}` | `cat:git-squash` |
| Project skill | `.claude/skills/{name}/` | `{name}` | `my-validator` |
| User skill | `~/.config/claude/skills/{name}/` | `{name}` | `code-review` |

**Linux path note:** User skills are at `~/.config/claude/skills/`, not `~/.claude/skills/`.

## Skill Directory Structure

```
{skill-name}/
  SKILL.md        — Frontmatter (description, user-invocable) + preprocessor directive or content
{skill-name}-first-use/
  SKILL.md        — Companion skill content as plain markdown
reference.md      — Short content returned on subsequent invocations (optional; empty if absent)
```

## Invoking Skills

### Via Skill Tool (recommended for all skill types)

The Skill tool triggers the full SkillLoader pipeline:

1. Claude Code routes to the skill's SKILL.md
2. SKILL.md preprocessor directive (`!` backtick) calls `load-skill.sh`
3. `load-skill.sh` invokes `SkillLoader.java`
4. SkillLoader checks marker file, returns full content or `reference.md`
5. Variable substitution and `@path` expansion run on the returned content

```
Skill tool:
  skill: "cat:git-squash"
  args: "optional arguments"
```

**This works in both main agent and subagents.** The Skill tool is available to all agent types, and
SkillLoader runs correctly in subagent context.

### Via `skills:` Frontmatter (agent definitions only)

The `skills:` field in agent YAML frontmatter injects skill content into a subagent's context at spawn
time.

```yaml
---
name: my-agent
skills:
  - cat:my-plugin-skill
---
```

**How it works:**
1. Claude Code looks up the skill name using `name`, `userFacingName()`, or `aliases`
2. Reads the skill's SKILL.md content
3. Runs the `!` backtick preprocessor on the content (expanding directives)
4. Injects the processed result into the subagent's system prompt

**Confirmed via empirical test:** A plugin skill with `!`echo "MARKER"`` in its SKILL.md had the
directive expanded when injected via frontmatter. The subagent saw the expanded output, not the raw
directive. Debug log: `[Agent: cat:preprocess-tester] Preloaded skill 'cat:preprocess-test'`.

**Limitations:**
- Only works when agent is spawned via the **Task tool** (not `--agent` CLI flag)
- Agent must be discovered at session start (cannot be created mid-session)
- Shares the parent's `CLAUDE_SESSION_ID`, so SkillLoader marker files are shared state — if the
  parent already invoked the skill, frontmatter injection gets `reference.md` instead of full content

**When to use:** For preloading skill content into subagents at spawn time. Works for both plain-text
and preprocessor-based plugin skills. Be aware of the shared marker file issue when the same skill is
also invoked by the parent agent.

## `-first-use` Skill Pattern

The `-first-use` pattern provides a companion skill directory for subagent preloading. The companion SKILL.md
contains the full static instructions for the skill and is injected directly into the subagent's context via
`skills:` frontmatter.

### Architecture

```
plugin/skills/
  {skill-name}/
    SKILL.md            — Main skill entry point (preprocessor directive calls SkillLoader)
  {skill-name}-first-use/
    SKILL.md            — Companion: full static instructions for subagent preloading
```

The companion SKILL.md contains:

```markdown
---
description: "Internal skill for subagent preloading. Do not invoke directly."
user-invocable: false
---

# Skill Title

Full skill instructions as plain markdown. These instructions are injected directly into the
subagent's context when the agent is spawned via the Task tool.
```

### Loading Paths

| Loader Path | How Invoked | Session Marker Used? |
|-------------|-------------|----------------------|
| Main agent via Skill tool | `cat:{skill-name}` | Yes — first-use vs reference |
| Subagent via `skills:` frontmatter | `cat:{skill-name}-first-use` | No — SKILL.md injected directly |

**Why subagents reference `-first-use` directly:**
- Subagents use `skills:` frontmatter, which preprocesses and injects SKILL.md content
- By pointing at `{skill-name}-first-use`, subagents get the full instructions in one step
- This bypasses the SkillLoader marker file, so the parent's "already loaded" state doesn't affect the
  subagent

### Creating a `-first-use` Companion Skill

1. Create directory: `plugin/skills/{skill-name}-first-use/`
2. Create `SKILL.md` with:
   - YAML frontmatter (`user-invocable: false`)
   - Full skill instructions as plain markdown
3. Update any subagent frontmatter that preloads the skill to reference `cat:{skill-name}-first-use`

### Visibility

The companion skill must have `user-invocable: false` in its YAML frontmatter to prevent users from
discovering or invoking it directly. It is an internal implementation detail of the parent skill.

## Session Markers (First-Use vs Reference)

SkillLoader tracks which skills have been loaded via marker files:

```
/tmp/cat-skills-loaded-{sessionId}
```

- **First invocation:** Returns full content (from `-first-use/SKILL.md`), writes skill name to marker file
- **Subsequent invocations:** Returns `reference.md` (or empty string if no reference.md exists)

### Shared Session ID Between Parent and Subagents

Subagents share the parent's `CLAUDE_SESSION_ID`. This means the marker file is shared state:

| Scenario | Result |
|----------|--------|
| Parent invokes skill, then subagent invokes same skill | Subagent gets `reference.md` (usually empty) |
| Subagent invokes skill first | Subagent gets full content; parent later gets `reference.md` |
| Two subagents invoke same skill | First gets full content; second gets `reference.md` |

**Implication:** If a skill must be available to both parent and subagent with full content, the
`reference.md` file must contain sufficient content for the second invocation. Most CAT skills lack a
`reference.md` file, which means the second invocation returns an empty string.

## Plugin Skill Name Resolution

Plugin skills are registered with the `cat:` prefix. The lookup function matches against three
properties:

```
A.name === query || A.userFacingName() === query || A.aliases?.includes(query)
```

| Query | Matches Plugin Skill? | Notes |
|-------|----------------------|-------|
| `cat:git-squash` | Yes | Matches `userFacingName()` |
| `git-squash` | Maybe | May match `name` property (depends on internal registration) |

**Best practice:** Always use the `cat:` prefix when referencing plugin skills in agent frontmatter or
Skill tool invocations to ensure reliable resolution.

## Patterns

### Subagent Needs Skill Content

**Both approaches work** — choose based on whether you want the content at spawn time or on demand.

```yaml
# ✅ Frontmatter: content injected at spawn (preprocessor runs)
---
skills:
  - cat:git-merge-linear-first-use
---

# ✅ Skill tool: content loaded on demand during execution
prompt: |
  Invoke /cat:git-merge-linear via the Skill tool before merging.
```

**Trade-offs:**
- **Frontmatter**: content is always available, no extra tool call needed. But uses tokens even if the
  subagent doesn't need the skill for every execution path.
- **Skill tool**: content loaded only when needed. But requires an extra tool call round-trip.

### Shared Marker File Caveat

Parent and subagent share `CLAUDE_SESSION_ID`, so the marker file at
`/tmp/cat-skills-loaded-{sessionId}` is shared state. If the parent already invoked a skill (or a
previous subagent did), subsequent invocations — whether via Skill tool or frontmatter injection —
get `reference.md` instead of full content.

**Workarounds:**
1. Let only the subagent invoke the skill (don't pre-invoke in parent)
2. Pass the skill output from parent to subagent via the delegation prompt
3. Ensure the skill has a meaningful `reference.md` file

### Creating a New Plugin Skill

1. Create directory: `plugin/skills/{skill-name}/`
2. Create `SKILL.md` with frontmatter and preprocessor directive
3. Create `plugin/skills/{skill-name}-first-use/SKILL.md` with skill content (using the `-first-use`
   companion pattern -- see above)
4. Optionally create `reference.md` for subsequent invocations
5. The skill is automatically available as `cat:{skill-name}`

### Creating a New Project Skill

1. Create directory: `.claude/skills/{skill-name}/`
2. Create `SKILL.md` with content (no preprocessor needed)
3. The skill is available as `{skill-name}` within the project
