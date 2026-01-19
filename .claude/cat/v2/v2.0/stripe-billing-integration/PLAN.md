# Task Plan: stripe-billing-integration

## Objective
Add Stripe Billing integration with Java webhook handlers for subscription management.

## Tasks
- [ ] Set up Stripe products and pricing for 3 tiers
- [ ] Add Stripe Java SDK dependency to license-validation-server
- [ ] Implement webhook controller for subscription events
- [ ] Handle customer.subscription.created - issue license
- [ ] Handle customer.subscription.updated - update tier
- [ ] Handle customer.subscription.deleted - revoke license
- [ ] Implement idempotent event processing with database dedup
- [ ] Add Stripe signature verification for webhooks

## Technical Approach

Per project conventions, all server-side code must be in Java.

**Java Components (added to license-validation-server):**
- `StripeWebhookController.java` - Webhook endpoint
- `StripeEventHandler.java` - Event processing logic
- `SubscriptionService.java` - License lifecycle management
- `WebhookEventRepository.java` - Idempotency tracking

**Webhook Flow:**
1. Stripe sends event to `/api/v1/webhook/stripe`
2. Verify Stripe signature (Stripe Java SDK)
3. Check event ID for idempotency
4. Process event, update license state
5. Return 200 OK

**Dependencies:**
- Stripe Java SDK (com.stripe:stripe-java)
- Existing license-validation-server infrastructure

## Verification
- [ ] Webhook receives and processes subscription events
- [ ] License issued on new subscription (JWT generated)
- [ ] Tier updated on plan change
- [ ] License revoked on cancellation
- [ ] Duplicate events handled idempotently (same result)
- [ ] Invalid signatures rejected (401)
