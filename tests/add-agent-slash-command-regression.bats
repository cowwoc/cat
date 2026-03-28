#!/usr/bin/env bats
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
#
# Regression test: after add-agent completes, the main agent must not mention internal slash commands
# (e.g., /cat:work, /cat:status) in conversational response text.
#
# These tests operate without live user input. The AskUserQuestion interaction is simulated by
# capturing hypothetical response strings and running the detection pattern against them, instead
# of invoking the actual skill.

TEST_DIR="$(cd "$(dirname "$BATS_TEST_FILENAME")" && pwd)"
PROJECT_ROOT="$(cd "$TEST_DIR/.." && pwd)"
FIRST_USE_MD="$PROJECT_ROOT/plugin/skills/add-agent/first-use.md"

# ─────────────────────────────────────────────────────────────────────────────
# Detection helper
#
# Simulates what a caller would check in a post-completion response:
#   does the text contain an internal slash command of the form /cat:<letter...>?
#
# Returns 0 (success) if a slash command IS detected (violation), 1 otherwise.
# ─────────────────────────────────────────────────────────────────────────────
contains_slash_command() {
    local text="$1"
    echo "$text" | grep -qE '/cat:[a-z]'
}

# ─────────────────────────────────────────────────────────────────────────────
# Guidance text presence tests
#
# Verify that first-use.md contains the prohibition so the LLM sees it.
# ─────────────────────────────────────────────────────────────────────────────

@test "first-use.md contains Post-completion workflow guidance" {
    grep -q 'Post-completion workflow' "$FIRST_USE_MD"
}

@test "first-use.md guidance prohibits internal slash commands in conversational text" {
    grep -q 'do NOT mention internal slash commands' "$FIRST_USE_MD"
}

@test "first-use.md guidance names /cat:work as a prohibited example" {
    grep -qF '/cat:work' "$FIRST_USE_MD"
}

@test "first-use.md guidance directs use of AskUserQuestion for next-step progression" {
    grep -q 'AskUserQuestion' "$FIRST_USE_MD"
}

# ─────────────────────────────────────────────────────────────────────────────
# Pattern detection tests
#
# Verify the /cat:[a-z] regex correctly identifies slash commands in response text.
# These mock the post-completion response without live user input.
# ─────────────────────────────────────────────────────────────────────────────

@test "detection: /cat:work in response is detected as a violation" {
    local response="Your issue has been created. You can start working on it with /cat:work."
    contains_slash_command "$response"
}

@test "detection: /cat:status in response is detected as a violation" {
    local response="Issue created successfully. To see project status, run /cat:status."
    contains_slash_command "$response"
}

@test "detection: /cat:add in response is detected as a violation" {
    local response="Done! To add another issue, use /cat:add."
    contains_slash_command "$response"
}

@test "detection: clean response with no slash commands is not flagged" {
    local response="Your issue has been created successfully. Would you like to start working on it now?"
    run contains_slash_command "$response"
    [ "$status" -ne 0 ]
}

@test "detection: response with AskUserQuestion offer (no slash command) is not flagged" {
    local response="Issue 'fix-login-bug' created under v2.1. Start working on it now?"
    run contains_slash_command "$response"
    [ "$status" -ne 0 ]
}

@test "detection: /cat: without a trailing letter is not flagged" {
    local response="The prefix cat: is used for skills."
    run contains_slash_command "$response"
    [ "$status" -ne 0 ]
}

@test "detection: uppercase /CAT:work is not flagged (pattern is lowercase-only)" {
    local response="Use /CAT:work to start."
    run contains_slash_command "$response"
    [ "$status" -ne 0 ]
}
