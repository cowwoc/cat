# Task Plan: jwt-token-generation

## Objective
Implement JWT-based license token generation and validation using Ed25519 cryptography in Java.

## Tasks
- [ ] Create Java project structure (Maven) in `server/` directory
- [ ] Generate Ed25519 keypair using Java security APIs
- [ ] Design JWT payload structure (tier, customer_id, expiry, issued_at, grace_period)
- [ ] Implement token signing service (Java)
- [ ] Implement token validation library (Java)
- [ ] Create Bash hook wrapper to invoke Java validation
- [ ] Add key rotation strategy documentation
- [ ] Write TestNG tests for all token operations

## Technical Approach

Per project conventions, all server-side code must be in Java.

**Java Components:**
- `LicenseTokenGenerator.java` - Signs tokens with private key
- `LicenseTokenValidator.java` - Validates tokens with public key
- `KeyPairManager.java` - Ed25519 keypair generation and storage
- `LicenseToken.java` - Token payload model

**Token Structure:**
- Header: { alg: "EdDSA", typ: "JWT" }
- Payload: { tier, customer_id, exp, iat, grace_days }
- Signature: Ed25519 signature with private key

**Integration:**
- Java service exposes validation via CLI or local socket
- Bash hook invokes Java validator during SessionStart
- Client validates using embedded public key only

**Dependencies:**
- Java 25+
- java.security (Ed25519 support native)
- TestNG for testing
- No external JWT library needed (manual JWT construction)

## Verification
- [ ] Java service generates valid signed tokens
- [ ] Validator correctly verifies signature with public key
- [ ] Invalid/tampered tokens rejected
- [ ] Expiry correctly enforced
- [ ] TestNG tests pass with 90%+ coverage
- [ ] Bash hook wrapper invokes Java validator successfully
