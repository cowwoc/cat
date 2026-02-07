#!/bin/bash
# Conditional skill loader: loads full content on first invocation,
# tiny reference on subsequent invocations within the same session.
ROOT="$1"
SKILL="$2"
SESSION_ID="$3"
PROJECT_DIR="$4"
F="/tmp/cat-skills-loaded-$SESSION_ID"

# Escape sed metacharacters for safe substitution
escape_sed() {
  printf '%s\n' "$1" | sed -e 's/[&\\/]/\\&/g'
}

# Function to substitute environment variables in content
substitute_vars() {
  local root_escaped project_dir_escaped session_id_escaped
  root_escaped=$(escape_sed "$ROOT")
  project_dir_escaped=$(escape_sed "$PROJECT_DIR")
  session_id_escaped=$(escape_sed "$SESSION_ID")

  sed \
    -e "s|\${CLAUDE_PLUGIN_ROOT}|$root_escaped|g" \
    -e "s|\${CLAUDE_PROJECT_DIR}|$project_dir_escaped|g" \
    -e "s|\${CLAUDE_SESSION_ID}|$session_id_escaped|g"
}

if grep -qx "$SKILL" "$F" 2>/dev/null; then
  substitute_vars < "$ROOT/skills/reference.md"
else
  CTX="$ROOT/skills/$SKILL/context.list"
  if [[ -f "$CTX" ]]; then
    echo "<execution_context>"
    while IFS= read -r line; do
      [[ -n "$line" ]] && cat "$ROOT/$line" | substitute_vars
    done < "$CTX"
    echo "</execution_context>"
  fi
  substitute_vars < "$ROOT/skills/$SKILL/content.md"
  echo "$SKILL" >> "$F"
fi
