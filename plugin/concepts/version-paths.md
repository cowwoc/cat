<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Version Path Resolution

> **See also:** [hierarchy.md](hierarchy.md) for version semantics and dependency rules.

## Overview
Centralized path resolution for flexible versioning schemes. All commands and skills should reference
this document instead of hardcoding version path assumptions.

## Supported Versioning Schemes

See [version-scheme.md](version-scheme.md) for detailed scheme documentation.

| Scheme | Structure | Example |
|--------|-----------|---------|
| Major only | `v$MAJOR/` | `v1/`, `v2/` |
| Major+Minor | `v$MAJOR/v$MAJOR.$MINOR/` | `v1/v1.0/`, `v1/v1.1/` |
| Major+Minor+Patch | `v$MAJOR/v$MAJOR.$MINOR/v$MAJOR.$MINOR.$PATCH/` | `v1/v1.0/v1.0.1/` |

## Directory Structure

Issues are placed **directly under the version directory** (not in a `issue/` subdirectory).

The version directory varies by scheme, but issue placement is consistent:

| Scheme | Issue Path |
|--------|-----------|
| MAJOR only | `.claude/cat/issues/v1/my-issue/` |
| MAJOR.MINOR | `.claude/cat/issues/v1/v1.0/my-issue/` |
| MAJOR.MINOR.PATCH | `.claude/cat/issues/v1/v1.0/v1.0.1/my-issue/` |

Example (MAJOR.MINOR scheme):
```
.claude/cat/issues/
└── v1/
    └── v1.0/
        ├── STATE.md
        ├── PLAN.md
        ├── CHANGELOG.md
        └── my-issue-name/      ← Issue directory (directly under version)
            ├── STATE.md
            └── PLAN.md
```

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

### Get Issue Path

```bash
# Get the path to a issue directory
# Usage: get_issue_path $MAJOR $MINOR $ISSUE_NAME [$PATCH]
get_issue_path() {
  local MAJOR="$1"
  local MINOR="$2"
  local ISSUE_NAME="$3"
  local PATCH="$4"

  local VERSION_PATH=$(get_version_path "$MAJOR" "$MINOR" "$PATCH")
  echo "${VERSION_PATH}/${ISSUE_NAME}"
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

# Find all issues in a version
# Usage: find_issues_in_version $MAJOR $MINOR
find_issues_in_version() {
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
| Issue dir | `$(get_version_path $MAJOR $MINOR)/${ISSUE_NAME}` |
| Issue STATE.md | `$(get_issue_path $MAJOR $MINOR $ISSUE_NAME)/STATE.md` |
| Issue PLAN.md | `$(get_issue_path $MAJOR $MINOR $ISSUE_NAME)/PLAN.md` |
| Version STATE.md | `$(get_version_path $MAJOR $MINOR)/STATE.md` |
| Version CHANGELOG.md | `$(get_version_path $MAJOR $MINOR)/CHANGELOG.md` |

### Glob Patterns

| Purpose | Pattern |
|---------|---------|
| All major versions | `.claude/cat/issues/v*` |
| All minor versions in major | `.claude/cat/issues/v${MAJOR}/v${MAJOR}.*` |
| All patches in minor | `.claude/cat/issues/v${MAJOR}/v${MAJOR}.${MINOR}/v${MAJOR}.${MINOR}.*` |
| All issues (any version) | `.claude/cat/issues/v*/v*.*/*/STATE.md` |

## Usage in Commands/Skills

Instead of hardcoding paths like:
```bash
# WRONG - hardcoded assumption
ISSUE_DIR=".claude/cat/issues/v$MAJOR/v$MAJOR.$MINOR/$ISSUE_NAME"
```

Use the resolution functions:
```bash
# CORRECT - flexible resolution
ISSUE_DIR=$(get_issue_path "$MAJOR" "$MINOR" "$ISSUE_NAME")
```
