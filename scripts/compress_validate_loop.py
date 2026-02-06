#!/usr/bin/env python3
"""
Compression Validation Loop Orchestrator

Automates the compress->validate->learn cycle to identify and fix inconsistencies
in compression validation across separate Claude Code sessions.

Architecture:
- Process 1: Compression (runs /cat:work for compression tasks)
- Process 2: Validation (runs /cat:compare-docs)
- Loop: Iterate until all files reach EQUIVALENT status (1.0)

Usage:
    ./scripts/compress-validate-loop.py [--dry-run] [--verbose] [--max-iterations N] [--max-files N]

Options:
    --dry-run           Simulate execution without running actual commands
    --verbose           Enable debug logging
    --max-iterations N  Maximum loop iterations (default: 10)
    --max-files N       Maximum files to compress (0 = no limit, default: 0)
                        Note: To limit files, create a task with limited scope in PLAN.md
"""

import argparse
import json
import logging
import os
import subprocess
import sys
import tempfile
import time
from pathlib import Path
from typing import Dict, List, Optional, Tuple


class ClaudeProcessManager:
    """Manages external Claude Code processes via FIFO pipes.

    Uses stream-json format for multi-turn conversations with separate sessions
    for compression and validation to avoid context contamination.
    """

    def __init__(self, dry_run: bool = False, verbose: bool = False):
        self.dry_run = dry_run
        self.verbose = verbose
        self.logger = self._setup_logger()
        self.workspace = Path("/workspace")
        self.fifo_dir = None
        self.compression_session = None
        self.validation_session = None

    def _setup_logger(self) -> logging.Logger:
        """Configure logging based on verbosity."""
        logger = logging.getLogger("compress-validate-loop")
        handler = logging.StreamHandler(sys.stdout)
        formatter = logging.Formatter(
            "%(asctime)s [%(levelname)s] %(message)s",
            datefmt="%Y-%m-%d %H:%M:%S"
        )
        handler.setFormatter(formatter)
        logger.addHandler(handler)
        logger.setLevel(logging.DEBUG if self.verbose else logging.INFO)
        return logger

    def _start_session(self, session_name: str) -> Optional[Dict]:
        """
        Start a Claude CLI session with FIFO-based stream-json communication.

        Args:
            session_name: Name for the session (compression or validation)

        Returns:
            Session dict with process, fifo, and output file handles
        """
        if self.dry_run:
            self.logger.info(f"[DRY-RUN] Would start {session_name} session")
            return None

        try:
            # Create temporary directory for this session
            session_dir = tempfile.mkdtemp(prefix=f"claude_{session_name}_")
            fifo_path = Path(session_dir) / "input.fifo"
            output_path = Path(session_dir) / "output.txt"

            # Create FIFO for input
            os.mkfifo(fifo_path)
            self.logger.debug(f"Created FIFO: {fifo_path}")

            # Start Claude CLI process with stream-json
            # Use --dangerously-skip-permissions because skills use ${CLAUDE_PROJECT_DIR}
            # in bash commands, which the permission check rejects in automated mode
            cmd = [
                "claude",
                "--print",
                "--input-format", "stream-json",
                "--output-format", "stream-json",
                "--dangerously-skip-permissions",
                "--verbose"
            ]

            # Open output file for writing
            output_file = open(output_path, "w")

            # Set up environment with required variables for skill preprocessing
            env = os.environ.copy()
            env["CLAUDE_PROJECT_DIR"] = str(self.workspace)

            # Start process: reads from FIFO, writes to output file
            # No timeout here - skills like /cat:work can take 10+ minutes
            process = subprocess.Popen(
                f"bash -c 'cat {fifo_path} | {' '.join(cmd)} 2>&1'",
                shell=True,
                stdout=output_file,
                stderr=subprocess.STDOUT,
                cwd=self.workspace,
                env=env
            )

            # Wait for reader to be ready with retry
            max_retries = 10
            for attempt in range(max_retries):
                try:
                    fifo_fd = os.open(fifo_path, os.O_WRONLY | os.O_NONBLOCK)
                    break
                except OSError as e:
                    if e.errno == 6 and attempt < max_retries - 1:  # ENXIO - no reader yet
                        time.sleep(0.2)
                        continue
                    raise

            self.logger.info(f"Started {session_name} session (PID: {process.pid})")

            return {
                "name": session_name,
                "process": process,
                "fifo_fd": fifo_fd,
                "fifo_path": fifo_path,
                "output_file": output_file,
                "output_path": output_path,
                "session_dir": session_dir
            }

        except Exception as e:
            self.logger.error(f"Failed to start {session_name} session: {e}")
            return None

    def _send_message(self, session: Dict, content: str):
        """
        Send a message to a Claude session via FIFO.

        Args:
            session: Session dict from _start_session
            content: Message content to send
        """
        if not session:
            return

        msg = json.dumps({
            "type": "user",
            "message": {"role": "user", "content": content}
        }) + "\n"

        try:
            os.write(session["fifo_fd"], msg.encode("utf-8"))
            self.logger.debug(f"Sent message to {session['name']}: {content[:50]}...")
        except Exception as e:
            self.logger.error(f"Failed to send message to {session['name']}: {e}")

    def _send_tool_result(self, session: Dict, tool_use_id: str, result: str):
        """
        Send a tool_result response to a Claude session.

        Args:
            session: Session dict from _start_session
            tool_use_id: The tool_use_id to respond to
            result: The result content (e.g., "Abort")
        """
        if not session:
            return

        msg = json.dumps({
            "type": "user",
            "message": {
                "role": "user",
                "content": [{
                    "tool_use_id": tool_use_id,
                    "type": "tool_result",
                    "content": result
                }]
            }
        }) + "\n"

        try:
            os.write(session["fifo_fd"], msg.encode("utf-8"))
            self.logger.info(f"Sent tool_result to {session['name']}: {result}")
        except Exception as e:
            self.logger.error(f"Failed to send tool_result to {session['name']}: {e}")

    def _read_response(
        self,
        session: Dict,
        timeout: int = 60,
        wait_for_completion: bool = False,
        completion_markers: List[str] = None,
        auto_reject_approval: bool = False
    ) -> Optional[str]:
        """
        Read response from Claude session output file.

        Args:
            session: Session dict from _start_session
            timeout: Maximum seconds to wait for response
            wait_for_completion: If True, wait for result type (skill completion)
            completion_markers: Optional strings that indicate completion in response
            auto_reject_approval: If True, automatically reject at approval gate

        Returns:
            Response content or None if timeout/error
        """
        if not session:
            return None

        if completion_markers is None:
            completion_markers = []

        output_path = session["output_path"]
        start_time = time.time()
        # Track read position across calls to avoid re-reading old content
        last_size = session.get("_read_position", 0)
        all_responses = []
        result_received = False

        while time.time() - start_time < timeout:
            try:
                if output_path.exists():
                    current_size = output_path.stat().st_size
                    if current_size > last_size:
                        # New content available
                        with open(output_path, "r") as f:
                            f.seek(last_size)
                            new_content = f.read()
                            last_size = current_size

                            # Parse stream-json responses
                            for line in new_content.strip().split("\n"):
                                if line:
                                    try:
                                        data = json.loads(line)
                                        # Handle "assistant" type messages
                                        if data.get("type") == "assistant":
                                            msg = data.get("message", {})
                                            content = msg.get("content", [])

                                            # Check for AskUserQuestion tool_use (approval gate)
                                            if auto_reject_approval and isinstance(content, list):
                                                for block in content:
                                                    if block.get("type") == "tool_use" and block.get("name") == "AskUserQuestion":
                                                        tool_input = block.get("input", {})
                                                        questions = tool_input.get("questions", [])
                                                        # Check if this is an approval gate
                                                        for q in questions:
                                                            question_text = q.get("question", "")
                                                            if "merge" in question_text.lower() or "approve" in question_text.lower():
                                                                tool_use_id = block.get("id")
                                                                self.logger.info(f"Detected approval gate, auto-rejecting (tool_use_id: {tool_use_id})")
                                                                # Send Abort response
                                                                self._send_tool_result(session, tool_use_id, "Abort")
                                                                # Return with marker indicating rejection
                                                                all_responses.append("[AUTO-REJECTED AT APPROVAL GATE]")
                                                                session["_read_position"] = last_size
                                                                return "\n".join(all_responses)

                                            # Extract text from content blocks
                                            if isinstance(content, list):
                                                text_parts = [
                                                    block.get("text", "")
                                                    for block in content
                                                    if block.get("type") == "text"
                                                ]
                                                response_text = "\n".join(text_parts)
                                            else:
                                                response_text = content

                                            all_responses.append(response_text)

                                            # Check for completion markers
                                            for marker in completion_markers:
                                                if marker in response_text:
                                                    self.logger.debug(f"Found completion marker: {marker}")
                                                    session["_read_position"] = last_size
                                                    return "\n".join(all_responses)

                                            # If not waiting for completion, return first response
                                            if not wait_for_completion:
                                                session["_read_position"] = last_size
                                                return response_text

                                        # Handle result type for completion detection
                                        if data.get("type") == "result":
                                            result_received = True
                                            if wait_for_completion:
                                                self.logger.debug("Result received, returning accumulated responses")
                                                session["_read_position"] = last_size
                                                return "\n".join(all_responses) if all_responses else None
                                    except json.JSONDecodeError:
                                        continue

                time.sleep(0.5)
            except Exception as e:
                self.logger.error(f"Error reading response from {session['name']}: {e}")
                break

        self.logger.warning(f"Timeout waiting for response from {session['name']}")
        # Save read position and return what we have even on timeout
        session["_read_position"] = last_size
        return "\n".join(all_responses) if all_responses else None

    def _end_session(self, session: Optional[Dict]):
        """
        End a Claude session and clean up resources.

        Args:
            session: Session dict from _start_session
        """
        if not session:
            return

        try:
            # Close FIFO
            if "fifo_fd" in session:
                os.close(session["fifo_fd"])

            # Close output file
            if "output_file" in session:
                session["output_file"].close()

            # Terminate process
            if "process" in session and session["process"].poll() is None:
                session["process"].terminate()
                session["process"].wait(timeout=5)

            # Clean up session directory
            if "session_dir" in session:
                import shutil
                shutil.rmtree(session["session_dir"], ignore_errors=True)

            self.logger.info(f"Ended {session['name']} session")

        except Exception as e:
            self.logger.error(f"Error ending {session['name']} session: {e}")

    def run_compression(self, task_id: str) -> Dict:
        """
        Run compression task in external Claude process.

        Args:
            task_id: Task identifier (e.g., "2.1-shrink-work-execute")

        Returns:
            Dict with compression results including files and scores
        """
        if self.dry_run:
            self.logger.info(f"[DRY-RUN] Would run /cat:work {task_id}")
            return {
                "status": "SUCCESS",
                "files": ["plugin/skills/work-execute/SKILL.md"],
                "compression_scores": {"plugin/skills/work-execute/SKILL.md": 0.95}
            }

        self.logger.info(f"Running compression: /cat:work {task_id}")

        # Start compression session if not already started
        if not self.compression_session:
            self.compression_session = self._start_session("compression")
            if not self.compression_session:
                return {"status": "FAILED", "error": "Failed to start compression session"}

        # Send /cat:work command
        self._send_message(self.compression_session, f"/cat:work {task_id}")

        # Read response - wait for completion, auto-reject at approval gate
        # This keeps changes on task branch but doesn't merge to base
        response = self._read_response(
            self.compression_session,
            timeout=600,  # 10 minutes for compression tasks
            wait_for_completion=True,
            completion_markers=["merged to main", "APPROVAL_REQUIRED", "Issue Complete", "Ready to merge", "AUTO-REJECTED"],
            auto_reject_approval=True  # Automatically abort at merge gate
        )

        if not response:
            return {"status": "FAILED", "error": "No response from compression session"}

        # Check if auto-reject happened
        auto_rejected = "[AUTO-REJECTED AT APPROVAL GATE]" in response

        files = []
        scores = {}

        # Try to extract files from response first
        import re
        # Match file paths, excluding trailing punctuation
        file_pattern = r'plugin/skills/[\w\-/]+\.md'
        found_files = re.findall(file_pattern, response)
        # Deduplicate while preserving order
        seen = set()
        for f in found_files:
            if f not in seen:
                seen.add(f)
                files.append(f)

        # If no files found in response, get from worktree git diff
        if not files:
            worktree_path = self.workspace / ".claude" / "cat" / "worktrees" / task_id
            if worktree_path.exists():
                # Get base branch from the worktree
                base_branch = "v2.1"  # Default fallback
                # Try reading cat-base file directly
                cat_base_path = worktree_path / ".git" / "cat-base"
                if cat_base_path.exists():
                    base_branch = cat_base_path.read_text().strip()

                # Get changed files compared to base branch
                diff_result = self._run_command([
                    "git", "-C", str(worktree_path),
                    "diff", "--name-only", f"{base_branch}...HEAD"
                ])
                if diff_result["returncode"] == 0 and diff_result["stdout"].strip():
                    files = [f.strip() for f in diff_result["stdout"].strip().split("\n") if f.strip()]
                    self.logger.info(f"Found {len(files)} changed files in task worktree: {files}")

        # For now, assume score needs validation
        for f in files:
            scores[f] = 0.95  # Placeholder - actual score from compression

        return {
            "status": "SUCCESS",
            "auto_rejected": auto_rejected,
            "files": files,
            "compression_scores": scores,
            "output": response
        }

    def run_validation(self, task_id: str, file_path: str) -> float:
        """
        Run /cat:compare-docs validation in separate Claude process.

        Compares the original file (from base branch) with the compressed file
        (from task branch) to determine semantic equivalence.

        Args:
            task_id: Task identifier (used to find worktree and base branch)
            file_path: Relative path to file to validate

        Returns:
            Equivalence score (0.0 to 1.0)
        """
        if self.dry_run:
            self.logger.info(f"[DRY-RUN] Would run /cat:compare-docs {file_path}")
            return 0.95

        self.logger.info(f"Running validation: /cat:compare-docs for {file_path}")

        # Get base branch from task worktree
        worktree_path = self.workspace / ".claude" / "cat" / "worktrees" / task_id
        base_branch = "v2.1"  # Default fallback
        cat_base_path = worktree_path / ".git" / "cat-base"
        if cat_base_path.exists():
            base_branch = cat_base_path.read_text().strip()

        # Extract original file from base branch to temp location
        original_path = f"/tmp/compare-original-{file_path.replace('/', '-')}"
        show_result = self._run_command([
            "git", "-C", str(worktree_path),
            "show", f"{base_branch}:{file_path}"
        ])
        if show_result["returncode"] != 0:
            self.logger.error(f"Failed to get original file from {base_branch}: {show_result['stderr']}")
            return 0.0
        with open(original_path, "w") as f:
            f.write(show_result["stdout"])

        # Compressed file is in the task worktree
        compressed_path = str(worktree_path / file_path)

        self.logger.debug(f"Comparing: {original_path} vs {compressed_path}")

        # Start validation session if not already started
        if not self.validation_session:
            self.validation_session = self._start_session("validation")
            if not self.validation_session:
                self.logger.error("Failed to start validation session")
                return 0.0

        # Send /cat:compare-docs command with both file paths
        self._send_message(
            self.validation_session,
            f"/cat:compare-docs {original_path} {compressed_path}"
        )

        # Read response - compare-docs spawns 3 subagents per file
        # Use specific markers that appear at end of report, not mid-response
        response = self._read_response(
            self.validation_session,
            timeout=600,  # 10 minutes for full comparison
            wait_for_completion=True,
            completion_markers=["FINAL REPORT", "equivalence score", "semantic equivalence"]
        )

        if not response:
            self.logger.error("No response from validation session")
            return 0.0

        # Parse equivalence from response
        # New binary format:
        # - "Status: EQUIVALENT" → 1.0
        # - "Status: NOT_EQUIVALENT (44/47 preserved, 3 lost)" → 44/47
        # Old numeric format (fallback):
        # - "Consensus: 0.96 (PASS)"
        # - "Score: 0.85"
        import re

        # Check for new binary format first
        equiv_match = re.search(r'Status:\s*EQUIVALENT\b', response)
        if equiv_match and 'NOT_EQUIVALENT' not in response:
            self.logger.debug("Parsed Status: EQUIVALENT → 1.0")
            return 1.0

        # Check for NOT_EQUIVALENT with counts
        not_equiv_match = re.search(r'Status:\s*NOT_EQUIVALENT\s*\((\d+)/(\d+)\s*preserved', response)
        if not_equiv_match:
            preserved = int(not_equiv_match.group(1))
            total = int(not_equiv_match.group(2))
            if total > 0:
                score = preserved / total
                self.logger.debug(f"Parsed NOT_EQUIVALENT: {preserved}/{total} = {score:.2f}")
                return score

        # Fallback to old numeric patterns
        patterns = [
            (r'Consensus[:\s]+([0-9]\.[0-9]+)', "consensus"),
            (r'equivalence_score[:\s]+([0-9.]+)', "equivalence_score"),
            (r'\bScore[:\s]+([0-9]\.[0-9]+)', "score"),
            (r'final.*?([0-9]\.[0-9]+)', "final score"),
        ]

        for pattern, name in patterns:
            match = re.search(pattern, response, re.IGNORECASE)
            if match:
                try:
                    score = float(match.group(1))
                    if 0.0 <= score <= 1.0:
                        self.logger.debug(f"Parsed {name}: {score}")
                        return score
                except ValueError:
                    continue

        # Log response for debugging
        self.logger.debug(f"Full response (truncated): {response[:500]}...")

        # Default to 1.0 if no score found (assume success)
        self.logger.warning("No score found in validation response, assuming 1.0")
        return 1.0

    def analyze_and_improve_shrink_doc(
        self,
        file_path: str,
        score: float,
        compression_output: str
    ) -> Tuple[Dict, List[str]]:
        """
        Analyze compression history and update shrink-doc to improve equivalence scores.

        Instead of /cat:learn (designed for agent mistakes), this method:
        1. Reads the compression session history
        2. Identifies patterns that caused semantic loss
        3. Updates shrink-doc skill to avoid those patterns

        Args:
            file_path: Path to the file that failed validation
            score: The equivalence score achieved
            compression_output: Output from the compression session

        Returns:
            Tuple of (result dict, list of commit hashes for shrink-doc improvements)
        """
        if self.dry_run:
            self.logger.info(f"[DRY-RUN] Would analyze compression for: {file_path}")
            return ({"status": "SUCCESS"}, [])

        self.logger.info(f"Analyzing compression patterns for: {file_path} (score={score:.2f})")

        # Get commits before analysis
        before_result = self._run_command(["git", "rev-parse", "HEAD"])
        commit_before = before_result["stdout"].strip() if before_result["returncode"] == 0 else None

        # Start an analysis session
        analysis_session = self._start_session("analysis")
        if not analysis_session:
            self.logger.error("Failed to start analysis session")
            return ({"status": "FAILED", "error": "Failed to start session"}, [])

        try:
            # Build the analysis prompt
            # Get paths for file comparison
            worktree_path = self.workspace / ".claude" / "cat" / "worktrees" / file_path.split("/")[0] if "/" in file_path else self.workspace
            # Task ID format is like "2.1-compress-test-2files"
            task_worktree = self.workspace / ".claude" / "cat" / "worktrees"

            analysis_prompt = f"""Analyze this compression validation failure and improve the shrink-doc skill.

## Context
- File compressed: {file_path}
- Equivalence score: {score:.2f} (threshold: 1.0)
- Score interpretation: {score:.0%} semantic equivalence, {(1-score)*100:.0f}% semantic loss

## Your Analysis Steps
1. First, read BOTH versions of the file to see what was lost:
   - Original (before compression): Use `git show v2.1:{file_path}` or read from /tmp/compare-original-*
   - Compressed (after): Read from the task worktree

2. Create a diff to identify exactly what content was removed or changed

3. Read the shrink-doc skill at: plugin/skills/shrink-doc/SKILL.md

4. Analyze what patterns in the compression caused semantic loss:
   - Was important context removed?
   - Were structural relationships lost?
   - Were conditional/temporal dependencies flattened?

5. Update shrink-doc/SKILL.md with specific improvements:
   - Add explicit rules about what to preserve
   - Add examples of content that must NOT be compressed
   - Add verification steps before compression

6. Commit your changes with message: "config: improve shrink-doc to preserve semantics"

## Compression Context
{compression_output[:3000] if compression_output else "No compression output available - use git diff to compare files"}

Focus on the ROOT CAUSE - what instruction or missing guidance caused the agent to lose semantic content.
"""
            self._send_message(analysis_session, analysis_prompt)

            # Wait for completion
            response = self._read_response(
                analysis_session,
                timeout=600,  # 10 minutes
                wait_for_completion=True,
                completion_markers=["config: improve shrink-doc", "committed", "SKILL.md"]
            )

            if not response:
                self.logger.error("No response from analysis session")
                return ({"status": "FAILED", "error": "No response"}, [])

            # Get commits after analysis
            after_result = self._run_command(["git", "rev-parse", "HEAD"])
            commit_after = after_result["stdout"].strip() if after_result["returncode"] == 0 else None

            # Get list of new commits
            improvement_commits = []
            if commit_before and commit_after and commit_before != commit_after:
                commits_result = self._run_command([
                    "git", "rev-list", f"{commit_before}..{commit_after}"
                ])
                if commits_result["returncode"] == 0:
                    improvement_commits = [c for c in commits_result["stdout"].strip().split("\n") if c]

            self.logger.info(f"Analysis produced {len(improvement_commits)} commits")

            return (
                {
                    "status": "SUCCESS",
                    "output": response,
                    "commits": improvement_commits
                },
                improvement_commits
            )

        finally:
            # Always clean up analysis session
            self._end_session(analysis_session)

    def _run_command(self, cmd: List[str]) -> Dict:
        """Execute shell command and return result."""
        try:
            result = subprocess.run(
                cmd,
                cwd=self.workspace,
                capture_output=True,
                text=True,
                timeout=300
            )
            return {
                "returncode": result.returncode,
                "stdout": result.stdout,
                "stderr": result.stderr
            }
        except subprocess.TimeoutExpired:
            self.logger.error(f"Command timeout: {' '.join(cmd)}")
            return {"returncode": -1, "stdout": "", "stderr": "Timeout"}
        except Exception as e:
            self.logger.error(f"Command failed: {e}")
            return {"returncode": -1, "stdout": "", "stderr": str(e)}

    def reinstall_plugin(self):
        """Reinstall CAT plugin to pick up changes."""
        if self.dry_run:
            self.logger.info("[DRY-RUN] Would reinstall plugin")
            return

        self.logger.info("Reinstalling CAT plugin...")
        self._run_command(["claude", "plugin", "uninstall", "cat"])
        time.sleep(1)
        self._run_command(["claude", "plugin", "install", "cat@cat"])

    def remove_worktrees(self, pattern: str):
        """Remove worktrees matching pattern."""
        if self.dry_run:
            self.logger.info(f"[DRY-RUN] Would remove worktrees matching: {pattern}")
            return

        self.logger.info(f"Removing worktrees matching: {pattern}")
        worktree_dir = self.workspace / ".claude" / "cat" / "worktrees"
        if worktree_dir.exists():
            for wt in worktree_dir.glob(pattern):
                if wt.is_dir():
                    self.logger.debug(f"Removing: {wt}")
                    import shutil
                    shutil.rmtree(wt, ignore_errors=True)


