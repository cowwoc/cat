# Plan: 2.1-rename-config-options

## Goal

Rename three config options to use personality/style-oriented names that better describe user working style:
- `verify` → `caution`
- `effort` → `curiosity`
- `patience` → `perfection` (scale inverted: high=act immediately, low=defer)

## Background

The config options are used throughout the plugin to control CAT behavior. The new names are being introduced
as part of a broader UX redesign that models user personality/style. A companion issue
(`2.1-add-personality-questionnaire`) adds a questionnaire that derives these values during `/cat:init`.

## Scope

All files under `plugin/` that read or document `effort`, `verify`, or `patience` config keys, plus the
config template, Java source in `client/`, and migration infrastructure.

## Research Findings

### Files Requiring Changes

**Config template:**
- `plugin/templates/config.json` — JSON keys `verify`, `effort`, `patience`

**Java source (production):**
- `client/src/main/java/io/github/cowwoc/cat/hooks/Config.java` — accessors `getVerify()`, `getEffort()`,
  `getPatience()` with string literals `"verify"`, `"effort"`, `"patience"` and default values in DEFAULTS map
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/CautionLevel.java` — renamed from `VerifyLevel.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/CuriosityLevel.java` — renamed from `EffortLevel.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/PerfectionLevel.java` — renamed from `PatienceLevel.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetInitOutput.java` — references `effort patience`
  in argument parsing

**Java source (tests):**
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/ConfigTest.java` — references to all three keys
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/HandlerOutputTest.java` — may reference config keys
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/CheckDataMigrationTest.java` — migration validation

**Plugin skills reading config via grep/sed:**
- `plugin/skills/work-review-agent/first-use.md` — greps for `"verify"` and `"patience"`
- `plugin/skills/work-implement-agent/first-use.md` — greps for `"effort"`
- `plugin/skills/stakeholder-review-agent/first-use.md` — greps for `"effort"`
- `plugin/skills/add-agent/first-use.md` — reads config via get-config-output

**Plugin skills with config documentation/menus:**
- `plugin/skills/config/first-use.md` — config wizard menus for verify, effort, patience
- `plugin/skills/init/first-use.md` — config template documentation

**Migration:**
- `plugin/migrations/2.1.sh` — existing migration has `curiosity → effort` rename (lines 483-497) that
  needs reversal; also references `verify` in session migration (lines 908-910)
- `plugin/migrations/registry.json` — migration descriptions reference old names

**Concepts and rules referencing config keys:**
- Various `plugin/concepts/*.md` and `plugin/rules/*.md` files that document `verify`, `effort`, `patience`
- `plugin/agents/work-verify.md` — references verify level

### Scale Inversion for perfection

The `patience` config controlled "how long to wait" (high=wait long, low=act fast). The replacement
`perfection` controls "how immediately to act on improvements" (high=act immediately, low=defer). This is
a semantic inversion: `patience: high` maps to `perfection: low` and vice versa. The migration script must
invert the value during conversion.

### Existing Migration Context

The `2.1.sh` migration already contains a `curiosity → effort` rename (Phase 6, lines 483-497). Since this
issue renames `effort → curiosity` (reversing that), the migration phase needs to be removed or updated to
be a no-op for this key. The migration must now handle:
- `verify → caution`
- `effort → curiosity`
- `patience → perfection` (with value inversion: high↔low, medium stays medium)

## Post-conditions

- `plugin/templates/config.json` uses new key names (`caution`, `curiosity`, `perfection`)
- All skill files that read `effort` now read `curiosity`
- All skill files that read `verify` now read `caution`
- All skill files that read `patience` now read `perfection`
- A migration script in `plugin/migrations/` converts existing `config.json` files from old to new key names
- Migration is idempotent (safe to run twice)
- The `perfection` scale is documented: high=act immediately, low=defer (inverted from former `patience`)
- All tests pass with no regressions
- `plugin/skills/config/first-use.md` updated to use new names in all menus and descriptions
- `plugin/skills/init/first-use.md` updated to use new names
- Config documentation reflects new names and their meanings

## Sub-Agent Waves

### Wave 1

1. **Rename Java enum classes** (file renames + content updates):
   - `client/src/main/java/io/github/cowwoc/cat/hooks/util/CautionLevel.java` — renamed from `VerifyLevel.java`:
     class name `CautionLevel`, Javadoc "caution level", enum values (NONE, CHANGED, ALL)
   - `client/src/main/java/io/github/cowwoc/cat/hooks/util/CuriosityLevel.java` — renamed from `EffortLevel.java`:
     class name `CuriosityLevel`, Javadoc "curiosity level", enum values (LOW, MEDIUM, HIGH)
   - `client/src/main/java/io/github/cowwoc/cat/hooks/util/PerfectionLevel.java` — renamed from `PatienceLevel.java`:
     class name `PerfectionLevel`, Javadoc "perfection level" with note about inverted scale (high=act immediately, low=defer),
     enum values (LOW, MEDIUM, HIGH)

