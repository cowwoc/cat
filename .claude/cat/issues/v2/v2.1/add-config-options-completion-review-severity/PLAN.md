# Plan: add-config-options-completion-review-severity

## Goal

Add `completionWorkflow`, `reviewThreshold`, and `minSeverity` to the `/cat:config` CURRENT_SETTINGS display and
ensure all three are fully configurable via the wizard. Currently `GetConfigOutput.java` omits all three from the
settings box, and the skill has no wizard step for `minSeverity`.

## Satisfies

- None

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** The config skill already has steps for `completionWorkflow` and `reviewThreshold`; care needed to
  avoid duplicating them.
- **Mitigation:** Read existing steps before adding `minSeverity` step.

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetConfigOutput.java` - add three settings to box
- `plugin/skills/config/first-use.md` - add `minSeverity` menu entry and wizard step; add `minSeverity` to menu

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Steps

1. **Read existing files** to understand current rendering and step structure:
   - `GetConfigOutput.java` (full render logic)
   - `plugin/skills/config/first-use.md` (existing steps for completionWorkflow, reviewThreshold)
   - `Config.java` (getters for completionWorkflow, reviewThreshold, minSeverity)

2. **Update `GetConfigOutput.java`** to include all three settings in the CURRENT_SETTINGS box:
   - Read `completionWorkflow` (default: `"merge"`) from config
   - Read `reviewThreshold` (default: `"low"`) from config
   - Read `minSeverity` (default: `"low"`) from config
   - Add lines to the rendered box, e.g.: `"  🔀 Completion: " + completionWorkflow`
   - Run `mvn -f client/pom.xml verify` to confirm tests pass

3. **Update `plugin/skills/config/first-use.md`**:
   - Add `minSeverity` entry to the main menu (after `reviewThreshold` entry)
   - Add a `<step name="min-severity">` wizard step with options: low, medium, high, critical
   - Verify completionWorkflow and reviewThreshold menu entries already exist (they do)

4. **Rebuild jlink** and verify the CURRENT_SETTINGS box shows all three settings:
   ```bash
   cd client && bash build-jlink.sh
   ```

5. **Commit** with message:
   `feature: add completionWorkflow, reviewThreshold, and minSeverity to /cat:config`

## Post-conditions

- [ ] `/cat:config` CURRENT_SETTINGS box displays `completionWorkflow`, `reviewThreshold`, and `minSeverity`
- [ ] All three are configurable via the wizard
- [ ] `minSeverity` has a dedicated wizard step with values: low, medium, high, critical
- [ ] `mvn -f client/pom.xml verify` passes
