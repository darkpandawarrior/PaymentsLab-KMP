# Stripe

- **Region:** Global
- **Archetype:** A (native SDK — Stripe's `PaymentSheet`, presented imperatively via
  `StripePaymentLauncherHost` rather than the Compose-first surface, so it can live in
  Activity/Compose scope alongside every other Tier-1 native-SDK gateway).
- **Status shipped:** `SANDBOX_READY` (client), but the backend `StripeAdapter.verify` is an
  explicit **STUB** — see below. Google Pay rides Stripe as the gateway of record in this app; it is
  not a separate settlement path.
- **Docs:** https://stripe.com/docs/payments/accept-a-payment?platform=android

## Real vs mock

`StripeAdapter` (`backend/src/main/kotlin/com/paymentslab/backend/Adapters.kt`):

- `createProviderOrder` returns a **demo-shaped** client secret (`pi_${orderId}_secret_demo`) plus
  the configured publishable key — it never calls Stripe's real `PaymentIntents.create`. A real
  implementation would create a genuine PaymentIntent server-side and hand back its real client
  secret.
- `verify` is explicitly commented as a **STUB**: it looks for a `payment_intent_status`/`marker`
  field in the client-supplied `extra` map and treats `"succeeded"` as `SUCCESS`, otherwise `PENDING`.
  The real implementation (`stripe.paymentIntents.retrieve(id).status`) is called out in the adapter's
  own KDoc as "a later milestone," not done here.

On the client, `StripeGateway.pay` genuinely presents Stripe's real `PaymentSheet` UI (test-mode
publishable key, 3DS2 test-card challenge included) — only the server-side settlement confirmation is
stubbed, not the client checkout experience.

## The client-result-is-a-hint gotcha

A `PaymentSheetResult.Completed` only means the SDK reported success — it does **not** hand back the
PaymentIntent id on this imperative surface, so `StripeGateway` derives a stable payment id from the
client-secret prefix (`pi_XXX_secret_YYY` → `pi_XXX`) purely for display; the server re-derives the
real intent id from the same client secret it minted and is the only authority on the true status.
`client_secret` is redacted automatically in `.raw` (the `Redactor` masks any field whose name
contains "secret").

## Test coverage — none at the gateway level

**`StripeGateway` has no test file at all** — unlike every other Tier-1 native-SDK gateway in this
catalog (Razorpay, Cashfree, Flutterwave, UPI intent all have a `*GatewayTest`), there is no
`StripeGatewayTest` in `external/kmp-toolkit/provider/stripe/src/test`. The backend `StripeAdapter`
is likewise untested: there is no `StripeAdapterTest` alongside `PaystackAdapterTest` /
`OmiseAdapterTest` / `SquareAdapterTest` / `PayPalAdapterTest`, and `BackendTest.kt`'s end-to-end
suite never exercises a `gatewayId = "stripe"` order. This is a real coverage gap, not an oversight
hidden by this doc — it should be closed before this gateway is trusted at Tier-1.

## Failure modes

`PaymentSheetResult.Canceled` → `PaymentResult.Cancelled`. `PaymentSheetResult.Failed` currently maps
every thrown SDK error to `FailureCode.GATEWAY_DECLINED` — the code has a `TODO(failure-taxonomy)`
noting it cannot yet reliably distinguish "the SDK itself errored" (e.g. missing publishable key)
from "the card was actually declined" without inspecting Stripe's exception subtypes, so both land in
the same bucket today.

## Getting real sandbox credentials

Sign up at https://dashboard.stripe.com/register — test-mode publishable/secret keys are available
immediately, no business verification required. Set `STRIPE_PUBLISHABLE_KEY` / `STRIPE_SECRET` on the
backend; wiring the real `PaymentIntents.create`/`.retrieve` calls into `StripeAdapter` (replacing the
demo-shaped secret and the marker-based stub) is the work still needed to make `verify` genuine.
