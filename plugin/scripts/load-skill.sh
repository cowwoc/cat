#!/bin/bash
# Conditional skill loader: loads full content on first invocation,
# tiny reference on subsequent invocations within the same session.

# Required parameters - fail fast if missing
if [[ -z "${1:-}" ]]; then
  echo "ERROR: CLAUDE_PLUGIN_ROOT (arg 1) is required" >&2
  exit 1
fi
if [[ -z "${2:-}" ]]; then
  echo "ERROR: SKILL name (arg 2) is required" >&2
  exit 1
fi
if [[ -z "${3:-}" ]]; then
  echo "ERROR: CLAUDE_SESSION_ID (arg 3) is required" >&2
  exit 1
fi
if [[ -z "${CLAUDE_PROJECT_DIR:-}" ]]; then
  echo "ERROR: CLAUDE_PROJECT_DIR environment variable is not set" >&2
  exit 1
fi

CLAUDE_PLUGIN_ROOT="$1"
SKILL="$2"
CLAUDE_SESSION_ID="$3"

# Invoke Java SkillLoader
"$CLAUDE_PLUGIN_ROOT/hooks/bin/java" \
  -Xms16m \
  -Xmx64m \
  -Dstdout.encoding=UTF-8 \
  -XX:+UseSerialGC \
  -XX:TieredStopAtLevel=1 \
  -m io.github.cowwoc.cat.hooks/io.github.cowwoc.cat.hooks.util.SkillLoader \
  "$CLAUDE_PLUGIN_ROOT" \
  "$SKILL" \
  "$CLAUDE_SESSION_ID" \
  "$CLAUDE_PROJECT_DIR"
