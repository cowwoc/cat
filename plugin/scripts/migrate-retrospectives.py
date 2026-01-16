#!/usr/bin/env python3
"""
Migration script for retrospective files.

Splits existing mistakes.json and retrospectives.json into time-based files:
- mistakes-YYYY-MM.json for mistakes by month
- retrospectives-YYYY-MM.json for retrospectives by month
- index.json for centralized config and file tracking

Usage:
    python3 migrate-retrospectives.py [--dry-run] [PROJECT_DIR]

Arguments:
    PROJECT_DIR     Path to project root (default: current directory)
    --dry-run       Show what would be done without making changes
"""

import json
import os
import sys
from collections import defaultdict
from datetime import datetime
from pathlib import Path


def get_year_month(timestamp: str) -> str:
    """Extract YYYY-MM from ISO timestamp."""
    # Handle various timestamp formats
    for fmt in [
        "%Y-%m-%dT%H:%M:%SZ",
        "%Y-%m-%dT%H:%M:%S%z",
        "%Y-%m-%dT%H:%M:%S.%fZ",
        "%Y-%m-%dT%H:%M:%S.%f%z",
    ]:
        try:
            dt = datetime.strptime(timestamp.replace("+00:00", "Z"), fmt)
            return dt.strftime("%Y-%m")
        except ValueError:
            continue

    # Fallback: try parsing just the date portion
    try:
        date_part = timestamp[:10]
        dt = datetime.strptime(date_part, "%Y-%m-%d")
        return dt.strftime("%Y-%m")
    except ValueError:
        raise ValueError(f"Cannot parse timestamp: {timestamp}")


