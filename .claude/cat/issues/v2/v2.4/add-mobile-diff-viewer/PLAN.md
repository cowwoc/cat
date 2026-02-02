# Plan: add-mobile-diff-viewer

## Goal
Create an execution-order diff viewer that shows code changes in the order they execute at runtime, using the existing 4-column box format. This enables meaningful code review on mobile devices by presenting changes as a narrative flow rather than scattered file-by-file diffs.

## Satisfies
- REQ-001: Execution-order diff viewer
- REQ-002: Integration with existing render-diff format
- REQ-003: Coverage verification
- REQ-004: Non-executable change categorization

## Key Design Decisions
1. **Execution order narrative**: Changes shown in the order a request/action encounters them at runtime
2. **100% coverage guarantee**: Every changed line must be categorized (execution flow, test, or non-executable)
3. **Reuse existing format**: Uses the 4-column box format from render-diff.py
4. **Silent validation**: Coverage verified internally but not shown to user in output
5. **Non-executable grouping**: Imports, types, comments, formatting shown separately after flows

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** Call graph analysis requires understanding code structure; accuracy depends on language support
- **Mitigation:** Start with TypeScript/JavaScript; fail gracefully for unsupported patterns

## Files to Modify
- `plugin/scripts/render-diff.py` - Extend with execution-order mode or create new script
- `plugin/skills/render-diff/SKILL.md` - Document new capability

## Acceptance Criteria
- [ ] Functionality works as described
- [ ] Tests written and passing
- [ ] Documentation updated
- [ ] No regressions to existing render-diff functionality
- [ ] All changed lines accounted for (validated internally)
- [ ] Execution flows show step numbers and file transitions
- [ ] Non-executable changes grouped separately

## Execution Steps
1. **Parse diff and categorize changes**
   - Files: plugin/scripts/render-diff.py
   - Categorize each hunk as: executable, test, import, type, comment, formatting
   - Verify: Categories cover 100% of changed lines

2. **Build execution flow from entry points**
   - Identify entry points (routes, handlers, main functions)
   - Trace call graph through changed code
   - Order hunks by execution sequence
   - Verify: Flow steps are numbered and connected

3. **Render execution-order output**
   - Use existing 4-column box format
   - Add flow headers (STEP N of M) and arrows between steps
   - Group non-executable changes at end
   - Verify: Output matches mockup format

4. **Add tests**
   - Test categorization logic
   - Test flow ordering
   - Test 100% coverage validation
   - Verify: All tests pass

5. **Update documentation**
   - Update SKILL.md with new mode
   - Document entry point detection heuristics
   - Verify: Docs reflect implementation
