"""
Validate PLAN.md execution steps don't contain expected values that prime fabrication.

Blocks Task tool calls when PLAN.md contains priming patterns in Execution Steps.

Related mistakes: M254, M265, M269, M273, M274, M276, M320, M346, M349, M355, M370, M421, M423
"""

import re
from pathlib import Path
from . import register_handler


class ValidatePlanMdHandler:
    """Validate PLAN.md doesn't prime subagents with expected outcomes."""

    # Patterns that indicate expected values in execution steps
    PRIMING_PATTERNS = [
        # Score expectations
        r'score\s*[=:]\s*[\d.]+',           # "score = 1.0" or "score: 1.0"
        r'score\s+of\s+[\d.]+',             # "score of 1.0"

        # Expected values
        r'expected\s*[=:]\s*[\d.]+',        # "expected = 5" or "expected: 5"
        r'should\s+be\s+[\d.]+',            # "should be 100"
        r'must\s+be\s+[\d.]+',              # "must be 1.0"

        # Threshold values in action descriptions
        r'verify.*\s+[\d.]+',               # "verify ... 1.0"
        r'check.*\s+[\d.]+',                # "check ... 100"
        r'ensure.*\s+[\d.]+',               # "ensure ... 0.95"

        # Specific status expectations
        r'achieve\s+\w+\s+status',          # "achieve EQUIVALENT status"
        r'result.*\s+status',               # "result should be PASSED status"
    ]

    def check(self, command: str, context: dict) -> dict | None:
        """
        Check if command is spawning a subagent with a PLAN.md that contains priming patterns.

        This runs on Bash commands, so we look for patterns that indicate reading PLAN.md
        and spawning implementation subagents (via work-with-issue orchestration).
        """
        # Only check if this appears to be reading PLAN.md for subagent execution
        # We look for patterns like EXECUTION_STEPS being read from PLAN.md
        if 'EXECUTION_STEPS' not in command:
            return None

        # Try to find PLAN.md path in the command
        # Look for patterns like ${ISSUE_PATH}/PLAN.md or direct paths
        plan_md_path = self._extract_plan_md_path(command, context)
        if not plan_md_path:
            return None

        # Read and validate PLAN.md
        try:
            plan_content = Path(plan_md_path).read_text()
            violations = self._check_execution_steps(plan_content)

            if violations:
                reason = self._format_block_message(violations, plan_md_path)
                return {
                    "decision": "block",
                    "reason": reason
                }
        except FileNotFoundError:
            # PLAN.md doesn't exist yet - allow (might be during initialization)
            return None
        except Exception as e:
            # Don't block on validation errors - log and allow
            return None

        return None

    def _extract_plan_md_path(self, command: str, context: dict) -> str | None:
        """Extract PLAN.md path from command or context."""
        # Check for ISSUE_PATH variable usage
        issue_path_match = re.search(r'\$\{ISSUE_PATH\}', command)
        if issue_path_match:
            # Try to get ISSUE_PATH from environment or context
            # In practice, we'll need to look at the actual command execution
            # For now, check if there's a cwd we can use
            cwd = context.get('cwd', '')
            if cwd:
                # Check if we're in a worktree and can find PLAN.md
                worktree_match = re.search(r'\.worktrees/([^/]+)', cwd)
                if worktree_match:
                    issue_id = worktree_match.group(1)
                    # Derive version from issue_id (e.g., 2.1-task-name -> v2/v2.1)
                    version_match = re.match(r'(\d+)\.(\d+)-', issue_id)
                    if version_match:
                        major = version_match.group(1)
                        minor = f"{major}.{version_match.group(2)}"
                        base_path = Path(cwd).parent.parent
                        plan_path = base_path / '.claude' / 'cat' / 'issues' / f'v{major}' / f'v{minor}' / issue_id.replace(f'{minor}-', '') / 'PLAN.md'
                        if plan_path.exists():
                            return str(plan_path)

        # Check for direct PLAN.md path in command
        plan_match = re.search(r'([^\s]+/PLAN\.md)', command)
        if plan_match:
            return plan_match.group(1)

        return None

    def _check_execution_steps(self, plan_content: str) -> list[str]:
        """
        Check execution steps section for priming patterns.

        Returns list of violation descriptions.
        """
        violations = []

        # Extract Execution Steps section
        # Pattern: ## Execution Steps ... ## next section
        exec_match = re.search(
            r'##\s+Execution\s+Steps\s*\n(.+?)(?=\n##|\Z)',
            plan_content,
            re.DOTALL | re.IGNORECASE
        )

        if not exec_match:
            return violations

        exec_steps = exec_match.group(1)

        # Check each priming pattern
        for pattern in self.PRIMING_PATTERNS:
            matches = list(re.finditer(pattern, exec_steps, re.IGNORECASE))
            for match in matches:
                # Get context around the match (line containing the match)
                start = max(0, exec_steps.rfind('\n', 0, match.start()))
                end = exec_steps.find('\n', match.end())
                if end == -1:
                    end = len(exec_steps)
                line = exec_steps[start:end].strip()

                violations.append(f"Pattern '{pattern}' found: {line}")

        return violations

    def _format_block_message(self, violations: list[str], plan_path: str) -> str:
        """Format blocking message with violation details."""
        violation_list = '\n'.join(f"  - {v}" for v in violations)

        return f"""**BLOCKED: PLAN.md contains expected values in Execution Steps**

File: {plan_path}

Violations found:
{violation_list}

**Why this is blocked:**
Including expected values in Execution Steps primes subagents to fabricate results.
When subagents see "verify score = 1.0", they report 1.0 regardless of actual results.

**Solution:**
Restructure PLAN.md to separate actions from outcomes:

```markdown
## Execution Steps (ACTIONS ONLY)
1. Compress files using /cat:shrink-doc
2. Run /compare-docs validation

## Success Criteria (MEASURABLE OUTCOMES)
- All files achieve EQUIVALENT status on /compare-docs
- Token reduction > 30%
```

Related mistakes: M254, M265, M269, M273, M274, M276, M320, M346, M349, M355, M370, M421, M423"""


register_handler(ValidatePlanMdHandler())
