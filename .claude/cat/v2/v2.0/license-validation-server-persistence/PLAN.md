# Plan: license-validation-server-persistence

## Goal
Set up database schema with JPA entities for licenses and device activations.

## Satisfies
- Data persistence for license-validation-server

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Schema design affects future scalability
- **Mitigation:** Keep schema simple, use standard JPA patterns

## Files to Modify
- server/pom.xml - Add Spring Data JPA, H2 dependencies

## Files to Create
- server/src/main/java/io/github/cowwoc/claudecodecat/persistence/entity/License.java
- server/src/main/java/io/github/cowwoc/claudecodecat/persistence/entity/DeviceActivation.java
- server/src/main/java/io/github/cowwoc/claudecodecat/persistence/repository/LicenseRepository.java
- server/src/main/java/io/github/cowwoc/claudecodecat/persistence/repository/DeviceActivationRepository.java
- server/src/main/resources/schema.sql
- server/src/test/java/io/github/cowwoc/claudecodecat/persistence/RepositoryTest.java

## Acceptance Criteria
- [ ] License entity persists to H2 database
- [ ] DeviceActivation tracks device-to-license bindings
- [ ] Repository methods work (findById, save, delete)
- [ ] Tests verify CRUD operations

## Execution Steps
1. **Step 1:** Add JPA and H2 dependencies to pom.xml
   - spring-boot-starter-data-jpa
   - h2 database (dev/test scope)
   - Verify: `mvn compile`

2. **Step 2:** Create License entity
   - Fields: id, customerId, tier, createdAt, expiresAt
   - Verify: Entity compiles

3. **Step 3:** Create DeviceActivation entity
   - Fields: id, licenseId, deviceFingerprint, activatedAt
   - Verify: Entity compiles

4. **Step 4:** Create repositories
   - LicenseRepository extends JpaRepository
   - DeviceActivationRepository with custom queries
   - Verify: Repository tests pass

5. **Step 5:** Add schema.sql for explicit DDL
   - Create tables with constraints
   - Verify: Application starts and schema is created
