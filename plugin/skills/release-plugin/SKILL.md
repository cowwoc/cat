# Release Claude Code Plugin

**Purpose**: Release a new version of a Claude Code plugin by merging to main, tagging, and preparing
the next version branch.

**CRITICAL**: Always use this skill for releases. Update CHANGELOG.md before running `git tag`.
The skill ensures changelog, version files, and tags stay synchronized.

**When to Use**:
- When user says "release", "publish", "tag a new version", or similar
- After completing work on a plugin version branch
- When ready to publish a new plugin version

## Prerequisites

- Current branch is the version branch to release (e.g., `v1.4`)
- All changes committed
- Both `package.json` and `.claude-plugin/plugin.json` have matching versions
- `migrations/` directory exists with `registry.json` and `lib/utils.sh`

## Release Process

### 1. Run Tests

Before releasing, ensure all tests pass:

```bash
npm test
```

**If tests fail**, fix the issues before proceeding. All tests must pass before release.

### 2. Verify Version Consistency

Before releasing, verify both JSON files have the same version:

```bash
echo "package.json:" && jq '.version' package.json
echo "plugin.json:" && jq '.version' .claude-plugin/plugin.json
```

**If versions differ**, fix them before proceeding. Both files must match.

### 2. Update CHANGELOG.md for Current Version

Before merging, ensure CHANGELOG.md documents what's in this release:

1. Verify the "Current Version" table shows CURRENT_VERSION
2. Verify the version history entry for CURRENT_VERSION lists all changes
3. Commit any CHANGELOG updates to the version branch

```bash
# If CHANGELOG needs updates
git add CHANGELOG.md
git commit -m "docs: update CHANGELOG for v${CURRENT_VERSION}"
```

### 3. Merge Current Version Branch to Main

```bash
# Get current version from package.json
CURRENT_VERSION=$(jq -r '.version' package.json)
echo "Releasing version: $CURRENT_VERSION"

# Checkout main and merge with fast-forward
git checkout main
git merge "v${CURRENT_VERSION}" --ff-only
```

**If fast-forward fails**: The branches have diverged. Either rebase the version branch onto main first,
or investigate why main has commits not in the version branch.

### 4. Delete the Merged Version Branch

```bash
# Delete local branch (now that it's merged)
git branch -d "v${CURRENT_VERSION}"
```

**Why delete?** Once merged and tagged, the version branch serves no purpose. Keeping it risks
accidental commits that diverge from the tag, causing confusion.

### 5. Create Release Tag on Main

```bash
# Create annotated tag for current version
git tag -a "v${CURRENT_VERSION}" -m "v${CURRENT_VERSION} release"
```

### 6. Create Next Version Branch

```bash
# Calculate next version (increment patch)
NEXT_VERSION=$(echo "$CURRENT_VERSION" | awk -F. '{print $1"."$2"."$3+1}')
echo "Next version: $NEXT_VERSION"

# Create and checkout new branch
git checkout -b "v${NEXT_VERSION}"
```

### 7. Increment Version Numbers

Update both JSON files to the next version:

```bash
# Update package.json
jq --arg v "$NEXT_VERSION" '.version = $v' package.json > package.json.tmp && mv package.json.tmp package.json

# Update plugin.json
jq --arg v "$NEXT_VERSION" '.version = $v' .claude-plugin/plugin.json > plugin.json.tmp && mv plugin.json.tmp .claude-plugin/plugin.json

# Verify updates
echo "package.json:" && jq '.version' package.json
echo "plugin.json:" && jq '.version' .claude-plugin/plugin.json
```

### 8. Create Migration Script for Next Version

Create a placeholder migration script for the next version. This ensures the migration system can track
version changes even if no structural changes are needed.

```bash
# Create migration script
cat > "migrations/${NEXT_VERSION}.sh" << 'MIGRATION_EOF'
#!/bin/bash
set -euo pipefail

# Migration to CAT ${NEXT_VERSION}
#
# Changes:
# - (Document structural changes here, or "No structural changes" if none)

trap 'echo "ERROR in ${NEXT_VERSION}.sh at line $LINENO: $BASH_COMMAND" >&2; exit 1' ERR

source "${CLAUDE_PLUGIN_ROOT}/migrations/lib/utils.sh"

# Add migration logic here if needed
# Example: Rename a config field
# jq '.newField = .oldField | del(.oldField)' .claude/cat/cat-config.json > tmp && mv tmp .claude/cat/cat-config.json

log_success "Migration to ${NEXT_VERSION} completed (no structural changes)"
MIGRATION_EOF

chmod +x "migrations/${NEXT_VERSION}.sh"
```

### 9. Add Migration Registry Entry

Add the new version to the migration registry:

```bash
# Add entry to registry
jq --arg ver "$NEXT_VERSION" --arg script "${NEXT_VERSION}.sh" \
  '.migrations += [{"version": $ver, "script": $script, "description": "Migration to " + $ver}]' \
  migrations/registry.json > migrations/registry.json.tmp && mv migrations/registry.json.tmp migrations/registry.json
```

### 10. Update CHANGELOG.md for Next Version

If the project has a CHANGELOG.md:

1. Update the "Current Version" table to show NEXT_VERSION
2. Add an empty version history section for NEXT_VERSION (to be filled during development)

### 11. Commit Version Bump

```bash
git add package.json .claude-plugin/plugin.json CHANGELOG.md migrations/
git commit -m "config: bump version to ${NEXT_VERSION}"
```

### 12. Push Everything to Origin

```bash
# Push main branch with new commits
git push origin main

# Push the release tag
git push origin "v${CURRENT_VERSION}"

# Delete the remote version branch (now merged)
git push origin --delete "v${CURRENT_VERSION}"

# Push the new version branch
git push -u origin "v${NEXT_VERSION}"
```

