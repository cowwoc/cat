# Plan: license-validation-server-docker

## Goal
Create Docker containerization for the license validation server.

## Satisfies
- Deployment readiness of license-validation-server

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Image size, startup time
- **Mitigation:** Use multi-stage build, Eclipse Temurin base image

## Files to Create
- server/Dockerfile
- server/docker-compose.yml
- server/.dockerignore

## Acceptance Criteria
- [ ] Docker image builds successfully
- [ ] Container starts and serves /actuator/health
- [ ] docker-compose up works for local development
- [ ] Image size is reasonable (< 300MB)

## Execution Steps
1. **Step 1:** Create Dockerfile
   - Multi-stage build (Maven build -> JRE runtime)
   - Eclipse Temurin 21 JRE as base
   - Verify: `docker build -t cat-license-server .`

2. **Step 2:** Create .dockerignore
   - Ignore target/, .git, .idea, etc.
   - Verify: Build context is minimal

3. **Step 3:** Create docker-compose.yml
   - Service for license server
   - PostgreSQL service for production-like testing
   - Health check configuration
   - Verify: `docker-compose up` starts services

4. **Step 4:** Test container
   - Verify /actuator/health responds
   - Verify /api/v1/license/validate works
   - Verify: Full integration test passes
