# Plan: two-stage-planning

## Objective
two-stage planning for token efficiency

## Details
Planning subagent now operates in two stages:

Stage 1 (~5K tokens):
- Produces high-level outlines for Conservative/Balanced/Aggressive
- Returns agent_id for later resumption
- User selects approach (or auto-select based on preference)

Stage 2 (~20K tokens):
- Resume same agent using Task tool's resume parameter
- Agent has full context from Stage 1, no re-exploration needed
- Produces detailed implementation spec for selected approach only

Token savings: ~25K total vs ~60K if all approaches fully detailed

Updated:
- PLAN.md templates: Stage 1 (outlines) + Stage 2 (detailed spec) format
- execute-task.md: Two-stage workflow pattern
- choose-approach: Agent resumption after selection
- spawn-subagent: Planning stage documentation

## Acceptance Criteria
- [x] Implementation complete
- [x] Verified working
