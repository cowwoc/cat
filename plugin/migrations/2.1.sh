#!/bin/bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
set -euo pipefail

# Migration to CAT 2.1
#
# Changes:
# - No structural changes (pre-demo polish release)

trap 'echo "ERROR in 2.1.sh at line $LINENO: $BASH_COMMAND" >&2; exit 1' ERR

source "${CLAUDE_PLUGIN_ROOT}/migrations/lib/utils.sh"

log_success "Migration to 2.1 completed (no structural changes)"
