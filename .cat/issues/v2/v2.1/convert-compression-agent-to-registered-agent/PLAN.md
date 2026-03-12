# Plan: convert-compression-agent-to-registered-agent

## Current State
The optimize-doc compression agent is a standalone document (`plugin/skills/optimize-doc/COMPRESSION-AGENT.md`) that
a general-purpose subagent reads via the Read tool as its first action. This wastes a tool call and delays the
subagent’s start — instructions are not available until after the Read completes.

## Target State
The compression agent is a registered agent in `plugin/agents/compression-agent.md` with proper frontmatter. The
orchestrator spawns it via `subagent_type: "cat:compression-agent"`, and its instructions are injected into the
subagent’s system context automatically — no Read tool call needed.

## Satisfies
None

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None — the compression behavior is identical; only the delivery mechanism changes
- **Mitigation:** E2E test: run optimize-doc and verify compression + validation still works

## Files to Modify
- `plugin/agents/compression-agent.md` — NEW: registered agent with frontmatter + content from COMPRESSION-AGENT.md
- `plugin/skills/optimize-doc/first-use.md` — change `subagent_type: "general-purpose"` to
  `subagent_type: "cat:compression-agent"` and remove the prompt line that tells the subagent to read
  COMPRESSION-AGENT.md
- `plugin/skills/optimize-doc/COMPRESSION-AGENT.md` — DELETE: no longer needed (content moved to registered agent)

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Waves

### Wave 1

- Create `plugin/agents/compression-agent.md`:
  - Files: `plugin/agents/compression-agent.md`
  - Add YAML frontmatter matching the pattern of existing agents (e.g., `work-execute.md`):
    ```yaml
    ---
    name: compression-agent
    description: Compression specialist for optimize-doc. Compresses documents while preserving execution equivalence.
    model: sonnet
    ---
    ```
  - Copy the body content from `plugin/skills/optimize-doc/COMPRESSION-AGENT.md` (everything after the license
    header), removing the "INTERNAL DOCUMENT" notice since the agent is now properly registered
  - Remove `{{FILE_PATH}}` and `{{OUTPUT_PATH}}` template variables from the body — these will be passed via
    the Task tool prompt instead (registered agents cannot use skill-loader variable substitution)
  - NOTE: Files in `plugin/agents/` are exempt from license headers per `.claude/rules/license-header.md`

- Update `plugin/skills/optimize-doc/first-use.md`:
  - Files: `plugin/skills/optimize-doc/first-use.md`
  - Locate the Step 3 subagent invocation block (Step 3: Invoke Compression Agent)
  - Change `subagent_type: "general-purpose"` to `subagent_type: "cat:compression-agent"`
  - Remove the prompt line: `Read the instructions at: plugin/skills/optimize-doc/COMPRESSION-AGENT.md`
  - Keep the prompt lines that pass FILE_PATH and OUTPUT_PATH (these become the subagent’s task-specific input)
  - Update the "Agent Type" note in Implementation Notes section: change `subagent_type: "general-purpose"`
    to `subagent_type: "cat:compression-agent"`
  - Update any prose references to "COMPRESSION-AGENT.md" to say "compression-agent" (the registered agent)

- Delete `plugin/skills/optimize-doc/COMPRESSION-AGENT.md`:
  - Files: `plugin/skills/optimize-doc/COMPRESSION-AGENT.md`
  - Remove the file entirely (content has been moved to `plugin/agents/compression-agent.md`)

## Post-conditions
- [ ] `plugin/agents/compression-agent.md` exists with proper frontmatter (name, description, model)
- [ ] `plugin/skills/optimize-doc/COMPRESSION-AGENT.md` no longer exists
- [ ] first-use.md references `subagent_type: "cat:compression-agent"` (not "general-purpose")
- [ ] first-use.md prompt no longer instructs subagent to Read COMPRESSION-AGENT.md
- [ ] User-visible behavior unchanged: optimize-doc compression produces equivalent results
- [ ] All tests pass (`mvn -f client/pom.xml test`)
- [ ] E2E: Run optimize-doc on a test document; compression subagent starts with instructions pre-loaded
  (no Read tool call for COMPRESSION-AGENT.md in subagent transcript)