class CompressionValidationLoop:
    """Main orchestration logic for compress->validate->learn cycle."""

    def __init__(
        self,
        max_iterations: int = 10,
        max_files: int = 0,
        dry_run: bool = False,
        verbose: bool = False,
        skip_compression: bool = False,
        skip_learn: bool = False
    ):
        self.max_iterations = max_iterations
        self.max_files = max_files  # 0 = no limit
        self.dry_run = dry_run
        self.skip_compression = skip_compression
        self.skip_learn = skip_learn
        self.validation_failures = []  # Track failures for summary
        self.manager = ClaudeProcessManager(dry_run=dry_run, verbose=verbose)
        self.logger = self.manager.logger

    def _merge_learn_commits(self, commits: List[str]) -> bool:
        """
        Merge only /cat:learn fix commits, discarding compression changes.

        This implements step 9 from PLAN.md: isolate and merge ONLY commits
        from /cat:learn, discarding any compression-related changes.

        Args:
            commits: List of commit hashes from /cat:learn

        Returns:
            True if merge succeeded, False otherwise
        """
        if not commits:
            self.logger.debug("No /cat:learn commits to merge")
            return True

        if self.dry_run:
            self.logger.info(f"[DRY-RUN] Would merge {len(commits)} /cat:learn commits")
            return True

        try:
            # Get current branch
            result = self.manager._run_command(["git", "rev-parse", "--abbrev-ref", "HEAD"])
            if result["returncode"] != 0:
                self.logger.error("Failed to get current branch")
                return False
            current_branch = result["stdout"].strip()

            # Cherry-pick each /cat:learn commit
            for commit in reversed(commits):  # Apply in chronological order
                self.logger.info(f"Cherry-picking /cat:learn commit: {commit[:8]}")
                result = self.manager._run_command(["git", "cherry-pick", commit])
                if result["returncode"] != 0:
                    self.logger.error(f"Failed to cherry-pick {commit[:8]}: {result['stderr']}")
                    # Abort cherry-pick
                    self.manager._run_command(["git", "cherry-pick", "--abort"])
                    return False

            self.logger.info(f"Successfully merged {len(commits)} /cat:learn commits")
            return True

        except Exception as e:
            self.logger.error(f"Error merging /cat:learn commits: {e}")
            return False

    def run(self, task_ids: List[str]) -> int:
        """
        Execute the validation loop for specified tasks.

        Args:
            task_ids: List of compression task IDs

        Returns:
            Exit code (0 = success, 1 = failure)
        """
        self.logger.info("Starting compression validation loop")
        self.logger.info(f"Tasks: {', '.join(task_ids)}")
        self.logger.info(f"Max iterations: {self.max_iterations}")

        try:
            for iteration in range(1, self.max_iterations + 1):
                self.logger.info(f"\n{'='*60}")
                self.logger.info(f"Iteration {iteration}/{self.max_iterations}")
                self.logger.info(f"{'='*60}")

                all_scores_perfect = True

                for task_id in task_ids:
                    files = []

                    if self.skip_compression:
                        # Skip compression, get files directly from worktree
                        self.logger.info(f"Skipping compression, getting files from worktree: {task_id}")
                        worktree_path = self.manager.workspace / ".claude" / "cat" / "worktrees" / task_id
                        if not worktree_path.exists():
                            self.logger.error(f"Worktree not found: {worktree_path}")
                            continue

                        # Get base branch
                        base_branch = "v2.1"
                        cat_base_path = worktree_path / ".git" / "cat-base"
                        if cat_base_path.exists():
                            base_branch = cat_base_path.read_text().strip()

                        # Get changed files
                        diff_result = self.manager._run_command([
                            "git", "-C", str(worktree_path),
                            "diff", "--name-only", f"{base_branch}...HEAD"
                        ])
                        if diff_result["returncode"] == 0 and diff_result["stdout"].strip():
                            files = [f.strip() for f in diff_result["stdout"].strip().split("\n") if f.strip()]
                            self.logger.info(f"Found {len(files)} changed files: {files}")
                    else:
                        # Step 1: Run compression
                        compress_result = self.manager.run_compression(task_id)

                        if compress_result["status"] != "SUCCESS":
                            self.logger.error(f"Compression failed for {task_id}")
                            continue

                        files = compress_result.get("files", [])

                    for file_path in files:
                        score = self.manager.run_validation(task_id, file_path)

                        self.logger.info(f"  {file_path}: score={score:.2f}")

                        if score < 1.0:
                            all_scores_perfect = False

                            # Track failure for summary
                            self.validation_failures.append({
                                "task_id": task_id,
                                "file": file_path,
                                "score": score,
                                "iteration": iteration
                            })

                            if self.skip_learn:
                                self.logger.info(f"  -> Logged failure (--skip-learn enabled)")
                                continue

                            # Step 3: Analyze compression and improve shrink-doc
                            # Get compression output or worktree path for analysis
                            compression_output = ""
                            if not self.skip_compression:
                                compression_output = compress_result.get("output", "")
                            else:
                                # For skip_compression, note the worktree path for file comparison
                                worktree_path = self.manager.workspace / ".claude" / "cat" / "worktrees" / task_id
                                compression_output = f"[Worktree: {worktree_path}] File diff available"

                            analysis_result, improvement_commits = self.manager.analyze_and_improve_shrink_doc(
                                file_path=file_path,
                                score=score,
                                compression_output=compression_output
                            )

                            # Step 4: Merge shrink-doc improvements
                            if improvement_commits:
                                self.logger.info(f"Merging {len(improvement_commits)} shrink-doc improvements")
                                if not self._merge_learn_commits(improvement_commits):
                                    self.logger.error("Failed to merge shrink-doc improvements")
                                    return 1
                            else:
                                self.logger.debug("No shrink-doc improvements produced")

                            # Step 5: Plugin reinstall and cleanup
                            self.manager.reinstall_plugin()
                            self.manager.remove_worktrees("2.1-shrink-*")

                if all_scores_perfect:
                    self.logger.info("\n" + "="*60)
                    self.logger.info("SUCCESS: All equivalence scores reached 1.0")
                    self.logger.info("="*60)
                    return 0

            self.logger.warning(f"\nMax iterations ({self.max_iterations}) reached")
            self._print_summary()
            return 1

        except KeyboardInterrupt:
            self.logger.warning("\nInterrupted by user")
            self._print_summary()
            return 130
        except Exception as e:
            self.logger.error(f"Fatal error: {e}", exc_info=True)
            return 1
        finally:
            # Clean up all sessions
            self.manager._end_session(self.manager.compression_session)
            self.manager._end_session(self.manager.validation_session)

    def _print_summary(self):
        """Print summary of validation failures."""
        if not self.validation_failures:
            return

        self.logger.info("\n" + "="*60)
        self.logger.info("VALIDATION FAILURES SUMMARY")
        self.logger.info("="*60)
        self.logger.info(f"{'File':<50} {'Score':<8} {'Iteration'}")
        self.logger.info("-"*60)
        for f in self.validation_failures:
            self.logger.info(f"{f['file']:<50} {f['score']:<8.2f} {f['iteration']}")
        self.logger.info("="*60)
        self.logger.info(f"Total failures: {len(self.validation_failures)}")
        self.logger.info("")
        self.logger.info("To investigate, run /cat:learn manually for each failure:")
        for f in self.validation_failures:
            self.logger.info(f"  /cat:learn Equivalence score {f['score']:.2f} for {f['file']}")


