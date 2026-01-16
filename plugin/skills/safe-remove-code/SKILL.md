---
name: safe-remove-code
description: Safely remove code patterns from multiple files with validation and rollback (project)
allowed-tools: Bash, Read, Edit, Grep, Glob
---

# Safe Code Removal Skill

**Purpose**: Safely remove code patterns (instrumentation, debugging code, deprecated patterns) from multiple files with strict validation to prevent accidentally gutting files.

**Created**: 2025-11-07 after accidentally gutting 7 hooks during timing code removal

**Performance**: Prevents catastrophic file damage through per-file validation, syntax checks, and functional testing

## The Problem

When removing instrumentation, debugging code, or other patterns from multiple files, aggressive removal scripts can accidentally delete functional code, leaving only boilerplate (shebang, set commands).

**Real Example** (2025-11-07):
- **Task**: Remove timing instrumentation from 47 hooks
- **Mistake**: Removal script was too aggressive
- **Impact**: 7 hooks reduced to 3 lines (only `#!/bin/bash` and `set -euo pipefail`)
- **Hooks destroyed**: auto-learn-from-mistakes.sh, block-data-loss.sh, detect-worktree-violation.sh, enforce-requirements-release.sh, load-todo.sh, detect-assistant-giving-up.sh, verify-convergence-entry.sh
- **Recovery**: Restored from backups
- **Root cause**: Didn't validate hooks after removal, declared task complete too early

## When to Use This Skill

### ✅ Use safe-remove-code When:

- Removing instrumentation code from multiple files
- Cleaning up debugging statements across codebase
- Removing deprecated patterns systematically
- Need validation that files remain functional after removal
- Pattern removal affects 5+ files

### ❌ Do NOT Use When:

- Removing code from single file (use Edit tool directly)
- Changes are complex refactoring (not simple removal)
- Pattern varies significantly across files
- Need to preserve some instances of pattern
- Pattern removal is part of larger refactoring task

## ⚠️ Critical Safety Rules

**MANDATORY BACKUP**: Always create timestamped backup before any removal
**PER-FILE VALIDATION**: Validate each file individually (syntax, size, integrity)
**FUNCTIONAL TESTING**: Run build and tests after all removals
**IMMEDIATE VERIFICATION**: Don't declare complete without verification
**AUTOMATIC CLEANUP**: Remove backups only after ALL validation passes
**PRECISE PATTERNS**: Use specific patterns, not vague regex

## Prerequisites

Before using this skill, verify:
- [ ] Working directory is clean: `git status` shows no uncommitted changes
- [ ] Know exact pattern to remove (tested with grep)
- [ ] Identified all files containing pattern
- [ ] Pattern is consistent across files
- [ ] Have test command available (build/test)

## Skill Workflow

### Release 1: Identify Removal Patterns

**❌ WRONG - Vague Pattern**:
```bash
# Dangerous: May match more than intended
sed -i '/timing/,/end/d' *.sh
```

**✅ CORRECT - Precise Pattern**:
```bash
# Identify EXACT patterns to remove
PATTERNS_TO_REMOVE=(
  "HOOK_START="
  "log_timing()"
  "trap.*timing.*exit"
)

# Test pattern on one file first
for pattern in "${PATTERNS_TO_REMOVE[@]}"; do
  echo "Pattern: $pattern"
  grep -n "$pattern" ~/.claude/hooks/example-hook.sh || echo "  No matches"
done
```

**Preview Matches**:
```bash
# See what will be removed before removing
for file in ~/.claude/hooks/*.sh; do
  if grep -q "PATTERN" "$file"; then
    echo "=== $(basename "$file") ==="
    grep -n "PATTERN" "$file"
  fi
done
```

### Release 2: Create Backups

**MANDATORY before any removal**:

```bash
# Create timestamped backups
BACKUP_SUFFIX=".backup-$(date +%Y%m%d-%H%M%S)"

for file in ~/.claude/hooks/*.sh; do
  if [[ -f "$file" ]] && [[ ! "$file" =~ \.backup ]]; then
    cp "$file" "${file}${BACKUP_SUFFIX}"
  fi
done

echo "✅ Backups created with suffix: $BACKUP_SUFFIX"
ls -la ~/.claude/hooks/*.backup-* | head -5
```

**Verify Backups Created**:
```bash
# Count backups
BACKUP_COUNT=$(find ~/.claude/hooks -name "*.backup-*" | wc -l)
ORIGINAL_COUNT=$(find ~/.claude/hooks -name "*.sh" ! -name "*.backup-*" | wc -l)

if [[ "$BACKUP_COUNT" -ne "$ORIGINAL_COUNT" ]]; then
  echo "❌ ERROR: Backup count mismatch!"
  echo "   Original files: $ORIGINAL_COUNT"
  echo "   Backups created: $BACKUP_COUNT"
  exit 1
fi

echo "✅ All $ORIGINAL_COUNT files backed up"
```

