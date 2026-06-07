#!/usr/bin/env bash
set -euo pipefail

skill_file="/workspace/.agents/skills/caveman/SKILL.md"
guard_file="/workspace/.cat/rules/common/caveman-guard.md"
if [[ ! -f "$skill_file" ]]; then
  printf '{}\n'
  exit 0
fi

guard_text=""
if [[ -f "$guard_file" ]]; then
  guard_text="$(sed '/\r$/d' "$guard_file")"
fi

skill_body="$(
  awk '
    { sub(/\r$/, "") }
    BEGIN { separators = 0 }
    /^---$/ { separators++; next }
    separators >= 2 { print }
  ' "$skill_file" | sed '1{/^$/d;}'
)"

if [[ -z "$skill_body" ]]; then
  printf '{}\n'
  exit 0
fi

context="$skill_body"
if [[ -n "$guard_text" ]]; then
  context="$guard_text"$'\n\n'"$skill_body"
fi

jq -Rn --arg ctx "$context" \
  '{hookSpecificOutput:{hookEventName:"SessionStart",additionalContext:$ctx}}'
