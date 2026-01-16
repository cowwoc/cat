"""
Base utilities for skill handlers.
"""

import subprocess
from pathlib import Path


def run_script(script_path: str | Path, *args, env: dict | None = None, **kwargs) -> str | None:
    """
    Run a script and return its stdout, or None on failure.

    Args:
        script_path: Path to the script to run
        *args: Arguments to pass to the script
        env: Environment variables (merged with os.environ)
        **kwargs: Additional arguments for subprocess.run

    Returns:
        stdout as string, or None if script failed or doesn't exist
    """
    import os

    script_path = Path(script_path)
    if not script_path.exists():
        return None

    # Merge environment
    run_env = os.environ.copy()
    if env:
        run_env.update(env)

    try:
        result = subprocess.run(
            [str(script_path)] + list(args),
            capture_output=True,
            text=True,
            timeout=30,
            env=run_env,
            **kwargs
        )
        if result.returncode == 0 and result.stdout.strip():
            return result.stdout.strip()
        return None
    except (subprocess.TimeoutExpired, subprocess.SubprocessError):
        return None


def run_python_script(script_path: str | Path, env: dict | None = None) -> str | None:
    """
    Run a Python script and return its stdout.

    Args:
        script_path: Path to the Python script
        env: Environment variables (merged with os.environ)

    Returns:
        stdout as string, or None if script failed
    """
    import os
    import sys

    script_path = Path(script_path)
    if not script_path.exists():
        return None

    run_env = os.environ.copy()
    if env:
        run_env.update(env)

    try:
        result = subprocess.run(
            [sys.executable, str(script_path)],
            capture_output=True,
            text=True,
            timeout=30,
            env=run_env,
        )
        if result.returncode == 0 and result.stdout.strip():
            return result.stdout.strip()
        return None
    except (subprocess.TimeoutExpired, subprocess.SubprocessError):
        return None
