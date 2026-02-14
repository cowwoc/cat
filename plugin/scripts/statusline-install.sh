#!/usr/bin/env bash
# Installation script for CAT statusline
# Handles checking existing configuration and installing/updating the statusline

set -euo pipefail

# Usage: statusline-install.sh MODE CLAUDE_PROJECT_DIR CLAUDE_PLUGIN_ROOT
# MODE: --check or --install

# Validate arguments
if [[ $# -lt 3 ]]; then
  echo '{"status": "ERROR", "message": "Missing required arguments. Usage: statusline-install.sh MODE CLAUDE_PROJECT_DIR CLAUDE_PLUGIN_ROOT"}' >&2
  exit 1
fi

MODE="$1"
CLAUDE_PROJECT_DIR="$2"
CLAUDE_PLUGIN_ROOT="$3"

# Validate mode
if [[ "$MODE" != "--check" && "$MODE" != "--install" ]]; then
  echo "{\"status\": \"ERROR\", \"message\": \"Invalid mode: $MODE. Must be --check or --install\"}" >&2
  exit 1
fi

# Check mode: verify existing statusline configuration
if [[ "$MODE" == "--check" ]]; then
  SETTINGS_FILE="${CLAUDE_PROJECT_DIR}/.claude/settings.json"

  if [[ ! -f "$SETTINGS_FILE" ]]; then
    echo '{"status": "NONE"}'
    exit 0
  fi

  # Check if statusLine key exists
  if ! command -v jq >/dev/null 2>&1; then
    echo '{"status": "ERROR", "message": "jq is required but not installed"}' >&2
    exit 1
  fi

  CURRENT_CONFIG=$(jq -c '.statusLine // null' "$SETTINGS_FILE" 2>/dev/null || echo "null")

  if [[ "$CURRENT_CONFIG" == "null" ]]; then
    echo '{"status": "NONE"}'
  else
    echo "{\"status\": \"EXISTING\", \"current_config\": $CURRENT_CONFIG}"
  fi

  exit 0
fi

# Install mode: install statusline
if [[ "$MODE" == "--install" ]]; then
  # Check jq availability
  if ! command -v jq >/dev/null 2>&1; then
    echo '{"status": "ERROR", "message": "jq is required but not installed"}' >&2
    exit 1
  fi

  # Create .claude directory if needed
  CLAUDE_DIR="${CLAUDE_PROJECT_DIR}/.claude"
  if ! mkdir -p "$CLAUDE_DIR" 2>/dev/null; then
    echo "{\"status\": \"ERROR\", \"message\": \"Failed to create directory: $CLAUDE_DIR\"}" >&2
    exit 1
  fi

  # Copy statusline script
  SOURCE_SCRIPT="${CLAUDE_PLUGIN_ROOT}/scripts/statusline-command.sh"
  DEST_SCRIPT="${CLAUDE_DIR}/statusline-command.sh"

  if [[ ! -f "$SOURCE_SCRIPT" ]]; then
    echo "{\"status\": \"ERROR\", \"message\": \"Source script not found: $SOURCE_SCRIPT\"}" >&2
    exit 1
  fi

  if ! cp "$SOURCE_SCRIPT" "$DEST_SCRIPT" 2>/dev/null; then
    echo "{\"status\": \"ERROR\", \"message\": \"Failed to copy script to: $DEST_SCRIPT\"}" >&2
    exit 1
  fi

  # Make script executable
  if ! chmod 755 "$DEST_SCRIPT" 2>/dev/null; then
    echo "{\"status\": \"ERROR\", \"message\": \"Failed to make script executable: $DEST_SCRIPT\"}" >&2
    exit 1
  fi

  # Create or update settings.json
  SETTINGS_FILE="${CLAUDE_DIR}/settings.json"

  if [[ ! -f "$SETTINGS_FILE" ]]; then
    echo '{}' > "$SETTINGS_FILE"
  fi

  # Verify settings.json is valid JSON
  if ! jq empty "$SETTINGS_FILE" 2>/dev/null; then
    echo "{\"status\": \"ERROR\", \"message\": \"Existing settings.json is not valid JSON: $SETTINGS_FILE\"}" >&2
    exit 1
  fi

  # Update settings.json with statusLine configuration
  TEMP_FILE="${SETTINGS_FILE}.tmp"
  if ! jq --arg cmd "$DEST_SCRIPT" '.statusLine = {"type": "command", "command": $cmd}' "$SETTINGS_FILE" > "$TEMP_FILE" 2>/dev/null; then
    rm -f "$TEMP_FILE"
    echo "{\"status\": \"ERROR\", \"message\": \"Failed to update settings.json with jq\"}" >&2
    exit 1
  fi

  if ! mv "$TEMP_FILE" "$SETTINGS_FILE" 2>/dev/null; then
    rm -f "$TEMP_FILE"
    echo "{\"status\": \"ERROR\", \"message\": \"Failed to replace settings.json\"}" >&2
    exit 1
  fi

  # Verify installation
  if [[ ! -x "$DEST_SCRIPT" ]]; then
    echo "{\"status\": \"ERROR\", \"message\": \"Script is not executable after installation: $DEST_SCRIPT\"}" >&2
    exit 1
  fi

  if ! jq empty "$SETTINGS_FILE" 2>/dev/null; then
    echo "{\"status\": \"ERROR\", \"message\": \"settings.json is not valid JSON after update\"}" >&2
    exit 1
  fi

  STATUSLINE_CONFIG=$(jq -c '.statusLine // null' "$SETTINGS_FILE" 2>/dev/null || echo "null")
  if [[ "$STATUSLINE_CONFIG" == "null" ]]; then
    echo "{\"status\": \"ERROR\", \"message\": \"statusLine not found in settings.json after update\"}" >&2
    exit 1
  fi

  # Success
  echo "{\"status\": \"OK\", \"script_path\": \"$DEST_SCRIPT\", \"settings_path\": \"$SETTINGS_FILE\"}"
  exit 0
fi

# Should never reach here
echo '{"status": "ERROR", "message": "Unexpected error in installation script"}' >&2
exit 1
