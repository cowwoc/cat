#!/usr/bin/env bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.

# Common CAT environment setup.
# Source this script to get validated CAT directory variables.
#
# Provides:
#   ENCODED_PROJECT_DIR - project path with / and . replaced by -
#   PROJECT_CAT_DIR     - external CAT storage path for this project
#   LOCKS_DIR           - lock files directory
#   WORKTREES_DIR       - worktrees directory

if [[ -z "${CLAUDE_CONFIG_DIR:-}" ]]; then
  echo "ERROR: CLAUDE_CONFIG_DIR is not set." >&2
  exit 1
fi

if [[ -z "${CLAUDE_PROJECT_DIR:-}" ]]; then
  echo "ERROR: CLAUDE_PROJECT_DIR is not set." >&2
  exit 1
fi

ENCODED_PROJECT_DIR=$(echo "${CLAUDE_PROJECT_DIR}" | tr '/.' '-')
PROJECT_CAT_DIR="${CLAUDE_CONFIG_DIR}/projects/${ENCODED_PROJECT_DIR}/cat"
LOCKS_DIR="${PROJECT_CAT_DIR}/locks"
WORKTREES_DIR="${PROJECT_CAT_DIR}/worktrees"
