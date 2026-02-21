# Plan: Add Iterative TDD Cycles to TDD Skill

## Goal
Add iterative RED-GREEN-REFACTOR cycling to the TDD skill so features are built incrementally one behavior at a time,
with per-cycle commits squashed before review.

## Satisfies
- None (infrastructure improvement)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Must preserve existing bug-fix verification loop
- **Mitigation:** Diagram shows both loops explicitly

## Files to Modify
- `plugin/skills/tdd-implementation/first-use.md` — Update state machine, add iteration step, update commit pattern
- `plugin/skills/tdd-implementation/tdd.md` — Update execution_flow, commit_pattern, context_budget sections

## Acceptance Criteria
- [ ] State machine diagram shows both feature iteration loop and bug verification loop
- [ ] New STEP 4 (ITERATE OR VERIFY) inserted between REFACTOR and VERIFY
- [ ] Steps renumbered sequentially (1-5)
- [ ] Commit pattern shows per-cycle granular commits and pre-review squash
- [ ] tdd.md execution_flow describes iterative cycles
- [ ] tdd.md commit_pattern shows multiple cycles and squash-before-review
- [ ] tdd.md context_budget updated for iterative nature
- [ ] Bug-fix verification loop preserved in both files
- [ ] E2E: Both files are internally consistent and cross-reference correctly

## Execution Steps
1. **Update state machine diagram** in `first-use.md`
   - Files: `plugin/skills/tdd-implementation/first-use.md`
   - Replace diagram with one showing feature iteration loop (REFACTOR → more behaviors? → RED) and bug verification
     loop (VERIFY → still fails? → RED)

2. **Insert STEP 4: ITERATE OR VERIFY** in `first-use.md`
   - Files: `plugin/skills/tdd-implementation/first-use.md`
   - Add new step between current STEP 3 (REFACTOR) and STEP 4 (VERIFY)
   - Content: check behaviors from plan, if uncovered remain → loop to STEP 1, else proceed to STEP 5
   - Renumber current STEP 4 to STEP 5

3. **Update commit pattern** in `first-use.md`
   - Files: `plugin/skills/tdd-implementation/first-use.md`
   - Show per-cycle commits during development
   - Add squash-before-review guidance using cat:git-squash

4. **Update tdd.md sections**
   - Files: `plugin/skills/tdd-implementation/tdd.md`
   - Update `<execution_flow>` to describe iterative cycles
   - Update `<commit_pattern>` to show multiple cycles and squash
   - Update `<context_budget>` for iterative nature
