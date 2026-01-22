# Plan: license-validation-server-spring-setup

## Goal
Add Spring Boot dependencies to existing server project and create the application bootstrap.

## Satisfies
- First step of license-validation-server decomposition

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** POM changes may conflict with existing dependencies
- **Mitigation:** Keep existing TestNG dependency, add Spring on top

## Files to Modify
- server/pom.xml - Add Spring Boot parent, dependencies

## Files to Create
- server/src/main/java/io/github/cowwoc/claudecodecat/CatLicenseServerApplication.java
- server/src/main/resources/application.yml

## Acceptance Criteria
- [ ] Spring Boot application starts successfully
- [ ] Health endpoint responds at /actuator/health
- [ ] Existing JWT tests still pass

## Execution Steps
1. **Step 1:** Update pom.xml with Spring Boot parent and dependencies
   - Add spring-boot-starter-parent
   - Add spring-boot-starter-web
   - Add spring-boot-starter-actuator
   - Keep existing TestNG dependency
   - Verify: `mvn compile`

2. **Step 2:** Create main application class
   - CatLicenseServerApplication with @SpringBootApplication
   - Verify: Application starts on port 8080

3. **Step 3:** Create application.yml configuration
   - Basic server port configuration
   - Actuator health endpoint enabled
   - Verify: /actuator/health returns 200

4. **Step 4:** Run existing tests
   - Verify: `mvn test` passes (JWT tests still work)
