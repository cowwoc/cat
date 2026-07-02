#!/usr/bin/env bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
CODEX_HOME="${CODEX_HOME:-${HOME}/.codex}"
CAT_PLUGIN_DATA="${CAT_PLUGIN_DATA:-${CODEX_HOME}/plugins/data/cat-cat}"

is_cat_source_tree() {
  local candidate="$1"
  [[ -n "${candidate}" && -f "${candidate}/client/pom.xml" ]]
}

normalize_path() {
  local path="$1"
  case "${path}" in
    "~")
      printf '%s\n' "${HOME}"
      ;;
    "~/"*)
      printf '%s\n' "${HOME}/${path:2}"
      ;;
    *)
      printf '%s\n' "${path}"
      ;;
  esac
}

resolve_current_git_root() {
  local git_root
  git_root="$(git rev-parse --show-toplevel 2>/dev/null || true)"
  if is_cat_source_tree "${git_root}"; then
    printf '%s\n' "${git_root}"
  fi
}

resolve_work_path() {
  local project_dir="$1"
  local config_path="${project_dir}/.cat/config.json"
  local work_path_template='${CAT_PROJECT_DIR}/.cat/work'
  local config_work_path=""
  if [[ -f "${config_path}" ]]; then
    config_work_path="$(sed -n 's/.*"workPath"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "${config_path}" | head -1)"
    if [[ -n "${config_work_path}" ]]; then
      work_path_template="${config_work_path}"
    fi
  fi
  work_path_template="${work_path_template//'${CAT_PROJECT_DIR}'/${project_dir}}"
  work_path_template="${work_path_template//'${CLAUDE_PROJECT_DIR}'/${project_dir}}"
  normalize_path "${work_path_template}"
}