### Release 3: Remove Code with Validation

**Create removal script with per-file validation**:

```bash
#!/bin/bash
# safe-pattern-removal.sh
# Removes specific patterns with per-file validation

set -euo pipefail

PATTERN="${1:-}"  # Pattern to remove
TARGET_DIR="${2:-.claude/hooks}"
MIN_LINES="${3:-10}"  # Minimum lines after removal (safety check)

if [[ -z "$PATTERN" ]]; then
  echo "Usage: $0 <pattern> [target-dir] [min-lines]" >&2
  exit 1
fi

echo "Removing pattern: $PATTERN"
echo "Target directory: $TARGET_DIR"
echo "Minimum lines after removal: $MIN_LINES"
echo ""

EXIT_CODE=0

for file in "$TARGET_DIR"/*.sh; do
  if [[ ! -f "$file" ]] || [[ "$file" =~ \.backup ]]; then
    continue
  fi

  filename=$(basename "$file")
  lines_before=$(wc -l < "$file")

  # Remove pattern
  sed -i "/$PATTERN/d" "$file"

  lines_after=$(wc -l < "$file")
  lines_removed=$((lines_before - lines_after))

  # Validate syntax
  if ! bash -n "$file" 2>/dev/null; then
    echo "❌ $filename: SYNTAX ERROR after removal" >&2
    # Restore from backup
    BACKUP=$(ls -t "${file}.backup-"* 2>/dev/null | head -1)
    if [[ -n "$BACKUP" ]]; then
      cp "$BACKUP" "$file"
      echo "   Restored from $BACKUP" >&2
    fi
    EXIT_CODE=1
    continue
  fi

  # Check if file was gutted
  functional_lines=$(grep -v '^\s*#' "$file" | grep -v '^\s*$' | wc -l)
  if [[ $functional_lines -lt $MIN_LINES ]]; then
    echo "⚠️  $filename: SUSPICIOUSLY SMALL after removal ($functional_lines functional lines, removed $lines_removed)" >&2
    echo "   Review manually to ensure functional code not removed" >&2
    EXIT_CODE=1
  else
    echo "✅ $filename: Removed $lines_removed lines ($functional_lines functional lines remain)"
  fi
done

if [[ $EXIT_CODE -eq 0 ]]; then
  echo ""
  echo "✅ Pattern removal complete with validation"
else
  echo ""
  echo "❌ Some files failed validation - review manually"
fi

exit $EXIT_CODE
```

**Execute Removal**:
```bash
# Save script
cat > /tmp/safe-pattern-removal.sh <<'EOF'
[Script content from above]
EOF
chmod +x /tmp/safe-pattern-removal.sh

# Run with pattern
/tmp/safe-pattern-removal.sh "PATTERN_TO_REMOVE" "~/.claude/hooks" 10
```

### Release 4: Functional Testing

**BEFORE removing backups, run functional tests**:

```bash
# 1. Syntax validation (quick check)
echo "Running syntax validation..."
for hook in ~/.claude/hooks/*.sh; do
  if [[ -f "$hook" ]] && [[ ! "$hook" =~ \.backup ]]; then
    if ! bash -n "$hook"; then
      echo "❌ SYNTAX ERROR: $hook"
      exit 1
    fi
  fi
done
echo "✅ All hooks pass syntax check"

# 2. Integrity check (file size)
echo ""
echo "Running integrity check..."
for hook in ~/.claude/hooks/*.sh; do
  if [[ -f "$hook" ]] && [[ ! "$hook" =~ \.backup ]]; then
    functional_lines=$(grep -v '^\s*#' "$hook" | grep -v '^\s*$' | wc -l)
    if [[ $functional_lines -lt 10 ]]; then
      echo "⚠️  $(basename "$hook"): Only $functional_lines functional lines"
    fi
  fi
done

# 3. Functional tests (if available)
echo ""
echo "Running functional tests..."
if [[ -f ~/.claude/hooks/tests/test-hooks.sh ]]; then
  bash ~/.claude/hooks/tests/test-hooks.sh || {
    echo "❌ Functional tests FAILED"
    exit 1
  }
  echo "✅ Functional tests passed"
else
  echo "⚠️  No functional tests available - manual verification required"
fi

# 4. Build test (if applicable)
if [[ -f /path/to/project/mvnw ]]; then
  echo ""
  echo "Running build test..."
  cd /path/to/project && ./mvnw clean verify -q || {
    echo "❌ Build FAILED after code removal"
    exit 1
  }
  echo "✅ Build passed"
fi
```

