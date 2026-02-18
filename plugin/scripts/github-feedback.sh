#!/usr/bin/env bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
#
# github-feedback.sh - GitHub operations for filing bug reports
#
# Wraps the GitHubFeedback Java class to search GitHub issues and open a pre-filled
# issue creation page in the user's browser. No authentication token is required.
#
# Usage:
#   github-feedback.sh search <query>
#   github-feedback.sh open <title> <body> [label1,label2,...]
#
# Environment:
#   CLAUDE_PLUGIN_ROOT   Plugin root directory (required)

set -euo pipefail

PLUGIN_ROOT="${CLAUDE_PLUGIN_ROOT:?CLAUDE_PLUGIN_ROOT must be set}"

if [[ $# -lt 2 ]]; then
  echo '{"status": "error", "message": "Usage: github-feedback.sh search <query> | github-feedback.sh open <title> <body> [labels]"}' >&2
  exit 1
fi

"$PLUGIN_ROOT/client/bin/java" \
  -Xms16m \
  -Xmx96m \
  -Dstdin.encoding=UTF-8 \
  -Dstdout.encoding=UTF-8 \
  -Dstderr.encoding=UTF-8 \
  -XX:+UseSerialGC \
  -XX:TieredStopAtLevel=1 \
  -m io.github.cowwoc.cat.hooks/io.github.cowwoc.cat.hooks.util.GitHubFeedback \
  "$@"
