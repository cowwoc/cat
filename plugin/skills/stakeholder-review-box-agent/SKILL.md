---
description: Internal - renders a stakeholder review box during review
model: haiku
user-invocable: false
argument-hint: "<issue> <stakeholder:status,...> <result> <summary>"
# Format: reviewers is comma-separated stakeholder:status pairs, e.g. requirements:APPROVED,architecture:CONCERNS
---
!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-stakeholder-review-box" "$1" "$2" "$3" "$4"`
