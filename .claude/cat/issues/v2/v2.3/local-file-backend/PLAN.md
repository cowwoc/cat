# Plan: local-file-backend

## Goal
Implement the local file backend that wraps current .claude/cat file-based storage behind the new storage abstraction
interface.

## Satisfies
- REQ-002

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Must maintain backwards compatibility with existing .claude/cat structure
- **Mitigation:** Existing behavior is the reference implementation

## Files to Modify
- New: `plugin/storage/backends/local.py` - Local file backend implementation
- Modify: Skills that currently do file I/O directly

## Acceptance Criteria
- [ ] LocalFileBackend implements storage interface
- [ ] All existing .claude/cat operations work through backend
- [ ] No changes to file structure (backwards compatible)
- [ ] Existing skills migrated to use abstraction

## Execution Steps
1. **Implement LocalFileBackend**
   - Files: `plugin/storage/backends/local.py`
   - Verify: Implements full storage interface
2. **Add backend registration**
   - Files: `plugin/storage/__init__.py`
   - Verify: Backend discoverable by type name
3. **Migrate one skill as proof**
   - Files: One representative skill
   - Verify: Skill works identically via backend
4. **Migrate remaining skills**
   - Files: All skills with storage operations
   - Verify: All tests pass
