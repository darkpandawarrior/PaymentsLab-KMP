# Cashfree on iOS

- **Region:** India
- **Archetype:** A (native SDK — `CashfreePGUISDK` Drop Checkout, real `core-ios-sdk` `2.5.5` via SPM)
- **Status shipped:** `SANDBOX_READY`
- **Docs:** https://docs.cashfree.com/docs/ios-integration

## Architecture

Same Swift-implements-Kotlin-interface boundary as Stripe (see `docs/providers/stripe-ios.md`).
`CashfreeCheckoutHostImpl.swift` implements `CashfreeCheckoutHost` against the real SDK's Drop
Checkout API:

```swift
let session = try CFSession.CFSessionBuilder()
    .setOrderID(orderId).setPaymentSessionId(paymentSessionId).setEnvironment(.SANDBOX).build()
let payment = try CFDropCheckoutPayment.CFDropCheckoutPaymentBuilder().setSession(session).build()
try pgService.doPayment(payment, viewController: rootViewController)
// delegate: CFResponseDelegate — verifyPayment(order_id:) / onError(_:order_id:)
```

`CashfreePGUISDK`'s SPM product graph doesn't declare its transitive binary dependencies
(`CashfreePGCoreSDK`, `CFNetworkSDK`, `CashfreeAnalyticsSDK`) — found by attempting the build, which
failed with "unable to resolve module dependency" until all four products were added to the Xcode
target explicitly.

## Known limitation (matches Android, not a regression)

The backend's `CashfreeAdapter.createProviderOrder` returns a **stub** `payment_session_id`
(`session_<orderId>_demo`), not a real Cashfree Orders API session — this is the same limitation
the Android `CashfreeGateway` already ships with, not something introduced building the iOS side.
`verify()` only ever inspects a `cf_status` marker both platforms send, never a live order lookup.

## Verified

Built via `xcodebuild`, SPM resolved the real SDK (pinned to the `master` branch — Cashfree's tags
are per-component, e.g. `ui-2.4.1`/`api-2.3.1`, not a single unified release), installed + launched
alongside Stripe/Razorpay/Omise — Cashfree shows `Sandbox ready` with no crash. Not verified:
tapping through to actually open Drop Checkout — no touch-injection tool available for iOS
Simulator in this environment.

## SPM pin

`core-ios-sdk` is pinned to the tag `2.5.5` (`upToNextMajorVersion`), not to the `master` branch it
carried until 2026-09-20. A branch requirement makes the build non-reproducible: SPM re-resolves
`master` to whatever HEAD happens to be, so the same commit of this repo could link a different SDK
on two machines. `2.5.5` is the newest *plain-semver* tag the repo publishes — the `ui-*`,
`api-*` and `analytics-*` tag series are per-product and are not resolvable by SPM's version rules.
