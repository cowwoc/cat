# Plan

## Goal

Extract all deterministic config operations (read and write) from the config skill into a Java CLI tool.
This covers the full round-trip: the questionnaire answer→config.json write, the manual settings write,
and the get-config-output calls used to display current values for the "(current)" indicators.

The new tool (e.g., `update-config`) should accept key=value arguments and atomically updates config.json.
Reading current values should use the existing get-config-output tool where possible.

## Pre-conditions

(none)

## Post-conditions

- [ ] A Java CLI tool (`update-config` or equivalent) accepts key=value pairs and atomically updates config.json
- [ ] The config skill's questionnaire write step delegates to the Java tool instead of constructing JSON inline
- [ ] The config skill's manual settings write step delegates to the Java tool
- [ ] Current-value display ("(current)" indicators) uses the Java tool for value lookup, not LLM inference
- [ ] User-visible behavior is unchanged (same values written, same display)
- [ ] Unit tests for the new Java tool cover happy path, unknown keys, and malformed input
- [ ] E2E: run /cat:config, answer the questionnaire, confirm config.json contains the correct values
