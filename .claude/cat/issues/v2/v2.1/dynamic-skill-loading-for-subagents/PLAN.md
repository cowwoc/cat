# Plan: Dynamic Skill Loading for Subagents

## Goal
Enable subagents to dynamically load skills on demand via a CLI tool and hook-based suggestions, replacing static
frontmatter preloading with lazy loading triggered by ConsiderSkills hook on Edit/Write operations.

## Satisfies
None (infrastructure improvement)

## Risk Assessment
- **Risk Level:** HIGH
- **Concerns:** Large scope touching skill loading infrastructure, all agent definitions, and hook system. Subagents may
  ignore skill loading suggestions. Transition from static to dynamic loading could break existing workflows.
- **Mitigation:** Incremental implementation with empirical testing at each stage. Keep load-skill as fallback alongside
  frontmatter during transition.

## Files to Modify

### A. Rename ForcedEvalSkills → ConsiderSkills
- `client/src/main/java/io/github/cowwoc/cat/hooks/prompt/ForcedEvalSkills.java` → rename to ConsiderSkills.java
- `client/src/main/java/io/github/cowwoc/cat/hooks/PromptHandler.java` - update references
- `client/src/main/java/io/github/cowwoc/cat/hooks/GetUserPromptSubmitOutput.java` - update references
- `client/src/test/**/ForcedEvalSkillsTest.java` → rename to ConsiderSkillsTest.java

### B. ConsiderSkills fires on PreToolUse Edit/Write
- `client/src/main/java/io/github/cowwoc/cat/hooks/GetEditOutput.java` - integrate ConsiderSkills
- `client/src/main/java/io/github/cowwoc/cat/hooks/GetWriteEditOutput.java` - integrate ConsiderSkills
- New or updated handler that presents skill list for yes/no evaluation on Edit/Write, tells agent to load matching
  skills via load-skill

### C. Drop WarnSkillEditWithoutBuilder
- `client/src/main/java/io/github/cowwoc/cat/hooks/edit/WarnSkillEditWithoutBuilder.java` - delete
- `client/src/main/java/io/github/cowwoc/cat/hooks/GetEditOutput.java` - remove from handler list

### D. Drop ForcedEvalSkills from UserPromptSubmit
- `client/src/main/java/io/github/cowwoc/cat/hooks/GetUserPromptSubmitOutput.java` - remove ForcedEvalSkills handler

### E. Restructure first-use skills
- `plugin/skills/*-first-use/SKILL.md` → move to `plugin/skills/*/first-use.md` (all ~50 directories)
- Delete empty `-first-use` directories
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/SkillLoader.java` - update `loadRawContent()` to look for
  `first-use.md` inside skill directory

### F. catAgentId system
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/SkillLoader.java` - rename sessionId to catAgentId, move marker
  files to `~/.config/claude/projects/-workspace/{sessionId}/skills-loaded-{catAgentId}`
- `plugin/scripts/load-skill.sh` - rename arg 3 from SESSION_ID to CAT_AGENT_ID
- Counter file at `~/.config/claude/projects/-workspace/{sessionId}/agent-counter.txt`
- All skill SKILL.md files that call load-skill.sh - update arg references

### G. load-skill as preloaded skill
- `plugin/skills/load-skill/first-use.md` - new skill teaching subagents to use load-skill.sh CLI
- `plugin/agents/*.md` - replace all `skills:` lists with single `load-skill` entry

### H. Remove render-output
- `plugin/skills/render-output/` - delete directory
- `plugin/skills/render-output-first-use/` - delete directory

## Acceptance Criteria
- [ ] ForcedEvalSkills renamed to ConsiderSkills with all references updated
- [ ] ConsiderSkills fires on PreToolUse Edit/Write and presents skill list for agent evaluation
- [ ] WarnSkillEditWithoutBuilder removed
- [ ] ForcedEvalSkills removed from UserPromptSubmit
- [ ] All `-first-use` directories consolidated into `*/first-use.md`
- [ ] SkillLoader uses catAgentId for marker file scoping (not shared sessionId)
- [ ] Agent counter persists at `~/.config/claude/projects/-workspace/{sessionId}/agent-counter.txt`
- [ ] load-skill preloaded skill exists and is added to all subagent agent definitions
- [ ] All other skills removed from subagent frontmatter
- [ ] render-output skill removed
- [ ] Existing tests pass, new tests cover ConsiderSkills and catAgentId logic
- [ ] Subagent can dynamically load a skill via load-skill CLI and follow its instructions

## Execution Steps
1. **Step 1:** Rename ForcedEvalSkills → ConsiderSkills (class, tests, all references)
   - Files: `client/src/main/java/**/ForcedEvalSkills.java`, test files, GetUserPromptSubmitOutput.java
2. **Step 2:** Remove ForcedEvalSkills/ConsiderSkills from UserPromptSubmit hook
   - Files: GetUserPromptSubmitOutput.java
3. **Step 3:** Delete WarnSkillEditWithoutBuilder and remove from GetEditOutput handler list
   - Files: WarnSkillEditWithoutBuilder.java, GetEditOutput.java
4. **Step 4:** Implement ConsiderSkills as PreToolUse Edit/Write handler with skill list evaluation and additionalContext
   output
   - Files: GetEditOutput.java, GetWriteEditOutput.java, new ConsiderSkills handler
5. **Step 5:** Add catAgentId support to SkillLoader (rename sessionId, persistent marker files, counter file)
   - Files: SkillLoader.java, load-skill.sh
6. **Step 6:** Move all `-first-use/SKILL.md` to `*/first-use.md` and update SkillLoader path resolution
   - Files: All ~50 first-use directories, SkillLoader.java
7. **Step 7:** Create load-skill first-use.md skill and add to all subagent agent definitions
   - Files: plugin/skills/load-skill/first-use.md, plugin/agents/*.md
8. **Step 8:** Remove all other skills from subagent agent frontmatter
   - Files: plugin/agents/*.md
9. **Step 9:** Delete render-output and render-output-first-use directories
   - Files: plugin/skills/render-output/, plugin/skills/render-output-first-use/
10. **Step 10:** Run tests, validate subagent can dynamically load skills via load-skill CLI

## Success Criteria
- [ ] No `-first-use` sibling directories remain under plugin/skills/
- [ ] No subagent agent definition has more than `load-skill` in its skills frontmatter
- [ ] ConsiderSkills hook fires on Edit/Write and injects additionalContext (not stderr)
- [ ] Subagent editing SKILL.md receives skill-builder suggestion and can load it via CLI
- [ ] All tests pass
