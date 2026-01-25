# Plan: branching-strategy-config

## Goal
Add git workflow configuration to /cat:init using a conversational wizard that asks clarifying
questions until it fully understands the user's preferred workflow. Document the workflow precisely
in PROJECT.md using RFC 2119 terminology (MUST/SHOULD/MAY) so skills can apply it without ambiguity.

## Satisfies
None - workflow improvement

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** Must handle various branching strategies without breaking existing workflows
- **Mitigation:** Default to current behavior if no strategy configured; wizard confirms understanding

## Design Principles

### Input: Flexible and Conversational
- Wizard asks open-ended questions, not just multiple-choice
- Follow-up questions clarify ambiguities
- Support free-form descriptions ("We use version branches, squash by topic...")
- Continue asking until agent is confident it understands completely

### Output: Precise and Machine-Parseable
- Document in PROJECT.md "## Git Workflow" section
- Use RFC 2119 keywords: MUST, MUST NOT, SHOULD, SHOULD NOT, MAY
- Include concrete examples showing correct/incorrect usage
- Use tables for structured options
- Skills can reliably parse and apply rules

### Clarification Philosophy
Agent MUST ask follow-up questions when:
- User uses ambiguous terms ("we usually...", "sometimes...")
- User's description doesn't cover a dimension (branching, merge style, squash, commits)
- User's answers seem inconsistent
- Agent is less than 90% confident it understands correctly

## Files to Modify
- plugin/commands/init.md - Add git workflow wizard section
- plugin/commands/work.md - Read PROJECT.md, validate branch, apply preferences
- plugin/commands/add.md - Read PROJECT.md for branch handling
- plugin/skills/git-commit/SKILL.md - Read PROJECT.md commit format
- plugin/skills/git-merge-linear/SKILL.md - Read PROJECT.md merge/squash preferences
- plugin/skills/git-squash/SKILL.md - Read PROJECT.md squash preferences
- plugin/skills/git-rebase/SKILL.md - Read PROJECT.md, warn on preference conflicts
- plugin/templates/project.md - Add Git Workflow section template

## Execution Steps

### Phase 1: Wizard Implementation

1. **Step 1:** Add git workflow wizard to /cat:init
   - Files: plugin/commands/init.md
   - Add new section after project setup for git workflow configuration
   - Wizard covers 4 dimensions: branching, merge style, squash policy, commit format
   - Use conversational approach with follow-up clarifications
   - Verify: Wizard asks questions and synthesizes understanding

2. **Step 2:** Implement iterative clarification loop
   - Files: plugin/commands/init.md
   - After initial questions, present synthesized understanding to user
   - Ask: "Did I understand correctly? Is there anything I missed or got wrong?"
   - If user corrects, update understanding and re-confirm
   - Loop until user confirms understanding is complete
   - Verify: User explicitly confirms wizard understood correctly

3. **Step 3:** Generate PROJECT.md Git Workflow section
   - Files: plugin/commands/init.md, plugin/templates/project.md
   - Convert confirmed understanding to RFC 2119 formatted markdown
   - Include tables, examples, and explicit MUST/SHOULD/MAY rules
   - Add "## Git Workflow" section to PROJECT.md
   - Verify: Generated section is precise and unambiguous

### Phase 2: Workflow Enforcement

4. **Step 4:** Add branch validation to /cat:work
   - Files: plugin/commands/work.md
   - Read PROJECT.md "## Git Workflow" section at workflow start
   - Before task execution, validate current branch matches expected pattern
   - If mismatch, warn user with actionable guidance
   - Verify: Warning shown when branch doesn't match task version

5. **Step 5:** Apply squash preference before review
   - Files: plugin/commands/work.md
   - Read squash policy from PROJECT.md
   - Before approval gate, apply appropriate squash strategy
   - Respect "keep all", "single commit", or "by topic" preference
   - Verify: Commits squashed according to documented preference

6. **Step 6:** Apply merge preference after approval
   - Files: plugin/commands/work.md
   - Read merge policy from PROJECT.md
   - Use correct merge method (rebase+ff, merge commit, squash merge)
   - Verify: Merge performed according to documented preference

