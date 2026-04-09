---
mainAgent: true
---
## Approval Gate Protocol

**CRITICAL**: Destructive operations (merge, force-push, branch deletion) require EXPLICIT authorization via
AskUserQuestion option selection. Conversational signals do NOT constitute authorization.

### Valid Approval Sources

- Explicit option selection from AskUserQuestion (user clicks "Approve and merge" or equivalent button)
- The `toolUseResult.answers` array contains a value that exactly matches one of the presented options

### Invalid Approval Sources

The following NEVER count as approval for destructive operations:

- Conversational signals: "continue", "ok", "yes", "go ahead", "proceed", "let's do it", "sounds good"
- Resume signals: "continue with the workflow", "keep going", "that's fine"
- Implicit consent: assuming permission from prior decisions or prior approval of a different gate
- Silence or timeout
- Any message that is not a direct selection of a presented AskUserQuestion option

### Interruption Handling

When a user sends a non-option message during an approval gate (clarifying question, comment, or any conversational
response):

1. The gate is considered REJECTED — no explicit option was selected
2. Answer the clarifying question or respond to the message
3. Re-present the full approval gate with all original options and context
4. Wait for explicit option selection before executing the destructive operation

**Do NOT** interpret a resumed conversation as implicit approval. Answering a question and then saying "continue" is a
resume signal, not an approval.

### Detection Logic

After AskUserQuestion invocation:

- `toolUseResult.answers` is empty or null → **GATE REJECTED**
- `toolUseResult.answers` does not match any presented option exactly → **GATE REJECTED**
- `toolUseResult.answers` matches a presented option exactly → **GATE ACCEPTED**

### Examples

**Gate accepted (CORRECT):**
- User clicks "Approve and merge" button → `answers: ["Approve and merge"]` → proceed

**Gate rejected (requires re-presentation):**
- User sends: "Why is this rebasing onto v2.1?" → no option selected → re-present gate
- User sends: "continue" → not an option → re-present gate
- User sends: "ok" → not an option → re-present gate
- User sends: "yes" → not an option → re-present gate

### Trust Level Override

Trust level "high" skips the approval gate entirely and proceeds directly to merge execution.

### Recovery From Gate Violation

If a merge or destructive operation executes without explicit approval gate selection:

1. Invoke the `/cat:learn-agent` skill immediately
2. Document the specific conversational signal that was misinterpreted as approval
3. Update enforcement rules to prevent the same misinterpretation in future sessions
4. Record which step in the workflow failed to enforce the gate

### Enforcement Checklist

Before every approval gate presentation, verify:

- [ ] Gate includes all relevant context (what will happen, branch names, commit counts)
- [ ] Gate includes all relevant options (approve, cancel, and any intermediate options)
- [ ] After gate invocation, `toolUseResult.answers` is checked before proceeding
- [ ] Any non-option message triggers re-presentation, not implicit approval
