"""
Handler for /cat:run-retrospective precomputation.

Gathers retrospective data and pre-computes formatted analysis output.
"""

import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from . import register_handler


def _load_json_file(path: Path) -> dict | None:
    """Load JSON file, return None on failure."""
    if not path.exists():
        return None
    try:
        return json.loads(path.read_text())
    except (json.JSONDecodeError, OSError):
        return None


def _parse_datetime(dt_str: str | None) -> datetime | None:
    """Parse ISO datetime string to datetime object, normalized to UTC.

    Rejects naive datetimes (no timezone info) per fail-fast principle.
    """
    if not dt_str or dt_str == "null":
        return None
    try:
        if dt_str.endswith('Z'):
            dt_str = dt_str[:-1] + '+00:00'
        dt = datetime.fromisoformat(dt_str)
        if dt.tzinfo is None:
            import sys
            print(f"WARNING: Naive datetime encountered: '{dt_str}'. Timestamps should include timezone. Skipping.", file=sys.stderr)
            return None
        return dt.astimezone(timezone.utc)
    except ValueError:
        return None


def _days_since(dt: datetime | None) -> int:
    """Calculate days since given datetime."""
    if dt is None:
        return 999  # Large number to trigger threshold
    now = datetime.now(timezone.utc)
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    delta = now - dt
    return delta.days


