# Plan: Dynamic Skill Loading for Subagents

## Goal
Enable subagents to discover and load skills dynamically. Inject the model-invocable skill list (names + descriptions)
and load-skill.sh usage instructions into subagent context at startup and after compaction. The dynamic listing includes
both which skills exist and how to load them via load-skill.sh. The main agent already receives the skill listing
natively from Claude Code; the injection targets subagents only.

## Satisfies
None (infrastructure improvement)

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** Skill discovery must match Claude Code's native format. Must not double-inject for the main agent.
  SubagentStart hook fires only at spawn; SessionStart with source="compact" fires after compaction for both agents.
- **Mitigation:** InjectSkillListing filters on source=="compact" to avoid duplicate injection at main agent startup.
  SubagentStartHook fires only for subagents (SubagentStart event). Format verified against Claude Code's native
  listing.

## Background

Claude Code provides model-invocable skill listings to the main agent at session startup and after compaction via its
native skill_listing system. Subagents spawned via the Task tool do NOT receive this listing — they only get skills
specified in `skills:` frontmatter (preloaded as prompt content).

Hook event behavior (verified from Claude Code source):

| Event | Main startup | Subagent startup | Main compaction | Subagent compaction |
|-------|-------------|-----------------|----------------|-------------------|
| SessionStart | YES ("startup") | NO | YES ("compact") | YES ("compact") |
| SubagentStart | NO | YES | NO | NO |

## Files to Modify

### A. SubagentStartHook (subagent spawn injection)
- `client/src/main/java/io/github/cowwoc/cat/hooks/SubagentStartHook.java` — NEW hook handler
- `plugin/hooks/hooks.json` — register SubagentStart hook
- `client/build-jlink.sh` — add subagent-start entry point

### B. InjectSkillListing (post-compaction injection, subagents only)
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/InjectSkillListing.java` — fires on SessionStart
  source=="compact", injects skill listing for subagents after compaction

### C. SkillDiscovery (shared discovery logic)
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/SkillDiscovery.java` — NEW utility extracting skill
  discovery from plugin skills, project commands, and user skills directories

### D. Rename hooks for clarity
- `GetEditOutput.java` → `PreEditHook.java`
- `GetWriteEditOutput.java` → `WriteEditHook.java`

### E. Remove obsolete handlers
- Delete `WarnSkillEditWithoutBuilder.java`
- Delete `ForcedEvalSkills.java` (renamed to ConsiderSkills, then removed entirely)
- Remove ForcedEvalSkills/ConsiderSkills from UserPromptSubmit handler list

### F. Restructure first-use skills
- `plugin/skills/*-first-use/SKILL.md` → move to `plugin/skills/*/first-use.md` (all ~50 directories)
- Delete empty `-first-use` directories
- Update `SkillLoader.java` path resolution

### G. catAgentId system
- `SkillLoader.java` — rename sessionId to catAgentId for per-agent marker file scoping
- `load-skill.sh` — rename arg 3 from SESSION_ID to CAT_AGENT_ID
- Counter file at `~/.config/claude/projects/-workspace/{sessionId}/agent-counter.txt`

### H. ClearSkillMarkers improvements
- `ClearSkillMarkers.java` — log warnings on deletion/directory listing failures instead of silent swallow

## Acceptance Criteria
- [ ] SubagentStartHook injects model-invocable skill list into subagent context at spawn
- [ ] InjectSkillListing reinjects skill list after subagent compaction (SessionStart source=="compact")
- [ ] Skill listing entry format matches Claude Code's native format ("- name: description")
- [ ] Skill listing is NOT injected into the main agent (no duplicate with Claude Code's native listing)
- [ ] SkillDiscovery discovers plugin skills, project commands, and user skills
- [ ] Skills with `model-invocable: false` frontmatter are excluded from the listing
- [ ] GetEditOutput renamed to PreEditHook, GetWriteEditOutput renamed to WriteEditHook
- [ ] WarnSkillEditWithoutBuilder removed
- [ ] ForcedEvalSkills/ConsiderSkills removed from UserPromptSubmit
- [ ] All `-first-use` directories consolidated into `*/first-use.md`
- [ ] SkillLoader uses catAgentId for marker file scoping
- [ ] Skill listing includes load-skill.sh usage instructions in header
- [ ] ClearSkillMarkers logs warnings on failures
- [ ] Existing tests pass, new tests cover SkillDiscovery and SubagentStartHook

## Execution Steps
1. **Step 1:** Rename ForcedEvalSkills → ConsiderSkills, then remove from UserPromptSubmit
2. **Step 2:** Delete WarnSkillEditWithoutBuilder, remove from PreEditHook handler list
3. **Step 3:** Rename GetEditOutput → PreEditHook, GetWriteEditOutput → WriteEditHook
4. **Step 4:** Create SkillDiscovery utility with shared skill discovery logic
5. **Step 5:** Create SubagentStartHook that injects skill listing via SubagentStart event
6. **Step 6:** Update InjectSkillListing to use SkillDiscovery and only fire on source=="compact"
7. **Step 7:** Match Claude Code's native skill listing format in formatSkillListing()
8. **Step 8:** Add catAgentId support to SkillLoader
9. **Step 9:** Move all `-first-use/SKILL.md` to `*/first-use.md`, update SkillLoader
10. **Step 10:** Update ClearSkillMarkers to log warnings on failures
11. **Step 11:** Run tests, verify skill listing appears in subagent context but not main agent

## Success Criteria
- [ ] SubagentStartHook registered in hooks.json and fires for subagents
- [ ] Subagent receives skill listing with load-skill.sh instructions and skill entries
- [ ] Main agent does NOT receive duplicate skill listing from our hooks
- [ ] No `-first-use` sibling directories remain under plugin/skills/
- [ ] Subagent can load a skill via load-skill.sh when it identifies one from the listing
- [ ] All tests pass
