# Task Plan: stripe-billing-integration

## Objective
Add Stripe Billing integration with webhook handlers for subscription management.

## Tasks
- [ ] Set up Stripe products and pricing for 3 tiers
- [ ] Implement webhook handler for subscription events
- [ ] Handle customer.subscription.created → issue license
- [ ] Handle customer.subscription.updated → update tier
- [ ] Handle customer.subscription.deleted → revoke license
- [ ] Implement idempotent event processing
- [ ] Add signature verification for webhooks

## Technical Approach
Per architect research: Buy payment processing from Stripe. Webhook-driven architecture with idempotent handlers.

## Verification
- [ ] Webhook receives and processes subscription events
- [ ] License issued on new subscription
- [ ] Tier updated on plan change
- [ ] License revoked on cancellation
- [ ] Duplicate events handled idempotently
