"""
Auto-invoke learn skill when mistakes are detected.

Monitors tool results for error patterns and suggests the learn
skill for root cause analysis.

PATTERN EVOLUTION:
  - Build failures, test failures, protocol violations
  - Merge conflicts, edit failures, skill step failures
  - Git operation failures, missing cleanup, self-acknowledged mistakes
  - Restore from backup, critical self-acknowledgments
  - Wrong working directory, parse errors, bash parse errors
  - Validation fabrication (scores without skill invocation)
"""

import re
from pathlib import Path
from . import register_handler


# Patterns for detecting validation scores (shared with detect_validation_fabrication)
VALIDATION_SCORE_PATTERNS = [
    r'(?:equivalence[_\s]?score|score)[:\s]+[01]\.\d+',
    r'"equivalence_score"[:\s]+[01]\.\d+',
    r'equivalence[:\s]+[01]\.\d+',
]

# Skills that legitimately produce validation scores
VALIDATION_SKILLS = ["compare-docs", "shrink-doc"]


class AutoLearnHandler:
    """Detect mistakes and suggest learn skill."""

    def __init__(self):
        self._last_line_cache = {}

    def check(self, tool_name: str, tool_result: dict, context: dict) -> dict | None:
        session_id = context.get("session_id", "")

        # Combine stdout and stderr
        stdout = tool_result.get("stdout", "") or ""
        stderr = tool_result.get("stderr", "") or ""
        tool_output = stdout + stderr

        # Get exit code
        exit_code = tool_result.get("exit_code", 0)
        if exit_code is None:
            exit_code = 0

        # Check assistant messages from conversation log
        last_assistant_message = self._get_recent_assistant_messages(session_id)

        # Run pattern detection
        mistake_type, mistake_details = self._detect_mistake(
            tool_name, tool_output, exit_code, last_assistant_message
        )

        if not mistake_type:
            return None

        # Build suggestion
        task_subject = f"LFM: Investigate {mistake_type} from {tool_name}"
        task_active_form = f"Investigating {mistake_type} mistake"

        return {
            "additionalContext": f"""📚 MISTAKE DETECTED: {mistake_type}

**MANDATORY**: Use TaskCreate to track this investigation:
- subject: "{task_subject}"
- description: "Investigate {mistake_type} detected during {tool_name} execution"
- activeForm: "{task_active_form}"

**Context**: Detected {mistake_type} during {tool_name} execution.
**Details**: {mistake_details[:500] if mistake_details else 'N/A'}"""
        }

    def _get_recent_assistant_messages(self, session_id: str) -> str:
        """Get recent assistant messages from conversation log."""
        if not session_id:
            return ""

        conv_log = Path.home() / ".config" / "claude" / "projects" / "-workspace" / f"{session_id}.jsonl"
        if not conv_log.exists():
            return ""

        try:
            with open(conv_log, 'r') as f:
                lines = f.readlines()

            current_count = len(lines)
            last_checked = self._last_line_cache.get(session_id, 0)

            if current_count <= last_checked:
                return ""

            new_lines = lines[last_checked:]
            self._last_line_cache[session_id] = current_count

            assistant_msgs = [line for line in new_lines if '"role":"assistant"' in line]
            return "\n".join(assistant_msgs[-5:])
        except Exception:
            return ""

    def _detect_mistake(self, tool_name: str, output: str, exit_code: int, assistant_msg: str) -> tuple:
        """Detect mistake type and details from output."""

        # Filter out JSON/JSONL content to avoid false positives from session history
        output = self._filter_json_content(output)

        # Pattern 1: Build failures
        if re.search(r"BUILD FAILURE|COMPILATION ERROR|compilation failure", output, re.I):
            return "build_failure", self._extract_context(output, r"error|failure", 5)

        # Pattern 2: Test failures (language-agnostic patterns)
        # - "Tests run: X, Failures: Y" (JUnit/Maven/TestNG)
        # - "X test(s) failed" or "X failures" with count
        # - "FAIL:" or "FAILED" at line start (pytest, go test, etc.)
        # - "test X ... FAILED" (pytest verbose)
        if re.search(
            r"Tests run:.*Failures: [1-9]|"  # JUnit-style
            r"\d+\s+tests?\s+failed|"         # "2 tests failed"
            r"\d+\s+failures?\b|"             # "2 failures"
            r"^(FAIL:|FAILED\s)|"             # Line starts with FAIL
            r"^\s*\S+\s+\.\.\.\s+FAILED",     # pytest verbose: "test_foo ... FAILED"
            output, re.I | re.M
        ):
            return "test_failure", self._extract_context(output, r"fail|error", 5)

        # Pattern 3: Protocol violations
        if re.search(r"PROTOCOL VIOLATION|🚨.*VIOLATION", output):
            return "protocol_violation", self._extract_context(output, r"violation", 5)

        # Pattern 4: Merge conflicts
        if re.search(r"CONFLICT \(|^<<<<<<<|^=======$|^>>>>>>>", output, re.M):
            return "merge_conflict", self._extract_context(output, r"CONFLICT \(|<<<<<<<", 3)

        # Pattern 5: Edit tool failures
        if re.search(r"String to replace not found|old_string not found", output, re.I):
            return "edit_failure", self._extract_context(output, r"string to replace not found|old_string not found", 2)

        # Pattern 6: Skill step failures
        if tool_name == "Skill" and re.search(
            r"\bERROR\b|\bFAILED\b|failed to|step.*(failed|failure)|could not|unable to",
            output, re.I
        ):
            return "skill_step_failure", self._extract_context(output, r"error|failed|could not|unable to", 5)

        # Pattern 7: Git operation failures (filtered)
        filtered = self._filter_git_noise(output)
        if re.search(r"^fatal:|^error: ", filtered, re.M):
            return "git_operation_failure", self._extract_context(filtered, r"^fatal:|^error: ", 3)

        # Pattern 8: Missing cleanup
        if re.search(r"why didn't you (remove|delete|clean|cleanup)", output, re.I):
            return "missing_cleanup", self._extract_context(output, r"why didn't you|didn't you", 2)

        # Pattern 9: Self-acknowledged mistakes
        if re.search(
            r"(you're|you are) (right|correct|absolutely right).*(should have|should've)|I should have.*instead",
            output, re.I
        ):
            return "self_acknowledged_mistake", self._extract_context(output, r"you're right|should have", 5)

        # Pattern 10: Restore from backup
        if re.search(
            r"(let me|I'll|I will|going to) (restore|reset).*(from|using|to).{0,10}backup",
            output, re.I
        ):
            return "restore_from_backup", self._extract_context(output, r"restore|reset|backup", 3)

        # Pattern 11: Critical self-acknowledgments (filtered)
        filtered = self._filter_git_noise(output)
        if re.search(
            r"CRITICAL (DISASTER|MISTAKE|ERROR|FAILURE|BUG|PROBLEM|ISSUE)|catastrophic|devastating",
            filtered, re.I
        ):
            return "critical_self_acknowledgment", self._extract_context(filtered, r"CRITICAL|catastrophic|devastating", 5)

        # Pattern 12: Wrong working directory
        if re.search(r"fatal: not a git repository|not a git repository \(or any", output, re.I):
            return "wrong_working_directory", self._extract_context(output, r"not a git repository", 3)

        # Pattern 12b: Missing pom.xml
        if re.search(r"Could not (find|locate) (the )?pom\.xml|No pom\.xml found", output, re.I):
            return "wrong_working_directory", self._extract_context(output, r"pom\.xml", 3)

        # Pattern 12c: Path errors in Bash
        if tool_name == "Bash" and re.search(
            r"No such file or directory.*(/workspace|/tasks)|cannot access.*/workspace",
            output, re.I
        ):
            return "wrong_working_directory", self._extract_context(output, r"No such file or directory|cannot access", 3)

        # Pattern 13: Parse errors (only on failure)
        if exit_code != 0 and re.search(
            r"parse error.*Invalid|jq: error|JSON.parse.*SyntaxError|malformed JSON",
            output, re.I
        ):
            return "parse_error", self._extract_context(output, r"parse error|jq: error|JSON|SyntaxError", 5)

        # Pattern 14: Bash parse errors
        if tool_name == "Bash" and re.search(r"\(eval\):[0-9]+:.*parse error", output):
            return "bash_parse_error", self._extract_context(output, r"\(eval\):[0-9]+:|parse error", 3)

        # Pattern 15: Validation fabrication (Task tool results with scores but no skill evidence)
        if tool_name == "Task":
            fabrication = self._detect_validation_fabrication(output)
            if fabrication:
                return "validation_fabrication", fabrication

        # Check assistant messages
        if assistant_msg:
            if re.search(
                r"I made a critical (error|mistake)|CRITICAL (DISASTER|MISTAKE|ERROR)",
                assistant_msg, re.I
            ):
                details = re.findall(r".*(?:critical|CRITICAL|catastrophic|devastating).*", assistant_msg)[:3]
                return "critical_self_acknowledgment", "\n".join(details)

            if re.search(
                r"My error|I (made|created) (a|an) (mistake|error)|I accidentally",
                assistant_msg, re.I
            ):
                details = re.findall(r".*(?:My error|mistake|error|accidentally).*", assistant_msg)[:3]
                return "self_acknowledged_mistake", "\n".join(details)

        return None, None

    def _filter_json_content(self, output: str) -> str:
        """Filter out JSON/JSONL content to avoid false positives.

        Session history files contain serialized JSON with embedded text
        that can trigger false pattern matches (e.g., 'test_failure' in
        category reference tables).
        """
        lines = output.split('\n')
        filtered = []
        for line in lines:
            stripped = line.strip()
            # Strip grep line number prefix (format: "123:json_content")
            grep_prefix_match = re.match(r'^(\d+):', stripped)
            if grep_prefix_match:
                stripped = stripped[grep_prefix_match.end():]
            # Skip JSONL lines (session history format)
            if stripped.startswith('{') and (
                '"type":' in stripped or
                '"parentUuid":' in stripped or
                '"sessionId":' in stripped
            ):
                continue
            # Skip JSON array elements
            if stripped.startswith('[{') or stripped.startswith('{"'):
                if '"type":' in stripped or '"message":' in stripped:
                    continue
            filtered.append(line)
        return '\n'.join(filtered)

    def _filter_git_noise(self, output: str) -> str:
        """Filter out git log output, JSON, diff lines."""
        lines = output.split('\n')
        filtered = []
        for line in lines:
            if line.strip().startswith('"'):
                continue
            if line.startswith(('+', '-', '@')):
                continue
            if re.match(r'^[a-f0-9]{7,}', line):
                continue
            if line.startswith(('commit ', 'Author:', 'Date:', '    ')):
                continue
            filtered.append(line)
        return '\n'.join(filtered)

    def _extract_context(self, output: str, pattern: str, lines_after: int) -> str:
        """Extract context around matching pattern."""
        lines = output.split('\n')
        result = []
        for i, line in enumerate(lines):
            if re.search(pattern, line, re.I):
                start = max(0, i - 2)
                end = min(len(lines), i + lines_after + 1)
                result.extend(lines[start:end])
                if len(result) > 20:
                    break
        return '\n'.join(result[:20])

    def _detect_validation_fabrication(self, output: str) -> str | None:
        """Detect validation scores in Task results without skill invocation evidence.

        Checks the SUBAGENT'S OUTPUT (not parent session) for evidence that
        validation skills were actually invoked.
        """
        # Check if output contains validation score patterns
        has_scores = False
        for pattern in VALIDATION_SCORE_PATTERNS:
            if re.search(pattern, output, re.IGNORECASE):
                has_scores = True
                break

        if not has_scores:
            return None

        # Check the TASK OUTPUT for evidence of skill invocation
        # The subagent's output should mention invoking validation skills
        for skill in VALIDATION_SKILLS:
            # Check for skill invocation patterns in the subagent's output
            if f"/cat:{skill}" in output:
                return None  # Subagent mentioned invoking the skill
            if f'skill": "{skill}"' in output or f"skill: {skill}" in output:
                return None  # Subagent mentioned invoking the skill
            if f"invoke {skill}" in output.lower() or f"invoked {skill}" in output.lower():
                return None  # Subagent mentioned invoking the skill
            if f"running {skill}" in output.lower() or f"ran {skill}" in output.lower():
                return None  # Subagent mentioned running the skill

        # Fabrication likely - scores present but no skill invocation evidence in output
        return self._extract_context(output, r"score|equivalence", 3)


register_handler(AutoLearnHandler())
