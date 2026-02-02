# Plan: add-feedback-command

## Goal
Create a `/cat:feedback` command that allows users to report bugs or request features, collecting relevant information and publishing issues to https://github.com/cowwoc/cat/issues with user permission.

## Satisfies
None - independent user support functionality

## Design

### Command Flow

1. **Ask feedback type**: Bug report or Feature request
2. **For bugs**: Run `/cat:learn` workflow to collect:
   - Error description
   - Steps to reproduce
   - Expected vs actual behavior
   - Relevant context (session ID, version, environment)
   - M-record analysis (if applicable)
3. **For features**: Collect:
   - Feature description
   - Use case / motivation
   - Proposed solution (optional)
4. **Preview**: Show user exactly what will be posted
5. **Permission**: Ask for explicit permission to publish publicly
6. **Publish**: Use `gh issue create` to post to GitHub

### Privacy Considerations

- Show full issue content before posting
- Require explicit "Yes, publish publicly" confirmation
- Warn about sensitive information (paths, usernames, etc.)
- Option to redact specific sections

### Integration with /cat:learn

For bug reports, reuse the learn-from-mistakes RCA workflow:
- Problem description
- Root cause analysis
- Prevention mechanisms identified
- Relevant context

But instead of applying fixes locally, package this as a GitHub issue.

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Privacy of user data in bug reports
- **Mitigation:** Preview + explicit permission before publishing

## Files to Modify
- `plugin/skills/feedback/feedback.md` - New skill definition
- `plugin/skills/feedback/index.md` - Skill registration

## Acceptance Criteria
- [ ] Functionality works as described
- [ ] Tests written and passing
- [ ] Documentation updated
- [ ] No regressions
- [ ] User sees full preview before publishing
- [ ] Explicit permission required before posting
- [ ] Uses `gh` CLI for GitHub integration

## Execution Steps
1. **Create skill structure**: Create `plugin/skills/feedback/` directory with skill definition
   - Verify: Skill files exist
2. **Implement bug report flow**: Integrate with /cat:learn for RCA collection
   - Verify: Bug info collected correctly
3. **Implement feature request flow**: Simple collection of feature details
   - Verify: Feature info collected correctly
4. **Add preview and permission gate**: Show content, require confirmation
   - Verify: User sees preview, must confirm
5. **Implement GitHub publishing**: Use `gh issue create`
   - Verify: Issue created on GitHub with correct labels
6. **Add to skill index**: Register in available skills
   - Verify: `/cat:feedback` appears in help
