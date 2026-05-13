#!/usr/bin/env bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: run-hook.sh <handler>" >&2
  exit 2
fi

handler="$1"
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
plugin_root="$(cd "$script_dir/../.." && pwd -P)"
plugin_version="$(basename "$plugin_root")"
plugin_name="$(basename "$(dirname "$plugin_root")")"
marketplace_name="$(basename "$(dirname "$(dirname "$plugin_root")")")"
input_file="$(mktemp)"
adapted_file="$(mktemp)"
cleanup() {
  rm -f "$input_file" "$adapted_file"
}
trap cleanup EXIT

cat > "$input_file"

project_dir="$(python3 - "$input_file" <<'PY'
import json
import os
import sys

try:
    with open(sys.argv[1], encoding="utf-8") as f:
        data = json.load(f)
except Exception:
    data = {}
print(data.get("cwd") or data.get("project_dir") or os.getcwd())
PY
)"

export CAT_PLUGIN_ROOT="$plugin_root"
export CAT_PROJECT_DIR="$project_dir"
export CAT_PLUGIN_DATA="${CODEX_PLUGIN_DATA:-${CAT_PLUGIN_DATA:-${HOME}/.codex/plugins/data/cat-cat}}"
export CAT_RUNTIME="codex"
export CLAUDE_PLUGIN_ROOT="$CAT_PLUGIN_ROOT"
export CLAUDE_PROJECT_DIR="$CAT_PROJECT_DIR"
export CLAUDE_PLUGIN_DATA="$CAT_PLUGIN_DATA"
export CLAUDE_CONFIG_DIR="${CODEX_HOME:-${HOME}/.codex}"

client_bin="${CAT_PLUGIN_DATA}/client/bin"

if [[ "$handler" == "codex-session-start" ]]; then
  exec "$client_bin/java" \
    -m io.github.cowwoc.cat.client/io.github.cowwoc.cat.codex.hook.SessionStartHook \
    "$CAT_PROJECT_DIR" \
    "$CLAUDE_PLUGIN_DATA" \
    "$CLAUDE_CONFIG_DIR" \
    "$marketplace_name" \
    "$plugin_name" \
    "$plugin_version" \
    "${TZ:-UTC}" \
    < "$input_file"
fi

python3 - "$input_file" > "$adapted_file" <<'PY'
import hashlib
import json
import os
import re
import sys

def first_string(*values):
    for value in values:
        if isinstance(value, str) and value:
            return value
    return ""

def first_object(*values):
    for value in values:
        if isinstance(value, dict):
            return value
    return {}

def first_value(*values):
    for value in values:
        if value is not None:
            return value
    return None

with open(sys.argv[1], encoding="utf-8") as f:
    data = json.load(f)

tool = data.get("tool") if isinstance(data.get("tool"), dict) else {}
tool_input = first_object(data.get("tool_input"), data.get("input"), data.get("arguments"), tool.get("input"))
raw_tool_result = first_value(data.get("tool_result"), data.get("tool_response"), data.get("result"),
                              data.get("output"))
if isinstance(raw_tool_result, dict):
    tool_result = raw_tool_result
elif isinstance(raw_tool_result, str):
    tool_result = {"stdout": raw_tool_result}
else:
    tool_result = {}
tool_name = first_string(data.get("tool_name"), data.get("toolName"), tool.get("name"), data.get("name"))

if tool_name in ("functions.exec_command", "exec_command"):
    command = first_string(tool_input.get("command"), tool_input.get("cmd"))
    tool_name = "Bash"
    tool_input = dict(tool_input)
    tool_input["command"] = command

session_id = first_string(data.get("session_id"), data.get("sessionId"), os.environ.get("CODEX_THREAD_ID"))
if not session_id:
    seed = first_string(data.get("conversation_id"), data.get("thread_id"), data.get("cwd"), os.getcwd())
    session_id = "codex_" + hashlib.sha256(seed.encode("utf-8")).hexdigest()[:16]
session_id = re.sub(r"[^A-Za-z0-9_-]", "_", session_id)

adapted = dict(data)
if adapted.get("transcript_path") is None:
    adapted.pop("transcript_path", None)
adapted["session_id"] = session_id
adapted["tool_name"] = tool_name
adapted["tool_input"] = tool_input
adapted["tool_result"] = tool_result
if "message" not in adapted:
    prompt = first_string(data.get("prompt"), data.get("user_prompt"), data.get("user_message"))
    if prompt:
        adapted["message"] = prompt

json.dump(adapted, sys.stdout)
PY

CAT_SESSION_ID="$(python3 - "$adapted_file" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as f:
    print(json.load(f).get("session_id", ""))
