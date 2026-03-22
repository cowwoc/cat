# Fix SHA256 Hash Parsing Fragility in Download Script

## Goal
Make SHA256 hash extraction in `plugin/scripts/download-git-filter-repo.sh` more precise and
robust by anchoring the regex to expected format boundaries, preventing false positives from
filenames or other fields that happen to contain 64-character hex sequences.

## Background
The current SHA256 extraction uses `grep -oE '[a-f0-9]{64}'` which matches any 64-character
lowercase hex string. In a SHA256 manifest file like:
```
abc123...def  git-filter-repo-v2.38.0-linux-x86_64
```
This can match parts of filenames if they contain long hex-like segments. A more targeted
approach anchors the pattern to line start or uses field-based extraction.

## Changes Required

1. Update the SHA256 extraction in `download-git-filter-repo.sh` to use a field-anchored pattern:
   - e.g., extract the first whitespace-delimited field (known to be the hash) rather than
     grepping for any 64-char hex string
   - or anchor with `^[a-f0-9]{64}[[:space:]]` to ensure the hash is at line start
2. Add a Bats test verifying that a manifest entry with a hex-looking filename does NOT cause
   false-positive hash extraction.
3. Ensure existing tests continue to pass.

## Post-conditions

- [ ] SHA256 extraction uses a field-anchored or line-start-anchored pattern
- [ ] A Bats test verifies no false-positive match from hex-like filenames
- [ ] All existing Bats tests continue to pass
- [ ] No regressions introduced
