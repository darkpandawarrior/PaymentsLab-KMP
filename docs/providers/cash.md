# Cash

- **Region:** Global
- **Archetype:** Archetype-D-simplest (per `CashGateway`'s own KDoc) — a record-only gateway for
  cash collected entirely outside the app (courier COD, in-person handoff). There is no provider to
  even ping: no SDK, no WebView, no network call from this gateway at all.
- **Status shipped:** `MOCK_MODE` (permanently — there is no "real" mode to upgrade to; cash never
  touches a payment processor).
- **Docs:** none — this is not a third-party integration.

## Flow

`prepare()` does nothing but pass the backend's `providerParams` through unchanged. `pay()` does
nothing but immediately return `PaymentResult.Pending(reason = AWAITING_WEBHOOK)` — there is no UI,
no SDK call, no redirect. The order is only ever resolved when a human (merchant/cashier) confirms
the cash was physically received, via the backend's `POST /mock/cash/{orderId}/settle` reconciliation
route (`CashAdapter` on the backend mints only a local `cash_ref`; `verify` always returns `PENDING`
too — the settle route is the only thing that ever moves this order out of pending).

This is deliberately the purest demonstration of the app's reconciliation-loop story: there's no
webhook signature to fake and no SDK callback in the way, just a human confirming money changed
hands out-of-band, exactly the pattern the journal-written-before-launch / process-death-recovery
design exists to handle safely.

## Test coverage

**`CashGatewayTest`** (2 tests, `commonTest` — this module is genuinely KMP, not Android-only):
`pay returns Pending with no network call - record-only` and `meta is honest about record-only mock
mode`. Small surface, small test file — there is no failure-code branching to exercise because
`pay()` has no failure path (it cannot fail; it does nothing but return `Pending`).

## Failure modes

There are none in the client gateway itself — `pay()` unconditionally returns `Pending`. The only
place a "failure" can occur is the backend settle route rejecting an already-settled or unknown
order, which is a REST-layer concern (`MockCheckoutRoutes.kt`), not something `CashGateway` itself
can surface.

## What is mocked

Everything about the "provider" is inherently mocked in the sense that there is no provider — this
gateway models a real-world payment method (cash handoff) that has no digital counterpart to fake.