### Release 5: Manual Review

**Sample files before declaring complete**:

```bash
# Check a few files to verify removal was clean
SAMPLE_FILES=(
  "~/.claude/hooks/auto-learn-from-mistakes.sh"
  "~/.claude/hooks/enforce-commit-squashing.sh"
  "~/.claude/hooks/load-todo.sh"
)

echo "Manual review of sample files:"
for file in "${SAMPLE_FILES[@]}"; do
  if [[ -f "$file" ]]; then
    echo ""
    echo "=== $(basename "$file") ==="
    echo "Lines: $(wc -l < "$file")"
    echo "Functional lines: $(grep -v '^\s*#' "$file" | grep -v '^\s*$' | wc -l)"
    echo ""
    echo "First 20 lines:"
    head -20 "$file"
  fi
done

echo ""
echo "⚠️  MANUAL VERIFICATION REQUIRED"
echo "   Review above output to confirm removal was clean"
echo "   If anything looks wrong, restore from backups"
echo ""
read -p "Do all files look correct? (y/n) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
  echo "Aborting - restore from backups if needed"
  exit 1
fi

echo "✅ Manual verification passed"
```

### Release 6: Cleanup Backups

**ONLY after validation passes**:

```bash
# Final confirmation
echo "All validation passed:"
echo "✅ Syntax check"
echo "✅ Integrity check"
echo "✅ Functional tests"
echo "✅ Manual review"
echo ""
read -p "Remove all backups? (y/n) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
  echo "Keeping backups for safety"
  exit 0
fi

# Remove backups
BACKUP_COUNT=$(find ~/.claude/hooks -name "*.backup-*" | wc -l)
rm -f ~/.claude/hooks/*.backup-*

echo "✅ Removed $BACKUP_COUNT backup files"
echo ""
echo "Code removal complete!"
```

## Validation Checklist

Before declaring code removal complete:

- [ ] **Backups created** with timestamp
- [ ] **Patterns identified** precisely (not vague)
- [ ] **Removal script** validates per-file
- [ ] **Syntax check** passes for all files
- [ ] **Integrity check** passes (no gutted files)
- [ ] **Functional tests** pass (hooks/build still work)
- [ ] **Manual review** of sample files
- [ ] **No errors** in validation output
- [ ] **Backups cleanup** only after all checks pass

## Anti-Patterns to Avoid

### ❌ WRONG: Bulk removal without validation

```bash
# Dangerous: No per-file validation
sed -i '/pattern/d' *.sh
echo "Done!"  # ← Declared complete without validation
```

### ❌ WRONG: Removing backups too early

```bash
# Removes backups before functional testing
rm *.backup
bash test.sh  # ← Too late if test fails
```

### ❌ WRONG: Vague patterns

```bash
# May match more than intended
sed -i '/^[[:space:]]*#/d' *.sh  # ← Removes ALL comments, including documentation
```

### ✅ CORRECT: Precise removal with validation

```bash
# Specific pattern with validation
sed -i '/^[[:space:]]*# TIMING:/d' *.sh
bash -n file.sh || { echo "Syntax error"; restore_backup; }
bash test.sh || { echo "Functional test failed"; restore_backup; }
```

## Recovery Procedures

If you discover files were gutted after removal:

### Step 1: Stop immediately

```bash
# Don't make it worse - stop removal process
echo "STOP: Gutted files detected"
```

### Step 2: Restore from backups

```bash
# Restore all files from most recent backup
for backup in ~/.claude/hooks/*.backup-*; do
  original="${backup%.backup-*}.sh"
  if [[ -f "$backup" ]]; then
    # Check if original was gutted
    functional_lines=$(grep -v '^\s*#' "$original" | grep -v '^\s*$' | wc -l)
    backup_lines=$(grep -v '^\s*#' "$backup" | grep -v '^\s*$' | wc -l)

    if [[ $functional_lines -lt 10 ]] && [[ $backup_lines -gt 10 ]]; then
      echo "Restoring $original from $backup"
      cp "$backup" "$original"
    fi
  fi
done

echo "✅ Restoration complete"
```

### Step 3: Validate restoration

```bash
# Verify files restored correctly
for hook in ~/.claude/hooks/*.sh; do
  if [[ -f "$hook" ]] && [[ ! "$hook" =~ \.backup ]]; then
    bash -n "$hook" || echo "❌ SYNTAX ERROR: $hook"
  fi
done

echo "✅ All files restored and validated"
```

