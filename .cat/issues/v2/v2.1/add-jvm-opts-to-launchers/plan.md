# Plan

## Goal

Add `-ea` (enable assertions) to dev builds and `CAT_JVM_OPTS` support to all launcher scripts.
`build-jlink.sh` should accept an `--enable-assertions` flag that bakes `-ea` into generated launchers.
All launchers should also expand `${CAT_JVM_OPTS:-}` so users can inject JVM flags at runtime.
The `/cat-update-client` skill should pass `--enable-assertions` when invoking the build.

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Shell script arg parsing, Maven exec-plugin profile override, launcher template change
- **Mitigation:** Unit-test launcher generation in isolation via harness before running a full jlink build

## Files to Modify

- `client/build-jlink.sh` — add `--enable-assertions` flag parsing; add `${CAT_JVM_OPTS:-}` and
  conditional `-ea` to launcher template; add BASH_SOURCE guard so file is sourceable by tests
- `client/pom.xml` — add Maven profile `enable-assertions` to pass `--enable-assertions` to
  exec-maven-plugin when `-DenableAssertions=true` is set
- `.claude/skills/cat-update-client/SKILL.md` — add `-DenableAssertions=true` to Step 1 mvn command
- `tests/build-jlink.bats` — add tests for new launcher behavior using `source` to call
  `generate_launchers()` in isolation

## Pre-conditions

(none)

## Jobs

### Job 1

TDD — write failing tests first, then implement to make them pass.

#### Step 1a: Write failing Bats tests in `tests/build-jlink.bats`

Add a `setup` helper that creates a temp dir with a minimal `HANDLERS` array and an `OUTPUT_DIR/bin/`
directory. Add these test cases (they will FAIL before implementation because `generate_launchers`
cannot be sourced):

```
@test "launcher always contains CAT_JVM_OPTS expansion" {
  # source build-jlink.sh (BASH_SOURCE guard prevents main from running)
  # call generate_launchers with ENABLE_ASSERTIONS=false
  # assert launcher contains '${CAT_JVM_OPTS:-}'
}

@test "launcher without --enable-assertions does not contain -ea" {
  # call generate_launchers with ENABLE_ASSERTIONS=false
  # assert launcher does NOT contain '-ea'
}

@test "launcher with --enable-assertions contains -ea" {
  # call generate_launchers with ENABLE_ASSERTIONS=true
  # assert launcher contains '-ea'
}

@test "build-jlink.sh exits non-zero on unknown argument" {
  # run build-jlink.sh --unknown-flag
  # assert exit code != 0
}
```

Exact test file path: `tests/build-jlink.bats`

The tests source `client/build-jlink.sh` directly (not via a harness). They set `OUTPUT_DIR`,
`ENABLE_ASSERTIONS`, `MODULE_NAME`, and a minimal `HANDLERS=("test-launcher:PreToolUseHook")` then
call `generate_launchers`. Assert on the generated file at `${OUTPUT_DIR}/bin/test-launcher`.

Run the tests to confirm they FAIL before implementation:
```bash
cd /workspace/.cat/work/worktrees/2.1-add-jvm-opts-to-launchers && \
  bats tests/build-jlink.bats 2>&1 | tail -20
```

#### Step 1b: Add BASH_SOURCE guard and arg parsing to `client/build-jlink.sh`

**BASH_SOURCE guard** (replace `main "$@"` at the bottom of the file):
```bash
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  main "$@"
fi
```

**Arg parsing** in `main()` — add before the function calls:
```bash
ENABLE_ASSERTIONS=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --enable-assertions) ENABLE_ASSERTIONS=true; shift ;;
    *) error "Unknown argument: $1" ;;
  esac
done
```

#### Step 1c: Update launcher template in `generate_launchers()` in `client/build-jlink.sh`

Replace the heredoc template with one that includes `${CAT_JVM_OPTS:-}` and `ASSERTIONS_FLAG`:

```sh
#!/bin/sh
DIR=`dirname $0`
exec "$DIR/java" \
  ${CAT_JVM_OPTS:-} \
  ASSERTIONS_FLAG \
  -Xms16m -Xmx96m \
  -Dstdin.encoding=UTF-8 \
  -Dstdout.encoding=UTF-8 \
  -Dstderr.encoding=UTF-8 \
  -XX:+UseSerialGC \
  -XX:TieredStopAtLevel=1 \
  -XX:AOTCache="$DIR/../lib/server/aot-cache.aot" \
  -m MODULE_CLASS "$@"
```

Update the `sed` replacement and validation block for each launcher:

