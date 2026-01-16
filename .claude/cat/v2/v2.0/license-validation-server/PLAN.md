# Task Plan: license-validation-server

## Objective
Build license validation server in Java for verifying license keys and managing entitlements.

## Tasks
- [ ] Create Java Spring Boot application structure
- [ ] Design REST API endpoints (/validate, /activate, /deactivate)
- [ ] Implement license key validation logic using JWT verification
- [ ] Set up database schema for licenses and entitlements (H2/PostgreSQL)
- [ ] Add rate limiting and security measures (Spring Security)
- [ ] Write integration tests with embedded server
- [ ] Add Docker containerization for deployment

## Technical Approach

Per project conventions, all server-side code must be in Java.

**Java Components:**
- Spring Boot 3.x REST application
- `LicenseController.java` - REST endpoints
- `LicenseService.java` - Business logic
- `LicenseRepository.java` - Data access
- `RateLimitFilter.java` - Request throttling

**API Design:**
- `POST /api/v1/license/validate` - Validate token, return entitlements
- `POST /api/v1/license/activate` - Activate license for device
- `POST /api/v1/license/deactivate` - Release device activation

**Dependencies:**
- Java 25+
- Spring Boot 3.x
- Spring Security
- Spring Data JPA
- TestNG for testing
- H2 (dev) / PostgreSQL (prod)

## Verification
- [ ] /validate endpoint returns correct tier for valid keys
- [ ] Invalid keys rejected with appropriate error (401/403)
- [ ] Rate limiting prevents abuse (429 response)
- [ ] Server handles offline grace period logic
- [ ] TestNG integration tests pass with 90%+ coverage
