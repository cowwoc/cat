# Plan: java-core-hooks

## Metadata
- **Parent:** migrate-python-to-java
- **Sequence:** 2 of 5
- **Estimated Tokens:** 20K

## Objective
Migrate core hook infrastructure: lib/config, entry points (get-*.py), and invoke-handler.

## Scope
- lib/config.py → Java configuration module
- get-skill-output.py → Java equivalent
- get-posttool-output.py → Java equivalent
- get-bash-pretool-output.py → Java equivalent
- get-bash-posttool-output.py → Java equivalent
- get-read-pretool-output.py → Java equivalent
- get-read-posttool-output.py → Java equivalent
- invoke-handler.py → Java equivalent

## Dependencies
- java-jdk-infrastructure (JDK runner scripts must exist)

## Files to Migrate
| Python | Java |
|--------|------|
| lib/config.py | src/cat/hooks/Config.java |
| get-skill-output.py | src/cat/hooks/GetSkillOutput.java |
| get-posttool-output.py | src/cat/hooks/GetPosttoolOutput.java |
| get-bash-pretool-output.py | src/cat/hooks/GetBashPretoolOutput.java |
| get-bash-posttool-output.py | src/cat/hooks/GetBashPosttoolOutput.java |
| get-read-pretool-output.py | src/cat/hooks/GetReadPretoolOutput.java |
| get-read-posttool-output.py | src/cat/hooks/GetReadPosttoolOutput.java |
| invoke-handler.py | src/cat/hooks/InvokeHandler.java |

## Execution Steps
1. Create Java project structure with Jackson 3 dependencies
2. Migrate Config class (JSON parsing, project detection)
3. Migrate each get-*-output entry point
4. Migrate invoke-handler dispatcher
5. Update shell scripts to call Java instead of Python

## Acceptance Criteria
- [ ] All entry points produce identical JSON output
- [ ] Config class reads same configuration files
- [ ] invoke-handler correctly dispatches to handlers
