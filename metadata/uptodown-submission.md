# Uptodown submission — PaymentsLab-KMP

## Verdict: worth doing

Uptodown runs a genuine free self-serve developer console (sign up, "Add new app", attach a file
— [en.uptodown.com/developers-console](https://en.uptodown.com/developers-console)), no per-app
fee, no country restriction found for India. Every file is scanned across 75+ VirusTotal engines and
Uptodown identity-verifies registering developers, which puts it at a 100/100 trust score with no
malware/phishing blacklist hits (source:
[mywot.com/scorecard/uptodown.com](https://www.mywot.com/scorecard/uptodown.com)). That's the
opposite of the reputational profile that ruled out APKPure for this app family — see the channel
verdict summary for the comparison. PaymentsLab-KMP bundles real payment-gateway SDKs (Razorpay,
Cashfree, Stripe, Play Services Wallet) on purpose as a portfolio demo, not a live transactional
product — say that plainly in the listing, same as the F-Droid metadata already does.

## Not done yet (owner-only, needs a real Uptodown account)

- Actual account signup and identity verification — cannot be scripted or done on your behalf.
- A 1024×500 featured banner graphic. None exists in the repo; `featureGraphic.png` in fastlane is a
  different aspect ratio for a different store. Uptodown lists this as recommended, not mandatory.
- Whether a privacy policy URL is mandatory on the form — not confirmed from outside the console.
  Since this app talks to real (if sandbox) payment-gateway SDKs, have an answer ready: state
  clearly that this is a demo build, not a live transactional app, and point at whatever privacy
  disclosure already covers the other stores rather than writing a new one for this submission.

## Account setup (do this yourself)

1. Go to https://en.uptodown.com/developers-console and register — free.
2. Complete Uptodown's developer verification step before your first submission goes live.

## Submit the app

1. Developers Console → Apps → **Add new app**.
2. Package name: `com.paymentslab.app` (must match exactly).
3. Upload the signed APK. Use the exact release asset, not a glob — the release also carries an
   unsigned or differently-named release build alongside it:
   `https://github.com/darkpandawarrior/PaymentsLab/releases/download/v2026.08.35.1.174/PaymentsLab-v2026.08.35.1.174.apk`
   (signing cert SHA-256: `e3cd9ed25baaa6db5501621a2a7399edc0878022f9b64b5d95446db0348dd19c` — verify
   with `apksigner verify --print-certs` before uploading).
4. Icon: Uptodown wants a square PNG, ≥256×256, corners rounded by the site itself. PaymentsLab-KMP
   ships an adaptive icon (no static PNG in source) — export it with Android Studio's Image Asset
   tool, or unzip the signed APK above and take `res/mipmap-xxxhdpi-v4/ic_launcher.png`.
5. Screenshots: vertical/portrait preferred. Reuse the existing set at
   `/Users/darkpandawarrior/Repos/Android/PaymentsLab/fastlane/metadata/android/en-US/images/phoneScreenshots/1.png`
   through `7.png`.
6. Category: closest fit is Finance/Business or Tools, matching the F-Droid `Categories: Money,
   Development` entry — confirm against Uptodown's own category list in the console, not
   independently verified here.
7. **Disclose the anti-features.** This app bundles proprietary gateway SDKs and talks to their
   proprietary payment networks by design — that's exactly why F-Droid tags it `NonFreeDep` +
   `NonFreeNet`. The full description below already states this plainly in the first paragraph;
   keep that paragraph in whatever you paste, don't trim it out for length.

## Copy to paste

**Title**
```
PaymentsLab-KMP
```

**Short description**
```
One PaymentGateway API, seven real gateway SDKs behind it. A dev demo.
```

**Full description** (trim if the form enforces a shorter limit than this — not confirmed)
```
PaymentsLab-KMP bundles proprietary payment gateway SDKs: Razorpay, Cashfree, Stripe, and Google Play Services Wallet. That is by design. This app exists to demonstrate real integration work against real gateway SDKs, not to be a free-software-only artifact. Read that up front and decide if that is acceptable to you before installing.

What it is: a single PaymentGateway contract implemented seven times over, once per native gateway SDK: Razorpay, Cashfree, Stripe, Google Pay (via Play Services Wallet, riding Stripe as the processor), Square, Omise, and a raw UPI intent flow. Two generic archetypes, hosted-webview redirect and mobile-money confirm-on-phone, cover a much larger catalog of other gateways behind the same interface, but those seven are the ones with a real native SDK underneath. The app picks a gateway, builds a checkout request through the shared interface, and the concrete adapter handles that gateway's own SDK calls, tokens, and callback shapes. A Ktor backend module does the server side of the flow: HMAC request signing and webhook signature verification, so the client never holds a raw API secret.

Who it is for: engineers evaluating integration architecture, specifically how to keep seven vendor SDKs from leaking their differences into the rest of an app. If you have ever had to swap a payment provider, or support two at once, this is that problem worked through end to end and left in the open.

What is technically interesting: it is full Kotlin Multiplatform. The PaymentGateway contract, the adapters, and the backend logic live in shared code; only the actual SDK bridging and platform checkout UI are platform-specific. Dependency injection runs on Koin across both the app and the backend. The backend is a real Ktor server, not a mock, and it is the thing actually signing requests and checking webhook signatures, the part most sample integrations skip.

Platforms: Android is the primary target and what you are looking at in this listing. The shared modules also build for iOS and for a Ktor-based backend, since the whole point is one contract working across targets, not just across gateways.

Honest caveats: this is a portfolio and demonstration project, not a production payments app, and it is not on the Play Store as a live transactional product. The gateway SDKs are proprietary and talk to proprietary payment networks.

Source is fully available and the project is GPL-3.0-or-later, at https://github.com/darkpandawarrior/PaymentsLab. The gateway SDK dependencies are the only non-free part; everything Siddharth Pandalai wrote is open.
```

## What I could not confirm (verify in the console before relying on it)

- Exact character limits for title / short description / full description.
- Whether a privacy policy URL field is mandatory.
- Review/approval turnaround time.
