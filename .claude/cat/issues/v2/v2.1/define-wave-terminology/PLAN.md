# Plan: define-wave-terminology

## Goal
Define issue, sub-issue, and wave terminology in README.md. A wave is a dependency-ordered group of sub-issues that
can execute in parallel (as used in migrate-python-to-java's STATE.md). Update skills that decompose or orchestrate
issues to define and follow waves. Waves are an internal execution concept, not user-facing.

## Satisfies
None - terminology and documentation

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Skills currently use inconsistent terminology for wave-like groupings
- **Mitigation:** Depends on rename-sub-issue-to-subissue completing first; wave rename is additive

## Files to Modify

### README.md - Add Terminology Section
- Define **issue**: Atomic unit of work tracked by CAT
- Define **sub-issue**: Child issue created when decomposing a parent issue that exceeds context limits
- Define **wave**: A group of sub-issues with satisfied dependencies that can execute in parallel. Waves are ordered:
  Wave 1 issues have no dependencies, Wave 2 issues depend on Wave 1, etc. (internal process, not user-facing)
- Reference migrate-python-to-java as canonical example (5 waves, Wave 3 has 6 concurrent sub-issues)

### plugin/skills/decompose-issue/SKILL.md - Use wave terminology in parallel execution plan
- The "Parallel Execution Plan" section currently uses `sub_issue_1`, `sub_issue_2` as grouping labels
- Rename these to `wave_1`, `wave_2`, etc. to match the wave definition
- Update the plan format to use "Wave N" headings consistent with migrate-python-to-java STATE.md format
- Instruct agents to group independent sub-issues into waves and document them in the parent STATE.md
- Note: `sub_task` -> `sub_issue` rename is handled by rename-sub-issue-to-subissue

### plugin/skills/delegate/SKILL.md - Formalize wave terminology
- Already uses "wave" (lines 473, 698, 773, 818) for dependency-based parallel grouping
- Add a brief definition where waves are first introduced: "A wave is a group of sub-issues whose dependencies are
  satisfied and can execute in parallel"
- Ensure wave execution logic follows wave ordering: complete all Wave N before starting Wave N+1

### plugin/skills/work/phase-prepare.md - Reference waves in decomposition
- When presenting decomposition option, reference that decomposed issues will be organized into waves

### plugin/skills/shrink-doc/SKILL.md - Use wave terminology for batch operations
- Already uses /cat:delegate for multiple files (line 472)
- Frame multi-file compression as wave execution: group documents into waves for parallel processing

### plugin/skills/batch-read/SKILL.md - Reference wave concept
- Batch reading groups files for parallel processing; reference wave terminology for consistency

## Acceptance Criteria
- [ ] README.md defines issue, sub-issue, and wave
- [ ] Wave definition matches migrate-python-to-java usage: dependency-ordered parallel execution groups
- [ ] decompose-issue SKILL.md uses wave_N grouping labels and Wave N headings
- [ ] delegate SKILL.md contains formal wave definition and follows wave ordering
- [ ] Skills that orchestrate parallel work reference wave terminology
- [ ] No regressions in existing functionality
- [ ] Tests pass

## Execution Steps
1. **Step 1:** Add terminology definitions section to README.md
   - Files: README.md
   - Define issue, sub-issue, wave with wave matching migrate-python-to-java's usage
2. **Step 2:** Update decompose-issue SKILL.md to use wave terminology
   - Files: plugin/skills/decompose-issue/SKILL.md
   - Replace sub_issue_N groupings with wave_N, use Wave N headings in STATE.md format
   - Instruct agents to define waves for decomposed issues
3. **Step 3:** Formalize wave definition in delegate SKILL.md
   - Files: plugin/skills/delegate/SKILL.md
   - Add definition, ensure wave ordering is enforced
4. **Step 4:** Update work/phase-prepare.md to reference waves in decomposition
   - Files: plugin/skills/work/phase-prepare.md
5. **Step 5:** Add wave terminology to shrink-doc and batch-read
   - Files: plugin/skills/shrink-doc/SKILL.md, plugin/skills/batch-read/SKILL.md
6. **Step 6:** Run test suite
   - Command: python3 /workspace/run_tests.py

## Success Criteria
- [ ] README.md contains clear definitions for issue, sub-issue, and wave
- [ ] decompose-issue SKILL.md generates Wave N groupings in parallel execution plans
- [ ] delegate/SKILL.md contains formal wave definition and respects wave ordering
- [ ] All tests pass
