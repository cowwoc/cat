---
description: Release a new plugin version by merging to main, tagging, and preparing the next version branch
disable-model-invocation: true
---

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
npm run test:all
```

This runs both BATS tests (shell scripts) and pytest tests (Python handlers).

**If tests fail**, fix the issues before proceeding. All tests must pass before release.

### 2. Validate CAT CHANGELOGs (if CAT-managed project)

If the project uses CAT planning (`.claude/cat/` directory exists), validate that all versions being
released have complete CHANGELOG.md files. This ensures release documentation is comprehensive.

**Validation process:**

1. **Identify the deepest version level** from package.json (e.g., `2.1.0` → minor level `v2.1`)
2. **Traverse up the version hierarchy** and validate each level
3. **Check required sections** exist and are populated

```bash
# Detect if CAT-managed project
if [[ -d ".claude/cat/issues" ]]; then
  echo "CAT project detected - validating changelogs..."

  # Parse version from package.json
  CURRENT_VERSION=$(jq -r '.version' package.json)
  MAJOR=$(echo "$CURRENT_VERSION" | cut -d. -f1)
  MINOR=$(echo "$CURRENT_VERSION" | cut -d. -f2)

  # Validate minor version CHANGELOG
  MINOR_CHANGELOG=".claude/cat/issues/v${MAJOR}/v${MAJOR}.${MINOR}/CHANGELOG.md"
  if [[ ! -f "$MINOR_CHANGELOG" ]]; then
    echo "ERROR: Missing CHANGELOG at $MINOR_CHANGELOG"
    echo "Run: Generate CHANGELOG from completed issues in this version"
    exit 1
  fi

  # Validate major version CHANGELOG
  MAJOR_CHANGELOG=".claude/cat/issues/v${MAJOR}/CHANGELOG.md"
  if [[ ! -f "$MAJOR_CHANGELOG" ]]; then
    echo "ERROR: Missing CHANGELOG at $MAJOR_CHANGELOG"
    exit 1
  fi

  # Check for required sections in minor CHANGELOG
  if ! grep -q "## Summary" "$MINOR_CHANGELOG" || \
     ! grep -q "## Issues Completed" "$MINOR_CHANGELOG"; then
    echo "WARNING: $MINOR_CHANGELOG may be incomplete"
    echo "Required sections: ## Summary, ## Issues Completed"
  fi

  echo "✓ CHANGELOG validation passed"
fi
```

**Required CHANGELOG sections:**
- `## Summary` - Brief description of what this version accomplished
- `## Issues Completed` - Table of issues with type, description, resolution

**Auto-update capability:**

If CHANGELOG is missing or incomplete, offer to auto-populate from STATE.md:

```bash
# Find all completed issues in this minor version
COMPLETED_ISSUES=$(find ".claude/cat/issues/v${MAJOR}/v${MAJOR}.${MINOR}" \
  -name "STATE.md" -exec grep -l "status: completed" {} \; 2>/dev/null \
  | xargs -I {} dirname {} | xargs -I {} basename {})

if [[ -n "$COMPLETED_ISSUES" ]]; then
  echo "Found completed issues: $COMPLETED_ISSUES"
  echo "Auto-generating CHANGELOG entries..."

  # Generate CHANGELOG template with issue list
  cat > "$MINOR_CHANGELOG" << EOF
# Changelog: v${MAJOR}.${MINOR}

**Completed**: $(date +%Y-%m-%d)

## Summary

[One-line description of what this version accomplished]

## Issues Completed

| Issue | Type | Description | Resolution |
|-------|------|-------------|------------|
EOF

  # Append each issue
  for issue_name in $COMPLETED_ISSUES; do
    ISSUE_PLAN=".claude/cat/issues/v${MAJOR}/v${MAJOR}.${MINOR}/${issue_name}/PLAN.md"
    if [[ -f "$ISSUE_PLAN" ]]; then
      GOAL=$(grep -A1 "^## Goal" "$ISSUE_PLAN" | tail -1 | head -c 50)
      echo "| ${issue_name} | - | ${GOAL}... | implemented |" >> "$MINOR_CHANGELOG"
    fi
  done

  echo ""
  echo "CHANGELOG generated. Review and commit before release."
fi
```

### 3. Verify Version Consistency

Before releasing, verify both JSON files have the same version:

```bash
echo "package.json:" && jq '.version' package.json
echo "plugin.json:" && jq '.version' .claude-plugin/plugin.json
```

**If versions differ**, fix them before proceeding. Both files must match.

### 4. Update Plugin CHANGELOG.md for Current Version

Before merging, ensure the root CHANGELOG.md documents what's in this release:

1. Verify the "Current Version" table shows CURRENT_VERSION
2. Verify the version history entry for CURRENT_VERSION lists all changes
3. Commit any CHANGELOG updates to the version branch

```bash
# If CHANGELOG needs updates
git add CHANGELOG.md
git commit -m "docs: update CHANGELOG for v${CURRENT_VERSION}"
```

### 5. Merge Current Version Branch to Main

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

### 6. Delete the Merged Version Branch

```bash
# Delete local branch (now that it's merged)
git branch -d "v${CURRENT_VERSION}"
```

**Why delete?** Once merged and tagged, the version branch serves no purpose. Keeping it risks
accidental commits that diverge from the tag, causing confusion.

