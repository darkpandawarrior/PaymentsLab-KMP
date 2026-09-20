# Razorpay on iOS

- **Region:** India
- **Archetype:** A (native SDK — `RazorpayCheckout`, real `razorpay-pod` `1.5.8` via SPM)
- **Status shipped:** `SANDBOX_READY`
- **Docs:** https://razorpay.com/docs/payments/payment-gateway/ios-integration/standard/

## Architecture

Same Swift-implements-Kotlin-interface boundary as Stripe (see `docs/providers/stripe-ios.md` for
the full reasoning). `RazorpayCheckoutHostImpl.swift` implements the Kotlin `RazorpayCheckoutHost`
protocol against the real SDK:

```swift
razorpay = RazorpayCheckout.initWithKey(keyId, andDelegateWithData: self)
razorpay?.open(options, displayController: rootViewController)
// delegate: onPaymentSuccess(_:andData:) / onPaymentError(_:description:andData:)
```

Same server contract as Android — `key_id`/`order_id` from `POST /orders`; the existing
`RazorpayAdapter`'s real HMAC-SHA256 signature verification serves both platforms unmodified. The
`razorpay_order_id`/`razorpay_signature` fields the SDK hands back in `andData` are forwarded
unredacted to the backend's `verify()` call, exactly like Android.

## Verified

Built via `xcodebuild`, SPM resolved the real SDK, installed + launched in an iPhone 17 Pro
Simulator alongside Stripe/Cashfree/Omise — Razorpay shows `Sandbox ready` in the catalog with no
crash (`docs/screenshots/ios_catalog_all_native.png`). Not verified: tapping through to actually
open Razorpay's checkout sheet — no touch-injection tool available for iOS Simulator in this
environment.
