---
description: >
  Trigger a GitHub Actions workflow from a feature branch by temporarily adding an 'on: push' trigger,
  running the workflow via 'gh workflow run', and cleaning up the trigger afterward. Use when: CI must run
  from a feature branch before the branch is merged to main. Trigger words: "trigger workflow from feature
  branch", "run CI from feature branch", "temporarily add push trigger".
argument-hint: "<cat_agent_id> <workflow_file>"
user-invocable: true
effort: medium
disable-model-invocation: true
---

!`"${CLAUDE_PLUGIN_DATA}/client/bin/get-skill" github-trigger-workflow-agent "${CLAUDE_SESSION_ID}" "$ARGUMENTS"`
