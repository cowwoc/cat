# Plan: java-skill-handlers

## Metadata
- **Parent:** migrate-python-to-java
- **Sequence:** 3 of 5 (Wave 3 - concurrent with java-bash-handlers and java-other-handlers)
- **Estimated Tokens:** 35K

## Objective
Create Java equivalents for 5 missing skill handlers and verify all 11 existing Java skill handlers produce identical
output to Python.

## Scope
- 11 Java skill handlers already exist - verify output matches Python
- 5 Python skill handlers have NO Java equivalent yet - create them
- Utility classes (DisplayUtils, JsonHelper, etc.) already exist

## Dependencies
- java-core-hooks (entry points must be wired up)

## Existing Java Implementations (verify only)

Skill handlers at `plugin/hooks/src/io/github/cowwoc/cat/hooks/skills/`:

| Python | Java (exists) |
|--------|---------------|
| `skill_handlers/base.py` | `DisplayUtils.java` (shared box/display utilities) |
| `skill_handlers/add_handler.py` | `GetAddOutput.java` |
| `skill_handlers/cleanup_handler.py` | `GetCleanupOutput.java` |
| `skill_handlers/config_handler.py` | `GetConfigOutput.java` |
| `skill_handlers/help_handler.py` | `GetHelpOutput.java` |
| `skill_handlers/init_handler.py` | `GetInitOutput.java` |
| `skill_handlers/render_diff_handler.py` | `GetRenderDiffOutput.java` |
| `skill_handlers/research_handler.py` | `GetResearchOutput.java` |
| `skill_handlers/stakeholder_handler.py` | `GetStakeholderOutput.java` |
| `skill_handlers/token_report_handler.py` | `GetTokenReportOutput.java` |
| `skill_handlers/work_handler.py` | `GetWorkOutput.java` |

Supporting utility classes (already exist):
- `ItemType.java`, `JsonHelper.java`, `TaskType.java`, `TerminalType.java`

## Missing Java Implementations (create new)

| Python | Java (to create) | Location |
|--------|------------------|----------|
| `skill_handlers/delegate_handler.py` | `GetDelegateOutput.java` | `plugin/hooks/src/io/github/cowwoc/cat/hooks/skills/` |
| `skill_handlers/monitor_subagents_handler.py` | `GetMonitorSubagentsOutput.java` | `plugin/hooks/src/io/github/cowwoc/cat/hooks/skills/` |
| `skill_handlers/run_retrospective_handler.py` | `GetRunRetrospectiveOutput.java` | `plugin/hooks/src/io/github/cowwoc/cat/hooks/skills/` |
| `skill_handlers/status_handler.py` | `GetStatusOutput.java` | `plugin/hooks/src/io/github/cowwoc/cat/hooks/skills/` |
| `skill_handlers/work_with_issue_handler.py` | `GetWorkWithIssueOutput.java` | `plugin/hooks/src/io/github/cowwoc/cat/hooks/skills/` |

## Execution Steps
1. **Create GetDelegateOutput.java** - Port logic from `delegate_handler.py`
2. **Create GetMonitorSubagentsOutput.java** - Port logic from `monitor_subagents_handler.py`
3. **Create GetRunRetrospectiveOutput.java** - Port logic from `run_retrospective_handler.py`
4. **Create GetStatusOutput.java** - Port logic from `status_handler.py`
5. **Create GetWorkWithIssueOutput.java** - Port logic from `work_with_issue_handler.py`
6. **Register new handlers** in `GetSkillOutput.java` dispatcher
7. **Verify all 16 handlers** produce identical output to Python equivalents
8. **Verify box characters** (special chars render correctly in Java)
9. **Run test suite** - `python3 /workspace/run_tests.py` to verify no regressions

## Acceptance Criteria
- [ ] 5 new Java skill handlers created and registered
- [ ] All 16 skill handlers produce identical output to Python equivalents
- [ ] Box characters render correctly
- [ ] All existing tests pass
