#!/usr/bin/env python3
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
"""
License validation for CAT.
Verifies Ed25519-signed JWT tokens and extracts tier information.
"""
from __future__ import annotations

import json
import base64
import sys
from pathlib import Path
from datetime import datetime, timezone
from typing import TYPE_CHECKING, Any

# Try to import cryptography, fail gracefully if not available
try:
    from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PublicKey
    from cryptography.hazmat.primitives.serialization import load_pem_public_key
    from cryptography.exceptions import InvalidSignature
    CRYPTO_AVAILABLE = True
except ImportError:
    CRYPTO_AVAILABLE = False
    if TYPE_CHECKING:
        from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PublicKey


def base64url_decode(data: str) -> bytes:
    """Decode base64url without padding."""
    padding = 4 - len(data) % 4
    if padding != 4:
        data += '=' * padding
    return base64.urlsafe_b64decode(data)


def load_public_key(key_path: Path) -> Any:
    """Load Ed25519 public key from PEM file."""
    with open(key_path, 'rb') as f:
        return load_pem_public_key(f.read())


def validate_token(token: str, public_key: Any) -> dict:
    """
    Validate JWT token and return payload.

    Returns dict with:
    - valid: bool - whether signature is valid
    - tier: str - license tier (or "indie" if invalid)
    - expired: bool - whether token is past expiration
    - inGrace: bool - whether in grace period
    - daysRemaining: int - days until expiration (negative if expired)
    - error: str | None - error message if any
    """
    result = {
        "valid": False,
        "tier": "indie",
        "expired": False,
        "inGrace": False,
        "daysRemaining": 0,
        "error": None
    }

    try:
        # Split JWT
        parts = token.split('.')
        if len(parts) != 3:
            result["error"] = "Invalid token format"
            return result

        header_b64, payload_b64, signature_b64 = parts

        # Verify signature
        message = f"{header_b64}.{payload_b64}".encode('utf-8')
        signature = base64url_decode(signature_b64)

        try:
            public_key.verify(signature, message)
        except InvalidSignature:
            result["error"] = "Invalid signature"
            return result

        # Decode payload
        payload_json = base64url_decode(payload_b64).decode('utf-8')
        payload = json.loads(payload_json)

        # Extract fields
        tier = payload.get("tier", "indie")
        exp = payload.get("exp")  # Unix timestamp
        grace_days = payload.get("grace_days", payload.get("graceDays", 7))

        result["valid"] = True
        result["tier"] = tier

        # Check expiration
        if exp:
            exp_dt = datetime.fromtimestamp(exp, tz=timezone.utc)
            now = datetime.now(timezone.utc)
            delta = exp_dt - now
            result["daysRemaining"] = delta.days

            if now > exp_dt:
                result["expired"] = True
                # Check grace period
                grace_delta = (now - exp_dt).days
                if grace_delta <= grace_days:
                    result["inGrace"] = True
                else:
                    # Past grace period - fall back to indie
                    result["tier"] = "indie"

        return result

    except Exception as e:
        result["error"] = str(e)
        return result


def find_config_file() -> Path | None:
    """Find cat-config.local.json in project directory."""
    # Check current directory and parents for .claude/cat/
    current = Path.cwd()
    for parent in [current] + list(current.parents):
        config_path = parent / ".claude" / "cat" / "cat-config.local.json"
        if config_path.exists():
            return config_path
    return None


def find_public_key() -> Path | None:
    """Find public key file."""
    # Check plugin config directory
    script_dir = Path(__file__).parent
    key_path = script_dir.parent / "config" / "cat-public-key.pem"
    if key_path.exists():
        return key_path

    # Check CLAUDE_PLUGIN_ROOT
    import os
    plugin_root = os.environ.get("CLAUDE_PLUGIN_ROOT")
    if plugin_root:
        key_path = Path(plugin_root) / "config" / "cat-public-key.pem"
        if key_path.exists():
            return key_path

    return None


def main():
    """Main entry point."""
    result = {
        "valid": False,
        "tier": "indie",
        "expired": False,
        "inGrace": False,
        "daysRemaining": 0,
        "error": None,
        "warning": None
    }

    # Check if cryptography is available
    if not CRYPTO_AVAILABLE:
        result["error"] = "cryptography library not available"
        result["warning"] = "Install with: pip install cryptography"
        print(json.dumps(result))
        return 1

    # Find config file
    config_path = find_config_file()
    if not config_path:
        # No config = free tier, not an error
        print(json.dumps(result))
        return 0

    # Read config
    try:
        with open(config_path) as f:
            config = json.load(f)
    except Exception as e:
        result["error"] = f"Failed to read config: {e}"
        print(json.dumps(result))
        return 1

    # Get token
    token = config.get("license")
    if not token:
        # No license = free tier
        print(json.dumps(result))
        return 0

    # Find public key
    key_path = find_public_key()
    if not key_path:
        result["error"] = "Public key not found"
        print(json.dumps(result))
        return 1

    # Load public key and validate
    try:
        public_key = load_public_key(key_path)
        result = validate_token(token, public_key)
    except Exception as e:
        result["error"] = f"Validation failed: {e}"

    # Add warning for grace period
    if result["inGrace"]:
        days_past = abs(result["daysRemaining"])
        result["warning"] = f"License expired {days_past} days ago. Renew soon."
    elif result["expired"] and not result["inGrace"]:
        result["warning"] = "License expired. Run /cat:login to renew."

    print(json.dumps(result))
    return 0 if result["valid"] else 1


if __name__ == "__main__":
    sys.exit(main())
