#!/bin/bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
#
# Progress indicator library for CAT scripts
#
# Provides consistent progress output with:
# - Step counter (e.g., [Step 3/11])
# - Percentage complete
# - Time elapsed
# - Estimated time remaining
#
# Usage:
#   source "$(dirname "$0")/lib/progress.sh"
#   progress_init 5
#   progress_step "Doing first thing"
#   progress_done "First thing complete"
#   progress_step "Doing second thing"
#   progress_done "Second thing complete"
#   progress_summary

# Global state
PROGRESS_START_TIME=
PROGRESS_TOTAL_STEPS=
PROGRESS_CURRENT_STEP=0

# Initialize progress tracking
# Args: total_steps
progress_init() {
  local total_steps="$1"
  PROGRESS_START_TIME=$(date +%s)
  PROGRESS_TOTAL_STEPS="$total_steps"
  PROGRESS_CURRENT_STEP=0
}

# Start a new step with progress info
# Args: description
progress_step() {
  local description="$1"
  PROGRESS_CURRENT_STEP=$((PROGRESS_CURRENT_STEP + 1))

  local now=$(date +%s)
  local elapsed=$((now - PROGRESS_START_TIME))
  local percent=$((PROGRESS_CURRENT_STEP * 100 / PROGRESS_TOTAL_STEPS))

  # Calculate ETA based on average time per completed step
  local eta="--"
  if [[ $PROGRESS_CURRENT_STEP -gt 1 ]] && [[ $elapsed -gt 0 ]]; then
    local completed_steps=$((PROGRESS_CURRENT_STEP - 1))
    local avg_per_step=$((elapsed / completed_steps))
    local remaining_steps=$((PROGRESS_TOTAL_STEPS - PROGRESS_CURRENT_STEP))
    local eta_seconds=$((avg_per_step * remaining_steps))
    eta="${eta_seconds}s"
  fi

  echo ""
  echo "[Step ${PROGRESS_CURRENT_STEP}/${PROGRESS_TOTAL_STEPS}] ${description} (${percent}% | ${elapsed}s elapsed | ~${eta} remaining)"
}

# Mark current step as done
# Args: message
progress_done() {
  local message="$1"
  echo "✅ ${message}"
}

# Print final summary
progress_summary() {
  local now=$(date +%s)
  local total_elapsed=$((now - PROGRESS_START_TIME))
  echo ""
  echo "════════════════════════════════════════"
  echo "Completed ${PROGRESS_TOTAL_STEPS} steps in ${total_elapsed}s"
  echo "════════════════════════════════════════"
}
