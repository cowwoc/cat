# Plan: storage-abstraction-design

## Goal
Design the storage abstraction interface that allows CAT to work with multiple issue tracker backends (local files, GitHub, Jira, Linear, etc.).

## Satisfies
- REQ-001

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** Interface must be flexible enough for diverse backends without over-engineering
- **Mitigation:** Start with local + GitHub requirements; design for extension

## Files to Modify
- New: `plugin/storage/interface.py` - Storage interface definition
- New: `plugin/storage/types.py` - Shared types (Version, Task, etc.)
- Modify: `plugin/cat-config-schema.json` - Add backend selection

## Acceptance Criteria
- [ ] Storage interface defined with CRUD operations for versions/tasks
- [ ] Common types defined (Version, Task, State, Plan)
- [ ] Backend selection added to cat-config.json schema
- [ ] Interface documented with extension guide

## Execution Steps
1. **Analyze current file-based operations**
   - Files: All skills that read/write .claude/cat
   - Verify: List of all storage operations identified
2. **Design storage interface**
   - Files: `plugin/storage/interface.py`
   - Verify: Interface covers all identified operations
3. **Define shared types**
   - Files: `plugin/storage/types.py`
   - Verify: Types support both file and issue-tracker models
4. **Update config schema**
   - Files: `plugin/cat-config-schema.json`
   - Verify: Backend selection configurable
