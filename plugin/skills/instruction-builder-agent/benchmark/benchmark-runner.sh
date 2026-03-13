#!/usr/bin/env bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
set -euo pipefail

# Global temp directory used by cmd_detect_changes; initialized to empty so the EXIT trap never
# references an unbound variable under set -u.
_BENCHMARK_TMP_DIR=""
trap '[[ -n "${_BENCHMARK_TMP_DIR}" ]] && rm -rf "${_BENCHMARK_TMP_DIR}"' EXIT

# benchmark-runner.sh — Incremental benchmark driver for instruction-builder-agent.
#
# Usage:
#   benchmark-runner.sh detect-changes <old_skill_sha> <new_skill_path> <test_cases_path>
#     Compares the skill at <old_skill_sha>:<new_skill_path> (git object) against the current file at
#     <new_skill_path> and outputs a JSON document identifying which test cases need re-running.
#     <test_cases_path> is the path to test-cases.json.
#
#   benchmark-runner.sh extract-units <skill_path>
#     Parses <skill_path>, strips frontmatter, and outputs a line-numbered text suitable for semantic
#     unit extraction. Does NOT extract units itself — unit extraction is performed inline by the agent
#     using validation-protocol.md. This command exists so agents can obtain a clean, line-numbered
#     version of the skill body for location tagging.
#
#   benchmark-runner.sh map-units <test_cases_path> <changed_units_json>
#     Given test-cases.json and a JSON array of changed semantic unit IDs, outputs the set of test case
#     IDs that cover at least one changed unit. All other test case IDs are carried forward as passing.
#     <changed_units_json> is a JSON array: ["unit_1", "unit_5", ...]
#
# All commands write JSON to stdout. Errors are written to stderr and exit non-zero.

COMMAND="${1:-}"

###############################################################################
# parse_frontmatter <skill_path>
#   Sets global FRONTMATTER and BODY_LINES (array of lines after frontmatter).
#
#   GLOBAL MUTATION CONTRACT: Callers MUST copy FRONTMATTER and BODY_LINES
#   to local variables immediately after each call before calling again, as
#   a second call overwrites the globals set by the first.
###############################################################################
parse_frontmatter() {
  local skill_path="$1"

  if [[ ! -f "$skill_path" ]]; then
    echo "ERROR: benchmark-runner.sh parse_frontmatter: file not found: $skill_path" >&2
    exit 1
  fi

  local in_frontmatter=0
  local frontmatter_done=0
  local line_num=0
  local fm_lines=()
  BODY_LINES=()

  while IFS= read -r line; do
    line_num=$((line_num + 1))

    if [[ $line_num -eq 1 && "$line" == "---" ]]; then
      in_frontmatter=1
      fm_lines+=("$line")
      continue
    fi

    if [[ $in_frontmatter -eq 1 ]]; then
      fm_lines+=("$line")
      if [[ "$line" == "---" && $line_num -gt 1 ]]; then
        in_frontmatter=0
        frontmatter_done=1
        continue
      fi
      continue
    fi

    BODY_LINES+=("$line")
  done < "$skill_path"

  if [[ $frontmatter_done -eq 0 ]]; then
    # No frontmatter — entire file is body (already in BODY_LINES from the loop above)
    FRONTMATTER=""
  else
    FRONTMATTER=$(printf '%s\n' "${fm_lines[@]}")
  fi
}

###############################################################################
# validate_path_within_boundary <boundary> <candidate>
#   Verifies that <candidate> resolves to a path inside <boundary>.
#   Exits with an error if a path traversal is detected.
###############################################################################
validate_path_within_boundary() {
  local boundary="$1" candidate="$2"
  local resolved_boundary resolved_candidate
  resolved_boundary=$(realpath --canonicalize-missing "$boundary")
  resolved_candidate=$(realpath --canonicalize-missing "$candidate")
  if [[ "$resolved_candidate" != "$resolved_boundary"* ]]; then
    echo "ERROR: Path traversal detected: '$candidate' is outside '$boundary'" >&2
    exit 1
  fi
}

###############################################################################
# compute_sha256 <file>
#   Outputs the SHA-256 hex digest of <file>, using sha256sum or shasum -a 256.
###############################################################################
compute_sha256() {
  local file="$1"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$file" | awk '{print $1}'
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$file" | awk '{print $1}'
  else
    echo "ERROR: benchmark-runner.sh: no sha256 tool available" >&2
    exit 1
  fi
}

###############################################################################
# extract_frontmatter_field <field_name>
#   Reads FRONTMATTER (global) and returns the value of <field_name>.
#   Outputs the raw value (unquoted) or empty string if not found.
###############################################################################
extract_frontmatter_field() {
  local field="$1"
  # Match "field: value" or "field: 'value'" or 'field: "value"'
  # Use grep without failing when field is absent, then strip prefix and quotes
  local matched
  matched=$(echo "$FRONTMATTER" | grep -m1 "^${field}:" 2>/dev/null || true)
  if [[ -z "$matched" ]]; then
    printf ''
    return 0
  fi
  printf '%s' "$matched" \
    | sed "s/^${field}:[[:space:]]*//" \
    | sed "s/^['\"]//" \
    | sed "s/['\"]\$//"
}