```bash
# Replace MODULE_CLASS and handle ASSERTIONS_FLAG
if [[ "$ENABLE_ASSERTIONS" == "true" ]]; then
  sed -e "s|MODULE_CLASS|$main_class|g" -e "s|ASSERTIONS_FLAG|-ea|g" \
    "$launcher" > "${launcher}.tmp"
else
  sed -e "s|MODULE_CLASS|$main_class|g" -e "/ASSERTIONS_FLAG/d" \
    "$launcher" > "${launcher}.tmp"
fi
mv "${launcher}.tmp" "$launcher"

# Validation
[[ -s "$launcher" ]] || error "Failed to generate launcher: $name (empty file)"
! grep -q "MODULE_CLASS" "$launcher" || error "Failed to generate launcher: $name (placeholder not removed)"
grep -q "$main_class" "$launcher" || error "Failed to generate launcher: $name (main class not found)"
! grep -q "ASSERTIONS_FLAG" "$launcher" || \
  error "Failed to generate launcher: $name (assertions placeholder not removed)"
grep -q "CAT_JVM_OPTS" "$launcher" || \
  error "Failed to generate launcher: $name (CAT_JVM_OPTS not found)"
if [[ "$ENABLE_ASSERTIONS" == "true" ]]; then
  grep -q "\-ea" "$launcher" || error "Failed to generate launcher: $name (-ea flag not found)"
fi
```

#### Step 1d: Add Maven profile to `client/pom.xml`

Add a `<profiles>` section before `</project>`:

```xml
	<profiles>
		<profile>
			<id>enable-assertions</id>
			<activation>
				<property>
					<name>enableAssertions</name>
					<value>true</value>
				</property>
			</activation>
			<build>
				<plugins>
					<plugin>
						<groupId>org.codehaus.mojo</groupId>
						<artifactId>exec-maven-plugin</artifactId>
						<version>3.6.3</version>
						<executions>
							<execution>
								<id>build-jlink-image</id>
								<configuration>
									<arguments>
										<argument>--enable-assertions</argument>
									</arguments>
								</configuration>
							</execution>
						</executions>
					</plugin>
				</plugins>
			</build>
		</profile>
	</profiles>
```

#### Step 1e: Run the Bats tests to confirm they pass

```bash
cd /workspace/.cat/work/worktrees/2.1-add-jvm-opts-to-launchers && \
  bats tests/build-jlink.bats 2>&1
```

All existing and new tests must pass.

#### Step 1f: Run full Java test suite

```bash
mvn -f /workspace/.cat/work/worktrees/2.1-add-jvm-opts-to-launchers/client/pom.xml test
```

All tests must pass.

#### Step 1g: Commit

Commit type: `feature:` (touches `client/`).
Update `.cat/issues/v2/v2.1/add-jvm-opts-to-launchers/index.json`: set `status` to `"closed"` and
add `"resolution": "implemented"`. Stage and commit all files together:

```bash
cd /workspace/.cat/work/worktrees/2.1-add-jvm-opts-to-launchers && \
  git add tests/build-jlink.bats client/build-jlink.sh client/pom.xml \
          .cat/issues/v2/v2.1/add-jvm-opts-to-launchers/index.json && \
  git commit -m "feature: add --enable-assertions flag and CAT_JVM_OPTS to jlink launchers"
```

### Job 2

Independent — update the `/cat-update-client` skill to pass `--enable-assertions`.

#### Step 2a: Update `.claude/skills/cat-update-client/SKILL.md`

In Step 1 of the skill, change:
```bash
mvn -f /workspace/client/pom.xml verify
```
To:
```bash
mvn -f /workspace/client/pom.xml verify -DenableAssertions=true
```

The surrounding text ("Build with Maven" heading and explanation paragraph) stays the same.

#### Step 2b: Commit

Commit type: `config:` (touches `.claude/`).

```bash
cd /workspace/.cat/work/worktrees/2.1-add-jvm-opts-to-launchers && \
  git add .claude/skills/cat-update-client/SKILL.md && \
  git commit -m "config: pass --enable-assertions to build in cat-update-client skill"
```

## Post-conditions

- [ ] `build-jlink.sh --enable-assertions` generates launchers with `-ea` flag
- [ ] `build-jlink.sh` without `--enable-assertions` generates launchers without `-ea`
- [ ] All generated launchers include `${CAT_JVM_OPTS:-}` expansion for runtime JVM flag injection
- [ ] The `/cat-update-client` skill passes `--enable-assertions` to the build
- [ ] Tests pass and no regressions introduced
- [ ] E2E: Build the client with `--enable-assertions`, verify a launcher script contains both `-ea`
  and `CAT_JVM_OPTS`