2. **Update Config.java**:
   - In DEFAULTS map: keys now use `"caution"`, `"curiosity"`, `"perfection"`
   - Accessor `getCaution()`, return type `CautionLevel`, string literal `"caution"`
   - Accessor `getCuriosity()`, return type `CuriosityLevel`, string literal `"curiosity"`
   - Accessor `getPerfection()`, return type `PerfectionLevel`, string literal `"perfection"`
   - Update all import statements for renamed enum classes

3. **Update GetInitOutput.java**:
   - Change argument references from `effort patience` to `curiosity perfection`
   - Update any string literals referencing old names

4. **Update all Java files that import or reference the renamed enum classes**:
   - Search for `import.*CautionLevel`, `import.*CuriosityLevel`, `import.*PerfectionLevel` across all Java files
   - Search for `getCaution()`, `getCuriosity()`, `getPerfection()` across all Java files (already renamed in Config.java)

5. **Update Java tests**:
   - `ConfigTest.java`: update all references to old key names (`"verify"`, `"effort"`, `"patience"`) to new
     names, update enum class references, update method call names
   - `HandlerOutputTest.java`: update any references to old config keys or enum classes
   - `CheckDataMigrationTest.java`: update migration test expectations
   - Any other test files that reference the old names

6. **Run `mvn -f client/pom.xml verify -e`** to ensure all Java changes compile and tests pass

7. **Commit Java changes**:
   `refactor: rename config option types verify→caution, effort→curiosity, patience→perfection`

### Wave 2

1. **Update config template**:
   - In `plugin/templates/config.json`: rename keys `"verify"` → `"caution"`, `"effort"` → `"curiosity"`,
     `"patience"` → `"perfection"`

2. **Update plugin skills that grep config values** (change grep patterns from old to new key names):
   - `plugin/skills/work-review-agent/first-use.md`: change `'"verify"'` grep to `'"caution"'`,
     change `'"patience"'` grep to `'"perfection"'`
   - `plugin/skills/work-implement-agent/first-use.md`: change `'"effort"'` grep to `'"curiosity"'`
   - `plugin/skills/stakeholder-review-agent/first-use.md`: change `'"effort"'` grep to `'"curiosity"'`
   - `plugin/skills/add-agent/first-use.md`: update any config key references

3. **Update config wizard skill**:
   - `plugin/skills/config/first-use.md`: rename all menu items and descriptions from
     `verify` → `caution`, `effort` → `curiosity`, `patience` → `perfection`.
     Update the step names and emoji descriptions to match new personality-oriented naming.
     For perfection, update the scale description to: high=act immediately on improvements, low=defer.

4. **Update init skill**:
   - `plugin/skills/init/first-use.md`: update config template documentation to use new key names

5. **Update migration script** (`plugin/migrations/2.1.sh`):
   - Remove or update the existing `curiosity → effort` Phase 6 migration (lines 483-497) since the rename
     is now reversed
   - Add new migration phase that renames: `verify → caution`, `effort → curiosity`, `patience → perfection`
   - For `patience → perfection`, invert the value: `high` → `low`, `low` → `high`, `medium` → `medium`
   - Make the migration idempotent: check if old keys exist before renaming, skip if already renamed
   - Update `plugin/migrations/registry.json` descriptions if needed

6. **Update concept and rule files** that reference config keys:
   - Search `plugin/concepts/` and `plugin/rules/` for references to `verify` (as config key), `effort`,
     `patience` and update to new names
   - Be careful to only change config-key references, NOT the English word "verify" when used as a verb
     (e.g., "verify implementation" should NOT be changed)

7. **Commit plugin changes**:
   `refactor: rename config keys verify→caution, effort→curiosity, patience→perfection in plugin`

8. **Fix work-agent config field reference**:
   - In `plugin/skills/work-agent/first-use.md` line 69: change the `"verify"` field name reference to `"caution"`
     so the documentation correctly identifies the config key that VERIFY/CAUTION is read from

9. **Fix work-review-agent documentation references**:
   - In `plugin/skills/work-review-agent/first-use.md` lines 174, 176, 186, 188: change all occurrences of
     `verify=none` to `caution=none` so the documentation about the config setting uses the renamed key

10. **Update index.json**: set status to `closed`, progress to 100%

## Success Criteria

- `mvn -f client/pom.xml verify -e` passes with zero errors
- `plugin/templates/config.json` contains only `caution`, `curiosity`, `perfection` (not the old names)
- No Java file references `VerifyLevel`, `EffortLevel`, or `PatienceLevel` class names
- No skill file greps for `"verify"`, `"effort"`, or `"patience"` as config keys
- Migration script in `plugin/migrations/2.1.sh` handles old→new key conversion with value inversion for
  perfection
- Migration is idempotent (running twice produces same result)