resolve_locked_worktree() {
  local project_dir="$1"
  local locks_dir
  locks_dir="$(resolve_work_path "${project_dir}")/locks"
  local session_ids=(
    "${CAT_SESSION_ID:-}"
    "${CODEX_THREAD_ID:-}"
    "${CODEX_SESSION_ID:-}"
    "${CLAUDE_SESSION_ID:-}"
    "${SESSION_ID:-}"
  )
  local sid lock_file lock_json locked_worktree
  if [[ ! -d "${locks_dir}" ]]; then
    return 0
  fi
  for sid in "${session_ids[@]}"; do
    [[ -z "${sid}" ]] && continue
    while IFS= read -r -d '' lock_file; do
      lock_json="$(tr -d '\n' < "${lock_file}")"
      if [[ "${lock_json}" =~ \"session_id\"[[:space:]]*:[[:space:]]*\"${sid}\" ]]; then
        locked_worktree="$(echo "${lock_json}" | sed -n 's/.*"worktrees"[[:space:]]*:[[:space:]]*{[[:space:]]*"\([^"]*\)".*/\1/p')"
        if is_cat_source_tree "${locked_worktree}"; then
          printf '%s\n' "${locked_worktree}"
          return 0
        fi
      fi
    done < <(find "${locks_dir}" -maxdepth 1 -type f -name '*.lock' -print0 2>/dev/null)
  done
}

resolve_project_dir() {
  local current_git_root locked_worktree
  current_git_root="$(resolve_current_git_root)"
  if [[ -n "${current_git_root}" && "${current_git_root}" != "${PROJECT_ROOT}" ]]; then
    printf '%s\n' "${current_git_root}"
    return 0
  fi
  locked_worktree="$(resolve_locked_worktree "${PROJECT_ROOT}")"
  if [[ -n "${locked_worktree}" ]]; then
    printf '%s\n' "${locked_worktree}"
    return 0
  fi
  if [[ -n "${current_git_root}" ]]; then
    printf '%s\n' "${current_git_root}"
    return 0
  fi
  printf '%s\n' "${PROJECT_ROOT}"
}

plugin_version() {
  local plugin_manifest="$1"
  python3 - "${plugin_manifest}" <<'PY'
import json
import sys
from pathlib import Path

manifest = json.loads(Path(sys.argv[1]).read_text())
version = manifest.get("version", "").strip()
if version:
    print(version)
PY
}

try_codex_plugin_browser_install() {
  local local_marketplace_root="$1"
  local marketplace_json escaped_marketplace_json
  marketplace_json="${local_marketplace_root}/.agents/plugins/marketplace.json"
  escaped_marketplace_json="${marketplace_json//\\/\\\\}"
  escaped_marketplace_json="${escaped_marketplace_json//\"/\\\"}"
  cat <<EOF | codex app-server proxy >/dev/null 2>&1
{"id":1,"method":"plugin/install","params":{"marketplacePath":"${escaped_marketplace_json}","pluginName":"cat","remoteMarketplaceName":null}}
EOF
}

install_release_artifact() {
  local project_dir="$1"
  local release_artifact="$2"
  local plugin_manifest="${release_artifact}/.codex-plugin/plugin.json"
  local plugin_version_value
  plugin_version_value="$(plugin_version "${plugin_manifest}")"
  if [[ -z "${plugin_version_value}" ]]; then
    echo "ERROR: Could not determine CAT plugin version from ${plugin_manifest}." >&2
    exit 1
  fi

  local local_marketplace_root="${LOCAL_MARKETPLACE_ROOT:-${CODEX_HOME}/plugins/cat-marketplace}"
  rm -rf "${local_marketplace_root}"
  mkdir -p "${local_marketplace_root}/plugins/cat"
  cp -R "${release_artifact}/." "${local_marketplace_root}/plugins/cat/"
  mkdir -p "${local_marketplace_root}/.agents/plugins"
  cat > "${local_marketplace_root}/.agents/plugins/marketplace.json" <<'JSON'
{
  "name": "cat",
  "interface": {
    "displayName": "CAT"
  },
  "plugins": [
    {
      "name": "cat",
      "source": {
        "source": "local",
        "path": "./plugins/cat"
      },
      "policy": {
        "installation": "INSTALLED_BY_DEFAULT",
        "authentication": "ON_INSTALL"
      },
      "category": "Productivity"
    }
  ]
}
JSON

  codex plugin marketplace remove cat 2>/dev/null || true
  codex plugin marketplace add "${local_marketplace_root}"

  local codex_plugin_cache_root="${CODEX_HOME}/plugins/cache/cat/cat"
  local codex_plugin_cache="${codex_plugin_cache_root}/${plugin_version_value}"
  rm -rf "${codex_plugin_cache_root}"

  if ! try_codex_plugin_browser_install "${local_marketplace_root}" || [[ ! -f "${codex_plugin_cache}/skills/add/SKILL.md" ]]; then
    mkdir -p "${codex_plugin_cache_root}"
    cp -R "${release_artifact}" "${codex_plugin_cache}"
  fi

  local project_codex_agents_dir="${project_dir}/.codex/agents"
  mkdir -p "${project_codex_agents_dir}"
  find "${project_codex_agents_dir}" -maxdepth 1 -type f -name 'cat-*.toml' -delete
  while IFS= read -r -d '' agent_file; do
    cp "${agent_file}" "${project_codex_agents_dir}/cat-$(basename "${agent_file}")"
  done < <(find "${release_artifact}/agents" -maxdepth 1 -type f -name '*.toml' -print0)

  local codex_config="${CODEX_CONFIG:-${CODEX_HOME}/config.toml}"
  mkdir -p "$(dirname "${codex_config}")"
  touch "${codex_config}"
  local config_tmp
  config_tmp="$(mktemp)"
  awk '
    /^\[.*\]$/ {
      if (in_cat_plugin && ! wrote_enabled)
        print "enabled = true"
      in_cat_plugin = ($0 == "[plugins.\"cat@cat\"]")
      wrote_enabled = 0
      found_cat_plugin = found_cat_plugin || in_cat_plugin
      print
      next
    }
    in_cat_plugin && /^[[:space:]]*enabled[[:space:]]*=/ {
      print "enabled = true"
      wrote_enabled = 1
      next
    }
    { print }
    END {
      if (in_cat_plugin && ! wrote_enabled)
        print "enabled = true"
      if (! found_cat_plugin) {
        print ""
        print "[plugins.\"cat@cat\"]"
        print "enabled = true"
      }
    }
  ' "${codex_config}" > "${config_tmp}"
  mv "${config_tmp}" "${codex_config}"

  mkdir -p "${CAT_PLUGIN_DATA}"
  chmod -R u+w "${CAT_PLUGIN_DATA}/client" 2>/dev/null || true
  rm -rf "${CAT_PLUGIN_DATA}/client"
  cp -R "${release_artifact}/client" "${CAT_PLUGIN_DATA}/client"

  "${CAT_PLUGIN_DATA}/client/bin/java" -version
  test -x "${CAT_PLUGIN_DATA}/client/bin/pre-bash"
  test -f "${CAT_PLUGIN_DATA}/client/VERSION"
  test -f "${codex_plugin_cache}/.codex-plugin/plugin.json"
  test -f "${codex_plugin_cache}/skills/add/SKILL.md"
  local arch_agent_file
  arch_agent_file="$(find "${project_codex_agents_dir}" -maxdepth 1 -type f \
    -name 'cat-stakeholder-architecture-*.toml' | head -1)"
  test -n "${arch_agent_file}"
  grep -F 'name = "cat-stakeholder-architecture-' "${arch_agent_file}" >/dev/null
  grep -F '[plugins."cat@cat"]' "${codex_config}" >/dev/null
  python3 - "${codex_config}" <<'PY'
from pathlib import Path
import sys

text = Path(sys.argv[1]).read_text().splitlines()
in_section = False
enabled = False
for line in text:
    stripped = line.strip()
    if stripped.startswith("[") and stripped.endswith("]"):
        in_section = stripped == '[plugins."cat@cat"]'
        continue
    if in_section and stripped == "enabled = true":
        enabled = True
        break
raise SystemExit(0 if enabled else 1)
PY
}

main() {
  local project_dir
  project_dir="$(resolve_project_dir)"
  echo "Using PROJECT_DIR=${project_dir}"

  mvn -f "${project_dir}/client/pom.xml" verify -Djlink.extra.args=--enable-assertions

  local bats_bin="${project_dir}/client/plugin/node_modules/.bin/bats"
  if [[ ! -x "${bats_bin}" ]]; then
    npm ci --prefix "${project_dir}/client/plugin"
  fi
  local bats_project
  bats_project="$(mktemp -d)"
  trap 'rm -rf "${bats_project}"' EXIT
  mkdir -p "${bats_project}/client"
  tar -C "${project_dir}" \
    --exclude='client/plugin/node_modules' \
    --exclude='client/*/target' \
    -cf - client/plugin client/distribution | tar -C "${bats_project}" -xf -
  ln -s "${project_dir}/client/plugin/node_modules" "${bats_project}/client/plugin/node_modules"
  npm run --prefix "${bats_project}/client/plugin" test

  local release_artifact="${project_dir}/client/distribution/target/engine/codex"
  test -f "${release_artifact}/client/VERSION"
  test -f "${release_artifact}/.codex-plugin/plugin.json"
  test -d "${release_artifact}/agents"

  install_release_artifact "${project_dir}" "${release_artifact}"
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  main "$@"
fi
