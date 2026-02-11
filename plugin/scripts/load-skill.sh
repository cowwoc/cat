#!/bin/bash
# Conditional skill loader: loads full content on first invocation,
# tiny reference on subsequent invocations within the same session.

CLAUDE_PLUGIN_ROOT="$1"
SKILL="$2"
CLAUDE_SESSION_ID="$3"
CLAUDE_PROJECT_DIR="${4:-}"
export CLAUDE_PROJECT_DIR
F="/tmp/cat-skills-loaded-$CLAUDE_SESSION_ID"

# Escape sed metacharacters for safe substitution
escape_sed() {
  printf '%s\n' "$1" | sed -e 's/[&\\/]/\\&/g'
}

# Function to substitute environment variables in content
substitute_vars() {
  local root_escaped session_id_escaped project_dir_escaped
  root_escaped=$(escape_sed "$CLAUDE_PLUGIN_ROOT")
  session_id_escaped=$(escape_sed "$CLAUDE_SESSION_ID")
  project_dir_escaped=$(escape_sed "$CLAUDE_PROJECT_DIR")

  sed \
    -e "s|\${CLAUDE_PLUGIN_ROOT}|$root_escaped|g" \
    -e "s|\${CLAUDE_SESSION_ID}|$session_id_escaped|g" \
    -e "s|\${CLAUDE_PROJECT_DIR}|$project_dir_escaped|g"
}

# Run skill handler if present (always runs for dynamic output)
HANDLER="$CLAUDE_PLUGIN_ROOT/skills/$SKILL/handler.sh"
if [[ -x "$HANDLER" ]]; then
  "$HANDLER" | substitute_vars
fi

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
