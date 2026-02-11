# Plan: port-standalone-scripts

## Current State
Three independent scripts with no inter-dependencies: `create-issue.py` (175 lines) creates issue
directory structure and commits, `load-skill.sh` (47 lines) loads skill files with env var
substitution, `get-progress-banner.sh` (250 lines) renders progress banners during work.

## Target State
Java equivalents in the hooks module that produce identical output.

## Satisfies
Parent: 2.1-port-workflow-scripts (sub-issue 2 of 4)

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** Scripts are used by skills but output contracts are simple
- **Mitigation:** Verify JSON output and banner rendering match originals

## Scripts to Port
- `plugin/scripts/create-issue.py` (175 lines) - Issue directory creation
  - Creates directory structure: STATE.md, PLAN.md
  - Commits with appropriate message
  - Output: JSON with status, issue_path, message
- `plugin/scripts/load-skill.sh` (47 lines) - Skill file loading
  - Reads SKILL.md files
  - Substitutes environment variables (CLAUDE_SESSION_ID, CLAUDE_PROJECT_DIR, etc.)
  - Output: processed skill content
- `plugin/scripts/get-progress-banner.sh` (250 lines) - Progress banner rendering
  - Renders phase-based progress banners (Preparing/Executing/Reviewing/Merging)
  - Uses box-drawing characters and phase symbols (○ ● ◉ ✗)
  - Output: formatted banner text

## Files to Create
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/util/IssueCreator.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/util/SkillLoader.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/util/ProgressBanner.java`
- Test files for all classes

## Files to Modify
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/module-info.java` - if new exports needed

## Execution Steps
1. Read `create-issue.py` - understand directory creation and commit logic
2. Read `load-skill.sh` - understand env var substitution patterns
3. Read `get-progress-banner.sh` - understand banner rendering with box drawing
4. Implement `IssueCreator.java` with directory creation and git commit
5. Implement `SkillLoader.java` with skill file loading and env var substitution
6. Implement `ProgressBanner.java` with phase-based progress rendering
7. Write tests for all classes
8. Run `mvn -f hooks/pom.xml verify`
9. Update STATE.md (status: closed, progress: 100%)

## Success Criteria
- [ ] IssueCreator produces identical JSON output and directory structure
- [ ] SkillLoader performs identical env var substitution
- [ ] ProgressBanner renders identical banner output
- [ ] All tests pass (`mvn -f hooks/pom.xml verify`)
