"""
Unified configuration loader for CAT plugin.

Implements three-layer config loading:
1. Defaults (hardcoded)
2. cat-config.json (project settings)
3. cat-config.local.json (user overrides, gitignored)
"""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any


# Default configuration values
DEFAULTS: dict[str, Any] = {
    "autoRemoveWorktrees": True,
    "trust": "medium",
    "verify": "changed",
    "curiosity": "low",
    "patience": "high",
    "terminalWidth": 120,
    "completionWorkflow": "merge"
}


def load_config(project_dir: str | Path = '.') -> dict[str, Any]:
    """Load configuration with three-layer override.

    Loading order (later overrides earlier):
    1. Default values
    2. cat-config.json (project settings)
    3. cat-config.local.json (user overrides)

    Args:
        project_dir: Project root directory containing .claude/cat/

    Returns:
        Merged configuration dictionary
    """
    project_path = Path(project_dir)
    config_dir = project_path / '.claude' / 'cat'

    # Start with defaults
    config = DEFAULTS.copy()

    # Layer 2: Load cat-config.json
    base_config_path = config_dir / 'cat-config.json'
    if base_config_path.exists():
        try:
            with open(base_config_path) as f:
                base_config = json.load(f)
                config.update(base_config)
        except (json.JSONDecodeError, IOError):
            # Invalid or unreadable base config - continue with defaults
            pass

    # Layer 3: Load cat-config.local.json (overrides base)
    local_config_path = config_dir / 'cat-config.local.json'
    if local_config_path.exists():
        try:
            with open(local_config_path) as f:
                local_config = json.load(f)
                config.update(local_config)
        except (json.JSONDecodeError, IOError):
            # Invalid or unreadable local config - continue with base
            pass

    return config


def get_config_value(key: str, project_dir: str | Path = '.', default: Any = None) -> Any:
    """Get a single configuration value.

    Args:
        key: Configuration key to retrieve
        project_dir: Project root directory
        default: Default value if key not found

    Returns:
        Configuration value or default
    """
    config = load_config(project_dir)
    return config.get(key, default)