### Step 4: Analyze what went wrong

```bash
# Invoke learn-from-mistakes skill
Skill: learn-from-mistakes

# Document the mistake for prevention
```

## Complete Example

### Example: Remove Timing Instrumentation

```bash
#!/bin/bash
# Complete workflow for removing timing code

set -euo pipefail

echo "=== Safe Code Removal: Timing Instrumentation ==="
echo ""

# Release 1: Identify patterns
echo "Release 1: Identifying patterns..."
PATTERNS=(
  "HOOK_START="
  "log_timing"
  "^[[:space:]]*# TIMING:"
)

# Preview what will be removed
echo "Preview of matches:"
for pattern in "${PATTERNS[@]}"; do
  echo "  Pattern: $pattern"
  grep -c "$pattern" ~/.claude/hooks/*.sh 2>/dev/null | grep -v ":0" || echo "    No matches"
done
echo ""

read -p "Continue with removal? (y/n) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
  echo "Aborted by user"
  exit 0
fi

# Release 2: Create backups
echo ""
echo "Release 2: Creating backups..."
BACKUP_SUFFIX=".backup-$(date +%Y%m%d-%H%M%S)"
for file in ~/.claude/hooks/*.sh; do
  if [[ -f "$file" ]] && [[ ! "$file" =~ \.backup ]]; then
    cp "$file" "${file}${BACKUP_SUFFIX}"
  fi
done
echo "✅ Backups created"

# Release 3: Remove patterns
echo ""
echo "Release 3: Removing patterns with validation..."
for pattern in "${PATTERNS[@]}"; do
  /tmp/safe-pattern-removal.sh "$pattern" "~/.claude/hooks" 10
done

# Release 4: Functional testing
echo ""
echo "Release 4: Running functional tests..."
for hook in ~/.claude/hooks/*.sh; do
  if [[ -f "$hook" ]] && [[ ! "$hook" =~ \.backup ]]; then
    bash -n "$hook" || {
      echo "❌ Syntax error in $(basename "$hook")"
      exit 1
    }
  fi
done
echo "✅ All hooks pass syntax check"

# Release 5: Manual review
echo ""
echo "Release 5: Manual review..."
echo "Sample files:"
head -30 ~/.claude/hooks/enforce-checkpoints.sh
echo ""
read -p "Does output look correct? (y/n) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
  echo "Aborted - restore from backups"
  exit 1
fi

# Release 6: Cleanup
echo ""
echo "Release 6: Cleaning up backups..."
rm -f ~/.claude/hooks/*.backup-*
echo "✅ Backups removed"

echo ""
echo "=== Code removal complete ==="
```

## Prevention via Automation

**Create pre-commit hook to catch gutted files**:

```bash
#!/bin/bash
# .git/hooks/pre-commit
# Prevents committing gutted hook files

HOOKS_DIR=".claude/hooks"
MIN_LINES=10

for hook in "$HOOKS_DIR"/*.sh; do
  if [[ -f "$hook" ]] && git diff --cached --name-only | grep -q "$(basename "$hook")"; then
    functional_lines=$(grep -v '^\s*#' "$hook" | grep -v '^\s*$' | wc -l)
    if [[ $functional_lines -lt $MIN_LINES ]]; then
      echo "❌ ERROR: Attempting to commit gutted hook: $(basename "$hook")" >&2
      echo "   Only $functional_lines functional lines (expected at least $MIN_LINES)" >&2
      echo "   File may have been accidentally damaged during code removal" >&2
      exit 1
    fi
  fi
done
```

## Summary

**Key Principles**:
1. **Precision over speed**: Use specific patterns, not vague ones
2. **Validation per file**: Check each file individually
3. **Test before cleanup**: Keep backups until ALL validation passes
4. **Multiple validation layers**: Syntax + integrity + functional tests
5. **Manual review**: Sample check before declaring complete

**Remember**: It's better to be slow and careful than fast and destructive. Functional code is irreplaceable; removal can always wait for proper validation.

## Related Documentation

- [tool-usage.md](../../docs/optional-modules/tool-usage.md) - Pattern matching best practices
- [git-workflow.md](../../docs/project/git-workflow.md) - Backup-Verify-Cleanup pattern

## Success Criteria

Code removal is successful when:
1. ✅ Backups created before removal
2. ✅ Patterns identified precisely
3. ✅ Per-file validation passed
4. ✅ Syntax checks passed
5. ✅ Integrity checks passed (no gutted files)
6. ✅ Functional tests passed
7. ✅ Manual review completed
8. ✅ Backups removed after all validation
9. ✅ No errors in any validation step
10. ✅ Files still work as intended
