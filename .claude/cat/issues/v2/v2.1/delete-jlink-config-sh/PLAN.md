# Plan: delete-jlink-config-sh

## Goal

Delete the unused `plugin/hooks/jlink-config.sh` file. No live code sources or invokes it — `session-start.sh` has
its own independent runtime acquisition logic. The file is a dead artifact from early jlink prototyping.

## Satisfies

None (cleanup)

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Future issues (ci-build-jlink-bundle, developer-local-bundle-rebuild) reference it in their PLANs
- **Mitigation:** Those PLANs describe modifying jlink-config.sh, but the actual CI workflow will need to be written
  from scratch anyway since session-start.sh already handles runtime acquisition independently. Update those PLANs
  to remove references.

## Files to Modify

- `plugin/hooks/jlink-config.sh` - Delete
- `plugin/hooks/README.md` - Remove references to `cat-jdk-25` and jlink-config.sh
- `.claude/cat/issues/v2/v2.1/ci-build-jlink-bundle/PLAN.md` - Remove jlink-config.sh references
- `.claude/cat/issues/v2/v2.1/developer-local-bundle-rebuild/PLAN.md` - Remove jlink-config.sh references

## Post-conditions

- [ ] `plugin/hooks/jlink-config.sh` deleted
- [ ] No remaining references to `jlink-config.sh` in README.md
- [ ] Issue PLANs updated to remove stale jlink-config.sh references
- [ ] E2E: `grep -r 'jlink-config' plugin/` returns no results

## Execution Steps

1. **Delete jlink-config.sh**
   - Files: `plugin/hooks/jlink-config.sh`
2. **Update README.md:** Remove jlink-config.sh references and `cat-jdk-25` paths
   - Files: `plugin/hooks/README.md`
3. **Update issue PLANs:** Remove stale references to jlink-config.sh in ci-build-jlink-bundle and
   developer-local-bundle-rebuild PLANs
   - Files: `.claude/cat/issues/v2/v2.1/ci-build-jlink-bundle/PLAN.md`,
     `.claude/cat/issues/v2/v2.1/developer-local-bundle-rebuild/PLAN.md`
4. **Verify:** Grep for any remaining references
