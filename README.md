<div align="center">

<img src="docs/assets/banner.gif" alt="PaymentsLab-KMP, an integration lab for the Android payments ecosystem" width="700"/>

### An Integration Lab for the Android payments ecosystem, every gateway behind one abstraction, with a live look at what actually happens on each transaction.

A Kotlin Multiplatform payments app that runs real flows against seven native gateway SDKs and
dozens more behind two generic archetypes, all through one `PaymentGateway` contract. A companion
Ktor backend owns order creation, real HMAC signature verification and webhook reconciliation,
because a client-side `Success` is only ever a hint.

[![CI](https://github.com/darkpandawarrior/PaymentsLab-KMP/actions/workflows/ci.yml/badge.svg)](https://github.com/darkpandawarrior/PaymentsLab-KMP/actions/workflows/ci.yml)
![Kotlin](https://img.shields.io/badge/Kotlin-2.4.20--RC-7F52FF?logo=kotlin&logoColor=white)
![Compose Multiplatform](https://img.shields.io/badge/Compose%20MP-1.12.0--rc01-4285F4?logo=jetpackcompose&logoColor=white)
![Platforms](https://img.shields.io/badge/platforms-Android%20%7C%20iOS%20%7C%20Web-3DDC84)
![Ktor](https://img.shields.io/badge/Ktor-3.5.1-087CFA?logo=ktor&logoColor=white)
<!-- AUTOGEN:badge -->
![Modules](https://img.shields.io/badge/modules-44-success)
<!-- /AUTOGEN:badge -->

**[Highlights](#highlights)** · **[Screens & flows](#screens--flows)** · **[Architecture](#architecture)** · **[Getting started](#getting-started)** · **[Roadmap](#roadmap)**

**Case study:** [PaymentsLab-KMP](https://cv-siddharth.vercel.app/project/paymentslab) &nbsp;·&nbsp; **Sibling project:** [Doori](https://github.com/darkpandawarrior/Doori) (offline-first mileage/expense) &nbsp;·&nbsp; built on the shared **`kmp-build-logic`** convention plugins + **`kmp-mvi-core`** MVI runtime (see [Shared infrastructure](#shared-infrastructure))

</div>

---

<details>
<summary><b>Table of contents</b></summary>

- [Why PaymentsLab-KMP](#why-paymentslab-kmp)
- [The one idea worth stealing](#the-one-idea-worth-stealing)
- [Highlights](#highlights)
- [Engineering decisions (the *why*)](#engineering-decisions-the-why)
- [Provider status matrix](#provider-status-matrix)
- [Screens & flows](#screens--flows)
  - [Also runs on iOS](#also-runs-on-ios)
- [Architecture](#architecture)
  - [System diagram](#system-diagram)
  - [Module map](#module-map)
  - [Data flow (one payment)](#data-flow-one-payment)
  - [Payment rails (beyond one-shot pay-in)](#payment-rails-beyond-one-shot-pay-in)
- [Shared infrastructure](#shared-infrastructure)
- [Tech stack](#tech-stack)
- [Getting started](#getting-started)
- [Testing & quality](#testing--quality)
- [Security posture](#security-posture)
- [Roadmap](#roadmap)
- [iOS readiness](#ios-readiness)

</details>

<!-- AUTOGEN:stats -->
> **At a glance**, **44-module** KMP architecture: **15 local** (7 core · 4 feature · 4 app/iOS/backend) + **29 composed** via `includeBuild(external/kmp-toolkit)` (10 shared core · 19 payment-provider gateways), **26** deterministic Roborazzi screenshots. *Numbers auto-generated from `settings.gradle.kts` by `scripts/gen-readme.sh`.*
<!-- /AUTOGEN:stats -->

## Why PaymentsLab-KMP

Payments is the hardest integration surface on Android: every gateway ships a different SDK, most of
them are Activity-callback-era, the client can lie about the outcome, and the interesting logic
(signatures, webhooks, idempotency, recovery) lives on the server. That makes it a perfect subject
for a **systems** showcase rather than a UI one.

PaymentsLab-KMP runs, and **step-by-step visualizes**: real payment flows across multiple providers,
all behind a single `PaymentGateway` abstraction, backed by a small Ktor server that does the order
creation, signature verification and webhook reconciliation a real integration requires.

I built it to be three things at once:

1. **An architecture showcase**: a full Kotlin Multiplatform, multi-module design where each
   provider is an isolated, swappable Gradle module, the domain core is unit-tested against fakes,
   and the messy SDK reality is confined behind a coroutine-friendly host.
2. **A living catalog**: every provider the app touches is documented: what integrating it takes,
   whether a solo developer can demo it in sandbox, and the gotchas.
3. **A teaching reference**: the Lab renders each payment's lifecycle as a live timeline, showing
   the actual (redacted) payloads at every hop, so the *why* of payment architecture is visible.

## The one idea worth stealing

> **A client-side `Success` is a hint, never proof.** Only the server, after signature verification
> and webhook reconciliation, decides the true state.

Everything in the design flows from that: a server that owns price and truth, a client that always
confirms before trusting, a **journal written to Room *before* the SDK launches** so a process death
mid-payment is always recoverable, and a **redaction layer** so no secret or PII ever renders or logs.

## Highlights

- 🧩 **Modular KMP architecture, 66 gateways behind it.** One Gradle module per native-SDK
  provider, contributed into a registry via Koin `getAll<PaymentGateway>()`, adding gateway *N+1*
  touches no existing code. Feature modules never depend on each other; they meet only at the
  `:app` composition root. The catalog spans 7 native-SDK integrations, 47 hosted-webview gateways,
  8 mobile-money flows, and 4 catalog-only/KYC-gated entries, all behind the one contract below.
- 💸 **More than pay-in, five money-movement rails, plus split payments.** Beyond one-shot checkout
  the server now models **payouts** (`/payouts`, money *out* to a beneficiary), **mandates &
  subscriptions** (`/mandates` + scheduled debits and cancel), a **card vault** (`/vault`, tokenize
  once, charge later by id), **marketplace Connect onboarding** (`/connect`, sub-merchant KYC + split
  payouts), and an **internal double-entry wallet ledger** (`/wallet`, seed / debit / refund against a
  real running balance), plus **split payments**, a two-leg orchestration that compensates if one leg
  fails. Ten new provider modules ride these rails, Paystack, Flutterwave, Paytm, Xendit, M-Pesa,
  Peach and NMI, plus a `wallet` balance rail and a `cash` record-only gateway, every one honest
  `MOCK_MODE` until real sandbox keys are set. Each rail is idempotency-keyed like the pay-in path.
- 🔌 **One contract, seven real SDKs, plus two generic archetypes.** Razorpay, Cashfree, Stripe
  (+ Google Pay), Square, Omise, and a raw UPI intent flow all implement the same tiny
  `PaymentGateway` interface. The Activity-callback SDKs are bridged into suspending coroutines by
  a `PaymentHost` that never leaks an `Activity` upward. `provider:hosted-webview` and
  `provider:mobile-money` cover the other 55 gateways generically, redirect-and-return-URL
  checkout and confirm-on-the-payer's-phone flows, respectively, behind the same contract.
- 🏠 **A real Home dashboard, not just a catalog.** Animated gateway-count and success-rate stats,
  recent activity, one tap into Explore, the redesign's front door, backed by the same
  server-authoritative payment journal every other screen reads.
- 🎨 **Real gateway branding, honestly degraded.** A `GatewayBranding` registry renders each
  provider's actual logo where a rights-cleared source exists (8 gateways today, sourced from
  `simple-icons`, CC0), and falls back to a deterministic, hash-colored monogram for the other 58
  every gateway gets an intentional-looking badge, and adding gateway *N+1* needs zero registry
  upkeep.
- 🔒 **Motion that reinforces security, not just decorates.** A brief `ShieldPulse` shield-icon
  draw-in on every payment-bearing screen visually reflects the `FLAG_SECURE` protection already in
  place underneath it, a half-second cue, not a gimmick, and it respects `LocalReducedMotion` like
  every other animated component in the app.
- 🪪 **Env-backed credentials that auto-degrade honestly.** `core:config` resolves each gateway's
  sandbox keys from `PLAB_<GATEWAY>_<MODE>_<KEY>` env vars; a gateway with no resolved credentials
  auto-degrades from `SANDBOX_READY` to `MOCK_MODE` instead of silently pretending to work, the Lab
  stays demoable and honest with zero real credentials configured.
- 🛡️ **Server is the source of truth.** A companion Ktor server creates orders (**price resolved
  server-side**), verifies payments with **real HMAC-SHA256** (Razorpay), and reconciles idempotent,
  signature-checked webhooks, the client callback is only ever treated as a hint.
- ⏱️ **Process-death recovery, for real.** Every in-flight payment is journaled to Room *before* the
  SDK opens; on cold start the orchestrator finds unresolved payments and reconciles them.
- 🔎 **Redaction by default.** A single choke point masks any secret/PII-shaped field before it can
  be rendered in the Lab timeline or written to a log.
- 🔐 **VAPT-grade security suite.** `:security` (composed from `external/kmp-toolkit`), real Android Keystore AES-256-GCM at-rest
  encryption, `FLAG_SECURE` on payment screens (blocks screenshots/recording), device-integrity
  checks (root/Magisk, emulator, debugger), and a certificate-pinning config.
- ♻️ **Pure, replayable state machine.** The lifecycle is a pure `(State, Event) -> Effects` reducer
  (zero coroutines/DI/IO); the orchestrator just executes its effects. A payment's path is a
  recorded event log that replays byte-for-byte identically, the auditing property money movement
  wants.
- 🧪 **A real quality gate.** ktlint + detekt across every module (including KMP `commonMain`),
  fake-based unit tests for the orchestrator/ViewModels/backend, **deterministic Roborazzi
  screenshot tests** covering every redesigned screen, run on every push via GitHub Actions.

## Engineering decisions (the *why*)

Most of what makes payments hard isn't the SDK call, it's everything around it: the process can die
mid-charge, the client can report a success the server never saw, and a retry that double-charges is
a support ticket with money attached. Each decision below is a deliberate answer to one of those
failure modes, not an accident of the framework.

| Decision | Why it's this way | What it rules out |
|---|---|---|
| **Journal-first, before the SDK launches** | The pending row is written to Room *before* `gateway.pay()` opens the SDK UI. If the OS kills the process while the payment sheet is up, cold start finds an unresolved row and reconciles it against the server. | A payment that happened but the app has no memory of, the classic "money left, app forgot" black hole. |
| **Server is truth; client success is a hint** | Price is resolved server-side, signatures are verified server-side (real HMAC-SHA256), and webhooks reconcile the final state. The client callback only ever *nudges* the orchestrator toward a `Verifying` step, it never sets the terminal state. | Trusting a tampered/replayed/spoofed client result. A rooted device saying `Success` changes nothing until the server agrees. |
| **Pure `(State, Event) -> Effects` reducer** | The lifecycle FSM has zero coroutines, DI or IO, given the same event log it produces byte-identical effects. The effectful shell (`PaymentOrchestrator`) just executes what the reducer decides. | Untestable, unreplayable payment logic. The audit trail money movement needs is a natural consequence, not bolted on. |
| **One Gradle module per gateway** | Each native-SDK provider is an isolated module contributed into a Koin registry via `getAll<PaymentGateway>()`. Adding gateway *N+1* touches no existing code and can't pull another provider's SDK onto the classpath. | A monolithic `PaymentManager` with a `when(provider)` that every integration has to edit, merge conflicts, blast radius, and R8 keep-rule bleed across providers. |
| **Rails as separate idempotent route sets** | Payouts / mandates / vault / connect / wallet are distinct concerns, money *out*, *recurring* auth, *stored* instruments, *marketplace* split, *internal* ledger, each with its own lifecycle and its own idempotency-keyed routes, mirroring the pay-in discipline. | Overloading "a payment" to mean five different money movements. A payout retry and a charge retry have different safety semantics; conflating them is how you double-pay a beneficiary. |
| **Redaction at a single choke point** | One `Redactor` allowlist masks any secret/PII-shaped field before it can render in the Lab timeline or hit a log, enforced at the boundary, not per-call-site. | A stray `Log.d(payload)` leaking a card number or secret key. The safe path is the only path. |
| **`MOCK_MODE` auto-degrade, never a fake success** | A gateway with no resolved sandbox credentials degrades from `SANDBOX_READY` to an honest `MOCK_MODE` badge, real integration code, clearly labelled as not-live, instead of pretending to work. | A demo that silently lies about what's actually wired. Reviewers see exactly what would need a key to go live. |

## Provider status matrix

The whole app is honest about what a solo developer can actually run without a business account.
This table highlights the native-SDK flagships and a few notable others, see the in-app catalog
(the Explore tab) for the full 66-gateway list, each with its own status badge and region.

| Provider | Category | Region | Sandbox (no KYC)? | In v1 | Notes |
|---|---|---|:--:|:--:|---|
| **Razorpay** | Gateway | India | ✅ | ✅ | Drop-in checkout; real HMAC-SHA256 signature verification |
| **Cashfree** | Gateway | India | ✅ | ✅ | Nextgen SDK; sandbox UPI simulator |
| **UPI intent** | Platform flow | India | ✅ | ✅ | Raw `upi://` intent, no SDK; client response is *unverifiable* by design |
| **Stripe** | Gateway | Global | ✅ | ✅ | PaymentSheet; 3DS2; Google Pay rides Stripe as the gateway |
| **Google Pay** | Method | Global | ✅ | ✅ | `ENVIRONMENT_TEST`, processed via Stripe |
| **Square** | Gateway | Global | ✅ | ✅ | In-App Payments SDK; real card-entry tokenization |
| **Omise** | Gateway | SE Asia | ✅ | ✅ | Manual tokenization; sandbox public/secret keypair |
| Paytm All-in-One | Gateway | India | ⚠️ | ✅ | Hosted-webview, `MOCK_MODE`, real MID needs business KYC |
| PhonePe PG | Gateway | India | ❌ | 📄 | MID needs business KYC, documented only |
| Juspay HyperSDK | Orchestrator | India | ❌ | 📄 | Credentials are B2B-issued, documented only |
| PayU | Gateway | India | ⚠️ | 📄 | Hosted-webview `MOCK_MODE`; shared test creds still a roadmap item |
| Play Billing | IAP |, | ⚠️ | 🔜 | Needs a Play Console listing, later milestone |

✅ runnable in sandbox · ⚠️ runs in `MOCK_MODE` (real integration code, no business KYC yet) ·
📄 catalog + docs only (gated) · 🔜 planned

## Screens & flows

Four screens behind one center action, not a flat list of tabs:

- **Home**: the front door. A gradient hero card with an animated gateway-count and success-rate
  stat (both server-derived, not hardcoded), a recent-activity preview pulled from the same Room
  journal every other screen reads, and one tap into Explore.
- **Explore** (the Integration Lab), catalogs every provider with a status badge *and* its real
  logo or a deterministic monogram fallback. Tapping one opens a lab that executes the full
  lifecycle and renders it as a **live timeline**: order created → SDK launched → client result →
  server verification → settled, each step expandable to its actual redacted payload. This is the
  teaching surface, the sequence, and the fact that a client `Success` still has to pass through
  *Verifying* before it's trusted, is the whole point.
- **Checkout**: reached via the bottom bar's center "Pay" FAB, not a peer tab: a product checkout
  (cart → pay) that reuses the exact same provider registry and orchestrator, with an inline
  mini-timeline so the "normal" flow is explained as it runs.
- **Activity** (was History), the transaction log, streamed from the Room journal, with status
  filter chips to narrow it down.

Every payment-bearing screen (Explore's provider lab, Checkout) opens with a brief `ShieldPulse`
shield-icon draw-in, visual reassurance that lines up with the real `FLAG_SECURE` protection
already active underneath it (screenshots/recording blocked). Motion elsewhere is deliberate, not
decorative: a shimmer on the `MOCK_MODE` badge, `SuccessBurst`/`FailureShake` terminal feedback, a
scramble-to-mask `RedactionReveal`, and an animated `PaymentFlowDiagram` that visualizes the
client-success-is-not-server-truth trust boundary as a moving packet. Every animated component
reads `LocalReducedMotion`, so system-level reduce-motion is honored everywhere, not bolted onto
one screen.

Rather than a wall of stills, the app is shown below as **animated journeys**: each GIF walks a
real multi-screen flow. Every frame is a deterministic JVM Roborazzi render (Robolectric, **no
emulator**), stitched with ffmpeg; nothing is mocked or hand-drawn. Refresh the frames with
`./gradlew :app:recordRoborazziDebug`, rebuild the GIFs with `scripts/make-flow-gif.sh`.

#### Flow 1 · Explore → verify, the teaching path

Home dashboard → the provider catalog → a provider's lab running the full lifecycle → **settled only
after server verification**. The whole idea is in the last two beats: a client `Success` is just a
hint, and the flow makes you watch it pass through *Verifying* before anything is trusted.

<div align="center">
<img src="docs/gifs/explore_verify_flow.gif" alt="Home dashboard to provider catalog to a running provider lab to a server-verified settled state" width="320" />
</div>

#### Flow 2 · Product checkout, the everyday happy path

The bottom bar's center **Pay** FAB: pick a product and a gateway, review the order, then watch the
inline mini-timeline pay and settle. Same provider registry and orchestrator as the lab, just
wrapped in a cart, so the "normal" purchase is explained as it runs.

<div align="center">
<img src="docs/gifs/checkout_flow.gif" alt="Checkout order summary to a paying mini-timeline to a settled success state" width="320" />
</div>

#### Flow 3 · Activity, the transaction journal

The full log streamed from the Room journal every other screen writes to, then narrowed with the
status filter chips.

<div align="center">
<img src="docs/gifs/activity_flow.gif" alt="Activity journal showing all transactions, then filtered to successful ones" width="320" />
</div>

Real gateway logos where a rights-cleared source exists, a generated monogram everywhere else, the
same `GatewayBranding` registry Explore's provider cards use:

<div align="center">
<img src="docs/screenshots/gateway_brand_badges.png" alt="Real logos (Stripe, PayPal) next to generated monogram fallbacks" width="320" />
</div>

<details>
<summary><b>Every screen, still</b>, the individual Roborazzi frames the flows are built from</summary>

<br/>

| Home | Explore (catalog) | Provider lab (running) |
|:---:|:---:|:---:|
| ![Home dashboard with animated gateway count and success rate](docs/screenshots/home_screen_dashboard.png) | ![Provider catalog with status badges and region filter](docs/screenshots/lab_home_screen_catalog.png) | ![Provider lab mid-run with a live payment timeline](docs/screenshots/provider_lab_screen_running.png) |

| Provider lab (settled) | Checkout (order summary) | Checkout (settled) |
|:---:|:---:|:---:|
| ![Provider lab settled after server verification](docs/screenshots/provider_lab_screen_settled_success.png) | ![Checkout order summary with product and gateway selected](docs/screenshots/checkout_screen_order_summary.png) | ![Checkout settled with a success outcome summary](docs/screenshots/checkout_screen_settled_success.png) |

| Activity (all) | Activity (filtered) | Live timeline component |
|:---:|:---:|:---:|
| ![Activity journal listing all transactions](docs/screenshots/history_screen_all.png) | ![Activity journal filtered to successful transactions](docs/screenshots/history_screen_with_filters.png) | ![Step timeline component showing the verify boundary](docs/screenshots/step_timeline_light.png) |

Screenshots are generated on the JVM (Robolectric, no emulator) and committed to
[`docs/screenshots/`](docs/screenshots/), see `ScreenshotCatalogTest`.

</details>

### Also runs on iOS

`ios/shared` packages the full 5-screen app, Home, Explore, provider lab, Checkout, Activity, the
same shared bottom-bar-plus-FAB chrome as Android, into a real `.framework`, consumed by a genuine
Xcode project at `ios/iosApp/`. Navigation is a plain `remember`-backed state machine instead of
Android's `NavController` (Navigation3 is Android-only), but the screen set is identical. The
screenshot below is a real iOS Simulator run of the same Compose UI, not a mockup:

<div align="center">
<img src="docs/screenshots/ios_catalog.png" alt="The same catalog UI running on iOS" width="320" />
</div>

**Native-SDK gateways aren't Android-only either, most of them ship real iOS SDKs.** All five
Stripe, Razorpay, Cashfree, Omise, and Square, are built as real native-SDK integrations on iOS,
the same pattern each time: a small Kotlin interface Kotlin/Native exports as a plain Objective-C
protocol, implemented in Swift against the vendor's real SDK (Kotlin/Native can't cinterop against
a Swift-only framework directly, so the direction has to run this way). Google Pay has no iOS
equivalent at all, Apple Pay is a separate Apple product, not a Google Pay port.

| Gateway | iOS SDK | Docs |
|---|---|---|
| Stripe | `StripePaymentSheet` (SPM, `26.1.0`) | [stripe-ios.md](docs/providers/stripe-ios.md) |
| Razorpay | `RazorpayCheckout` (SPM, `razorpay-pod` `1.5.4`) | [razorpay-ios.md](docs/providers/razorpay-ios.md) |
| Cashfree | `CashfreePGUISDK` Drop Checkout (SPM, `core-ios-sdk`) | [cashfree-ios.md](docs/providers/cashfree-ios.md) |
| Omise | `OmiseSDK` manual tokenization (SPM, `5.6.3`) | [omise-ios.md](docs/providers/omise-ios.md) |
| Square | `SQIPCardEntryViewController` (CocoaPods, `1.6.7`, no SPM distribution exists) | [square-ios.md](docs/providers/square-ios.md) |

<div align="center">
<img src="docs/screenshots/ios_catalog_all_native.png" alt="Stripe, Razorpay, Cashfree, Omise and Square all showing in the iOS catalog, real SDKs linked" width="320" />
</div>

Build it yourself, **Square's CocoaPods dependency means the workspace, not the bare project, is
now the entry point**:

```bash
cd ios/iosApp
pod install   # needs Ruby >=3.0 for CocoaPods; see docs/providers/square-ios.md if your system Ruby is older
xcodebuild -workspace iosApp.xcworkspace -scheme iosApp -sdk iphonesimulator build
```

The `EmbedKotlinFramework` build phase runs the Gradle framework build automatically; the first
`xcodebuild` also resolves the four SPM-based SDKs.

## Architecture

The heart of the design is a deliberately tiny, platform-agnostic contract, with all the messy
platform reality pushed to the edges.

```kotlin
interface PaymentGateway {
    val id: GatewayId
    val meta: GatewayMeta
    suspend fun prepare(created: CreatedOrder): PreparedPayment
    suspend fun pay(host: PaymentHost, prepared: PreparedPayment): PaymentResult
}
```

- **`PaymentHost`** is opaque in `commonMain`, it carries no platform types, so the contract stays
  multiplatform. On Android the concrete host owns the `ComponentActivity` + ActivityResult plumbing
  and bridges each SDK's callback back into the suspending `pay()` call. This indirection is what lets
  Compose-era, coroutine-based feature code drive Activity-callback-era gateway SDKs without leaking
  Activity references upward.
- **`PaymentOrchestrator`** (in `core:orchestration`) coordinates one payment across four
  collaborators, the registry, the backend (server truth), the gateway SDK (client hint) and the
  journal (crash insurance), and emits a `Flow<PaymentStep>` the Lab renders as a timeline. Because
  every collaborator is an interface, the whole flow is exercised in `commonTest` with fakes: no
  Android, no network, no real SDK.
- **The backend** mirrors the same plugin shape: thin Ktor routes over per-provider `GatewayAdapter`s.

### System diagram

The registry contributes gateways; the orchestrator coordinates one payment across four collaborators;
the Ktor backend owns truth via order/verify/webhook plus the five money-movement rails.

```mermaid
flowchart TD
    UI["Compose UI<br/>(Home · Explore · Checkout · Activity)"] --> ORCH["PaymentOrchestrator<br/>core:orchestration<br/>(effectful shell)"]
    ORCH --> FSM["Pure reducer<br/>(State,Event)-&gt;Effects"]

    subgraph REG["Gateway registry — Koin getAll&lt;PaymentGateway&gt;()"]
        NATIVE["7 native-SDK modules<br/>razorpay · stripe · cashfree<br/>square · omise · googlepay · upi-intent"]
        GENERIC["2 generic archetypes<br/>hosted-webview (47) · mobile-money (8)"]
        RAILMOD["rail-backed modules<br/>paystack · flutterwave · paytm · xendit<br/>mpesa · peach · nmi · wallet · cash · connect"]
    end

    ORCH -->|"client hint"| REG
    REG --> HOST["PaymentHost<br/>(Android: Activity + ActivityResult<br/>bridged to suspend fun)"]
    ORCH -->|"journal-first"| JOURNAL[("Room journal<br/>core:data<br/>process-death recovery")]

    ORCH -->|"server = truth"| BE["Ktor backend"]

    subgraph BACKEND["backend — Ktor JVM (source of truth)"]
        PAYIN["Pay-in<br/>/orders · /verify (HMAC-SHA256)<br/>/webhooks (idempotent)"]
        RAILS["Rails<br/>/payouts · /mandates · /vault<br/>/connect · /wallet"]
    end

    BE --> BACKEND

    WORKER["PaymentReconciliationWorker<br/>WorkManager"] -.->|"cold-start reconcile"| JOURNAL
    WORKER -.-> BE
```

### Module map

```
Local (this repo, 14 include()s in settings.gradle.kts)
────────────────────────────────────────────────────────
app/                       Android composition root — Koin wiring, navigation, AndroidPaymentHost
backend/                   Ktor JVM server — orders, verify, webhooks, status (server = truth),
                           plus payout / mandate / vault / connect / wallet-ledger rails
core/
  config/                  Env-backed credential resolution (PLAB_* keys), MOCK_MODE auto-degrade  (KMP)
  protocol/                @Serializable wire DTOs shared with the backend's JVM target    (KMP + jvm)
  orchestration/           the effectful shell + fsm/ — a PURE (State,Event)->Effects reducer it
                           drives; the tested heart (journal-first, server-as-truth, replayable)
  network/                 Ktor client implementing PaymentBackend
  data/                    Room KMP journal (process-death recovery)
  designsystem/            Compose Multiplatform theme, tokens, StepTimeline, Motion Kit
                           (shimmer, terminal feedback, flow diagram, ShieldPulse), PayloadCard,
                           GatewayBranding (real logo / monogram fallback), AppShell (nav chrome)
  common/                  UiText, KMP logging, initKoin()/platformModule() (iOS-ready DI entry point)
feature/
  home/                    dashboard — animated stats, recent activity (the app's front door)
  lab/                     provider catalog (Explore) + live per-provider lab timeline
  checkout-demo/           product checkout, reached via the bottom bar's center FAB
  history/                 transaction log from the Room journal (Activity), with status filters
ios/shared/                iOS-facing KMP surface (see iOS readiness)
app/ .../work/             PaymentReconciliationWorker — WorkManager process-death reconciliation
build-logic/               convention plugins (kmp.library / kmp.compose / cmp.feature / …)

Composed via includeBuild("external/kmp-toolkit") + dependency substitution
(25 modules — none of these have a local build.gradle.kts; the local core/security
and provider/* directories are stale build/ cache left over from the extraction)
────────────────────────────────────────────────────────
payments-api/              The frozen contract: PaymentGateway, PaymentResult, PaymentHost,
                           PaymentBackend, PendingPaymentJournal, PaymentStep, Redactor   (KMP + jvm)
security/                  Keystore AES-256-GCM store, FLAG_SECURE, device-integrity, pinning config
common/, mvi-core/, network/, designsystem/     shared KMP infra (see Shared infrastructure)
provider/
  razorpay/ cashfree/ upi-intent/ stripe/ googlepay/ square/ omise/  one module per native SDK
  hosted-webview/          generic archetype for SDK-less, redirect-and-return-URL gateways (47)
  mobile-money/            generic archetype for confirm-on-payer's-phone flows (8)
  paystack/ flutterwave/ paytm/ xendit/ mpesa/ peach/ nmi/          dedicated modules (MOCK_MODE)
  wallet/                  internal double-entry ledger rail — pay from a stored balance
  cash/                    record-only "mark paid in cash" gateway (no SDK, no network)
  stripe-connect/          marketplace sub-merchant onboarding + split payouts
```

### Data flow (one payment)

1. UI → `PaymentOrchestrator.pay(host, gatewayId, catalogItemId)`.
2. Orchestrator → backend `POST /orders`. **Journal-first:** a pending row is written to Room
   *before* the SDK launches, the process-death insurance.
3. `gateway.prepare()` → `gateway.pay(host, …)` → the SDK UI / UPI chooser.
4. The callback is mapped to a `PaymentResult`. **The client result is a hint, never truth.**
5. Orchestrator → backend `verify` (signature check) and/or polls `GET /payments/{id}` with backoff.
   Server state, updated by webhooks, is the single source of truth.
6. Terminal state → journal row resolved → history; the Lab timeline renders every step + payload.

### Payment rails (beyond one-shot pay-in)

A real platform moves money in more than one direction. Each rail below is a thin, idempotent Ktor
route set over an in-memory store, mirroring the pay-in path's journal-first, server-as-truth
discipline, and every one is `MOCK_MODE`-honest until real keys are configured.

| Rail | Routes | What it models |
|---|---|---|
| **Payouts** | `POST /payouts`, `GET /payouts/{id}`, mock `settle` | Money *out* to a beneficiary, Paystack Transfers rides it |
| **Mandates / subscriptions** | `POST /mandates`, `…/debits`, `…/cancel` | Recurring authorization + scheduled debits (Razorpay-shaped) |
| **Card vault** | `POST /vault/{customer}/instruments`, `…/charge` | Tokenize an instrument once, charge later by id (Stripe / Peach / NMI) |
| **Connect** | `POST /connect/onboard`, `…/{account}/payouts` | Marketplace sub-merchant KYC onboarding + split payouts (Stripe Connect) |
| **Wallet ledger** | `POST /wallet/{acct}/{seed,debit,refund}`, `GET …/balance` | Internal double-entry balance, the `wallet` provider pays from it |
| **Split payments** | order with split legs | Two-leg orchestration that compensates if one leg fails |

## Shared infrastructure

PaymentsLab-KMP doesn't vendor its build and MVI plumbing, it consumes two standalone repositories as
Gradle [included builds](https://docs.gradle.org/current/userguide/composite_builds.html), the same
way [Doori](https://github.com/darkpandawarrior/Doori) does. Composite builds mean these aren't
copy-pasted boilerplate; a fix in either repo flows into every consumer.

| Repo | Role here | Wired in |
|---|---|---|
| **`kmp-build-logic`** | The convention plugins (`kmp.library`, `kmp.compose`, `cmp.feature`, …) that keep every module configured identically. Included in `pluginManagement` so every module applies them by id. | `settings.gradle.kts` → `includeBuild("external/kmp-build-logic")` |
| **`kmp-mvi-core`** (published as `com.siddharth.kmp:mvi-core`) | The `State`/`Event`/`Effect` MVI runtime the four `feature:*` modules build their ViewModels on. Substituted from the `:mvi-core` module of the kmp-toolkit monorepo at build time. | `settings.gradle.kts` → `includeBuild("external/kmp-toolkit")`; consumed by `feature:home`, `feature:lab`, `feature:checkout-demo`, `feature:history` |

This is the point of the split: the payments domain lives here, the reusable KMP scaffolding lives
once, upstream, and both this repo and its sibling stay on the same foundation without drift.

## Tech stack

| Layer | Technology |
|---|---|
| Language | Kotlin **2.4.20-Beta1** (K2) |
| UI | Compose Multiplatform **1.12.0-beta01**, Material 3 |
| Build | AGP **9.4.0-alpha04**, Gradle Kotlin DSL, convention plugins, version catalog |
| DI | Koin **4.2.2** (multiplatform) |
| Client networking | Ktor **3.5.1** client (OkHttp + Darwin engines) + kotlinx-serialization |
| Backend | Ktor **3.5.1** server (Netty), in-memory store (swappable for Exposed/SQLite) |
| Database | Room **2.8.4** (KMP, bundled SQLite), the pending-payment journal |
| Concurrency | Coroutines + Flow (no LiveData); `kotlinx-datetime`, immutable collections |
| Provider SDKs | Razorpay Checkout, Cashfree PG (api + ui), Stripe PaymentSheet, Play Services Wallet |
| Security | Android Keystore (AES-256-GCM), FLAG_SECURE, device-integrity, OkHttp CertificatePinner |
| Background | WorkManager (payment reconciliation), Koin-backed WorkerFactory |
| Testing | JUnit, MockK, Turbine, kotlinx-coroutines-test, Koin-Test, **Roborazzi** screenshots; fake-first |
| Quality | detekt **2.0.0-alpha.5**, ktlint, **dependency-guard**, **Kover** coverage floor, Compose stability config |
| Shrinking | **R8** full-mode (release minify + resource shrink; APK 42M→13M), payment-SDK keep rules, mapping.txt |
| Observability | `CrashReporter` abstraction (Napier default; Crashlytics/Sentry = one-line DI swap) |
| Variants | `debug` / `release` / **`vapt`** (flips security bypass flags); per-env `BACKEND_URL` |
| Release | Fastlane (versioning + build lanes), `release.yml` (tag → GitHub Release + R8 mapping) |
| Targets | Android (compileSdk **37**, minSdk **24**) + iOS-ready KMP core; **JDK 25** |

## Getting started

CI runs on **JDK 25** (`.github/workflows/ci.yml`) with no special pin in `gradle.properties`, the
earlier JDK 21 requirement was a `detekt` 1.23.x limitation (it couldn't run on JDK 23+); the
upgrade to detekt **2.0.0-alpha.5** lifted that constraint. Use whatever JDK Gradle picks up
locally, or match CI with `sdk use java 25-tem` / your platform's equivalent if you hit a
version-specific issue.

```bash
git clone https://github.com/darkpandawarrior/PaymentsLab-KMP.git
cd PaymentsLab-KMP

# 1. Start the backend (in-memory store; sandbox/test adapters) — serves on :8080
./gradlew :backend:run

# 2. Build & install the app (points at 10.0.2.2:8080 from the emulator)
./gradlew :app:installDebug
```

### Web preview (wasmJs, MOCK_MODE)

The gateway catalog + explained-checkout demo also run in the browser, `:web` is a
Kotlin/Wasm Compose shell over the same feature modules, orchestrator FSM and hosted-webview
archetype, with in-memory `PaymentBackend`/`PendingPaymentJournal` fakes instead of `:backend`
(so everything is `MOCK_MODE` by construction, no keys, no server). This build is what the
portfolio site embeds as `public/paymentslab-app/` (same packaging as Kursi's `cmp-web`).

```bash
# Dev loop with hot reload
./gradlew :web:wasmJsBrowserDevelopmentRun

# Production dist → web/build/dist/wasmJs/productionExecutable/
./gradlew :web:wasmJsBrowserDistribution
```

Native-SDK archetype-A providers (Stripe/Razorpay/Cashfree/…) are Android/iOS-only by
construction and are excluded from the wasm target; Room-backed History likewise.

Sandbox credentials are read from environment variables (see `backend/.env.example`); the app only
ever ships publishable keys, never secrets. Real end-to-end payment execution needs a device plus
each provider's sandbox keys, the SDK integrations compile and the flows are fully wired regardless.

Every gateway below the original three (Razorpay/Stripe/Cashfree) follows one convention
`PLAB_<GATEWAY>_<MODE>_<KEY>`, resolved by `core:config`'s `EnvCredentialStore`. Unset → the
gateway auto-degrades to `MOCK_MODE`; set → it upgrades to real, no code change either way.
`backend/.env.example` predates the Tier-1 real-SDK batch, so the current full set is:

| Gateway | Vars | Self-serve sandbox? |
|---|---|---|
| Paystack | `PLAB_PAYSTACK_TEST_SECRET_KEY` | Yes, paystack.com |
| PayPal | `PLAB_PAYPAL_TEST_CLIENT_ID`, `_CLIENT_SECRET` | Yes, developer.paypal.com |
| Square | `PLAB_SQUARE_TEST_APPLICATION_ID`, `_ACCESS_TOKEN`, `_LOCATION_ID` | Yes, developer.squareup.com/apps |
| Omise | `PLAB_OMISE_TEST_PUBLIC_KEY`, `_SECRET_KEY` | Yes, dashboard.omise.co/signup |

<details>
<summary><b>All build &amp; tooling commands</b></summary>

```bash
# The full local + CI gate: ktlint + detekt (all modules) + every unit test
./gradlew fastGate

# Build the debug APK
./gradlew :app:assembleDebug

# Individual test suites
./gradlew :backend:test                          # Ktor testApplication (real HMAC, webhook idempotency)
./gradlew :core:orchestration:testAndroidHostTest # the tested heart, against fakes
./gradlew :provider:razorpay:testDebugUnitTest    # SDK-callback → PaymentResult mapping

# Static analysis only
./gradlew ktlintCheck detekt
```

</details>

## Testing & quality

- **The tested heart.** `PaymentOrchestrator` is covered end-to-end in `commonTest` with fakes
  happy path timeline, journal-before-launch ordering, server-overrides-client-success, pending→poll,
  cancellation, and cold-start recovery. No Android, no network, no real SDK.
- **Fakes, not mocks.** ViewModels and the orchestrator are tested against fake repositories/gateways
  that implement the real interfaces; mocks are reserved for external services.
- **Backend.** Ktor `testApplication` asserts server-authoritative pricing, a **real** Razorpay
  HMAC-SHA256 pass/fail, idempotent webhook dedup, and 400s on unknown item/gateway.
- **Static analysis.** ktlint and detekt run across every module, detekt is pointed at the KMP
  `commonMain`/`androidMain` source sets, not just `src/main`, so the core and features are actually
  analyzed.
- **CI.** `.github/workflows/ci.yml` provisions JDK 25 and runs `fastGate` + `:app:assembleDebug` on
  every push and PR.
- **Distribution.** `release.yml` tags a build and publishes an unsigned APK to GitHub Releases
  (which also makes the app trackable via [Obtainium](https://github.com/ImranR98/Obtainium), no
  separate config needed, it just needs a tagged release with an APK asset). `play-deploy.yml`
  (internal/beta/production), `fdroid-deploy.yml` (reproducible `-Pfdroid` build), `indus-deploy.yml`
  (PhonePe Indus Appstore), `amazon-appstore-deploy.yml`, `huawei-appgallery-deploy.yml`,
  `samsung-galaxy-store-deploy.yml`, and `aptoide-deploy.yml` are all real pipelines gated on repo
  secrets, see `keystore.properties.template` and each workflow's header comment for what to
  configure. All are no-ops until secrets are set. **Uptodown** has no public submission API, it's
  a manual web-form upload (in the future, point it at the GitHub Release APK).

## Security posture

- Secret keys live only in backend environment variables; the APK ships publishable keys only.
  `backend/.env.example` documents every credential; nothing sensitive is hardcoded.
- Client success callbacks are never trusted, the server verifies, and webhooks reconcile.
- Webhook signatures are verified before processing, and handlers are idempotent (event-id dedup).
- Idempotency keys on order creation; the app retries the *status check*, never the charge.
- The `Redactor` allowlist masks any secret/PII-shaped field before it is rendered or logged.
- **At-rest:** `:security` (composed from `external/kmp-toolkit`) stores saved tokens with Android Keystore AES-256-GCM (non-exportable
  key, TEE/StrongBox-backed). **On-screen:** payment routes are `FLAG_SECURE`. **Device:** launch-time
  root/emulator/debugger inspection. **Transport:** a certificate-pinning config (placeholder pins
  here, the localhost backend is intentionally unpinned; the pattern is real).

## Roadmap

- Braintree (headless v5) · PayU with real shared test creds (the gateway is already wired in
  `MOCK_MODE`, this is about upgrading it, not building it)
- Refunds (full/partial) and real RBI network tokens, the saved-card **vault rail** ships in
  `MOCK_MODE` today (Stripe / Peach / NMI); this is the real-token upgrade
- Real e-mandate & Stripe Billing execution, the **mandate/subscription rail** ships in `MOCK_MODE`
  today (create / debit / cancel); this is wiring the real recurring debits
- Google Play Billing v8 (needs a Play Console listing)
- ~~Extract & publish `core:payments-api` as a standalone KMP library~~, done: it now lives in
  `external/kmp-toolkit`'s `:payments-api` module, pulled in via `includeBuild` + dependency
  substitution alongside `:security`, `:common`, `:mvi-core`, `:network`, `:designsystem` and all
  19 provider gateways (see [Module map](#module-map))
- Fix `scripts/gen-readme.sh`, the AUTOGEN stats block silently went stale after the provider
  extraction because `grep -c '^include(":provider:'` returns exit 1 on zero matches under `set -e`
- Expand `GatewayBranding`'s curated real-logo tier beyond the current 8 (the other 58 gateways
  render a generated monogram today, accurate, not broken, but a growing real-logo set would be
  nice)

## iOS readiness

The core is Kotlin Multiplatform, and iOS isn't a roadmap item anymore, it's a real, working app.
`ios/shared` packages the KMP-safe core plus all 5 native-SDK gateways with real Swift-side SDK
implementations (Stripe, Razorpay, Cashfree, Omise, Square) into a `.framework`, consumed by a
genuine Xcode project at `ios/iosApp/` with its own 5-screen navigation shell mirroring Android's.
See [Also runs on iOS](#also-runs-on-ios) above for the details and a real Simulator screenshot.

---

<div align="center">
<sub>Built as a portfolio flagship, a systems-level look at Android payments, not just a checkout screen.</sub>

<br /><br />

<sub>
<a href="https://cv-siddharth.vercel.app/">Portfolio</a> ·
<a href="https://github.com/darkpandawarrior/Doori">Doori, sibling KMP app (offline-first mileage/expense)</a> ·
Shared infra: <code>kmp-build-logic</code> · <code>kmp-mvi-core</code>
</sub>
</div>
