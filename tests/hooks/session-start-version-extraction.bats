#!/usr/bin/env bats
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
# Tests for version extraction from plugin.json in session-start.sh.
# Covers standard, whitespace-variant, pre-release, and malformed inputs.

setup() {
  TEST_DIR=$(mktemp -d)
  BATS_TEST_SOURCED=true source "${BATS_TEST_DIRNAME}/../../plugin/hooks/session-start.sh" || true

  PLUGIN_ROOT="${TEST_DIR}/plugin-root"
  mkdir -p "${PLUGIN_ROOT}/.claude-plugin"
}

teardown() {
  rm -rf "$TEST_DIR"
}

# Helper: write a plugin.json with given raw content
write_plugin_json() {
  printf '%s' "$1" > "${PLUGIN_ROOT}/.claude-plugin/plugin.json"
}

# Helper: extract version using the same grep/sed pipeline as session-start.sh main()
extract_version() {
  local plugin_json="${PLUGIN_ROOT}/.claude-plugin/plugin.json"
  grep -o '"version"[[:space:]]*:[[:space:]]*"[^"]*"' "$plugin_json" | sed 's/.*"\([^"]*\)"$/\1/'
}

@test "standard well-formatted version is extracted correctly" {
  write_plugin_json '{"name":"cat","version":"2.1.0"}'
  local version
  version=$(extract_version)
  [ "$version" = "2.1.0" ]
  run validate_semver "$version"
  [ "$status" -eq 0 ]
}

@test "version with extra whitespace around colon is extracted" {
  write_plugin_json '{"name":"cat","version" : "2.1.0"}'
  local version
  version=$(extract_version)
  [ "$version" = "2.1.0" ]
  run validate_semver "$version"
  [ "$status" -eq 0 ]
}

@test "two-component version (X.Y) is extracted and passes validate_semver" {
  write_plugin_json '{"name":"cat","version":"2.1"}'
  local version
  version=$(extract_version)
  [ "$version" = "2.1" ]
  run validate_semver "$version"
  [ "$status" -eq 0 ]
}

@test "version with -alpha suffix is extracted but fails validate_semver" {
  write_plugin_json '{"name":"cat","version":"2.1.0-alpha"}'
  local version
  version=$(extract_version)
  [ "$version" = "2.1.0-alpha" ]
  run validate_semver "$version"
  [ "$status" -ne 0 ]
}

@test "version with -beta suffix is extracted but fails validate_semver" {
  write_plugin_json '{"name":"cat","version":"2.1.0-beta"}'
  local version
  version=$(extract_version)
  [ "$version" = "2.1.0-beta" ]
  run validate_semver "$version"
  [ "$status" -ne 0 ]
}

@test "missing version field returns empty string" {
  write_plugin_json '{"name":"cat"}'
  local version
  version=$(extract_version)
  [ -z "$version" ]
}

@test "malformed version field (no closing quote) returns empty string" {
  write_plugin_json '{"name":"cat","version":"2.1.0}'
  local version
  version=$(extract_version)
  [ -z "$version" ]
}
