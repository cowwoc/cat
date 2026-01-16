# Release Claude Code Plugin

**Purpose**: Release a new version of a Claude Code plugin by merging to main, tagging, and preparing
the next version branch.

**When to Use**:
- After completing work on a plugin version branch
- When ready to publish a new plugin version

## Prerequisites

- Current branch is the version branch to release (e.g., `v1.4`)
- All changes committed and tested
- Both `package.json` and `.claude-plugin/plugin.json` have matching versions

## Release Process

### 1. Verify Version Consistency

Before releasing, verify both JSON files have the same version:

```bash
echo "package.json:" && jq '.version' package.json
echo "plugin.json:" && jq '.version' .claude-plugin/plugin.json
```

**If versions don't match**, fix them before proceeding.

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

### 4. Create Release Tag on Main

```bash
# Create annotated tag for current version
git tag -a "v${CURRENT_VERSION}" -m "v${CURRENT_VERSION} release"
```

### 5. Create Next Version Branch

```bash
# Calculate next version (increment patch)
NEXT_VERSION=$(echo "$CURRENT_VERSION" | awk -F. '{print $1"."$2"."$3+1}')
echo "Next version: $NEXT_VERSION"

# Create and checkout new branch
git checkout -b "v${NEXT_VERSION}"
```

### 6. Increment Version Numbers

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

### 7. Update CHANGELOG.md for Next Version

If the project has a CHANGELOG.md:

1. Update the "Current Version" table to show NEXT_VERSION
2. Add an empty version history section for NEXT_VERSION (to be filled during development)

### 8. Commit Version Bump

```bash
git add package.json .claude-plugin/plugin.json CHANGELOG.md
git commit -m "config: bump version to ${NEXT_VERSION}"
```

### 9. Push Everything to Origin

```bash
# Push main branch with new commits
git push origin main

# Push the release tag
git push origin "v${CURRENT_VERSION}"

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

# 4. Create release tag
git tag -a v1.4 -m "v1.4 release"

# 5. Create next version branch
git checkout -b v1.5

# 6. Increment versions
jq '.version = "1.5"' package.json > package.json.tmp && mv package.json.tmp package.json
jq '.version = "1.5"' .claude-plugin/plugin.json > plugin.json.tmp && mv plugin.json.tmp .claude-plugin/plugin.json

# 7. Update CHANGELOG for next version
# - Update version table to 1.5
# - Add empty v1.5 section to history

# 8. Commit version bump
git add package.json .claude-plugin/plugin.json CHANGELOG.md
git commit -m "config: bump version to 1.5"

# 9. Push everything
git push origin main
git push origin v1.4
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

## Related

- Plugin installation: `claude plugin add {repo-url}`
- Plugin update: `claude plugin update {plugin-name}`
