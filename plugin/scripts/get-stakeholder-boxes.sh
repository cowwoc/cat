#!/usr/bin/env bash
# get-stakeholder-boxes.sh - Generate stakeholder review box templates
#
# USAGE: get-stakeholder-boxes.sh
#
# OUTPUTS: Pre-rendered stakeholder box templates
#
# This script is designed to be called via silent preprocessing (!`command`).

cat << 'EOF'
## Pre-rendered Stakeholder Boxes

**CRITICAL**: Copy-paste the EXACT boxes below. Do NOT reconstruct or retype them.

### STAKEHOLDER_SELECTION

╭─── STAKEHOLDER SELECTION ────────────────────────────────────╮
│                                                              │
│ Stakeholder Review: {N} of 10 stakeholders selected          │
│                                                              │
│ Running: {running-list}                                      │
│                                                              │
│ Skipped:                                                     │
│   - {stakeholder1}: {reason1}                                │
│   - {stakeholder2}: {reason2}                                │
│                                                              │
╰──────────────────────────────────────────────────────────────╯

### STAKEHOLDER_REVIEW

╭─── STAKEHOLDER REVIEW ───────────────────────────────────────╮
│                                                              │
│ Task: {task-name}                                            │
│                                                              │
├──────────────────────────────────────────────────────────────┤
│ Spawning reviewers...                                        │
│ ├── {stakeholder1} {status1}                                 │
│ ├── {stakeholder2} {status2}                                 │
│ └── {stakeholderN} {statusN}                                 │
├──────────────────────────────────────────────────────────────┤
│ Result: {APPROVED|CONCERNS|REJECTED} ({summary})             │
│                                                              │
╰──────────────────────────────────────────────────────────────╯

### CRITICAL_CONCERN

┌─ CRITICAL ───────────────────────────────────────────────────┐
│ [{Stakeholder}] {concern-description}                        │
│ └─ {file-location}                                           │
│                                                              │
└──────────────────────────────────────────────────────────────┘

### HIGH_CONCERN

┌─ HIGH ───────────────────────────────────────────────────────┐
│ [{Stakeholder}] {concern-description}                        │
│ └─ {file-location}                                           │
│                                                              │
└──────────────────────────────────────────────────────────────┘

---

**INSTRUCTION**: Copy-paste box structures VERBATIM, then replace ONLY placeholder text inside.
Repeat reviewer lines and concern boxes as needed for actual data.
EOF
