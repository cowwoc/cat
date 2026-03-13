#!/usr/bin/env bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.

# Common CAT environment setup.
# Source this script to get validated CAT directory variables.
#
# Requires:
#   CLAUDE_PROJECT_DIR - the project directory
#
# Provides:
#   LOCKS_DIR     - lock files directory
#   WORKTREES_DIR - worktrees directory

if [[ -z "${CLAUDE_PROJECT_DIR:-}" ]]; then
  echo "ERROR: CLAUDE_PROJECT_DIR is not set." >&2
  exit 1
fi

LOCKS_DIR="${CLAUDE_PROJECT_DIR}/.cat/work/locks"
WORKTREES_DIR="${CLAUDE_PROJECT_DIR}/.cat/work/worktrees"
