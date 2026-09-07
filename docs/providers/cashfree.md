# Cashfree

- **Region:** India
- **Archetype:** A (native SDK — the nextgen `com.cashfree.pg.*` Drop Checkout,
  `CFPaymentGatewayService.doPayment`).
- **Status shipped:** `SANDBOX_READY` (client), but the backend `CashfreeAdapter.verify` is an
  explicit **STUB** — see below.
- **Docs:** https://docs.cashfree.com/docs/android-sdk

## Real vs mock

`CashfreeAdapter` (`backend/src/main/kotlin/com/paymentslab/backend/Adapters.kt`):

- `createProviderOrder` returns a **demo-shaped** session (`payment_session_id = "session_${orderId}_demo"`)
  rather than calling Cashfree's real `POST /orders` to mint one.
- `verify` is explicitly commented as **STUB, mirrors Stripe**: it checks the client-supplied
  `order_status`/`marker` field in `extra` for `"PAID"`/`"succeeded"` → `SUCCESS`, otherwise `PENDING`.
  The real implementation (`GET /orders/{order_id}` → `order_status`) is called out as a later
  milestone, not done here.

The client side is genuinely wired to the real SDK: `CashfreeGateway.pay` builds a real `CFSession`
(`Environment.SANDBOX`) and launches the real Drop Checkout UI (UPI + cards + net-banking), including
Cashfree's sandbox UPI simulator that lets you approve or decline a UPI collect request end-to-end
without a real PSP app. Only the server-side settlement confirmation is stubbed.

## The callback bridge

Cashfree's SDK reports terminal state via a `CFCheckoutResponseCallback` registered on the host
Activity in `onCreate`, not a return value or a per-call listener — `CashfreeCheckoutRelay` bridges
that into the `suspendCancellableCoroutine` `pay()` suspends on, the same shape as
`RazorpayCallbackRelay`. `onPaymentVerify` means the SDK *initiated* verification, not that it
succeeded — the orchestrator still confirms server-side (today, via the stub above) before trusting a
`Success`.

## Test coverage

- **`CashfreeCheckoutRelayTest`** (pure-JVM, 6 tests): the single-flight relay/resume-once bridge
  logic `pay()`'s coroutine depends on — verify/failure outcome forwarding, at-most-once firing on a
  duplicate SDK callback, a second `awaitResult` while one is in flight being rejected, a cancelled
  coroutine's late callback being dropped via `clearPending`, and a callback arriving before
  `awaitResult` is registered being a no-op. This is the highest-value thing to lock down for this
  archetype, per the test file's own doc comment — no Android or SDK types are touched.
- **No `CashfreeGatewayTest`** exists (unlike Razorpay/Flutterwave/UPI-intent's dedicated gateway
  tests) — the failure-code mapping (`mapFailureCode`'s string-matching on `errorCode`/`errorMessage`)
  is untested at the unit level.
- **No `CashfreeAdapterTest`** exists on the backend, and `BackendTest.kt` never exercises a
  `gatewayId = "cashfree"` order — the stub `verify` path has no automated coverage at all.

## Failure modes

`mapFailureCode` string-matches the lowercased `errorCode`/`errorMessage` for `"cancel"` →
`USER_CANCELLED`, `"network"`/`"timeout"` → `NETWORK_ERROR`, `"decline"`/`"failed"`/`"insufficient"`
→ `GATEWAY_DECLINED`, else `SDK_ERROR`. Cashfree's SDK has no separate cancel callback — a user
dismissing the sheet surfaces through `onPaymentFailure` with a cancellation-shaped message, which
this string match is what detects it.

## Getting real sandbox credentials

Sign up at https://merchant.cashfree.com/merchants/signup — test-mode App ID/secret are available
without business KYC. Set `CASHFREE_APP_ID` / `CASHFREE_SECRET` on the backend; wiring the real
`POST /orders` and `GET /orders/{order_id}` calls into `CashfreeAdapter` (replacing the demo session
id and the marker-based stub) is the work still needed to make `verify` genuine.