###############################################################################
# body_with_line_numbers <skill_path>
#   Outputs the skill body (after frontmatter) with line numbers matching
#   the original file, suitable for location-tagged semantic unit extraction.
###############################################################################
body_with_line_numbers() {
  local skill_path="$1"
  parse_frontmatter "$skill_path"

  # Determine the offset: frontmatter occupies some lines at the top.
  local total_lines
  total_lines=$(wc -l < "$skill_path")
  local body_count=${#BODY_LINES[@]}
  local fm_line_count=$((total_lines - body_count))

  local i=0
  for line in "${BODY_LINES[@]}"; do
    local original_line_num=$((fm_line_count + i + 1))
    printf '%d\t%s\n' "$original_line_num" "$line"
    i=$((i + 1))
  done
}

###############################################################################
# sha256 <string>
#   Outputs the SHA-256 hex digest of <string>.
###############################################################################
sha256() {
  local _tmp_sha256
  _tmp_sha256=$(mktemp)
  printf '%s' "$1" > "$_tmp_sha256"
  compute_sha256 "$_tmp_sha256"
  rm -f "$_tmp_sha256"
}

###############################################################################
# diff_skill_bodies <old_body_file> <new_body_file>
#   Runs unified diff and outputs changed line ranges as JSON.
#   Output: {"changed_ranges": [{"start": N, "end": M}, ...]}
###############################################################################
diff_skill_bodies() {
  local old_file="$1"
  local new_file="$2"

  local diff_output
  diff_output=$(diff -u "$old_file" "$new_file" 2>/dev/null || true)

  if [[ -z "$diff_output" ]]; then
    printf '{"changed_ranges":[]}'
    return 0
  fi

  # Parse unified diff hunk headers: @@ -old_start,old_count +new_start,new_count @@
  # We care about the NEW file line numbers (where changes land in the updated skill).
  local ranges=()
  local in_hunk=0
  local new_start=0
  local new_end=0

  while IFS= read -r line; do
    if [[ "$line" =~ ^@@\ -[0-9]+(,[0-9]+)?\ \+([0-9]+)(,([0-9]+))?\ @@ ]]; then
      # Save previous hunk if any
      if [[ $in_hunk -eq 1 && $new_end -ge $new_start ]]; then
        ranges+=("{\"start\":${new_start},\"end\":${new_end}}")
      fi
      new_start="${BASH_REMATCH[2]}"
      local count="${BASH_REMATCH[4]:-1}"
      if [[ "$count" -eq 0 ]]; then
        new_end=$((new_start))
      else
        new_end=$((new_start + count - 1))
      fi
      in_hunk=1
    fi
  done <<< "$diff_output"

  # Save last hunk
  if [[ $in_hunk -eq 1 && $new_end -ge $new_start ]]; then
    ranges+=("{\"start\":${new_start},\"end\":${new_end}}")
  fi

  if [[ ${#ranges[@]} -eq 0 ]]; then
    printf '{"changed_ranges":[]}'
    return 0
  fi

  local joined
  joined=$(printf '%s,' "${ranges[@]}")
  joined="${joined%,}"
  printf '{"changed_ranges":[%s]}' "$joined"
}

###############################################################################
# parse_unit_location <location_str>
#   Converts a location string like "line 15" or "lines 15-22" into
#   start and end line numbers. Sets LOC_START and LOC_END globals.
###############################################################################
parse_unit_location() {
  local loc="$1"
  LOC_START=0
  LOC_END=0

  if [[ "$loc" =~ ^line[s]?[[:space:]]+([0-9]+)-([0-9]+)$ ]]; then
    LOC_START="${BASH_REMATCH[1]}"
    LOC_END="${BASH_REMATCH[2]}"
  elif [[ "$loc" =~ ^line[s]?[[:space:]]+([0-9]+)$ ]]; then
    LOC_START="${BASH_REMATCH[1]}"
    LOC_END="${LOC_START}"
  else
    # Unknown format — treat as unparseable; unit is considered unchanged
    LOC_START=0
    LOC_END=0
  fi
}

###############################################################################
# ranges_overlap <start1> <end1> <start2> <end2>
#   Returns 0 (true) if the two line ranges overlap, 1 (false) otherwise.
###############################################################################
ranges_overlap() {
  local s1="$1" e1="$2" s2="$3" e2="$4"
  if [[ $s1 -le $e2 && $s2 -le $e1 ]]; then
    return 0
  fi
  return 1
}

###############################################################################
# cmd_extract_units <skill_path>
#   Outputs the skill body with line numbers to stdout so the agent can
#   pass it to the semantic extraction algorithm inline.
###############################################################################
cmd_extract_units() {
  local skill_path="${1:-}"
  if [[ -z "$skill_path" ]]; then
    echo "ERROR: benchmark-runner.sh extract-units: missing <skill_path> argument" >&2
    echo "Usage: benchmark-runner.sh extract-units <skill_path>" >&2
    exit 1
  fi
  if [[ ! -f "$skill_path" ]]; then
    echo "ERROR: benchmark-runner.sh extract-units: file not found: $skill_path" >&2
    exit 1
  fi
  body_with_line_numbers "$skill_path"
}

###############################################################################
# cmd_detect_changes <old_skill_sha> <new_skill_path> <test_cases_path>
#   Compares old vs new skill to identify which semantic units (by location)
#   were changed. For each changed unit, lists associated test case IDs.
#
#   Output JSON:
#   {
#     "skill_changed": true|false,
#     "frontmatter_changed": true|false,
#     "body_changed": true|false,
#     "changed_ranges": [{"start": N, "end": M}, ...],
#     "all_test_case_ids": ["TC1", "TC2", ...],
#     "rerun_test_case_ids": ["TC1"],
#     "carryforward_test_case_ids": ["TC2"],
#     "semantic_units_path_hint": "Run: benchmark-runner.sh extract-units <new_skill_path>"
#   }
#
#   NOTE: This command identifies WHICH test cases to re-run based on changed line ranges.
#   The agent must supply the semantic_unit -> test_case mapping by reading test-cases.json
#   (which contains `semantic_unit_id` fields per test case) and cross-referencing with
#   the semantic units whose locations overlap the changed_ranges.
###############################################################################
cmd_detect_changes() {
  local old_sha="${1:-}"
  local new_skill_path="${2:-}"
  local test_cases_path="${3:-}"

  if [[ -z "$old_sha" || -z "$new_skill_path" || -z "$test_cases_path" ]]; then
    echo "ERROR: benchmark-runner.sh detect-changes: missing arguments" >&2
    echo "Usage: benchmark-runner.sh detect-changes <old_skill_sha> <new_skill_path> <test_cases_path>" >&2
    exit 1
  fi

  if [[ ! -f "$new_skill_path" ]]; then
    echo "ERROR: benchmark-runner.sh detect-changes: new skill file not found: $new_skill_path" >&2
    exit 1
  fi

  if [[ ! -f "$test_cases_path" ]]; then
    echo "ERROR: benchmark-runner.sh detect-changes: test cases file not found: $test_cases_path" >&2
    exit 1
  fi

  # Retrieve the old version via git show
  local old_content
  # Derive repo-relative path for git show
  local repo_root
  repo_root=$(git -C "$(dirname "$new_skill_path")" rev-parse --show-toplevel 2>/dev/null || true)
  if [[ -z "$repo_root" ]]; then
    echo "ERROR: benchmark-runner.sh detect-changes: cannot determine git repo root from: $new_skill_path" >&2
    exit 1
  fi
  local rel_path
  rel_path="${new_skill_path#"${repo_root}/"}"

  if ! old_content=$(git -C "$repo_root" show "${old_sha}:${rel_path}" 2>/dev/null); then
    echo "ERROR: benchmark-runner.sh detect-changes: git show ${old_sha}:${rel_path} failed." >&2
    echo "Verify that the SHA '$old_sha' exists and the path '$rel_path' was tracked at that commit." >&2
    exit 1
  fi

  # Write old and new bodies to temp files for diffing
  _BENCHMARK_TMP_DIR=$(mktemp -d)
  local tmp_dir="${_BENCHMARK_TMP_DIR}"

  local old_full_path="${tmp_dir}/old_skill.md"
  local new_full_path="${tmp_dir}/new_skill.md"
  printf '%s\n' "$old_content" > "$old_full_path"
  cp "$new_skill_path" "$new_full_path"

  # Parse frontmatter from both versions
  local OLD_FRONTMATTER OLD_BODY_LINES NEW_FRONTMATTER NEW_BODY_LINES

  parse_frontmatter "$old_full_path"
  OLD_FRONTMATTER="$FRONTMATTER"
  OLD_BODY_LINES=("${BODY_LINES[@]+"${BODY_LINES[@]}"}")

  parse_frontmatter "$new_full_path"
  NEW_FRONTMATTER="$FRONTMATTER"
  NEW_BODY_LINES=("${BODY_LINES[@]+"${BODY_LINES[@]}"}")

  # Compare frontmatter
  local fm_changed="false"
  if [[ "$(sha256 "$OLD_FRONTMATTER")" != "$(sha256 "$NEW_FRONTMATTER")" ]]; then
    fm_changed="true"
  fi

  # Write body files for diff
  local old_body_file="${tmp_dir}/old_body.txt"
  local new_body_file="${tmp_dir}/new_body.txt"
  printf '%s\n' "${OLD_BODY_LINES[@]+"${OLD_BODY_LINES[@]}"}" > "$old_body_file"
  printf '%s\n' "${NEW_BODY_LINES[@]+"${NEW_BODY_LINES[@]}"}" > "$new_body_file"

  # Diff bodies
  local diff_json
  diff_json=$(diff_skill_bodies "$old_body_file" "$new_body_file")

  local body_changed="false"
  local changed_ranges_json
  changed_ranges_json=$(printf '%s' "$diff_json" | grep -o '"changed_ranges":\[.*\]' | sed 's/"changed_ranges"://')
  if [[ "$changed_ranges_json" != "[]" ]]; then
    body_changed="true"
  fi

  local skill_changed="false"
  if [[ "$fm_changed" == "true" || "$body_changed" == "true" ]]; then
    skill_changed="true"
  fi

  # Collect all test case IDs from test-cases.json
  # Format: {"test_cases": [{"test_case_id": "TC1", "semantic_unit_id": "unit_1", ...}, ...]}
  # JSON is pretty-printed (multi-line), so accumulate fields across lines within each object.
  local all_tc_ids=()
  local current_tc_id="" current_unit_id=""
  local depth=0
  while IFS= read -r line; do
    local extracted_tc extracted_unit
    # Track nesting depth to detect top-level array element boundaries
    if [[ "$line" =~ \{ ]]; then depth=$(( depth + $(grep -o '{' <<< "$line" | wc -l) )); fi
    if [[ "$line" =~ \} ]]; then depth=$(( depth - $(grep -o '}' <<< "$line" | wc -l) )); fi
    if extracted_tc=$(printf '%s' "$line" | grep -o '"test_case_id"[[:space:]]*:[[:space:]]*"[^"]*"' | sed 's/.*"\([^"]*\)"$/\1/' 2>/dev/null) && [[ -n "$extracted_tc" ]]; then
      current_tc_id="$extracted_tc"
    fi
    if extracted_unit=$(printf '%s' "$line" | grep -o '"semantic_unit_id"[[:space:]]*:[[:space:]]*"[^"]*"' | sed 's/.*"\([^"]*\)"$/\1/' 2>/dev/null) && [[ -n "$extracted_unit" ]]; then
      current_unit_id="$extracted_unit"
    fi
    # A top-level array element ends when depth returns to 1 (inside the array but outside any object)
    if [[ $depth -eq 1 && -n "$current_tc_id" ]]; then
      all_tc_ids+=("$current_tc_id")
      current_tc_id=""
      current_unit_id=""
    fi
  done < "$test_cases_path"

  # When there are no changed ranges OR skill did not change, all test cases carry forward
  if [[ "$skill_changed" == "false" ]] || [[ "$changed_ranges_json" == "[]" ]]; then
    local all_ids_json="["
    local first=1
    for tc_id in "${all_tc_ids[@]+"${all_tc_ids[@]}"}"; do
      if [[ $first -eq 1 ]]; then first=0; else all_ids_json+=","; fi
      all_ids_json+="\"${tc_id}\""
    done
    all_ids_json+="]"

    printf '{"skill_changed":%s,"frontmatter_changed":%s,"body_changed":%s,"changed_ranges":[],"all_test_case_ids":%s,"rerun_test_case_ids":[],"carryforward_test_case_ids":%s,"semantic_units_path_hint":"Run: benchmark-runner.sh extract-units %s"}\n' \
      "$skill_changed" "$fm_changed" "$body_changed" "$all_ids_json" "$all_ids_json" "$new_skill_path"
    return 0
  fi

  # When frontmatter changed, all test cases must re-run (frontmatter affects model/behavior globally)
  if [[ "$fm_changed" == "true" ]]; then
    local all_ids_json="["
    local first=1
    for tc_id in "${all_tc_ids[@]+"${all_tc_ids[@]}"}"; do
      if [[ $first -eq 1 ]]; then first=0; else all_ids_json+=","; fi
      all_ids_json+="\"${tc_id}\""
    done
    all_ids_json+="]"

    printf '{"skill_changed":true,"frontmatter_changed":true,"body_changed":%s,"changed_ranges":%s,"all_test_case_ids":%s,"rerun_test_case_ids":%s,"carryforward_test_case_ids":[],"semantic_units_path_hint":"Run: benchmark-runner.sh extract-units %s"}\n' \
      "$body_changed" "$changed_ranges_json" "$all_ids_json" "$all_ids_json" "$new_skill_path"
    return 0
  fi

  # Body changed but frontmatter unchanged.
  # We need to determine which test cases are affected.
  # The mapping from semantic units to line ranges requires the agent to have run semantic extraction.
  # This command provides the changed_ranges so the agent can perform the mapping.
  # We output all test case IDs with a note that the agent must apply semantic unit location filtering.
  # The map-units command handles the final filtering once the agent has extracted units.

  local all_ids_json="["
  local first=1
  for tc_id in "${all_tc_ids[@]+"${all_tc_ids[@]}"}"; do
    if [[ $first -eq 1 ]]; then first=0; else all_ids_json+=","; fi
    all_ids_json+="\"${tc_id}\""
  done
  all_ids_json+="]"

  printf '{"skill_changed":true,"frontmatter_changed":false,"body_changed":true,"changed_ranges":%s,"all_test_case_ids":%s,"rerun_test_case_ids":[],"carryforward_test_case_ids":[],"requires_unit_mapping":true,"semantic_units_path_hint":"Run: benchmark-runner.sh extract-units %s to get line-numbered body, then apply semantic unit extraction, then run: benchmark-runner.sh map-units %s <changed_unit_ids_json>"}\n' \
    "$changed_ranges_json" "$all_ids_json" "$new_skill_path" "$test_cases_path"
}

###############################################################################
# cmd_map_units <test_cases_path> <changed_units_json>
#   Given test-cases.json and a JSON array of changed semantic unit IDs,
#   outputs which test cases must re-run and which carry forward.
#
#   Output JSON:
#   {
#     "all_test_case_ids": ["TC1", "TC2", "TC3"],
#     "rerun_test_case_ids": ["TC1", "TC2"],
#     "carryforward_test_case_ids": ["TC3"]
#   }
###############################################################################
cmd_map_units() {
  local test_cases_path="${1:-}"
  local changed_units_json="${2:-}"

  if [[ -z "$test_cases_path" || -z "$changed_units_json" ]]; then
    echo "ERROR: benchmark-runner.sh map-units: missing arguments" >&2
    echo "Usage: benchmark-runner.sh map-units <test_cases_path> <changed_units_json>" >&2
    exit 1
  fi

  if [[ ! -f "$test_cases_path" ]]; then
    echo "ERROR: benchmark-runner.sh map-units: test cases file not found: $test_cases_path" >&2
    exit 1
  fi

  # Parse changed_units_json: a JSON array like ["unit_1", "unit_5"]
  # Extract unit IDs by matching quoted strings
  local changed_units=()
  while IFS= read -r unit_id; do
    if [[ -n "$unit_id" ]]; then
      changed_units+=("$unit_id")
    fi
  done < <(printf '%s' "$changed_units_json" | grep -o '"[^"]*"' 2>/dev/null | tr -d '"' || true)

  if [[ ${#changed_units[@]} -eq 0 ]]; then
    # No changed units — all test cases carry forward
    local all_ids=()
    local cur_tc="" depth=0
    while IFS= read -r line; do
      if [[ "$line" =~ \{ ]]; then depth=$(( depth + $(grep -o '{' <<< "$line" | wc -l) )); fi
      if [[ "$line" =~ \} ]]; then depth=$(( depth - $(grep -o '}' <<< "$line" | wc -l) )); fi
      local tc_id
      if tc_id=$(printf '%s' "$line" | grep -o '"test_case_id"[[:space:]]*:[[:space:]]*"[^"]*"' | sed 's/.*"\([^"]*\)"$/\1/' 2>/dev/null) && [[ -n "$tc_id" ]]; then
        cur_tc="$tc_id"
      fi
      if [[ $depth -eq 1 && -n "$cur_tc" ]]; then
        all_ids+=("$cur_tc")
        cur_tc=""
      fi
    done < "$test_cases_path"

    local all_ids_json="["
    local first=1
    for tc_id in "${all_ids[@]+"${all_ids[@]}"}"; do
      if [[ $first -eq 1 ]]; then first=0; else all_ids_json+=","; fi
      all_ids_json+="\"${tc_id}\""
    done
    all_ids_json+="]"
    printf '{"all_test_case_ids":%s,"rerun_test_case_ids":[],"carryforward_test_case_ids":%s}\n' \
      "$all_ids_json" "$all_ids_json"
    return 0
  fi

  # Parse test-cases.json to build test_case_id -> semantic_unit_id mapping.
  # JSON is pretty-printed (multi-line), so accumulate fields across lines.
  local all_ids=()
  local rerun_ids=()
  local carryforward_ids=()

  local current_tc_id="" current_unit_id=""
  local depth=0
  while IFS= read -r line; do
    local extracted_tc extracted_unit
    # Track nesting depth to detect top-level array element boundaries
    if [[ "$line" =~ \{ ]]; then depth=$(( depth + $(grep -o '{' <<< "$line" | wc -l) )); fi
    if [[ "$line" =~ \} ]]; then depth=$(( depth - $(grep -o '}' <<< "$line" | wc -l) )); fi
    if extracted_tc=$(printf '%s' "$line" | grep -o '"test_case_id"[[:space:]]*:[[:space:]]*"[^"]*"' \
        | sed 's/.*"test_case_id"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/' 2>/dev/null) && [[ -n "$extracted_tc" ]]; then
      current_tc_id="$extracted_tc"
    fi
    if extracted_unit=$(printf '%s' "$line" | grep -o '"semantic_unit_id"[[:space:]]*:[[:space:]]*"[^"]*"' \
        | sed 's/.*"semantic_unit_id"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/' 2>/dev/null) && [[ -n "$extracted_unit" ]]; then
      current_unit_id="$extracted_unit"
    fi
    # A top-level array element ends when depth returns to 1 (inside the array but outside any object)
    if [[ $depth -eq 1 && -n "$current_tc_id" ]]; then
      all_ids+=("$current_tc_id")

      # Check if this test case's semantic_unit_id is in the changed set
      local is_changed=0
      for changed_unit in "${changed_units[@]}"; do
        if [[ "$changed_unit" == "$current_unit_id" ]]; then
          is_changed=1
          break
        fi
      done

      if [[ $is_changed -eq 1 ]]; then
        rerun_ids+=("$current_tc_id")
      else
        carryforward_ids+=("$current_tc_id")
      fi

      current_tc_id=""
      current_unit_id=""
    fi
  done < "$test_cases_path"

  # Build output JSON
  local all_json="[" rerun_json="[" carry_json="["
  local first=1

  for tc_id in "${all_ids[@]+"${all_ids[@]}"}"; do
    if [[ $first -eq 1 ]]; then first=0; else all_json+=","; fi
    all_json+="\"${tc_id}\""
  done
  all_json+="]"

  first=1
  for tc_id in "${rerun_ids[@]+"${rerun_ids[@]}"}"; do
    if [[ $first -eq 1 ]]; then first=0; else rerun_json+=","; fi
    rerun_json+="\"${tc_id}\""
  done
  rerun_json+="]"

  first=1
  for tc_id in "${carryforward_ids[@]+"${carryforward_ids[@]}"}"; do
    if [[ $first -eq 1 ]]; then first=0; else carry_json+=","; fi
    carry_json+="\"${tc_id}\""
  done
  carry_json+="]"

  printf '{"all_test_case_ids":%s,"rerun_test_case_ids":%s,"carryforward_test_case_ids":%s}\n' \
    "$all_json" "$rerun_json" "$carry_json"
}


###############################################################################
# cmd_extract_model <skill_path>
#   Reads the YAML frontmatter of the skill at <skill_path> and prints the
#   value of the `model:` field to stdout.  Falls back to "haiku" when the
#   field is absent.
#
#   This drives correct model selection for benchmark-run subagents: each
#   skill declares which model it targets, and benchmarks must use that same
#   model to produce meaningful compliance signal.
#
#   Output: a single line containing the model name (e.g. "sonnet", "haiku",
#           "claude-sonnet-4-5").
###############################################################################
cmd_extract_model() {
  local skill_path="${1:-}"
  if [[ -z "$skill_path" ]]; then
    echo "ERROR: benchmark-runner.sh extract-model: missing <skill_path> argument" >&2
    echo "Usage: benchmark-runner.sh extract-model <skill_path>" >&2
    exit 1
  fi
  if [[ ! -f "$skill_path" ]]; then
    echo "ERROR: benchmark-runner.sh extract-model: file not found: $skill_path" >&2
    exit 1
  fi

  parse_frontmatter "$skill_path"
  local value
  value=$(extract_frontmatter_field "model")

  if [[ -z "$value" ]]; then
    echo "WARNING: benchmark-runner.sh extract-model: no 'model:' field in frontmatter of $skill_path; falling back to 'haiku'" >&2
    printf 'haiku'
  else
    printf '%s' "$value"
  fi
}

###############################################################################
# cmd_persist_artifacts <skill_path> <artifacts_dir> <session_id> <worktree_root> <phase>
#   Records benchmark run artifacts:
#     1. Computes SHA-256 of the skill file.
#     2. Creates <skill_dir>/benchmark/ directory.
#     3. Copies <artifacts_dir>/test-cases.json to <skill_dir>/benchmark/test-cases.json.
#     4. Computes SHA-256 of the copied test-cases.json.
#     5. Writes <skill_dir>/benchmark/benchmark.json with structured metadata.
#     6. Stages and commits the artifacts (with retry on git lock contention).
#
#   Arguments:
#     <skill_path>    Worktree-relative path to the skill file
#                     (e.g. plugin/skills/my-skill/first-use.md)
#     <artifacts_dir> Absolute path to BENCHMARK_ARTIFACTS_DIR
#     <session_id>    CLAUDE_SESSION_ID (used in commit message and benchmark.json)
#     <worktree_root> Absolute path to the worktree root (git rev-parse --show-toplevel)
#     <phase>         Phase label written into benchmark.json
#                     (e.g. "sprt", "post-hardening", "post-compression")
###############################################################################
cmd_persist_artifacts() {
  local skill_path="${1:-}"
  local artifacts_dir="${2:-}"
  local session_id="${3:-}"
  local worktree_root="${4:-}"
  local phase="${5:-}"

  if [[ -z "$skill_path" || -z "$artifacts_dir" || -z "$session_id" || -z "$worktree_root" || -z "$phase" ]]; then
    echo "ERROR: benchmark-runner.sh persist-artifacts: missing arguments" >&2
    echo "Usage: benchmark-runner.sh persist-artifacts <skill_path> <artifacts_dir> <session_id> <worktree_root> <phase>" >&2
    exit 1
  fi

  if [[ ! -d "$worktree_root" ]]; then
    echo "ERROR: benchmark-runner.sh persist-artifacts: worktree root not found: $worktree_root" >&2
    exit 2
  fi

  if [[ ! -d "$artifacts_dir" ]]; then
    echo "ERROR: benchmark-runner.sh persist-artifacts: artifacts directory not found: $artifacts_dir" >&2
    exit 2
  fi

  local abs_skill_path="${worktree_root}/${skill_path}"
  validate_path_within_boundary "$worktree_root" "$abs_skill_path"
  if [[ ! -f "$abs_skill_path" ]]; then
    echo "ERROR: benchmark-runner.sh persist-artifacts: skill file not found: $abs_skill_path" >&2
    exit 2
  fi

  local test_cases_src="${artifacts_dir}/test-cases.json"
  if [[ ! -f "$test_cases_src" ]]; then
    echo "ERROR: benchmark-runner.sh persist-artifacts: test-cases.json not found: $test_cases_src" >&2
    exit 2
  fi

  # Determine skill directory (parent of the skill file)
  local skill_dir
  skill_dir=$(dirname "$abs_skill_path")

  # Create benchmark/ subdirectory under the skill directory
  local test_dir="${skill_dir}/benchmark"
  mkdir -p "$test_dir"

  # Copy test-cases.json into the skill's benchmark/ directory
  local test_cases_dest="${test_dir}/test-cases.json"
  validate_path_within_boundary "$skill_dir" "$test_cases_dest"
  cp "$test_cases_src" "$test_cases_dest"

  # Compute hashes
  local skill_hash
  skill_hash=$(compute_sha256 "$abs_skill_path")

  local test_cases_hash
  test_cases_hash=$(compute_sha256 "$test_cases_dest")

  # Compute worktree-relative path for the test-cases entry
  # e.g. skill_path = plugin/skills/foo/first-use.md
  #      rel_test_cases_path = plugin/skills/foo/benchmark/test-cases.json
  local skill_prefix
  skill_prefix=$(dirname "$skill_path")
  local rel_test_cases_path="${skill_prefix}/benchmark/test-cases.json"

  # Timestamp (ISO-8601 UTC)
  local timestamp
  timestamp=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

  # Write benchmark.json into the skill's benchmark/ directory.
  # This file is auto-generated by persist-artifacts and captures the input state of each benchmark
  # run (skill SHA-256, test-cases SHA-256, session, phase, timestamp). It is NOT a config file.
  # The merge-results command appends overall_decision when the benchmark completes.
  local benchmark_json="${skill_dir}/benchmark/benchmark.json"
  cat > "$benchmark_json" <<EOF
{
  "_comment": "Auto-generated snapshot of the last benchmark run. Not a config file.",
  "session_id": "${session_id}",
  "phase": "${phase}",
  "timestamp": "${timestamp}",
  "skill": {
    "path": "${skill_path}",
    "sha256": "${skill_hash}"
  },
  "test_cases": {
    "path": "${rel_test_cases_path}",
    "sha256": "${test_cases_hash}"
  }
}
EOF

  # Stage the artifacts
  local rel_skill_prefix
  rel_skill_prefix=$(dirname "$skill_path")

  local rel_test_cases_dest="${rel_skill_prefix}/benchmark/test-cases.json"
  local rel_benchmark_json="${rel_skill_prefix}/benchmark/benchmark.json"

  git -C "$worktree_root" add "$rel_test_cases_dest"
  git -C "$worktree_root" add "$rel_benchmark_json"

  # Commit with retry on lock contention (exponential backoff with jitter)
  local commit_msg="benchmark: persist artifacts [session: ${session_id}, phase: ${phase}]"
  local max_retries=3
  local attempt=0
  local committed=false

  while [[ $attempt -lt $max_retries ]]; do
    if git -C "$worktree_root" commit -m "$commit_msg" 2>&1; then
      committed=true
      break
    fi
    attempt=$(( attempt + 1 ))
    if [[ $attempt -lt $max_retries ]]; then
      # Exponential backoff: base 2^attempt seconds, plus jitter
      local base=$(( 2 ** attempt ))
      local jitter=$(( RANDOM % (base + 1) ))
      local sleep_secs=$(( base + jitter ))
      echo "INFO: benchmark-runner.sh: git commit failed (attempt ${attempt}/${max_retries}), retrying in ${sleep_secs}s..." >&2
      sleep "$sleep_secs"
    fi
  done

  if [[ "$committed" != "true" ]]; then
    echo "ERROR: benchmark-runner.sh persist-artifacts: git commit failed after ${max_retries} attempts" >&2
    exit 3
  fi

  echo "benchmark-runner.sh: artifacts committed for phase=${phase}, session=${session_id}"
}

###############################################################################
# SPRT constants — Sequential Probability Ratio Test (Wald 1945)
#
#   The SPRT tests H0 (compliance rate p0) against H1 (non-compliance rate p1).
#   After each observation, the log-likelihood ratio is updated:
#     PASS: log_ratio += ln(p0/p1)   = SPRT_LOG_PASS
#     FAIL: log_ratio += ln((1-p0)/(1-p1)) = SPRT_LOG_FAIL
#
#   Decision boundaries derived from type-I (alpha) and type-II (beta) error rates:
#     A = ln((1-beta)/alpha) = ln(19) ~= 2.944   (accept boundary → ACCEPT when log_ratio >= A)
#     B = ln(beta/(1-alpha)) = ln(0.0526) ~= -2.944  (reject boundary → REJECT when log_ratio <= B)
#
#   Parameters:
#     p0 = 0.95  (compliance hypothesis H0: acceptable compliance rate)
#     p1 = 0.85  (non-compliance hypothesis H1: unacceptable compliance rate)
#     alpha = beta = 0.05  (false-accept and false-reject error rates)
###############################################################################
readonly SPRT_LOG_PASS=0.1112   # ln(p0/p1) = ln(0.95/0.85)
readonly SPRT_LOG_FAIL=-1.0986  # ln((1-p0)/(1-p1)) = ln(0.05/0.15)
readonly SPRT_ACCEPT=2.944
readonly SPRT_REJECT=-2.944

# Number of smoke-test runs before escalating to full SPRT (Option B).
readonly SMOKE_RUNS=3

# Prior compliance boost applied to initial log_ratio when --prior-boost is used (Option A).
# Equivalent to approximately 10 prior PASS observations.
readonly PRIOR_BOOST=1.112

###############################################################################
# cmd_init_sprt <rerun_tc_ids_json> <prior_benchmark_json_path> [--prior-boost]
#
#   Initialises per-test-case SPRT state combining:
#     - Carry-forward results from prior benchmark for unchanged test cases
#     - Fresh SPRT state for changed test cases
#
#   Arguments:
#     <rerun_tc_ids_json>     JSON array of test case IDs that must re-run,
#                             e.g. '["TC1","TC2"]'
#     <prior_benchmark_json>  Path to prior benchmark.json or "none"
#     --prior-boost           Optional: initialise rerun cases at PRIOR_BOOST
#                             when they ACCEPTED in the prior benchmark
#
#   Output: {"sprt_state": {"TC1": {...}, "TC2": {...}, ...}}
###############################################################################
cmd_init_sprt() {
  local rerun_json="${1:-}"
  local prior_path="${2:-}"
  local use_prior_boost=0

  shift 2 2>/dev/null || true
  for arg in "$@"; do
    if [[ "$arg" == "--prior-boost" ]]; then
      use_prior_boost=1
    fi
  done

  if [[ -z "$rerun_json" || -z "$prior_path" ]]; then
    echo "ERROR: benchmark-runner.sh init-sprt: missing arguments" >&2
    echo "Usage: benchmark-runner.sh init-sprt <rerun_tc_ids_json> <prior_benchmark_json_path> [--prior-boost]" >&2
    exit 1
  fi

  local has_prior=0
  if [[ "$prior_path" != "none" ]]; then
    if [[ ! -f "$prior_path" ]]; then
      echo "ERROR: benchmark-runner.sh init-sprt: prior benchmark file not found: $prior_path" >&2
      exit 1
    fi
    has_prior=1
  fi

  local rerun_ids=()
  while IFS= read -r uid; do
    [[ -n "$uid" ]] && rerun_ids+=("$uid")
  done < <(printf '%s' "$rerun_json" | grep -o '"[^"]*"' | tr -d '"')

  is_rerun_id() {
    local id="$1"
    for r in "${rerun_ids[@]+"${rerun_ids[@]}"}"; do
      [[ "$r" == "$id" ]] && return 0
    done
    return 1
  }

  # Extract the "decision" string value for a given test_case_id from the prior benchmark
  get_prior_decision_val() {
    local tc_id="$1"
    if [[ $has_prior -eq 0 ]]; then echo "INCONCLUSIVE"; return; fi
    awk -v id="$tc_id" '
      /"test_case_id"[[:space:]]*:[[:space:]]*"/ {
        split($0, parts, /"test_case_id"[[:space:]]*:[[:space:]]*"/)
        split(parts[2], vparts, /"/)
        cur_id = vparts[1]
      }
      cur_id == id && /"decision"[[:space:]]*:[[:space:]]*"/ {
        split($0, dparts, /"decision"[[:space:]]*:[[:space:]]*"/)
        split(dparts[2], dvparts, /"/)
        print dvparts[1]
        exit
      }
    ' "$prior_path"
  }

  # Extract a numeric field value for a given test_case_id from the prior benchmark
  get_prior_num_val() {
    local tc_id="$1" field="$2"
    if [[ $has_prior -eq 0 ]]; then echo "0"; return; fi
    awk -v id="$tc_id" -v fld="$field" '
      /"test_case_id"[[:space:]]*:[[:space:]]*"/ {
        split($0, parts, /"test_case_id"[[:space:]]*:[[:space:]]*"/)
        split(parts[2], vparts, /"/)
        cur_id = vparts[1]
      }
      cur_id == id {
        pattern = "\"" fld "\""
        idx = index($0, pattern)
        if (idx > 0) {
          rest = substr($0, idx + length(pattern))
          sub(/^[[:space:]]*:[[:space:]]*/, "", rest)
          if (match(rest, /^[-0-9.]+/)) {
            print substr(rest, 1, RLENGTH)
            exit
          }
        }
      }
    ' "$prior_path"
  }

  # Collect all test case IDs that appear in the prior benchmark
  local all_prior_ids=()
  if [[ $has_prior -eq 1 ]]; then
    while IFS= read -r tc_id; do
      [[ -n "$tc_id" ]] && all_prior_ids+=("$tc_id")
    done < <(grep -o '"test_case_id"[[:space:]]*:[[:space:]]*"[^"]*"' "$prior_path" \
      | sed 's/.*"\([^"]*\)"$/\1/')
  fi

  # Union of prior IDs and rerun IDs (deduplication)
  local all_ids=()

  sprt_seen_in_all() {
    local id="$1"
    for s in "${all_ids[@]+"${all_ids[@]}"}"; do
      [[ "$s" == "$id" ]] && return 0
    done
    return 1
  }

  for tid in "${all_prior_ids[@]+"${all_prior_ids[@]}"}"; do
    sprt_seen_in_all "$tid" || all_ids+=("$tid")
  done
  for tid in "${rerun_ids[@]+"${rerun_ids[@]}"}"; do
    sprt_seen_in_all "$tid" || all_ids+=("$tid")
  done

  local state_entries=""
  local first_entry=1

  for tc_id in "${all_ids[@]+"${all_ids[@]}"}"; do
    if [[ $first_entry -eq 1 ]]; then first_entry=0; else state_entries+=","; fi

    if is_rerun_id "$tc_id"; then
      local initial_lr="0"
      if [[ $use_prior_boost -eq 1 && $has_prior -eq 1 ]]; then
        local prior_dec
        prior_dec=$(get_prior_decision_val "$tc_id")
        if [[ "$prior_dec" == "ACCEPT" ]]; then
          initial_lr="$PRIOR_BOOST"
        fi
      fi
      state_entries+="\"${tc_id}\":{\"log_ratio\":${initial_lr},\"passes\":0,\"fails\":0,\"runs\":0,\"decision\":\"INCONCLUSIVE\",\"carried_forward\":false,\"smoke_runs_done\":0}"
    else
      local cf_lr cf_passes cf_fails cf_runs cf_decision cf_smoke
      cf_lr=$(get_prior_num_val "$tc_id" "log_ratio"); cf_lr="${cf_lr:-0}"
      cf_passes=$(get_prior_num_val "$tc_id" "passes"); cf_passes="${cf_passes:-0}"
      cf_fails=$(get_prior_num_val "$tc_id" "fails"); cf_fails="${cf_fails:-0}"
      cf_runs=$(get_prior_num_val "$tc_id" "runs"); cf_runs="${cf_runs:-0}"
      cf_decision=$(get_prior_decision_val "$tc_id"); cf_decision="${cf_decision:-INCONCLUSIVE}"
      cf_smoke=$(get_prior_num_val "$tc_id" "smoke_runs_done"); cf_smoke="${cf_smoke:-0}"
      state_entries+="\"${tc_id}\":{\"log_ratio\":${cf_lr},\"passes\":${cf_passes},\"fails\":${cf_fails},\"runs\":${cf_runs},\"decision\":\"${cf_decision}\",\"carried_forward\":true,\"smoke_runs_done\":${cf_smoke}}"
    fi
  done

  printf '{"sprt_state":{%s}}\n' "$state_entries"
}

###############################################################################
# extract_state_field <state_json> <tc_id> <field> [pattern]
#   Extracts a single field value for <tc_id> from inline sprt_state JSON.
#   <pattern> is an optional grep pattern for the value (default: [^,}]*).
###############################################################################
extract_state_field() {
  local state_json="$1" tc_id="$2" field="$3" pattern="${4:-[^,}]*}"
  printf '%s' "$state_json" | tr '\n' ' ' \
    | grep -o "\"${tc_id}\"[[:space:]]*:{[^}]*}" \
    | grep -o "\"${field}\"[[:space:]]*:[[:space:]]*${pattern}" \
    | sed 's/.*:[[:space:]]*//' | sed 's/"//g'
}

###############################################################################
# cmd_update_sprt <sprt_state_path> <tc_id> <passed>
#
#   Applies one observation (PASS or FAIL) to the SPRT log_ratio for a single
#   test case and re-evaluates boundary conditions.
#
#   Arguments:
#     <sprt_state_path>  Path to sprt_state JSON
#     <tc_id>            Test case ID to update
#     <passed>           "true" (PASS) or "false" (FAIL)
#
#   Output: Updated sprt_state JSON written to stdout.
###############################################################################
cmd_update_sprt() {
  local state_path="${1:-}"
  local tc_id="${2:-}"
  local passed="${3:-}"

  if [[ -z "$state_path" || -z "$tc_id" || -z "$passed" ]]; then
    echo "ERROR: benchmark-runner.sh update-sprt: missing arguments" >&2
    echo "Usage: benchmark-runner.sh update-sprt <sprt_state_path> <tc_id> <passed>" >&2
    exit 1
  fi
  if [[ ! -f "$state_path" ]]; then
    echo "ERROR: benchmark-runner.sh update-sprt: state file not found: $state_path" >&2
    exit 1
  fi
  if [[ "$passed" != "true" && "$passed" != "false" ]]; then
    echo "ERROR: benchmark-runner.sh update-sprt: <passed> must be 'true' or 'false', got: $passed" >&2
    exit 1
  fi

  local state_json
  state_json=$(cat "$state_path")

  local cur_lr cur_passes cur_fails cur_runs cur_smoke cur_cf
  cur_lr=$(extract_state_field "$state_json" "$tc_id" "log_ratio" '[-0-9.e+]*')
  cur_lr="${cur_lr:-0}"
  cur_passes=$(extract_state_field "$state_json" "$tc_id" "passes" '[0-9]*')
  cur_passes="${cur_passes:-0}"
  cur_fails=$(extract_state_field "$state_json" "$tc_id" "fails" '[0-9]*')
  cur_fails="${cur_fails:-0}"
  cur_runs=$(extract_state_field "$state_json" "$tc_id" "runs" '[0-9]*')
  cur_runs="${cur_runs:-0}"
  cur_smoke=$(extract_state_field "$state_json" "$tc_id" "smoke_runs_done" '[0-9]*')
  cur_smoke="${cur_smoke:-0}"
  cur_cf=$(extract_state_field "$state_json" "$tc_id" "carried_forward" '[a-z]*')
  cur_cf="${cur_cf:-false}"

  local increment
  if [[ "$passed" == "true" ]]; then increment="$SPRT_LOG_PASS"; else increment="$SPRT_LOG_FAIL"; fi

  local new_lr new_passes new_fails new_runs new_smoke
  new_lr=$(awk "BEGIN { printf \"%.6f\", ${cur_lr} + ${increment} }")
  new_runs=$(( cur_runs + 1 ))
  new_smoke=$(( cur_smoke < SMOKE_RUNS ? cur_smoke + 1 : cur_smoke ))
  if [[ "$passed" == "true" ]]; then
    new_passes=$(( cur_passes + 1 )); new_fails="$cur_fails"
  else
    new_passes="$cur_passes"; new_fails=$(( cur_fails + 1 ))
  fi

  local new_decision
  if awk "BEGIN { exit (${new_lr} >= ${SPRT_ACCEPT}) ? 0 : 1 }"; then
    new_decision="ACCEPT"
  elif awk "BEGIN { exit (${new_lr} <= ${SPRT_REJECT}) ? 0 : 1 }"; then
    new_decision="REJECT"
  else
    new_decision="INCONCLUSIVE"
  fi

  local new_entry
  new_entry="\"${tc_id}\":{\"log_ratio\":${new_lr},\"passes\":${new_passes},\"fails\":${new_fails},\"runs\":${new_runs},\"decision\":\"${new_decision}\",\"carried_forward\":${cur_cf},\"smoke_runs_done\":${new_smoke}}"

  awk -v key="\"${tc_id}\"" -v new_e="$new_entry" '
  {
    line = $0
    idx = index(line, key ":{")
    if (idx == 0) { print line; next }
    before = substr(line, 1, idx - 1)
    rest = substr(line, idx + length(key) + 1)
    depth = 0
    for (i = 1; i <= length(rest); i++) {
      c = substr(rest, i, 1)
      if (c == "{") depth++
      else if (c == "}") {
        depth--
        if (depth == 0) { print before new_e substr(rest, i + 1); next }
      }
    }
    print line
  }
  ' <<< "$state_json"
}

###############################################################################
# cmd_check_boundary <sprt_state_path> <tc_id>
#
#   Returns the current SPRT boundary decision for a single test case.
#
#   Output: {"test_case_id":"TC1","decision":"...","log_ratio":N,"runs":N,
#            "smoke_runs_done":N,"carried_forward":bool}
###############################################################################
cmd_check_boundary() {
  local state_path="${1:-}"
  local tc_id="${2:-}"

  if [[ -z "$state_path" || -z "$tc_id" ]]; then
    echo "ERROR: benchmark-runner.sh check-boundary: missing arguments" >&2
    echo "Usage: benchmark-runner.sh check-boundary <sprt_state_path> <tc_id>" >&2
    exit 1
  fi
  if [[ ! -f "$state_path" ]]; then
    echo "ERROR: benchmark-runner.sh check-boundary: state file not found: $state_path" >&2
    exit 1
  fi

  local state_json
  state_json=$(cat "$state_path")

  local lr runs smoke cf decision
  lr=$(extract_state_field "$state_json" "$tc_id" "log_ratio" '[-0-9.e+]*'); lr="${lr:-0}"
  runs=$(extract_state_field "$state_json" "$tc_id" "runs" '[0-9]*'); runs="${runs:-0}"
  smoke=$(extract_state_field "$state_json" "$tc_id" "smoke_runs_done" '[0-9]*'); smoke="${smoke:-0}"
  cf=$(extract_state_field "$state_json" "$tc_id" "carried_forward" '[a-z]*'); cf="${cf:-false}"
  decision=$(extract_state_field "$state_json" "$tc_id" "decision" '"[^"]*"'); decision="${decision:-INCONCLUSIVE}"

  printf '{"test_case_id":"%s","decision":"%s","log_ratio":%s,"runs":%s,"smoke_runs_done":%s,"carried_forward":%s}\n' \
    "$tc_id" "$decision" "$lr" "$runs" "$smoke" "$cf"
}

###############################################################################
# cmd_smoke_status <sprt_state_path> <tc_id>
#
#   Determines whether a test case is in the smoke-test phase (Option B:
#   3 quick trials before full SPRT) or should escalate.
#
#   Output: {"test_case_id":"TC1","in_smoke_phase":bool,"smoke_runs_done":N,
#            "smoke_runs_remaining":N,"escalate_to_full_sprt":bool}
###############################################################################
cmd_smoke_status() {
  local state_path="${1:-}"
  local tc_id="${2:-}"

  if [[ -z "$state_path" || -z "$tc_id" ]]; then
    echo "ERROR: benchmark-runner.sh smoke-status: missing arguments" >&2
    echo "Usage: benchmark-runner.sh smoke-status <sprt_state_path> <tc_id>" >&2
    exit 1
  fi
  if [[ ! -f "$state_path" ]]; then
    echo "ERROR: benchmark-runner.sh smoke-status: state file not found: $state_path" >&2
    exit 1
  fi

  local state_json
  state_json=$(cat "$state_path")

  local smoke cf decision
  smoke=$(extract_state_field "$state_json" "$tc_id" "smoke_runs_done" '[0-9]*'); smoke="${smoke:-0}"
  cf=$(extract_state_field "$state_json" "$tc_id" "carried_forward" '[a-z]*'); cf="${cf:-false}"
  decision=$(extract_state_field "$state_json" "$tc_id" "decision" '"[^"]*"'); decision="${decision:-INCONCLUSIVE}"

  local remaining=$(( SMOKE_RUNS - smoke ))
  [[ $remaining -lt 0 ]] && remaining=0

  local in_smoke="false" escalate="false"
  if [[ "$cf" == "false" && $smoke -lt $SMOKE_RUNS ]]; then
    in_smoke="true"
  elif [[ "$cf" == "false" && $smoke -ge $SMOKE_RUNS && "$decision" == "INCONCLUSIVE" ]]; then
    escalate="true"
  fi

  printf '{"test_case_id":"%s","in_smoke_phase":%s,"smoke_runs_done":%s,"smoke_runs_remaining":%s,"escalate_to_full_sprt":%s}\n' \
    "$tc_id" "$in_smoke" "$smoke" "$remaining" "$escalate"
}

###############################################################################
# cmd_merge_results <new_sprt_state_path> <prior_benchmark_json_path>
#                   <carryforward_ids_json>
#
#   Merges new SPRT decisions with carried-forward results to produce a
#   complete benchmark.json summary ready for committing.
#
#   Output: {"timestamp":"...","overall_decision":"...","incremental":true,
#            "test_cases":[...]}
###############################################################################
cmd_merge_results() {
  local state_path="${1:-}"
  local prior_path="${2:-}"
  local cf_json="${3:-}"

  if [[ -z "$state_path" || -z "$prior_path" || -z "$cf_json" ]]; then
    echo "ERROR: benchmark-runner.sh merge-results: missing arguments" >&2
    echo "Usage: benchmark-runner.sh merge-results <new_sprt_state_path> <prior_benchmark_json_path> <carryforward_ids_json>" >&2
    exit 1
  fi
  if [[ ! -f "$state_path" ]]; then
    echo "ERROR: benchmark-runner.sh merge-results: state file not found: $state_path" >&2
    exit 1
  fi

  local state_json
  state_json=$(cat "$state_path")

  local all_tc_ids=()
  while IFS= read -r tc_id; do
    [[ -n "$tc_id" ]] && all_tc_ids+=("$tc_id")
  done < <(printf '%s' "$state_json" \
    | grep -o '"[^"]*"[[:space:]]*:{' \
    | grep -v '"sprt_state"' \
    | sed 's/"//g; s/[[:space:]]*:{//')

  local tc_entries="" first_tc=1 overall="ACCEPT"

  for tc_id in "${all_tc_ids[@]+"${all_tc_ids[@]}"}"; do
    local lr passes fails runs decision cf
    lr=$(extract_state_field "$state_json" "$tc_id" "log_ratio" '[-0-9.e+]*'); lr="${lr:-0}"
    passes=$(extract_state_field "$state_json" "$tc_id" "passes" '[0-9]*'); passes="${passes:-0}"
    fails=$(extract_state_field "$state_json" "$tc_id" "fails" '[0-9]*'); fails="${fails:-0}"
    runs=$(extract_state_field "$state_json" "$tc_id" "runs" '[0-9]*'); runs="${runs:-0}"
    decision=$(extract_state_field "$state_json" "$tc_id" "decision" '"[^"]*"'); decision="${decision:-INCONCLUSIVE}"
    cf=$(extract_state_field "$state_json" "$tc_id" "carried_forward" '[a-z]*'); cf="${cf:-false}"

    if [[ "$decision" == "REJECT" ]]; then
      overall="REJECT"
    elif [[ "$decision" == "INCONCLUSIVE" && "$overall" != "REJECT" ]]; then
      overall="INCONCLUSIVE"
    fi

    if [[ $first_tc -eq 1 ]]; then first_tc=0; else tc_entries+=","; fi
    tc_entries+="{\"test_case_id\":\"${tc_id}\",\"log_ratio\":${lr},\"passes\":${passes},\"fails\":${fails},\"runs\":${runs},\"decision\":\"${decision}\",\"carried_forward\":${cf}}"
  done

  local timestamp
  timestamp=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
  printf '{"timestamp":"%s","overall_decision":"%s","incremental":true,"test_cases":[%s]}\n' \
    "$timestamp" "$overall" "$tc_entries"
}

###############################################################################
# Main dispatch
###############################################################################
case "$COMMAND" in
  extract-units)
    shift
    cmd_extract_units "$@"
    ;;
  extract-model)
    shift
    cmd_extract_model "$@"
    ;;
  detect-changes)
    shift
    cmd_detect_changes "$@"
    ;;
  map-units)
    shift
    cmd_map_units "$@"
    ;;
  persist-artifacts)
    shift
    cmd_persist_artifacts "$@"
    ;;
  init-sprt)
    shift
    cmd_init_sprt "$@"
    ;;
  update-sprt)
    shift
    cmd_update_sprt "$@"
    ;;
  check-boundary)
    shift
    cmd_check_boundary "$@"
    ;;
  smoke-status)
    shift
    cmd_smoke_status "$@"
    ;;
  merge-results)
    shift
    cmd_merge_results "$@"
    ;;
  "")
    echo "ERROR: benchmark-runner.sh: no command specified." >&2
    echo "Usage: benchmark-runner.sh <command> [args...]" >&2
    echo "Commands: extract-units, extract-model, detect-changes, map-units, persist-artifacts, init-sprt, update-sprt, check-boundary, smoke-status, merge-results" >&2
    exit 1
    ;;
  *)
    echo "ERROR: benchmark-runner.sh: unknown command: $COMMAND" >&2
    echo "Valid commands: extract-units, extract-model, detect-changes, map-units, persist-artifacts, init-sprt, update-sprt, check-boundary, smoke-status, merge-results" >&2
    exit 1
    ;;
esac
