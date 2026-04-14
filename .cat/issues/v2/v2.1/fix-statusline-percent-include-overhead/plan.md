# Plan

## Goal

Fix the statusline context-usage percentage calculation so that system prompt and system tools overhead
are **included** in the displayed percentage — not subtracted. The only tokens that should be excluded
from the total are the autocommit buffer (the reservation Claude Code holds back from the visible window
to allow completing a response).

The correct formula is:

```
percent_used = used_tokens / (model_total_context - autocommit_buffer) * 100
```

Where:
- `used_tokens` — as reported by Claude Code (includes system prompt, tools, conversation turns)
- `model_total_context` — full context window for the model (e.g. 200,000 or 1,000,000)
- `autocommit_buffer` — the autocommit reservation only; no other deductions

This supersedes the approach implemented in `fix-statusline-context-percent-calculation`, which
subtracted a fixed overhead (34,500 tokens) from both numerator and denominator. That approach was
incorrect: it hid real token usage (the system prompt and tools are actual consumption the user needs
to see reflected in the percentage).

## Pre-conditions

(none)

## Post-conditions

- [ ] `StatuslineCommand` calculates context percentage as `used_tokens / (total_context - autocommit_buffer)`
- [ ] System prompt and tool overhead tokens are reflected in `percent_used` (not subtracted)
- [ ] The only subtraction from total is the autocommit buffer size
- [ ] All unit tests pass (`mvn -f client/pom.xml verify -e`)
