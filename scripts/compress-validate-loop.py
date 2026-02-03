#!/usr/bin/env python3
"""
Compression Validation Loop Orchestrator - CLI wrapper

This is a thin wrapper that imports and runs the main module.
Use compress_validate_loop.py for the actual implementation.
"""

import sys
from pathlib import Path

# Add scripts directory to path
SCRIPT_DIR = Path(__file__).parent
sys.path.insert(0, str(SCRIPT_DIR))

from compress_validate_loop import main

if __name__ == "__main__":
    main()