7. **Step 7:** Add version completion workflow
   - Files: plugin/commands/work.md
   - When all tasks for a version complete, check branching strategy
   - If version-branch strategy: prompt to merge to main and create next version branch
   - Verify: Prompt appears after completing last task of a version

### Phase 3: Skill Updates

8. **Step 8:** Update git-merge-linear skill
   - Files: plugin/skills/git-merge-linear/SKILL.md
   - Add step: Read PROJECT.md "## Git Workflow" section
   - Apply documented merge and squash policies
   - Remove hardcoded assumptions about rebase/squash
   - Verify: Skill respects PROJECT.md preferences

9. **Step 9:** Update git-squash skill
   - Files: plugin/skills/git-squash/SKILL.md
   - Read squash policy from PROJECT.md before executing
   - If policy says "keep all commits", warn and require explicit override
   - Apply correct grouping strategy (single, by-topic, keep-all)
   - Verify: Skill respects PROJECT.md squash preference

10. **Step 10:** Update git-commit skill
    - Files: plugin/skills/git-commit/SKILL.md
    - Read commit format from PROJECT.md
    - Validate commit messages match documented pattern
    - Show examples of correct format from PROJECT.md
    - Verify: Skill enforces documented commit convention

11. **Step 11:** Update git-rebase skill
    - Files: plugin/skills/git-rebase/SKILL.md
    - Read merge policy from PROJECT.md
    - If "merge commits" preferred, warn about potential conflict
    - Verify: Skill warns when action conflicts with preference

12. **Step 12:** Update /cat:add for branch creation
    - Files: plugin/commands/add.md
    - Read branching strategy from PROJECT.md
    - Create branches following documented naming pattern
    - Verify: Task branches created according to documented strategy

## Example PROJECT.md Output

```markdown
## Git Workflow

### Branching Strategy

| Branch Type | Pattern | Purpose |
|-------------|---------|---------|
| Main | `main` | Production-ready releases |
| Version | `v{major}.{minor}` | Development for specific version |
| Task | `{version}-{task-name}` | Individual task work |

**Rules:**
- Task branches MUST be created from version branches
- Task branches MUST merge back to their parent version branch
- Version branches SHOULD merge to `main` when all tasks complete
- Direct commits to `main` or version branches MUST NOT occur

### Merge Policy

**Pre-merge requirements:**
- MUST rebase task branch onto base branch
- MUST have clean working directory
- MUST pass all tests

**Merge method:**
- MUST use fast-forward merge (no merge commits)
- Linear history MUST be maintained

### Squash Policy

**When:** Before requesting user review

**Strategy:** By topic (commit type grouping)
- Group commits by type prefix (feature:, bugfix:, etc.)
- Each type becomes one commit
- Planning commits fold into implementation commit

**Example:**
```
BEFORE:                          AFTER:
feature: add form                feature: add login form with validation
feature: add validation    →     bugfix: fix form typo
bugfix: fix typo
planning: update STATE.md
```

### Commit Format

**Pattern:** `{type}: {description}`

**Valid types:**
| Type | Use For |
|------|---------|
| `feature:` | New functionality |
| `bugfix:` | Defect fixes |
| `refactor:` | Code restructuring |
| `docs:` | Documentation changes |
| `test:` | Test changes |
| `config:` | Configuration changes |
| `planning:` | CAT planning artifacts |

**Rules:**
- Type MUST be lowercase
- Description MUST use imperative mood ("add" not "added")
- Scope in parentheses MUST NOT be used
- Line length SHOULD be ≤72 characters
```

## Acceptance Criteria
- [ ] /cat:init includes git workflow wizard
- [ ] Wizard asks clarifying questions until understanding confirmed
- [ ] User explicitly confirms wizard understood correctly
- [ ] PROJECT.md contains precise "## Git Workflow" section
- [ ] RFC 2119 terminology used (MUST/SHOULD/MAY)
- [ ] Concrete examples included in documentation
- [ ] /cat:work validates branch before execution
- [ ] /cat:work applies squash preference before review
- [ ] /cat:work applies merge preference after approval
- [ ] Version completion prompts for merge when all tasks done
- [ ] All git skills read and respect PROJECT.md preferences
- [ ] Existing projects without config continue to work (backward compatible)