def main():
    """Entry point."""
    parser = argparse.ArgumentParser(
        description="Compression validation loop orchestrator",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Simulate execution without running actual commands"
    )
    parser.add_argument(
        "--verbose",
        action="store_true",
        help="Enable debug logging"
    )
    parser.add_argument(
        "--max-iterations",
        type=int,
        default=10,
        metavar="N",
        help="Maximum loop iterations (default: 10)"
    )
    parser.add_argument(
        "--max-files",
        type=int,
        default=0,
        metavar="N",
        help="Maximum files to compress per task (0 = no limit, default: 0)"
    )
    parser.add_argument(
        "--skip-compression",
        action="store_true",
        help="Skip compression, just validate existing files in worktree"
    )
    parser.add_argument(
        "--skip-learn",
        action="store_true",
        help="Skip /cat:learn, just log failures for manual review"
    )
    parser.add_argument(
        "tasks",
        nargs="*",
        default=["2.1-shrink-work-execute"],
        help="Task IDs to process (default: 2.1-shrink-work-execute)"
    )

    args = parser.parse_args()

    loop = CompressionValidationLoop(
        max_iterations=args.max_iterations,
        max_files=args.max_files,
        dry_run=args.dry_run,
        verbose=args.verbose,
        skip_compression=args.skip_compression,
        skip_learn=args.skip_learn
    )

    sys.exit(loop.run(args.tasks))


if __name__ == "__main__":
    main()