class RunRetrospectiveHandler:
    """Handler for /cat:run-retrospective skill."""

    def handle(self, context: dict) -> str | None:
        """Gather retrospective data and return formatted output."""
        project_root = context.get("project_root")
        if not project_root:
            return None

        retro_dir = Path(project_root) / ".claude" / "cat" / "retrospectives"
        index_file = retro_dir / "index.json"

        if not index_file.exists():
            return self._no_retrospectives_message()

        index = _load_json_file(index_file)
        if not index:
            return self._error_message("Failed to read index.json")

        # Extract config and state
        config = index.get("config", {})
        interval_days = config.get("trigger_interval_days", 14)
        threshold = config.get("mistake_count_threshold", 10)
        last_retro_str = index.get("last_retrospective")
        mistakes_since = index.get("mistake_count_since_last", 0)

        # Calculate days since last retrospective
        last_retro = _parse_datetime(last_retro_str)
        days_since = _days_since(last_retro)

        # Check trigger conditions
        time_triggered = days_since >= interval_days
        count_triggered = mistakes_since >= threshold
        triggered = time_triggered or count_triggered

        if not triggered:
            return self._not_triggered_message(
                days_since, interval_days, mistakes_since, threshold
            )

        # Gather mistakes for analysis
        mistakes = self._gather_mistakes(retro_dir, last_retro_str)

        # Build analysis output
        return self._build_analysis_output(
            index=index,
            mistakes=mistakes,
            days_since=days_since,
            interval_days=interval_days,
            mistakes_since=mistakes_since,
            threshold=threshold,
            time_triggered=time_triggered,
            count_triggered=count_triggered,
            last_retro_str=last_retro_str,
            retro_dir=retro_dir
        )

    def _gather_mistakes(self, retro_dir: Path, since: str | None) -> list[dict]:
        """Gather all mistakes since last retrospective."""
        all_mistakes = []

        # Read all mistake split files
        for mistake_file in retro_dir.glob("mistakes-*.json"):
            data = _load_json_file(mistake_file)
            if data and "mistakes" in data:
                all_mistakes.extend(data["mistakes"])

        if not since or since == "null":
            return all_mistakes

        # Filter to mistakes after last retrospective
        since_dt = _parse_datetime(since)
        if since_dt is None:
            return all_mistakes

        filtered = []
        for m in all_mistakes:
            m_dt = _parse_datetime(m.get("timestamp"))
            if m_dt and m_dt > since_dt:
                filtered.append(m)

        return filtered

    def _category_breakdown(self, mistakes: list[dict]) -> dict[str, list[str]]:
        """Group mistakes by category."""
        categories: dict[str, list[str]] = {}
        for m in mistakes:
            cat = m.get("category", "unknown")
            mid = m.get("id", "?")
            if cat not in categories:
                categories[cat] = []
            categories[cat].append(mid)
        return categories

    def _check_action_effectiveness(self, index: dict, mistakes: list[dict]) -> list[dict]:
        """Check effectiveness of implemented action items."""
        results = []
        action_items = index.get("action_items", [])

        for action in action_items:
            if action.get("status") != "implemented":
                continue

            completed_str = action.get("completed_date")
            if not completed_str:
                continue

            completed_dt = _parse_datetime(completed_str)
            if completed_dt is None:
                continue

            pattern_id = action.get("pattern_id", "")
            action_id = action.get("id", "")

            # Count mistakes with this pattern after completion
            post_fix_count = 0
            post_fix_ids = []
            for m in mistakes:
                m_dt = _parse_datetime(m.get("timestamp"))
                if m_dt and m_dt > completed_dt:
                    keywords = m.get("pattern_keywords", [])
                    # Check if related to this pattern
                    if pattern_id.lower() in " ".join(keywords).lower():
                        post_fix_count += 1
                        post_fix_ids.append(m.get("id"))

            # Also check effectiveness_check from index
            eff = action.get("effectiveness_check", {})
            verdict = eff.get("verdict", "pending")

            results.append({
                "id": action_id,
                "pattern_id": pattern_id,
                "verdict": verdict,
                "post_fix_count": eff.get("mistakes_after", post_fix_count),
                "pre_fix_count": eff.get("mistakes_before", 0),
            })

        return results

    def _build_analysis_output(self, **kwargs) -> str:
        """Build the formatted analysis output."""
        index = kwargs["index"]
        mistakes = kwargs["mistakes"]
        days_since = kwargs["days_since"]
        interval_days = kwargs["interval_days"]
        mistakes_since = kwargs["mistakes_since"]
        threshold = kwargs["threshold"]
        time_triggered = kwargs["time_triggered"]
        count_triggered = kwargs["count_triggered"]
        last_retro_str = kwargs["last_retro_str"]

        trigger_reason = []
        if time_triggered:
            trigger_reason.append(f"time ({days_since} >= {interval_days} days)")
        if count_triggered:
            trigger_reason.append(f"count ({mistakes_since} >= {threshold})")

        # Category breakdown
        categories = self._category_breakdown(mistakes)
        cat_lines = []
        for cat, ids in sorted(categories.items(), key=lambda x: -len(x[1])):
            cat_lines.append(f"- {cat}: {len(ids)} ({', '.join(ids[:5])}{'...' if len(ids) > 5 else ''})")

        # Action effectiveness
        effectiveness = self._check_action_effectiveness(index, mistakes)
        eff_lines = []
        for e in effectiveness:
            if e["verdict"] == "effective":
                eff_lines.append(f"- {e['id']}: ✓ effective")
            elif e["verdict"] == "partially_effective":
                eff_lines.append(f"- {e['id']}: ⚠ partially effective ({e['post_fix_count']} post-fix)")
            elif e["verdict"] == "ineffective":
                eff_lines.append(f"- {e['id']}: ✗ ineffective → escalated")
            else:
                eff_lines.append(f"- {e['id']}: pending evaluation")

        # Patterns summary
        patterns = index.get("patterns", [])
        pattern_lines = []
        for p in patterns:
            status = p.get("status", "unknown")
            pid = p.get("pattern_id", "?")
            occurrences = p.get("occurrences_total", 0)
            pattern_lines.append(f"- {pid}: {status} ({occurrences} total)")

        # Open action items
        action_items = index.get("action_items", [])
        open_actions = [a for a in action_items if a.get("status") == "open"]
        open_lines = []
        for a in open_actions:
            priority = a.get("priority", "medium")
            desc = a.get("description", "")[:60]
            open_lines.append(f"- [{a['id']}] ({priority}): {desc}...")

        # Get next retrospective ID
        max_retro_num = 0
        for retro_file in kwargs["retro_dir"].glob("retrospectives-*.json"):
            data = _load_json_file(retro_file)
            if data and "retrospectives" in data:
                for r in data["retrospectives"]:
                    rid = r.get("id", "R000")
                    if rid.startswith("R"):
                        try:
                            num = int(rid[1:])
                            max_retro_num = max(max_retro_num, num)
                        except ValueError:
                            pass
        next_retro_id = f"R{max_retro_num + 1:03d}"

        now_str = datetime.now().strftime("%Y-%m-%d")
        period_start = last_retro_str[:10] if last_retro_str else "beginning"

        output = f"""SCRIPT OUTPUT RETROSPECTIVE ANALYSIS:

## 📊 Retrospective {next_retro_id} Analysis

**Trigger:** {' + '.join(trigger_reason)}
**Period:** {period_start} to {now_str}
**Mistakes to Analyze:** {len(mistakes)}

---

### Category Breakdown

{chr(10).join(cat_lines) if cat_lines else "No mistakes to analyze."}

---

### Action Item Effectiveness

{chr(10).join(eff_lines) if eff_lines else "No implemented action items to evaluate."}

---

### Pattern Status

{chr(10).join(pattern_lines[:10]) if pattern_lines else "No patterns identified."}
{f"... and {len(pattern_lines) - 10} more" if len(pattern_lines) > 10 else ""}

---

### Open Action Items

{chr(10).join(open_lines) if open_lines else "No open action items."}

---

### Next Steps

If proceeding with retrospective:
1. Review category breakdown for emerging patterns
2. Evaluate action item effectiveness
3. Derive new action items for unaddressed patterns
4. Create escalations for ineffective actions
5. Update index.json with retrospective record

**To execute:** Follow the SKILL.md workflow steps 5-9 using this analysis.

---

INSTRUCTION: Output the above analysis EXACTLY as shown.
Use this data to guide retrospective execution per SKILL.md workflow."""

        return output

    def _not_triggered_message(
        self, days_since: int, interval: int, count: int, threshold: int
    ) -> str:
        """Message when retrospective is not triggered."""
        return f"""SCRIPT OUTPUT RETROSPECTIVE STATUS:

## ℹ️ No Retrospective Needed

**Time trigger:** {days_since}/{interval} days
**Count trigger:** {count}/{threshold} mistakes

Neither threshold is met. Retrospective will trigger when:
- {interval - days_since} more days pass, OR
- {threshold - count} more mistakes are recorded

INSTRUCTION: Output this status. Do not run retrospective workflow."""

    def _no_retrospectives_message(self) -> str:
        """Message when retrospectives directory doesn't exist."""
        return """SCRIPT OUTPUT RETROSPECTIVE STATUS:

## ⚠️ No Retrospective Data

The `.claude/cat/retrospectives/` directory does not exist.

To initialize:
1. Create `.claude/cat/retrospectives/index.json` with config
2. Record mistakes via `/cat:learn`
3. Run `/cat:run-retrospective` when thresholds are met

INSTRUCTION: Output this status. Cannot run retrospective without data."""

    def _error_message(self, error: str) -> str:
        """Error message."""
        return f"""SCRIPT OUTPUT RETROSPECTIVE ERROR:

## ❌ Error

{error}

INSTRUCTION: Output this error. Cannot proceed with retrospective."""


# Register handler
_handler = RunRetrospectiveHandler()
register_handler("run-retrospective", _handler)
