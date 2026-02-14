#!/usr/bin/env python3
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
"""UserPromptSubmit hook: inject forced-eval instruction for low-activation skills.

This hook injects a system-reminder that forces Claude to explicitly evaluate whether
each low-activation skill matches the user's prompt before proceeding. This achieves
100% activation rate for skills that previously had low activation.

Based on the forced-eval approach from:
https://scottspence.com/posts/measuring-claude-code-skill-activation-with-sandboxed-evals
"""

import json
import sys


FORCED_EVAL_INSTRUCTION = """
SKILL ACTIVATION CHECK: Before responding, evaluate whether the user's message matches any of these skills. For each skill, decide YES or NO. If YES, invoke it using the Skill tool BEFORE doing anything else.

Skills to evaluate:
- cat:add — Add a new issue/task to a version. Triggers: "add a task", "add a new issue", "create a new issue", "new task for", "I need to track"
- cat:work — Start working on, resume, or continue an existing issue. Triggers: "work on", "resume", "continue working", "pick up", "start working". NOT for viewing status.
- cat:research — Research how to implement something. Triggers: "research", "look up", "investigate", "best practices", "find out how"
- cat:learn — Record mistakes and perform root cause analysis. Triggers: "record this mistake", "document what went wrong", "learn from this"
- cat:monitor-subagents — Check status of RUNNING SUBAGENTS (not current session tokens). Triggers: "check subagents", "monitor subagents", "subagent status"
- cat:run-retrospective — Run retrospective analysis on recorded learnings. Triggers: "run retrospective", "analyze patterns", "retrospective on learnings"
- cat:shrink-doc — Shrink/compress document files while maintaining meaning. Triggers: "shrink", "compress doc", "reduce token usage", "make file smaller"

If NONE match, proceed normally without invoking any skill.
""".strip()


def main():
    try:
        hook_input = json.loads(sys.stdin.read())

        output = {
            "additionalContext": FORCED_EVAL_INSTRUCTION
        }

        print(json.dumps(output))

    except Exception:
        print("{}")
        sys.exit(0)


if __name__ == "__main__":
    main()