PY
)"
export CAT_SESSION_ID
export CLAUDE_SESSION_ID="$CAT_SESSION_ID"

if [[ "$handler" == "post-tool-use-failure" ]]; then
  if ! python3 - "$adapted_file" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as f:
    data = json.load(f)

tool_result = data.get("tool_result")
if not isinstance(tool_result, dict):
    tool_result = {}
exit_code = tool_result.get("exit_code", tool_result.get("exitCode", 0))
try:
    exit_code = int(exit_code)
except (TypeError, ValueError):
    exit_code = 0

has_error = isinstance(data.get("error"), str) and bool(data["error"])
if exit_code != 0 or has_error:
    sys.exit(0)
sys.exit(1)
PY
  then
    echo '{}'
    exit 0
  fi
fi

if [[ "$handler" == "pre-write" ]]; then
  python3 - "$adapted_file" "$client_bin/pre-write" <<'PY'
import json
import subprocess
import sys

def as_dict(value):
    if isinstance(value, dict):
        return value
    return {}

def patch_candidates(data):
    for key in ("tool_input", "input", "arguments"):
        value = data.get(key)
        if isinstance(value, str):
            yield value
        if isinstance(value, dict):
            for nested_key in ("patch", "input", "content", "cmd", "command"):
                nested = value.get(nested_key)
                if isinstance(nested, str):
                    yield nested
    for key in ("patch", "content", "cmd", "command"):
        value = data.get(key)
        if isinstance(value, str):
            yield value

def add_file_content(lines, start_index):
    content_lines = []
    index = start_index + 1
    while index < len(lines):
        line = lines[index]
        if line.startswith("*** "):
            break
        if line.startswith("+"):
            content_lines.append(line[1:])
        index += 1
    return "\n".join(content_lines)

def parse_patch(patch):
    files = []
    lines = patch.splitlines()
    for index, line in enumerate(lines):
        if line.startswith("*** Add File: "):
            files.append({
                "tool_name": "Write",
                "file_path": line.removeprefix("*** Add File: ").strip(),
                "content": add_file_content(lines, index),
            })
        elif line.startswith("*** Update File: "):
            files.append({
                "tool_name": "Edit",
                "file_path": line.removeprefix("*** Update File: ").strip(),
            })
        elif line.startswith("*** Delete File: "):
            files.append({
                "tool_name": "Edit",
                "file_path": line.removeprefix("*** Delete File: ").strip(),
            })
        elif line.startswith("*** Move to: "):
            files.append({
                "tool_name": "Write",
                "file_path": line.removeprefix("*** Move to: ").strip(),
            })
    return [entry for entry in files if entry["file_path"]]

with open(sys.argv[1], encoding="utf-8") as f:
    data = json.load(f)

patch = next((candidate for candidate in patch_candidates(data)
              if "*** Begin Patch" in candidate and "*** End Patch" in candidate), "")
if not patch:
    print(json.dumps({
        "decision": "block",
        "reason": "Codex apply_patch hook could not find patch text in hook input."
    }))
    sys.exit(0)

file_entries = parse_patch(patch)
if not file_entries:
    print(json.dumps({
        "decision": "block",
        "reason": "Codex apply_patch hook could not extract changed file paths from the patch."
    }))
    sys.exit(0)

additional_contexts = []
for entry in file_entries:
    per_file = dict(data)
    tool_input = {"file_path": entry["file_path"]}
    if "content" in entry:
        tool_input["content"] = entry["content"]
    per_file["tool_name"] = entry["tool_name"]
    per_file["tool_input"] = tool_input

    completed = subprocess.run([sys.argv[2]], input=json.dumps(per_file), text=True,
                               stdout=subprocess.PIPE, stderr=sys.stderr, check=False)
    stdout = completed.stdout.strip()
    if not stdout:
        continue
    try:
        output = json.loads(stdout)
    except json.JSONDecodeError:
        print(stdout)
        sys.exit(0)
    if output.get("decision") == "block":
        print(json.dumps(output))
        sys.exit(0)
    hook_output = output.get("hookSpecificOutput")
    if isinstance(hook_output, dict):
        context = hook_output.get("additionalContext")
        if isinstance(context, str) and context:
            additional_contexts.append(context)

if not additional_contexts:
    print("{}")
else:
    print(json.dumps({
        "hookSpecificOutput": {
            "hookEventName": "PreToolUse",
            "additionalContext": "\n\n".join(additional_contexts),
        }
    }))
PY
  exit $?
fi

exec "$client_bin/$handler" < "$adapted_file"
