---
description: Consolidate scattered documentation using a four-phase classify-extract-reconstruct-verify pipeline.
model: sonnet
disable-model-invocation: true
user-invocable: false
argument-hint: "<catAgentId> <path-to-document-or-directory>"
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/skill-loader" consolidate-doc-agent "$0"`
