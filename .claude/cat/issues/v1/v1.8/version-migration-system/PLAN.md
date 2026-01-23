# Plan: version-migration-system

## Objective
add version migration system for CAT upgrades

## Details
Add infrastructure to detect and migrate CAT installations when users
upgrade to newer plugin versions:

- migrations/lib/utils.sh: Version comparison, backup, logging utilities
- migrations/registry.json: Version-to-migration-script mapping
- migrations/1.8.sh: Baseline migration adding version field
- hooks/check-upgrade.sh: SessionStart hook detecting version mismatches

The system:
- Compares plugin version to cat-config.json version on session start
- Creates backup before migration
- Runs applicable migrations in version order
- Warns on downgrade (no auto-migration to prevent data loss)

Also updates release-plugin skill to create migration scripts/entries
as part of the release process.

## Acceptance Criteria
- [x] Implementation complete
- [x] Verified working