### 7. Create Release Tag on Main

```bash
# Create annotated tag for current version
git tag -a "v${CURRENT_VERSION}" -m "v${CURRENT_VERSION} release"
```

### 8. Create Next Version Branch

**Note**: Do NOT create a tag for the next version. Tags are only created when releasing a version
(step 5), not when starting a new version. The next version gets a branch only.

```bash
# Calculate next version (increment patch)
NEXT_VERSION=$(echo "$CURRENT_VERSION" | awk -F. '{print $1"."$2"."$3+1}')
echo "Next version: $NEXT_VERSION"

# Create and checkout new branch (NOT a tag)
git checkout -b "v${NEXT_VERSION}"
```

### 9. Update README Development Branch Reference

Update README.md to reference the new development branch:

```bash
# Update the development branch reference in README.md
sed -i "s/| \`v[0-9.]*\` | Next version in development |/| \`v${NEXT_VERSION}\` | Next version in development |/" README.md
```

**Why?** The README tells users which branch contains the next version in development.
This must be updated to reference the new version branch.

### 10. Increment Version Numbers

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

### 11. Create Migration Script for Next Version

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

### 12. Add Migration Registry Entry

Add the new version to the migration registry:

```bash
# Add entry to registry
jq --arg ver "$NEXT_VERSION" --arg script "${NEXT_VERSION}.sh" \
  '.migrations += [{"version": $ver, "script": $script, "description": "Migration to " + $ver}]' \
  migrations/registry.json > migrations/registry.json.tmp && mv migrations/registry.json.tmp migrations/registry.json
```

### 13. Update Plugin CHANGELOG.md for Next Version

If the project has a CHANGELOG.md:

1. Update the "Current Version" table to show NEXT_VERSION
2. Add an empty version history section for NEXT_VERSION (to be filled during development)

### 14. Commit Version Bump

```bash
git add package.json .claude-plugin/plugin.json CHANGELOG.md README.md migrations/
git commit -m "config: bump version to ${NEXT_VERSION}"
```

### 15. Push Everything to Origin

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
# 1. Run tests (on v1.4 branch)
npm run test:all

# 2. Validate CAT CHANGELOGs (if CAT-managed)
# - Check .claude/cat/issues/v1/v1.4/CHANGELOG.md exists
# - Check .claude/cat/issues/v1/CHANGELOG.md exists
# - Auto-generate if missing

# 3. Verify versions match
jq '.version' package.json           # "1.4"
jq '.version' .claude-plugin/plugin.json  # "1.4"

# 4. Verify plugin CHANGELOG.md is complete for v1.4
# - Current Version table shows 1.4
# - Version history has v1.4 entry with all changes listed

# 5. Merge to main
git checkout main
git merge v1.4 --ff-only

# 6. Delete the merged version branch
git branch -d v1.4

# 7. Create release tag
git tag -a v1.4 -m "v1.4 release"

# 8. Create next version branch
git checkout -b v1.5

# 9. Update README development branch reference
sed -i "s/| \`v[0-9.]*\` | Next version in development |/| \`v1.5\` | Next version in development |/" README.md

# 10. Increment versions
jq '.version = "1.5"' package.json > package.json.tmp && mv package.json.tmp package.json
jq '.version = "1.5"' .claude-plugin/plugin.json > plugin.json.tmp && mv plugin.json.tmp .claude-plugin/plugin.json

# 11. Create migration script for 1.5
cat > migrations/1.5.sh << 'EOF'
#!/bin/bash
set -euo pipefail
source "${CLAUDE_PLUGIN_ROOT}/migrations/lib/utils.sh"
log_success "Migration to 1.5 completed (no structural changes)"
EOF
chmod +x migrations/1.5.sh

# 12. Add migration registry entry
jq '.migrations += [{"version": "1.5", "script": "1.5.sh", "description": "Migration to 1.5"}]' \
  migrations/registry.json > migrations/registry.json.tmp && mv migrations/registry.json.tmp migrations/registry.json

# 13. Update CHANGELOG for next version
# - Update version table to 1.5
# - Add empty v1.5 section to history

# 14. Commit version bump
git add package.json .claude-plugin/plugin.json CHANGELOG.md README.md migrations/
git commit -m "config: bump version to 1.5"

# 15. Push everything
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
main ──────────────────●── tag v1.4 (RELEASED version gets tag)
                        │
                        │ checkout -b v1.5
                        ▼
v1.5 branch ─────────●── (bump version, commit - NO tag yet)
```

**Key principle**: Tags mark releases. The new version (v1.5) only gets a tag when IT is released.

## Verification Checklist

After release, verify:

- [ ] CAT CHANGELOGs complete (if CAT-managed): minor and major level CHANGELOG.md files exist
- [ ] Main updated: `git log main --oneline -1` shows merged commits
- [ ] Tag exists: `git tag -l v{CURRENT_VERSION}`
- [ ] Tag pushed: `git ls-remote --tags origin v{CURRENT_VERSION}`
- [ ] On new branch: `git branch --show-current` shows `v{NEXT_VERSION}`
- [ ] README updated: Development table shows `v{NEXT_VERSION}`
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

## Related

- Plugin installation: `claude plugin add {repo-url}`
- Plugin update: `claude plugin update {plugin-name}`