def migrate_retrospectives(project_dir: str, dry_run: bool = False) -> dict:
    """
    Migrate retrospective files to time-based split format.

    Returns a dict with migration stats.
    """
    retro_dir = Path(project_dir) / ".claude" / "cat" / "retrospectives"

    # Check if directory exists
    if not retro_dir.exists():
        print(f"No retrospectives directory found at {retro_dir}")
        return {"status": "skipped", "reason": "directory not found"}

    mistakes_file = retro_dir / "mistakes.json"
    retro_file = retro_dir / "retrospectives.json"
    index_file = retro_dir / "index.json"

    # Check if already migrated
    if index_file.exists():
        print(f"Migration already complete: {index_file} exists")
        return {"status": "skipped", "reason": "already migrated"}

    stats = {
        "mistakes_total": 0,
        "mistakes_by_period": {},
        "retrospectives_total": 0,
        "retrospectives_by_period": {},
        "files_created": [],
    }

    # Load existing data
    mistakes_data = {"mistakes": []}
    retro_data = {
        "last_retrospective": None,
        "mistake_count_since_last": 0,
        "config": {
            "mistake_count_threshold": 5,
            "trigger_interval_days": 7,
        },
    }

    if mistakes_file.exists():
        with open(mistakes_file, "r") as f:
            mistakes_data = json.load(f)

    if retro_file.exists():
        with open(retro_file, "r") as f:
            retro_data = json.load(f)

    # Group mistakes by year-month
    mistakes_by_period = defaultdict(list)
    for mistake in mistakes_data.get("mistakes", []):
        ts = mistake.get("timestamp")
        if ts:
            period = get_year_month(ts)
            mistakes_by_period[period].append(mistake)

    stats["mistakes_total"] = len(mistakes_data.get("mistakes", []))
    stats["mistakes_by_period"] = {k: len(v) for k, v in mistakes_by_period.items()}

    # Group retrospectives by year-month (if any retrospective records exist)
    retro_by_period = defaultdict(list)
    for retro in retro_data.get("retrospectives", []):
        ts = retro.get("timestamp")
        if ts:
            period = get_year_month(ts)
            retro_by_period[period].append(retro)

    stats["retrospectives_total"] = len(retro_data.get("retrospectives", []))
    stats["retrospectives_by_period"] = {k: len(v) for k, v in retro_by_period.items()}

    # Build index.json
    index_data = {
        "version": "2.0",
        "config": retro_data.get("config", {
            "mistake_count_threshold": 5,
            "trigger_interval_days": 7,
        }),
        "last_retrospective": retro_data.get("last_retrospective"),
        "mistake_count_since_last": retro_data.get("mistake_count_since_last", 0),
        "files": {
            "mistakes": sorted([f"mistakes-{p}.json" for p in mistakes_by_period.keys()]),
            "retrospectives": sorted([f"retrospectives-{p}.json" for p in retro_by_period.keys()]),
        },
    }

    print(f"\n{'DRY RUN - ' if dry_run else ''}Migration Summary:")
    print(f"  Mistakes: {stats['mistakes_total']} total")
    for period, count in sorted(stats["mistakes_by_period"].items()):
        print(f"    {period}: {count} mistakes")

    print(f"  Retrospectives: {stats['retrospectives_total']} total")
    for period, count in sorted(stats["retrospectives_by_period"].items()):
        print(f"    {period}: {count} retrospectives")

    if dry_run:
        print("\nDry run complete. No files were modified.")
        return stats

    # Write split files
    for period, mistakes in mistakes_by_period.items():
        split_file = retro_dir / f"mistakes-{period}.json"
        split_data = {
            "period": period,
            "mistakes": sorted(mistakes, key=lambda m: m.get("timestamp", "")),
        }
        with open(split_file, "w") as f:
            json.dump(split_data, f, indent=2)
        stats["files_created"].append(str(split_file))
        print(f"  Created: {split_file.name}")

    for period, retros in retro_by_period.items():
        split_file = retro_dir / f"retrospectives-{period}.json"
        split_data = {
            "period": period,
            "retrospectives": sorted(retros, key=lambda r: r.get("timestamp", "")),
        }
        with open(split_file, "w") as f:
            json.dump(split_data, f, indent=2)
        stats["files_created"].append(str(split_file))
        print(f"  Created: {split_file.name}")

    # Write index.json
    with open(index_file, "w") as f:
        json.dump(index_data, f, indent=2)
    stats["files_created"].append(str(index_file))
    print(f"  Created: {index_file.name}")

    # Verify migration
    print("\nVerifying migration...")

    # Count mistakes in split files
    total_in_splits = 0
    for mistakes_split in retro_dir.glob("mistakes-*.json"):
        with open(mistakes_split, "r") as f:
            split_data = json.load(f)
            total_in_splits += len(split_data.get("mistakes", []))

    if total_in_splits != stats["mistakes_total"]:
        print(f"  ERROR: Mistake count mismatch!")
        print(f"    Original: {stats['mistakes_total']}")
        print(f"    In splits: {total_in_splits}")
        return {"status": "error", "reason": "count mismatch", **stats}

    print(f"  Verified: {total_in_splits} mistakes preserved correctly")

    # Rename original files to .backup
    if mistakes_file.exists():
        backup_file = retro_dir / "mistakes.json.backup"
        os.rename(mistakes_file, backup_file)
        print(f"  Backed up: mistakes.json -> mistakes.json.backup")

    if retro_file.exists():
        backup_file = retro_dir / "retrospectives.json.backup"
        os.rename(retro_file, backup_file)
        print(f"  Backed up: retrospectives.json -> retrospectives.json.backup")

    print("\nMigration complete!")
    stats["status"] = "success"
    return stats


def main():
    dry_run = "--dry-run" in sys.argv
    args = [a for a in sys.argv[1:] if not a.startswith("--")]

    project_dir = args[0] if args else os.getcwd()

    print(f"Migrating retrospective files in: {project_dir}")
    result = migrate_retrospectives(project_dir, dry_run=dry_run)

    if result.get("status") == "error":
        sys.exit(1)

    return result


if __name__ == "__main__":
    main()
