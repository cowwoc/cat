# Plan: add-engine-specific-runner-streaming-states

## Goal

Add engine-specific streaming/session runner modes so a parent agent can interact with a nested engine one turn at a
time and distinguish whether the nested agent is working, waiting on a tool/model response, waiting for the next
request, or fully terminated.

The runner layer should expose both:

- a session-oriented streaming interface for agents and orchestrators that want turn-by-turn control; and
- a deterministic one-shot adapter for existing `prompt-tester` and grader workflows.

The one-shot adapter may be implemented on top of the streaming interface, but `turn.completed` must not be treated as
process termination in the session API. It only means the current turn is done and the session may be ready for the
next request.

## Parent Requirements

None — runner observability improvement for nested engine execution.

## Risk Assessment

- **Risk Level:** MEDIUM
- **Concerns:** Cross-engine behavior differs; overclaiming state certainty would mislead parent workflows. A naive
  implementation could also confuse "turn complete" with "session terminated" and break multi-turn scenarios.
- **Mitigation:** Define a shared session/turn state contract with separate turn and process lifecycles. Allow
  engine-specific in-flight substates only when grounded by real stream events.

## Files to Modify

- `client/common-cli/**` — shared session/turn state contract, one-shot adapter, persistence, and parent-facing status
  plumbing
- `client/codex-cli/**` — Codex streaming/session implementation and JSON lifecycle event mapping
- `client/claude-cli/**` — Claude streaming/session implementation and stream-json lifecycle event mapping
- `client/plugin/skills/**` and `client/plugin/agents/**` — update workflow/docs where parent polling or waiting
  semantics depend on nested runner state
- Tests under the affected modules

## Pre-conditions

- [ ] All dependent issues are closed

## Jobs

### Job 1: Define a shared nested-runner session contract

- Add a common schema/model for nested runner sessions and turns.
- Separate process/session state from turn state. At minimum, represent:
  - `working`
  - `waiting_for_next_request`
  - `completed`
  - `timeout`
  - `error`
- Allow optional engine-specific in-flight substates such as:
  - `waiting_for_tool_result`
  - `waiting_for_model`
- Track at least:
  - session id
  - current turn id
  - latest event timestamp
  - turn state
  - process/session state
  - whether the caller may submit another turn
- Define what evidence is required before a state may be reported.

### Job 2: Add a streaming session runner API

- Add runner operations for:
  - starting a nested engine session
  - submitting a turn
  - reading streamed events
  - reading the latest state
  - closing the session
- Keep a one-shot helper that opens a session, submits the scripted turn(s), collects artifacts, and closes the
  session.
- Ensure direct runner users can interact one turn at a time without going through `prompt-tester`.
- Ensure existing one-shot callers can continue to receive final parsed artifacts without managing the session.

### Job 3: Implement Codex streaming/session support

- Use the best available Codex transport for streaming session behavior.
- Map Codex JSON lifecycle events into the shared session/turn state contract.
- Distinguish:
  - current turn working
  - current turn waiting on model
  - current turn waiting on tool result
  - current turn completed
  - session waiting for next request
  - process/session terminated
- Do not treat `turn.completed` as process termination in session mode.
- Keep `codex-runner` as the CAT adapter layer rather than calling `codex exec` directly from higher-level workflow
  code.

### Job 4: Implement Claude streaming/session support

- Use Claude `stream-json` output to infer a safe minimum activity model.
- Map Claude stream events into the shared session/turn state contract.
- Report turn completion, session termination, timeout, and error reliably.
- Report `working` only while the process is alive and grounded by streaming activity.
- Add richer waiting substates only if the stream exposes them explicitly enough to avoid guesswork.

### Job 5: Make prompt-tester a deterministic adapter over streaming sessions

- Update SPRT/testcase execution so `prompt-tester` can run scripted turns against the streaming runner session:
  - submit Turn 1
  - read streamed events until that turn completes
  - collect the agent response/artifacts
  - submit the next testcase turn when the scenario has one
  - close the session when the testcase is complete
- Preserve the existing prompt-tester CLI behavior as a one-shot command from the caller's perspective.
- Persist the same run artifacts currently used by deterministic assertions and graders.
- Do not require the parent interactive agent to manually feed SPRT testcase turns.

### Job 6: Expose parent-facing status and waiting semantics

- Update the parent orchestration path so it can distinguish:
  - nested agent still working
  - nested agent blocked waiting on tool/model activity
  - nested agent completed the current turn and is waiting for the next request
  - nested engine process/session has terminated
- Ensure silent hangs remain distinguishable from clean completion.
- Publish enough state for a parent agent to decide whether to wait, send the next request, close the session, or
  report a hang.

### Job 7: Preserve the runner adapter layer

- Keep engine runners as CAT's adapter boundary. Higher-level CAT code should not invoke `codex exec` or `claude`
  directly.
- The runner layer remains responsible for:
  - auth and base-url normalization
  - sandbox and isolation setup
  - plugin/runtime-under-test selection
  - agent instruction injection
  - structured event capture
  - timeout and lifecycle handling
  - parsed CAT artifacts for prompt-tester and graders
- Direct engine invocation is acceptable only inside engine-specific runner implementations.

### Job 8: Add regression and end-to-end coverage

- Add focused tests for shared session/turn state parsing and validation.
- Add engine-specific tests for Codex and Claude event streams.
- Add tests proving `turn.completed` and process termination are distinct states.
- Add an end-to-end prompt-tester scenario proving the CLI feeds multiple testcase turns through the streaming
  adapter and still behaves like a one-shot command to its caller.
- Add an end-to-end direct-runner scenario proving an agent/orchestrator can submit one turn, inspect streamed state,
  then submit another turn before closing the session.

## Post-conditions

- [ ] Shared nested-runner session/turn contract exists and is documented
- [ ] `codex-runner` exposes streaming/session operations and maps Codex events into the shared state contract
- [ ] `claude-runner` exposes streaming/session operations and maps Claude stream-json events into the shared state
  contract
- [ ] Existing one-shot runner behavior is preserved through an adapter over the streaming/session interface
- [ ] `prompt-tester` can feed scripted multi-turn testcases through the streaming adapter while remaining a one-shot
  CLI to its caller
- [ ] Agents or orchestrators that invoke an engine runner directly can submit turns one at a time and decide when to
  close the nested session
- [ ] Parent workflows can distinguish active work, turn completion, waiting for next request, timeout, error, and
  process/session termination without relying on process silence alone
- [ ] Engine-specific waiting substates are only reported when grounded by actual stream evidence
- [ ] Higher-level CAT orchestration continues to use runner adapters instead of invoking engine CLIs directly
- [ ] Tests cover shared contract behavior, both engine integrations, prompt-tester adapter behavior, and direct
  turn-by-turn runner usage
