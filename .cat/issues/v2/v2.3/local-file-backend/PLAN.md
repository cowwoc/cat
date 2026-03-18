# Plan: local-file-backend

## Goal
Implement the local file backend that wraps current .cat file-based storage behind the new storage abstraction
interface.

## Parent Requirements
- REQ-002

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Must maintain backwards compatibility with existing .cat structure
- **Mitigation:** Existing behavior is the reference implementation

## Files to Modify
- New: `plugin/storage/backends/local.py` - Local file backend implementation
- Modify: Skills that currently do file I/O directly

## Post-conditions
- [ ] LocalFileBackend implements storage interface
- [ ] All existing .cat operations work through backend
- [ ] No changes to file structure (backwards compatible)
- [ ] Existing skills migrated to use abstraction

## Sub-Agent Waves

### Wave 1
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
