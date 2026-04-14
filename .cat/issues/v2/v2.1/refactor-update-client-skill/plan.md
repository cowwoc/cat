# Plan

## Goal

Fix hook errors during cat-update-client by merging steps 2, 3, and 4 into a single Bash call so the
client symlink is never dangling when hooks fire.

**Root cause:** Step 2 recreates the symlink pointing to `INSTALL_PATH/client` before that directory
exists (the plugin reinstall wipes it). When post-bash fires after step 2, and when pre-bash fires before
step 3, the symlink is dangling — `pre-bash`, `post-bash`, and `post-tool-use` binaries are not found.

**Fix:** Merge steps 2 (reinstall + symlink recreate), 3 (rm -rf + cp jlink), and 4 (write VERSION) into
one Bash call. By the time any hook fires, the client directory is fully populated.

Note: `rm -rf` must be used directly (not `cat:safe-rm-agent`) because during this window the hook binary
is inaccessible — this was established by the closed issue `fix-update-client-step3-rm-rf`.

## Pre-conditions

(none)

## Post-conditions

1. **Bash block merge:** Steps 2, 3, and 4 execute in a single Bash code block in SKILL.md (verify with `git diff`)
2. **Symlink and jlink atomic:** The lines `ln -sfn`, `rm -rf`, and `cp -r` all appear within the same Bash block (no intermediate echo statements between them)
3. **Step structure:** The file contains exactly 3 numbered steps: "2. Reinstall Plugin and Install jlink Runtime", "3. Verify", and no Step 4 or Step 5
4. **Step prose:** Section 2 contains the CRITICAL warning text about single Bash call and dangling symlinks
5. **No hook errors:** Running `cat-update-client` produces no error output on stderr containing "pre-bash", "post-bash", or "post-tool-use"
6. **Verification passes:** The Verify section (now Step 3) executes successfully

## Jobs

### Job 1

**File:** `plugin/skills/cat-update-client/SKILL.md` (relative to project root)

#### Changes

**Change 1 — Merge Step 2, 3, and 4 into single Bash block:**

In the "### 2. Reinstall the Plugin" section, find the existing Bash code block that ends with:
```bash
ln -sfn "${INSTALL_PATH}/client" "${CLAUDE_PLUGIN_ROOT}/client"
echo "INSTALL_PATH=${INSTALL_PATH}"
```

Add the following lines immediately after `ln -sfn` and before the first `echo` statement:

```bash
# Install jlink runtime image (formerly Step 3)
rm -rf "${INSTALL_PATH}/client"
cp -r /workspace/client/target/jlink "${INSTALL_PATH}/client"

# Write VERSION file (formerly Step 4)
echo "2.1" > "${INSTALL_PATH}/client/VERSION"
```

So the combined block becomes:
```bash
[unlink and reinstall code...]
ln -sfn "${INSTALL_PATH}/client" "${CLAUDE_PLUGIN_ROOT}/client"

# Install jlink runtime image (formerly Step 3)
rm -rf "${INSTALL_PATH}/client"
cp -r /workspace/client/target/jlink "${INSTALL_PATH}/client"

# Write VERSION file (formerly Step 4)
echo "2.1" > "${INSTALL_PATH}/client/VERSION"

echo "INSTALL_PATH=${INSTALL_PATH}"
```

**Change 2 — Update Step 2 heading and prose:**

Change the heading from:
```
### 2. Reinstall the Plugin
```

To:
```
### 2. Reinstall Plugin and Install jlink Runtime
```

Update the prose to explain that steps 2, 3, and 4 now execute together. Add after the first paragraph:

```
**CRITICAL:** All three steps (reinstall, jlink installation, and VERSION write) execute in a single Bash
call. This ensures that by the time any hook fires (pre-bash, post-bash, post-tool-use), the client
directory is fully populated — the symlink is never dangling.
```

**Change 3 — Remove old Step 3 and Step 4 sections:**

Delete the entire "### 3. Install jlink Runtime Image to Plugin Cache" section (including both code blocks
for `rm -rf` and `cp -r`).

Delete the entire "### 4. Write VERSION file" section.

**Change 4 — Renumber Step 5 to Step 3:**

Change the heading "### 5. Verify" to "### 3. Verify".

No other changes to the verify section are needed.

#### Verification

After making all changes:
1. The merged Step 2 Bash block must contain (in order): unlink, reinstall, `ln -sfn "${INSTALL_PATH}/client"`, `rm -rf "${INSTALL_PATH}/client"`, `cp -r /workspace/client/target/jlink "${INSTALL_PATH}/client"`, and `echo "2.1" > "${INSTALL_PATH}/client/VERSION"`
2. Step 3 (formerly Step 5) heading reads "### 3. Verify" and its content is unchanged
3. No section header "### 3. Install jlink Runtime Image to Plugin Cache" exists in file
4. No section header "### 4. Write VERSION file" exists in file
5. No section header "### 5. Verify" exists in file (renumbered to Step 3)
6. The CRITICAL warning paragraph starting with "**CRITICAL:** All three steps..." appears in Step 2 prose
7. Run `git diff plugin/skills/cat-update-client/SKILL.md` and verify it shows exactly: merged bash content in Step 2, old Step 3 and Step 4 sections deleted (shown as `-` lines), and Step 5 heading changed to Step 3
