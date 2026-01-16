#!/usr/bin/env python3
"""
invoke-handler.py - Direct handler invocation for testing and CLI use.

Usage:
    echo '{"handler": "cleanup", "context": {...}}' | python3 invoke-handler.py
    python3 invoke-handler.py '{"handler": "cleanup", "context": {...}}'

This script properly sets up the Python path to allow importing handlers
that use relative imports.
"""

import json
import sys
from pathlib import Path

# Add this directory to path BEFORE importing handlers
SCRIPT_DIR = Path(__file__).parent
sys.path.insert(0, str(SCRIPT_DIR))

from skill_handlers import get_handler


def main():
    # Read input from argument or stdin
    if len(sys.argv) > 1:
        data = json.loads(sys.argv[1])
    elif not sys.stdin.isatty():
        data = json.loads(sys.stdin.read())
    else:
        print("Usage: invoke-handler.py '<json>' or pipe JSON to stdin", file=sys.stderr)
        sys.exit(1)

    handler_name = data.get("handler")
    context = data.get("context", {})

    if not handler_name:
        print("Error: 'handler' key required in input", file=sys.stderr)
        sys.exit(1)

    handler = get_handler(handler_name)
    if not handler:
        print(f"Error: No handler registered for '{handler_name}'", file=sys.stderr)
        sys.exit(1)

    result = handler.handle(context)
    if result:
        print(result)


if __name__ == "__main__":
    main()
