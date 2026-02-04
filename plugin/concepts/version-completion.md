# Workflow: Version Completion

## When to Load

Load this workflow when **all issues in a minor version are completed** (no pending/in-progress).

## Minor Version Complete

### Check Minor Completion

```bash
# Count pending/in-progress issues in this minor version
PENDING_COUNT=$(find ".claude/cat/issues/v${MAJOR}/v${MAJOR}.${MINOR}/" -name "STATE.md" -exec grep -l 'Status.*pending\|Status.*in-progress' {} \; 2>/dev/null | wc -l)

if [[ "$PENDING_COUNT" -eq 0 ]]; then
  MINOR_COMPLETE=true
fi
```

### Check Requirements Satisfaction

**MANDATORY**: Before marking a version complete, verify all requirements are satisfied.

> **Note**: This check is implicit and always runs - it is not listed in the Exit gate section.
> Exit gates are for user-defined additional conditions (tests passing, manual sign-off, etc.).
> Requirements can be defined at any version level (major, minor, or patch).

> **See also:** [version-scheme.md](version-scheme.md) for versioning scheme details.

1. **Extract requirements from version PLAN.md**:
   - Read the version's PLAN.md (works for any level: major, minor, or patch)
   - Parse the Requirements table for all REQ-XXX IDs

2. **Collect satisfied requirements from all issues**:
   - For each completed issue in the minor version
   - Read the issue's PLAN.md and extract the `## Satisfies` section
   - Build a set of all satisfied requirement IDs

3. **Identify unsatisfied requirements**:
   ```
   unsatisfied = version_requirements - issue_satisfied_requirements
   ```

4. **Block completion if unsatisfied requirements exist**:
   - If `unsatisfied` is not empty, display:
     ```
     ⚠️ Cannot complete v{major}.{minor}: unsatisfied requirements

     The following requirements are not satisfied by any completed issue:
     - REQ-XXX: [requirement description]
     - REQ-YYY: [requirement description]

     Options:
     1. Add an issue to satisfy these requirements
     2. Remove requirements that are no longer needed
     3. Mark requirements as deferred (update PLAN.md)
     ```
   - Use AskUserQuestion to let user choose resolution path
   - Do NOT mark the version as complete until resolved

5. **Proceed if all requirements satisfied**:
   - All must-have requirements must be satisfied
   - should-have and nice-to-have may be deferred with explicit notation

### Verify and Finalize Version CHANGELOG

**MANDATORY**: Before marking a version complete, verify the version-level CHANGELOG.md is ready for release.

> **Purpose**: Version-level CHANGELOG.md contains user-facing release notes that get copied to the
> root CHANGELOG.md. This step ensures the content is complete and properly formatted.

1. **Check CHANGELOG.md exists**:

   ```bash
   VERSION_CHANGELOG=".claude/cat/issues/v${MAJOR}/v${MAJOR}.${MINOR}/CHANGELOG.md"
   if [[ ! -f "$VERSION_CHANGELOG" ]]; then
     echo "ERROR: Version CHANGELOG.md not found at $VERSION_CHANGELOG"
     echo "Create it using the template from plugin/templates/changelog.md"
     exit 1
   fi
   ```

2. **Verify CHANGELOG has user-facing content** (not just placeholder text):

   Check that the CHANGELOG contains actual feature descriptions, not just `*(To be filled)*`:

   ```bash
   if grep -q "To be filled" "$VERSION_CHANGELOG"; then
     echo "⚠️ Version CHANGELOG.md contains placeholder text"
     echo "Please update with actual user-facing release notes before completing version."
   fi
   ```

3. **Present CHANGELOG for review**:

   Display the version CHANGELOG content (excluding the Internal Reference section):

   ```bash
   # Show content up to the Internal Reference section
   sed '/^## Internal Reference/,$d' "$VERSION_CHANGELOG"
   ```

   Use AskUserQuestion:
   - header: "Changelog Review"
   - question: "Review the version CHANGELOG above. Is it ready for release?"
   - options:
     - "Looks good" - Proceed with version completion
     - "Edit CHANGELOG" - Make changes before proceeding
     - "View full file" - Show complete CHANGELOG including internal reference

   **If "Edit CHANGELOG":**
   - Open the file for editing: `$VERSION_CHANGELOG`
   - Return to this step after edits are saved

4. **Update Completed date**:

   ```bash
   TODAY=$(date +%Y-%m-%d)
   sed -i "s/\*\*Completed\*\*: (in progress)/\*\*Completed\*\*: $TODAY/" "$VERSION_CHANGELOG"
   sed -i "s/\*\*Completed\*\*: (pending)/\*\*Completed\*\*: $TODAY/" "$VERSION_CHANGELOG"
   ```

### Update Root CHANGELOG.md

