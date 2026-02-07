#!/bin/bash
# Conditional skill loader: loads full content on first invocation,
# tiny reference on subsequent invocations within the same session.

# Read environment variables (fail-fast if not set)
if [ -z "${CLAUDE_PLUGIN_ROOT:-}" ]; then
  echo "ERROR: CLAUDE_PLUGIN_ROOT not set in environment" >&2
  exit 1
fi

if [ -z "${CLAUDE_SESSION_ID:-}" ]; then
  echo "ERROR: CLAUDE_SESSION_ID not set in environment" >&2
  exit 1
fi

SKILL="$1"
F="/tmp/cat-skills-loaded-$CLAUDE_SESSION_ID"

# Escape sed metacharacters for safe substitution
escape_sed() {
  printf '%s\n' "$1" | sed -e 's/[&\\/]/\\&/g'
}

# Function to substitute environment variables in content
substitute_vars() {
  local root_escaped session_id_escaped
  root_escaped=$(escape_sed "$CLAUDE_PLUGIN_ROOT")
  session_id_escaped=$(escape_sed "$CLAUDE_SESSION_ID")

  sed \
    -e "s|\${CLAUDE_PLUGIN_ROOT}|$root_escaped|g" \
    -e "s|\${CLAUDE_SESSION_ID}|$session_id_escaped|g"
}

if grep -qx "$SKILL" "$F" 2>/dev/null; then
  substitute_vars < "$CLAUDE_PLUGIN_ROOT/skills/reference.md"
else
  CTX="$CLAUDE_PLUGIN_ROOT/skills/$SKILL/context.list"
  if [[ -f "$CTX" ]]; then
    echo "<execution_context>"
    while IFS= read -r line; do
      [[ -n "$line" ]] && cat "$CLAUDE_PLUGIN_ROOT/$line" | substitute_vars
    done < "$CTX"
    echo "</execution_context>"
  fi
  substitute_vars < "$CLAUDE_PLUGIN_ROOT/skills/$SKILL/content.md"
  echo "$SKILL" >> "$F"
fi
