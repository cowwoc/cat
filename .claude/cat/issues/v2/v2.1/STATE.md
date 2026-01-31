# v2.1 State: Pre-Demo Polish

## Status
- **Status:** in-progress
- **Progress:** 12%
- **Dependencies:** [v2.0]

## Summary
Finalize naming conventions and UI polish before recording demo videos.

## Tasks Pending
- scan-outdated-templates
- add-opus-model-guidance
- add-deployment-stakeholder
- holistic-review-skill
- holistic-review-criteria
- python-prerendered-output
- migrate-to-silent-preprocessing (decomposed)
  - update-skill-builder-docs
  - migrate-progress-banners
  - migrate-status-displays
  - migrate-remaining-handlers
- optimize-work-command-context
- migrate-python-to-java
- release-changelog-validation
- compress-skills-md (from decomposed compress-md-files)
- compress-concepts-md (from decomposed compress-md-files)
- compress-templates-md (from decomposed compress-md-files)
- compress-stakeholders-md (from decomposed compress-md-files)
- deduplicate-embedded-content
- subagent-doc-heavy-steps
- unify-output-template-delivery
- rename-precomputed-output
- command-optimizer
- shrink-doc-token-metrics
- batch-finalization-subagent
- rename-stakeholders
- rename-task-scripts (from decomposed rename-task-to-issue)
- rename-task-in-skills (from decomposed rename-task-to-issue)
- rename-task-remaining (from decomposed rename-task-to-issue)
- suggest-issue-names
- research-new-issues
- centralize-emoji-widths
- show-task-header-at-approval-gate
- monitor-subagents-handler
- run-retrospective-handler
- optimize-execution-handler
- use-patch-id-for-commit-tracking
- work-skill-banner-delegation
- show-active-agents-in-status

## Tasks Decomposed
- rename-task-to-issue → [rename-task-scripts, rename-task-in-skills, rename-task-in-concepts, rename-task-in-commands, rename-task-remaining]
- compress-md-files → [compress-skills-md, compress-commands-md, compress-concepts-md, compress-templates-md, compress-stakeholders-md]
- migrate-to-silent-preprocessing → [update-skill-builder-docs, migrate-progress-banners, migrate-status-displays, migrate-remaining-handlers]

## Tasks Completed
- acceptance-criteria-options
- compress-commands-md (duplicate of compress-skills-md - commands moved to skills/)
- rename-task-in-commands (duplicate of rename-task-in-skills - commands moved to skills/)
- rename-task-in-concepts (from decomposed rename-task-to-issue)
- self-discover-env-vars
