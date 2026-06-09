# Plan: require-javadoc-for-private-methods

## Goal

Update the Java Checkstyle configuration so private methods are required to have Javadoc, then
bring existing Java sources into compliance.

## Parent Requirements

- Keep CAT's Java conventions consistently enforced by automated tooling.
- Reduce drift between documented coding standards and what the build actually validates.

## Pre-conditions

- `client/config/checkstyle.xml` currently enforces `MissingJavadocMethod` only for compact record
  constructors.
- The Java modules already run Checkstyle as part of the Maven verification flow.

## Post-conditions

- [ ] `client/config/checkstyle.xml` requires Javadoc for private methods in addition to existing
      public/protected coverage.
- [ ] Any necessary scope or token configuration is chosen deliberately so constructors, helpers,
      and other private methods are covered without unintentionally expanding enforcement beyond the
      intended method kinds.
- [ ] Existing Java sources under `client/**/src/main/java` and `client/**/src/test/java` are
      updated or exempted intentionally so the new rule passes cleanly.
- [ ] Any required test-source suppressions are reviewed rather than carried forward by accident.
- [ ] `mvn -f client/pom.xml verify` passes with the updated Checkstyle rule enabled.
- [ ] Documentation or rule references that describe Javadoc expectations are updated if they no
      longer match the enforced behavior.

## Implementation Notes

- Start with `client/config/checkstyle.xml`, which currently declares `JavadocMethod` and a
  narrow `MissingJavadocMethod` configuration for `COMPACT_CTOR_DEF`.
- Confirm whether the right Checkstyle shape is:
  - expanding `MissingJavadocMethod` access scope to include `private`, or
  - adding a second `MissingJavadocMethod` module tuned for private methods while preserving the
    compact-constructor workaround.
- Audit current violations before changing source so the implementation can distinguish:
  - private methods that should gain Javadoc,
  - private helpers that should be inlined/removed instead,
  - any generated or exceptional cases that deserve explicit suppression.
- Keep the outcome narrow: the purpose is to require Javadoc for private methods, not to broaden
  unrelated style policy.

## Verification

- Run `mvn -f client/pom.xml verify`.
- If the violation count is large, capture the affected packages/classes in the implementation notes
  so remediation can be reviewed systematically.
