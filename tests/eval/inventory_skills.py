#!/usr/bin/env python3
"""
Inventory all user-invocable CAT skills from SKILL.md files.
Extracts skill names and descriptions for eval test case generation.
"""

import json
import re
import yaml
from pathlib import Path
from typing import List, Dict, Any


def parse_yaml_frontmatter(content: str) -> Dict[str, Any]:
    """
    Extract YAML frontmatter from markdown file.

    Args:
        content: Markdown file content with YAML frontmatter

    Returns:
        Dictionary of parsed YAML frontmatter fields
    """
    match = re.match(r'^---\n(.*?)\n---', content, re.DOTALL)
    if not match:
        return {}

    yaml_content = match.group(1)

    try:
        # Use PyYAML for robust parsing
        result = yaml.safe_load(yaml_content)
        return result if isinstance(result, dict) else {}
    except yaml.YAMLError as e:
        print(f"WARNING: Failed to parse YAML frontmatter: {e}")
        return {}


def inventory_skills(plugin_root: Path) -> List[Dict[str, str]]:
    """Find all user-invocable skills in plugin/skills/*/SKILL.md."""
    skills = []
    skills_dir = plugin_root / 'plugin' / 'skills'

    if not skills_dir.exists():
        raise FileNotFoundError(f"Skills directory not found: {skills_dir}")

    for skill_dir in sorted(skills_dir.iterdir()):
        if not skill_dir.is_dir():
            continue

        skill_file = skill_dir / 'SKILL.md'
        if not skill_file.exists():
            continue

        content = skill_file.read_text()
        frontmatter = parse_yaml_frontmatter(content)

        # Skip if explicitly marked as not user-invocable
        if frontmatter.get('user-invocable') == False:
            continue

        # Extract skill info
        skill_name = skill_dir.name
        description = frontmatter.get('description', '')
        argument_hint = frontmatter.get('argument-hint', '')

        skills.append({
            'name': skill_name,
            'description': description,
            'argument_hint': argument_hint,
            'path': str(skill_file)
        })

    return skills


def main():
    """Generate skill inventory JSON."""
    # Find plugin root (go up from tests/eval to workspace)
    script_dir = Path(__file__).parent
    workspace_root = script_dir.parent.parent

    skills = inventory_skills(workspace_root)

    output = {
        'total_skills': len(skills),
        'skills': skills
    }

    output_file = script_dir / 'skill_inventory.json'
    output_file.write_text(json.dumps(output, indent=2))

    print(f"Inventoried {len(skills)} user-invocable skills")
    print(f"Output: {output_file}")

    # Print summary
    for skill in skills:
        print(f"  - {skill['name']}: {skill['description']}")


if __name__ == '__main__':
    main()