**MANDATORY**: Copy the version's release notes to the root CHANGELOG.md.

1. **Extract user-facing content from version CHANGELOG**:

   ```bash
   # Extract content between title and Internal Reference section
   VERSION_CONTENT=$(sed -n '/^# Changelog:/,/^## Internal Reference/p' "$VERSION_CHANGELOG" | \
     sed '1d' | sed '/^## Internal Reference/,$d' | \
     sed '/^>/d')  # Remove the PURPOSE note
   ```

2. **Format for root CHANGELOG**:

   Transform the version CHANGELOG format to root CHANGELOG format:

   ```bash
   # Add date prefix and version header
   ROOT_ENTRY="### ${TODAY}: v${MAJOR}.${MINOR}

   ${VERSION_CONTENT}"
   ```

3. **Insert into root CHANGELOG.md**:

   Insert the new entry after the "## Version History" line:

   ```bash
   ROOT_CHANGELOG="CHANGELOG.md"

   # Find the line with "## Version History" and insert after it
   # Skip any existing "In Development" section for this version
   ```

   **Note**: If an "In Development" entry exists for this version, replace it with the completed entry.

4. **Commit CHANGELOG updates**:

   ```bash
   git add "$VERSION_CHANGELOG" "$ROOT_CHANGELOG"
   git commit -m "docs: update CHANGELOG for v${MAJOR}.${MINOR} release"
   ```

### Celebration and Review Prompt

Display completion celebration:

```
---

## Issue Complete

**{issue-name}** merged to main.

## 🎉 Minor Version v{major}.{minor} Complete!

All issues in this minor version are done.

---
```

### Stakeholder Review Option

Use AskUserQuestion:
- header: "Version Complete"
- question: "Would you like to run a stakeholder review on v{major}.{minor}?"
- options:
  - "Run stakeholder review" - Comprehensive multi-perspective quality review
  - "Skip review" - Continue to next version without review
  - "View status first" - Show /cat:status before deciding

**If "Run stakeholder review":**
Invoke `/cat:stakeholder-review .claude/cat/issues/v{major}/v{major}.{minor}`

**If "Skip review":**
Continue with next steps.

---

## Major Version Complete

### Check Major Completion

```bash
# Count incomplete minor versions in this major
INCOMPLETE_MINORS=$(find ".claude/cat/issues/v${MAJOR}" -maxdepth 1 -name "v${MAJOR}.*" -type d | while read dir; do
  [ -f "$dir/STATE.md" ] && ! grep -q 'Status.*completed' "$dir/STATE.md" && echo "$dir"
done | wc -l)

if [[ "$INCOMPLETE_MINORS" -eq 0 ]]; then
  MAJOR_COMPLETE=true
fi
```

### Check Major Requirements Satisfaction

**MANDATORY**: Before marking a major version complete, verify all minor versions have satisfied
their requirements.

1. **For each minor version in the major**:
   - Verify its requirements satisfaction status
   - A major version cannot be complete if any minor has unsatisfied must-have requirements

2. **Aggregate requirements coverage**:
   - Report total requirements across all minor versions
   - Report satisfaction rate: `{satisfied}/{total} requirements met`

3. **Block completion if any minor has unsatisfied must-have requirements**:
   - Display which minor versions have gaps
   - Require resolution before major completion

### Verify Minor Version CHANGELOGs

**MANDATORY**: Verify all minor versions in this major have finalized CHANGELOGs.

```bash
for MINOR_DIR in .claude/cat/issues/v${MAJOR}/v${MAJOR}.*/; do
  MINOR_CHANGELOG="${MINOR_DIR}CHANGELOG.md"
  if [[ ! -f "$MINOR_CHANGELOG" ]]; then
    echo "⚠️ Missing CHANGELOG: $MINOR_CHANGELOG"
  elif grep -q "To be filled" "$MINOR_CHANGELOG"; then
    echo "⚠️ Incomplete CHANGELOG: $MINOR_CHANGELOG"
  fi
done
```

If any CHANGELOGs are missing or incomplete, block major completion until resolved.

### Major Completion Celebration

```
---

## 🏆 Major Version v{major} Complete!

All minor versions in v{major} are done.

---
```

### Major Stakeholder Review Option

Use AskUserQuestion:
- header: "Major Version Complete"
- question: "Would you like to run a comprehensive stakeholder review on v{major}?"
- options:
  - "Run full review" - Review entire major version (Recommended for releases)
  - "Skip review" - Continue to next major version
  - "View status first" - Show /cat:status before deciding

---

## Next Steps After Version Completion

```
Use `/cat:status` to see overall progress.
Use `/cat:add` to add more issues or versions.
```

---

## When NOT to Load

- When issues remain in current version
- During normal issue execution
- When finding next issue