## Complete Example

Releasing v1.4 and preparing v1.5:

```bash
# 1. Verify versions match (on v1.4 branch)
jq '.version' package.json           # "1.4"
jq '.version' .claude-plugin/plugin.json  # "1.4"

# 2. Verify CHANGELOG.md is complete for v1.4
# - Current Version table shows 1.4
# - Version history has v1.4 entry with all changes listed

# 3. Merge to main
git checkout main
git merge v1.4 --ff-only

# 4. Delete the merged version branch
git branch -d v1.4

# 5. Create release tag
git tag -a v1.4 -m "v1.4 release"

# 6. Create next version branch
git checkout -b v1.5

# 7. Increment versions
jq '.version = "1.5"' package.json > package.json.tmp && mv package.json.tmp package.json
jq '.version = "1.5"' .claude-plugin/plugin.json > plugin.json.tmp && mv plugin.json.tmp .claude-plugin/plugin.json

# 8. Create migration script for 1.5
cat > migrations/1.5.sh << 'EOF'
#!/bin/bash
set -euo pipefail
source "${CLAUDE_PLUGIN_ROOT}/migrations/lib/utils.sh"
log_success "Migration to 1.5 completed (no structural changes)"
EOF
chmod +x migrations/1.5.sh

# 9. Add migration registry entry
jq '.migrations += [{"version": "1.5", "script": "1.5.sh", "description": "Migration to 1.5"}]' \
  migrations/registry.json > migrations/registry.json.tmp && mv migrations/registry.json.tmp migrations/registry.json

# 10. Update CHANGELOG for next version
# - Update version table to 1.5
# - Add empty v1.5 section to history

# 11. Commit version bump
git add package.json .claude-plugin/plugin.json CHANGELOG.md migrations/
git commit -m "config: bump version to 1.5"

# 12. Push everything
git push origin main
git push origin v1.4                # push tag
git push origin --delete v1.4       # delete remote branch
git push -u origin v1.5
```

## Workflow Diagram

```
v1.4 branch ──●──●──●─┐
                        │ merge --ff-only
                        ▼
main ──────────────────●── (tag v1.4)
                        │
                        │ checkout -b v1.5
                        ▼
v1.5 branch ─────────●── (bump version, commit)
```

## Verification Checklist

After release, verify:

- [ ] Main updated: `git log main --oneline -1` shows merged commits
- [ ] Tag exists: `git tag -l v{CURRENT_VERSION}`
- [ ] Tag pushed: `git ls-remote --tags origin v{CURRENT_VERSION}`
- [ ] On new branch: `git branch --show-current` shows `v{NEXT_VERSION}`
- [ ] Versions updated: both files show `{NEXT_VERSION}`
- [ ] Migration script exists: `ls migrations/{NEXT_VERSION}.sh`
- [ ] Migration registered: `jq '.migrations[-1]' migrations/registry.json` shows `{NEXT_VERSION}`
- [ ] Branch pushed: `git ls-remote --heads origin v{NEXT_VERSION}`

## Troubleshooting

### Fast-forward merge fails

```bash
# Check what's different
git log main..v{VERSION} --oneline
git log v{VERSION}..main --oneline

# If main has commits not in version branch, rebase:
git checkout v{VERSION}
git rebase main
git checkout main
git merge v{VERSION} --ff-only
```

### Tag already exists

```bash
# Delete and recreate if pointing to wrong commit
git tag -d v{VERSION}
git push origin --delete v{VERSION}
git tag -a v{VERSION} -m "v{VERSION} release"
git push origin v{VERSION}
```

### Version mismatch between files

```bash
# Check both files
jq '.version' package.json
jq '.version' .claude-plugin/plugin.json

# Fix the one that's wrong
jq --arg v "{CORRECT_VERSION}" '.version = $v' {FILE} > {FILE}.tmp && mv {FILE}.tmp {FILE}
```

## Main-Only Workflow (Simplified)

If working directly on main without version branches:

```bash
# 1. Update CHANGELOG.md FIRST
# - Replace "In development" with actual release notes
# - Commit the changelog update

# 2. Tag and push
CURRENT_VERSION=$(jq -r '.version' package.json)
git tag -a "v${CURRENT_VERSION}" -m "v${CURRENT_VERSION} release"
git push origin main
git push origin "v${CURRENT_VERSION}"

# 3. Bump version for next development cycle
NEXT_VERSION=$(echo "$CURRENT_VERSION" | awk -F. '{print $1"."$2"."$3+1}')
jq --arg v "$NEXT_VERSION" '.version = $v' package.json > package.json.tmp && mv package.json.tmp package.json

# 4. Create migration script and registry entry
cat > "migrations/${NEXT_VERSION}.sh" << EOF
#!/bin/bash
set -euo pipefail
source "\${CLAUDE_PLUGIN_ROOT}/migrations/lib/utils.sh"
log_success "Migration to ${NEXT_VERSION} completed (no structural changes)"
EOF
chmod +x "migrations/${NEXT_VERSION}.sh"
jq --arg ver "$NEXT_VERSION" --arg script "${NEXT_VERSION}.sh" \
  '.migrations += [{"version": $ver, "script": $script, "description": "Migration to " + $ver}]' \
  migrations/registry.json > migrations/registry.json.tmp && mv migrations/registry.json.tmp migrations/registry.json

# 5. Commit and push
git add package.json CHANGELOG.md migrations/
git commit -m "config: bump version to ${NEXT_VERSION}"
git push origin main
```

**Key principle**: CHANGELOG.md must be updated BEFORE creating the release tag.

## Related

- Plugin installation: `claude plugin add {repo-url}`
- Plugin update: `claude plugin update {plugin-name}`
