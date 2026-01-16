# Plan: github-issues-backend

## Goal
Implement GitHub Issues backend that stores planning metadata as GitHub Issues with labels for organization.

## Satisfies
- REQ-003

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** GitHub API rate limits; authentication complexity; label/milestone mapping
- **Mitigation:** Cache reads; use GitHub CLI auth; document mapping conventions

## Files to Modify
- New: `plugin/storage/backends/github.py` - GitHub Issues backend
- Modify: `plugin/cat-config-schema.json` - GitHub-specific config options

## Acceptance Criteria
- [ ] GitHubBackend implements storage interface
- [ ] Versions map to GitHub Milestones
- [ ] Tasks map to GitHub Issues with labels
- [ ] State/progress tracked via issue metadata
- [ ] Works with `gh` CLI authentication
- [ ] Rate limiting handled gracefully

## Execution Steps
1. **Design GitHub mapping**
   - Verify: Document how versions/tasks map to Issues/Milestones/Labels
2. **Implement GitHubBackend**
   - Files: `plugin/storage/backends/github.py`
   - Verify: Implements storage interface
3. **Add GitHub config options**
   - Files: `plugin/cat-config-schema.json`
   - Verify: Repo, auth method configurable
4. **Test end-to-end**
   - Verify: Can create version and task via GitHub backend
