# Razorpay

- **Region:** India
- **Archetype:** A (native SDK — `com.razorpay:checkout`'s `Checkout.open`, the Activity-callback →
  coroutine bridge pattern every Tier-1 native-SDK gateway in this app follows).
- **Status shipped:** `SANDBOX_READY` — the only pay-in gateway with a **real** server-side crypto
  check, not a stub (see below).
- **Docs:** https://razorpay.com/docs/payments/payment-gateway/android-integration/standard/

## The Activity-callback → coroutine bridge

`Checkout.open(activity, options)` is fire-and-forget: Razorpay reports the result by invoking
`com.razorpay.PaymentResultWithDataListener` **on the Activity**, not through a per-call callback.
`RazorpayGateway.pay` reconciles that with the app's single-suspend-function `PaymentGateway`
contract via `RazorpayCallbackRelay`:

1. The app's `MainActivity` implements Razorpay's listener and forwards `onPaymentSuccess` /
   `onPaymentError` to `RazorpayCallbackRelay`.
2. `pay()` registers a one-shot listener on the relay, then `suspendCancellableCoroutine`s while
   `Checkout.open` runs.
3. The relay fires exactly once (an `AtomicBoolean` resume-once guard covers a defensive
   double-emit); the coroutine resumes and the relay slot clears.

## Real vs mock — the genuine crypto path

Unlike every hosted-webview/stub gateway in this catalog, Razorpay's backend `RazorpayAdapter`
(`backend/src/main/kotlin/com/paymentslab/backend/Adapters.kt`) is **not** a stub:

- `verify` recomputes `HMAC-SHA256("$orderId|$paymentId")` with the account secret and does a
  constant-time compare against the client-supplied `razorpay_signature` — this is real
  cryptographic verification, the same check a production Razorpay integration performs.
- `verifyWebhook` does the same real HMAC compare against the `X-Razorpay-Signature` header over the
  raw webhook body.

`razorpay_signature` goes into `PaymentResult.Success.verification` **unredacted** (server-bound,
the server needs the real value) and also into `.raw` via `Redactor`, where it renders masked — the
client never verifies the signature itself; a client `Success` is only a hint until the server's
HMAC check passes.

## Test coverage

- **`RazorpayGatewayTest`** (pure-JVM, 2 tests): the callback → `PaymentResult` mapping — the
  signature reaches `verification` intact, and is masked in `.raw`. Deliberately does not touch
  `Checkout.*` error-code constants (that mapping needs the Android SDK class loaded, so it's covered
  by instrumented tests instead, not this JVM suite).
- **`BackendTest`** (`backend/src/test/kotlin/com/paymentslab/backend/BackendTest.kt`): end-to-end
  Ktor `testApplication` coverage — order creation, `verify` succeeding with the correct HMAC and
  failing with a wrong signature, the `/webhooks/razorpay` route accepting a genuine signature and
  rejecting a forged one, and idempotency-key dedup/race behavior on `/orders`. This is the most
  thoroughly tested gateway in the whole catalog because it is the only one with real crypto to
  assert against.

## Failure modes

`mapErrorCode` normalizes Razorpay's `Checkout.*` constants: `PAYMENT_CANCELED` → `USER_CANCELLED`,
`NETWORK_ERROR`/`TLS_ERROR` → `NETWORK_ERROR`, `INVALID_OPTIONS` → `CONFIG_MISSING`, anything else →
`GATEWAY_DECLINED` (the bank/gateway rejected the payment). A `Checkout.open` throw (bad activity
state, SDK misconfiguration) is caught and mapped to `SDK_ERROR` without ever leaving the coroutine
unresumed.

## What is mocked

Nothing about the crypto path is mocked — the demo defaults (`RAZORPAY_KEY_ID`/`RAZORPAY_SECRET` env
vars default to `rzp_test_…`/`test_razorpay_secret`) are real Razorpay **test-mode** shaped values,
just not a live registered account's keys. Swap in a real `rzp_test_…` key pair from
https://dashboard.razorpay.com (self-serve, test mode needs no business KYC) and the same HMAC code
path becomes genuinely live against Razorpay's sandbox.
