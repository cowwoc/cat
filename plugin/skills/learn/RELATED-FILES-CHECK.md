<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Check Related Files for Similar Mistakes (M341)

Lazy-loaded after fixing a file to check if similar files have the same vulnerability.

## When to Use

After implementing a fix, check if similar files have the same vulnerability.
Fixing one file while leaving identical vulnerabilities in similar files means the same mistake WILL recur.

## Workflow

### 1. Identify the Pattern Fixed

```yaml
pattern_fixed:
  file_type: "skill"  # skill, hook, handler, config, etc.
  vulnerability: "weak copy-paste instruction for script output content"
  fix_applied: "added prominent MANDATORY OUTPUT REQUIREMENT header"
```

### 2. Find Related Files

```bash
# Examples by file type:

# Skills with script output content
grep -l '!\`' plugin/skills/*/SKILL.md

# Handlers with similar validation
find plugin/hooks -name "*.py" -exec grep -l "similar_pattern" {} \;

# Config files with same structure
find . -name "cat-config.json"
```

### 3. Check Each Related File

- Read the file
- Determine if it has the same weakness
- If yes: apply the same fix pattern

### 4. Record Related Fixes

```yaml
related_files_checked:
  - path: "plugin/skills/help/SKILL.md"
    had_vulnerability: true
    fixed: true
  - path: "plugin/skills/work/SKILL.md"
    had_vulnerability: true
    fixed: true
  - path: "plugin/skills/init/SKILL.md"
    had_vulnerability: false
    reason: "uses named box references instead of direct copy-paste"
```

## Skip This Step Only When

- The fix is truly unique to one file (e.g., typo fix)
- No similar files exist (verified, not assumed)
