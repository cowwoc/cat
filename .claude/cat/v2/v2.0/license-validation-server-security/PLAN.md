# Plan: license-validation-server-security

## Goal
Add rate limiting and security measures to protect the license API.

## Satisfies
- Security requirements of license-validation-server

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** Misconfigured security could block legitimate requests
- **Mitigation:** Start with generous limits, tune based on usage

## Files to Modify
- server/pom.xml - Add Spring Security, Bucket4j dependencies

## Files to Create
- server/src/main/java/io/github/cowwoc/claudecodecat/security/RateLimitFilter.java
- server/src/main/java/io/github/cowwoc/claudecodecat/security/SecurityConfig.java
- server/src/test/java/io/github/cowwoc/claudecodecat/security/RateLimitTest.java

## Acceptance Criteria
- [ ] Rate limiting returns 429 after threshold exceeded
- [ ] Actuator endpoints require authentication (or are restricted)
- [ ] CORS configured for allowed origins
- [ ] Tests verify rate limiting behavior

## Execution Steps
1. **Step 1:** Add security dependencies to pom.xml
   - spring-boot-starter-security
   - bucket4j-core for rate limiting
   - Verify: `mvn compile`

2. **Step 2:** Create RateLimitFilter
   - Token bucket algorithm (e.g., 100 requests/minute)
   - Return 429 Too Many Requests when exceeded
   - Verify: Filter compiles

3. **Step 3:** Create SecurityConfig
   - Permit /api/v1/license/** endpoints
   - Secure /actuator/** (except health)
   - Configure CORS
   - Verify: Security config applies

4. **Step 4:** Add rate limiting tests
   - Exceed limit, verify 429 response
   - Verify limits reset after window
   - Verify: All tests pass
