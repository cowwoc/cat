# Version Path Resolution

## Overview
Centralized path resolution for flexible versioning schemes. All commands and skills should reference
this document instead of hardcoding version path assumptions.

## Supported Versioning Schemes

| Scheme | Structure | Example |
|--------|-----------|---------|
| Major only | `v$MAJOR/` | `v1/`, `v2/` |
| Major+Minor | `v$MAJOR/v$MAJOR.$MINOR/` | `v1/v1.0/`, `v1/v1.1/` |
| Major+Minor+Patch | `v$MAJOR/v$MAJOR.$MINOR/v$MAJOR.$MINOR.$PATCH/` | `v1/v1.0/v1.0.1/` |

## Path Resolution Functions

### Detect Versioning Scheme

```bash
# Detect which versioning scheme a project uses
detect_version_scheme() {
  local ISSUES_DIR=".claude/cat/issues"

  # Check for patch-level directories (most specific first)
  if find "$ISSUES_DIR" -mindepth 3 -maxdepth 3 -type d -name "v*.*.*" 2>/dev/null | head -1 | grep -q .; then
    echo "patch"
    return
  fi

  # Check for minor-level directories
  if find "$ISSUES_DIR" -mindepth 2 -maxdepth 2 -type d -name "v*.*" 2>/dev/null | head -1 | grep -q .; then
    echo "minor"
    return
  fi

  # Default to major-only
  echo "major"
}
```

### Get Version Path

```bash
# Get the path to a version directory
# Usage: get_version_path $MAJOR [$MINOR] [$PATCH]
get_version_path() {
  local MAJOR="$1"
  local MINOR="$2"
  local PATCH="$3"
  local BASE=".claude/cat/issues"

  if [[ -n "$PATCH" ]]; then
    echo "${BASE}/v${MAJOR}/v${MAJOR}.${MINOR}/v${MAJOR}.${MINOR}.${PATCH}"
  elif [[ -n "$MINOR" ]]; then
    echo "${BASE}/v${MAJOR}/v${MAJOR}.${MINOR}"
  else
    echo "${BASE}/v${MAJOR}"
  fi
}
```

### Get Task Path

```bash
# Get the path to a task directory
# Usage: get_task_path $MAJOR $MINOR $TASK_NAME [$PATCH]
get_task_path() {
  local MAJOR="$1"
  local MINOR="$2"
  local TASK_NAME="$3"
  local PATCH="$4"

  local VERSION_PATH=$(get_version_path "$MAJOR" "$MINOR" "$PATCH")
  echo "${VERSION_PATH}/${TASK_NAME}"
}
```

### Find Version Directories

```bash
# Find all minor versions under a major version
# Usage: find_minor_versions $MAJOR
find_minor_versions() {
  local MAJOR="$1"
  local BASE=".claude/cat/issues/v${MAJOR}"

  find "$BASE" -mindepth 1 -maxdepth 1 -type d -name "v${MAJOR}.*" 2>/dev/null | sort -V
}

# Find all tasks in a version
# Usage: find_tasks_in_version $MAJOR $MINOR
find_tasks_in_version() {
  local MAJOR="$1"
  local MINOR="$2"
  local VERSION_PATH=$(get_version_path "$MAJOR" "$MINOR")

  find "$VERSION_PATH" -mindepth 1 -maxdepth 1 -type d ! -name "v*" 2>/dev/null
}
```

## Path Patterns for Commands

### Standard Paths

| Purpose | Pattern |
|---------|---------|
| Major version dir | `.claude/cat/issues/v${MAJOR}` |
| Minor version dir | `.claude/cat/issues/v${MAJOR}/v${MAJOR}.${MINOR}` |
| Patch version dir | `.claude/cat/issues/v${MAJOR}/v${MAJOR}.${MINOR}/v${MAJOR}.${MINOR}.${PATCH}` |
| Task dir | `$(get_version_path $MAJOR $MINOR)/${TASK_NAME}` |
| Task STATE.md | `$(get_task_path $MAJOR $MINOR $TASK_NAME)/STATE.md` |
| Task PLAN.md | `$(get_task_path $MAJOR $MINOR $TASK_NAME)/PLAN.md` |
| Version STATE.md | `$(get_version_path $MAJOR $MINOR)/STATE.md` |
| Version CHANGELOG.md | `$(get_version_path $MAJOR $MINOR)/CHANGELOG.md` |

### Glob Patterns

| Purpose | Pattern |
|---------|---------|
| All major versions | `.claude/cat/issues/v*` |
| All minor versions in major | `.claude/cat/issues/v${MAJOR}/v${MAJOR}.*` |
| All patches in minor | `.claude/cat/issues/v${MAJOR}/v${MAJOR}.${MINOR}/v${MAJOR}.${MINOR}.*` |
| All tasks (any version) | `.claude/cat/issues/v*/v*.*/*/STATE.md` |

## Usage in Commands/Skills

Instead of hardcoding paths like:
```bash
# WRONG - hardcoded assumption
TASK_DIR=".claude/cat/issues/v$MAJOR/v$MAJOR.$MINOR/$TASK_NAME"
```

Use the resolution functions:
```bash
# CORRECT - flexible resolution
TASK_DIR=$(get_task_path "$MAJOR" "$MINOR" "$TASK_NAME")
```
