# Plan: auto-decomposition-parallel-execution

## Objective
add auto-decomposition and parallel execution for large tasks

## Details
## Problem Solved
- Large tasks could exceed context limits without proactive decomposition
- Subagent token usage was not reported to users
- No automatic parallel execution after decomposition

## Solution Implemented
- Add analyze_task_size step to execute-task.md that estimates token usage
- Auto-trigger decompose-task when estimate exceeds threshold
- Generate wave-based parallel execution plans in decompose-task
- Auto-invoke parallel-execute for independent subtasks
- Add mandatory token metrics reporting in collect_and_report step

## Key Changes
- execute-task.md: New analyze_task_size step with estimation formula
- decompose-task/SKILL.md: Step 8 generates parallel execution plan
- parallel-execute/SKILL.md: Auto-Trigger from Decomposition section
- collect-results/SKILL.md: Mandatory token metrics reporting
- spawn-subagent/SKILL.md: Hook Inheritance (A008) section
- block-merge-commits.sh: Fixed stdin parsing for hook input
- workflows/execute-task.md: Renumbered steps, added task analysis

## Configuration
Threshold = contextLimit × targetContextUsage / 100
Default: 200000 × 40% = 80,000 tokens

## Acceptance Criteria
- [x] Implementation complete
- [x] Verified working
