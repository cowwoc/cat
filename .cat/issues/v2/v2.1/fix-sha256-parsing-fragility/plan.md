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

## Research Findings

The `sha256sum_portable` function in `plugin/scripts/download-git-filter-repo.sh` already uses
`awk '{print $1}'` to extract the hash from sha256sum output:

```bash
sha256sum "${file}" 2>/dev/null | awk '{print $1}'
```

`sha256sum` outputs lines in the format: `<hash>  <filepath>`. Using `awk '{print $1}'` extracts
the first whitespace-delimited field, which is always the hash. This approach is robust against
filenames containing long hex-like segments because it extracts by field position, not by regex match.

The SHA256 format validation in `release.conf` parsing uses `grep -qE '^[a-f0-9]{64}$'` with
start/end anchors, which is also already robust.

**What remains:** The post-conditions require a Bats regression test that explicitly verifies no
false-positive hash extraction occurs when the filename of a downloaded binary contains a 64-char
hex-like segment. This test is currently absent from `tests/download-git-filter-repo.bats`.

**Approach chosen:** Add a Bats test that stubs `sha256sum` to return a manifest line whose filename
portion includes a 64-char hex segment. Verify that the script still extracts only the correct hash
(the first field) and does not exit with an error or return the filename segment as the hash.

## Changes Required

1. Add a Bats test in `tests/download-git-filter-repo.bats` verifying that when `sha256sum` outputs
   a line where the filename contains a 64-char hex-like segment, hash extraction still succeeds and
   returns the correct hash (not the filename hex segment).
2. Ensure all existing Bats tests in `tests/download-git-filter-repo.bats` continue to pass.

## Post-conditions

- [ ] The existing SHA256 extraction in `plugin/scripts/download-git-filter-repo.sh` is confirmed to use
  `awk '{print $1}'` (field-position-based extraction), which is already robust against hex-like filenames
  — no script change is required
- [ ] A Bats test verifies no false-positive match from hex-like filenames
- [ ] All existing Bats tests continue to pass
- [ ] No regressions introduced

## Jobs

### Job 1
- In `tests/download-git-filter-repo.bats`, add a new test after the existing cache-hit test (around
  line 171) with the following specification:
  - Test name: `"sha256sum output with hex-like filename does not cause false-positive hash extraction"`
  - Setup: stub python3 to fail (call `write_python3_stub`), stub `uname` to return Linux/x86_64
    (same pattern as the stale-cache test), create a cached binary in `FAKE_PLUGIN_ROOT/lib/` named
    `git-filter-repo-linux-x64`
  - Write a custom `sha256sum` stub to `STUB_BIN_DIR/sha256sum` that outputs a line where:
    - The hash (field 1) is `FAKE_SHA256_LINUX_X64` (the correct expected hash)
    - The filename (field 2) is a name that itself contains a 64-char hex-like segment embedded within
      it, e.g. `git-filter-repo-linux-x64-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa`
    - The stub format: `printf '%s  git-filter-repo-linux-x64-aaaa...aaaa\n' "${FAKE_SHA256_LINUX_X64}"`
      where `aaaa...aaaa` is 64 hex chars (can reuse `FAKE_SHA256_LINUX_X64` itself for the embedded hex)
  - Write a `.version` file for the cached binary containing the correct RELEASE_TAG
    (`git-filter-repo-v2.38.0`) so the version check passes
  - Run the script with `run env PATH="${STUB_BIN_DIR}:${SAFE_PATH}" bash "${DOWNLOAD_SCRIPT}"`
  - Assertions:
    - `[ "${status}" -eq 0 ]` — script exits successfully
    - `[ "${output}" = "${CACHE_DIR}/git-filter-repo-linux-x64" ]` — returns the correct binary path,
      not a hash fragment
- After adding the test, run the full test suite for this file to confirm all tests pass:
  ```bash
  cd /workspace/.cat/work/worktrees/2.1-fix-sha256-parsing-fragility && bats tests/download-git-filter-repo.bats
  ```
- If any existing test fails, investigate and fix before committing
- Update index.json: set status to `closed`, progress to `100`
- Commit all changes with message: `bugfix: add test for SHA256 hash extraction with hex-like filenames`
