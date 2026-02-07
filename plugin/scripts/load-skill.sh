#!/bin/bash
# Conditional skill loader: loads full content on first invocation,
# tiny reference on subsequent invocations within the same session.
ROOT="$1"
SKILL="$2"
F="/tmp/cat-skills-loaded-$3"

if grep -qx "$SKILL" "$F" 2>/dev/null; then
  cat "$ROOT/skills/reference.md"
else
  CTX="$ROOT/skills/$SKILL/context.list"
  if [[ -f "$CTX" ]]; then
    echo "<execution_context>"
    while IFS= read -r line; do
      [[ -n "$line" ]] && cat "$ROOT/$line"
    done < "$CTX"
    echo "</execution_context>"
  fi
  cat "$ROOT/skills/$SKILL/content.md"
  echo "$SKILL" >> "$F"
fi
