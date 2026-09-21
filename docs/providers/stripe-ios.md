# Stripe on iOS

- **Region:** Global
- **Archetype:** A (native SDK — `StripePaymentSheet`, real `stripe-ios` 26.11.0 via SPM)
- **Status shipped:** `SANDBOX_READY`
- **Docs:** https://docs.stripe.com/payments/quickstart?platform=ios

## Why this exists

The B8 iOS work initially framed every native-SDK gateway (Stripe/Razorpay/Cashfree/Square/Omise/
GooglePay) as "Android-only by construction." That was wrong for five of the six — Stripe,
Razorpay, Cashfree, Square, and Omise all publish real iOS SDKs; only Google Pay is genuinely
Android-specific (Apple Pay is iOS's native wallet, a completely separate Apple product using
PassKit, not a Google Pay port). This entry corrects that by building the real thing for Stripe.

The actual reason the Android `provider:stripe` module doesn't run on iOS: it's built as an
Android-only Gradle module (`paymentslab.android.provider`, no iOS target) directly wrapping
Stripe's **Android** SDK types (`com.stripe.android.paymentsheet.PaymentSheet`, an Android class).
That's a scoping choice made when the module was built, not a platform limitation — Stripe's iOS
SDK (`StripePaymentSheet`, Swift) does the equivalent job on iOS.

## Architecture: Swift implements a Kotlin interface

Kotlin/Native can't cinterop directly against Stripe's Swift-only iOS SDK (Kotlin/Native cinterop
targets C/Objective-C headers, not arbitrary Swift modules). The correct direction — and the one
built here — is the reverse: Kotlin declares a small interface, Kotlin/Native exports it as a plain
Objective-C protocol automatically, and **Swift implements it** against the real SDK:

```kotlin
// ios/shared (Kotlin) — deliberately callback-based, not `suspend`, for unambiguous interop
interface StripeCheckoutHost {
    fun presentPaymentSheet(
        clientSecret: String,
        publishableKey: String,
        merchantDisplayName: String,
        onResult: (StripeCheckoutOutcome) -> Unit,
    )
}
```

```swift
// ios/iosApp/iosApp/StripeCheckoutHostImpl.swift — the real PaymentSheet call
final class StripeCheckoutHostImpl: NSObject, StripeCheckoutHost {
    func presentPaymentSheet(clientSecret: String, publishableKey: String, merchantDisplayName: String,
                              onResult: @escaping (StripeCheckoutOutcome) -> Void) {
        StripeAPI.defaultPublishableKey = publishableKey
        var configuration = PaymentSheet.Configuration()
        configuration.merchantDisplayName = merchantDisplayName
        let paymentSheet = PaymentSheet(paymentIntentClientSecret: clientSecret, configuration: configuration)
        // ... paymentSheet.present(from:completion:) maps the result to StripeCheckoutOutcome
    }
}
```

`StripeIosGateway.pay()` wraps the callback in `suspendCancellableCoroutine` — the same pattern
every Activity-callback-era Android SDK in this app already uses, just crossing a Kotlin/Swift
boundary instead of an Android callback boundary.

`StripeCheckoutHostImpl()` is constructed in Swift and handed down at app startup:
`KoinInitKt.doInitKoin(stripeCheckoutHost: StripeCheckoutHostImpl())` — Koin never tries to
construct the platform-specific implementation itself.

## Backend

No changes needed. The existing `StripeAdapter` (built pre-B8, already serving the Android app)
returns the same `client_secret`/`publishable_key` pair regardless of which client called
`POST /orders` — both platforms consume the identical contract.

## SPM dependency

`https://github.com/stripe/stripe-ios`, product `StripePaymentSheet`, pinned to `26.11.0` (verified
via the GitHub releases API — the repo hosts `Package.swift` directly at its root, no separate SPM
mirror repo needed). Min iOS 15, matching this project's deployment target.

## Verified

Built via `xcodebuild -scheme iosApp -sdk iphonesimulator build` — SPM resolved and cloned the real
SDK. Installed + launched in an iPhone 17 Pro Simulator (`xcrun simctl`): Stripe appears in the
catalog as `Sandbox ready`, the app didn't crash with the real SDK linked in
(`docs/screenshots/ios_catalog_stripe.png`). **Not verified**: actually tapping through to present
PaymentSheet and complete a real card entry — `xcrun simctl` has no touch-injection tool to drive
that interaction non-interactively in this environment.
