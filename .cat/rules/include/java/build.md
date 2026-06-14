## Build System

- **Build Tool:** Maven with Maven Wrapper (`./mvnw`)
- **Test Framework:** TestNG (not JUnit)
- **JSON Library:** Jackson 3.x with `JsonMapper`
- **Validation Library:** requirements.java 13.2+

### Running the Build

**MANDATORY:** Use `mvn verify -e` (not `mvn test`) to run the full build including compiler and linters:

```bash
mvn -f client/pom.xml verify -e
```

Treat linter errors (Checkstyle, PMD) the same as compiler errors — both must be fixed before any commit.

**Do NOT skip linters:**
- ❌ `mvn -f client/pom.xml test -Dcheckstyle.skip=true`
- ❌ `mvn -f client/pom.xml verify -Dpmd.skip=true`
- ✅ `mvn -f client/pom.xml verify -e`

**Fix ALL errors before rerunning the build.** After collecting the full output from one `mvn verify -e` run,
apply all fixes across all files without any intermediate recompilation. Run `mvn verify -e` again only once
all fixes have been applied.
