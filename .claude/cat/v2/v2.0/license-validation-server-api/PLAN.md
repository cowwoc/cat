# Plan: license-validation-server-api

## Goal
Implement REST API endpoints for license validation, activation, and deactivation.

## Satisfies
- Core API functionality of license-validation-server

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Need to integrate with existing LicenseTokenValidator
- **Mitigation:** Reuse existing JWT validation code

## Files to Create
- server/src/main/java/io/github/cowwoc/claudecodecat/api/LicenseController.java
- server/src/main/java/io/github/cowwoc/claudecodecat/api/dto/ValidateRequest.java
- server/src/main/java/io/github/cowwoc/claudecodecat/api/dto/ValidateResponse.java
- server/src/main/java/io/github/cowwoc/claudecodecat/api/dto/ActivateRequest.java
- server/src/main/java/io/github/cowwoc/claudecodecat/api/dto/ActivateResponse.java
- server/src/main/java/io/github/cowwoc/claudecodecat/service/LicenseService.java
- server/src/test/java/io/github/cowwoc/claudecodecat/api/LicenseControllerTest.java

## Acceptance Criteria
- [ ] POST /api/v1/license/validate returns entitlements for valid token
- [ ] POST /api/v1/license/activate registers device
- [ ] POST /api/v1/license/deactivate releases device
- [ ] Invalid tokens return 401
- [ ] Tests cover all endpoints

## Execution Steps
1. **Step 1:** Create DTO classes for request/response
   - ValidateRequest, ValidateResponse
   - ActivateRequest, ActivateResponse
   - Verify: Classes compile

2. **Step 2:** Create LicenseService
   - Integrate with existing LicenseTokenValidator
   - Add validate(), activate(), deactivate() methods
   - Verify: Service unit tests pass

3. **Step 3:** Create LicenseController
   - POST /api/v1/license/validate
   - POST /api/v1/license/activate
   - POST /api/v1/license/deactivate
   - Verify: Integration tests with MockMvc

4. **Step 4:** Add error handling
   - Return 401 for invalid tokens
   - Return 400 for malformed requests
   - Verify: Error scenarios tested
