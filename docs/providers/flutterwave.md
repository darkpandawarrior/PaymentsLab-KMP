# Flutterwave

- **Region:** Africa
- **Archetype:** a dedicated `provider:flutterwave` module built on top of
  `provider:hosted-webview`'s shared `HostedCheckoutRelay`, rather than either a native SDK or a
  generic Tier-3 hosted-webview config entry. The widest capability-per-gateway combo in the
  catalog on paper (cards + wallets + mobile-money + 3DS in one Flutterwave Standard/Rave checkout).
- **Status shipped:** `MOCK_MODE` — and, verified this session, **there is no backend adapter for
  it at all** (see below), so end-to-end checkout does not work today despite the client module
  being fully wired into the DI registry.
- **Docs:** https://developer.flutterwave.com/docs/collecting-payments/standard

## Why it gets a dedicated module instead of a Tier-3 config line

`FlutterwaveGateway.pay` reuses the exact same `HostedCheckoutRelay`/`HostedCheckoutHost` mechanics
every Tier-3 hosted-webview gateway (`allHostedGatewayConfigs`) rides — nothing about the checkout
plumbing is different. What this module owns natively instead of a config entry is the gateway
identity, `GatewayMeta`, and the return-URL contract, because Flutterwave's real Rave/Standard
checkout genuinely spans cards, bank transfers, USSD, and mobile-money (M-Pesa, MTN MoMo, Airtel
Money) with 3D Secure on the card path — `Capability` has no dedicated `MOBILE_MONEY`/`THREE_DS`
values yet, so that breadth is called out honestly in the `blurb` rather than invented as new enum
cases; the advertised capability flags (`ONE_TIME_PAYMENT`, `CARDS`, `WALLET`) only claim what the
existing enum can represent.

## Real vs mock — verified gap

Unlike every other single-instance gateway in this catalog, grepping the backend for `flutterwave`
(case-insensitive, across `backend/src/main/kotlin`) finds **zero matches**: no `FlutterwaveAdapter`
class, and `gatewayId = "flutterwave"` is not registered in `Application.module`'s `GatewayRegistry`.
Concretely, `POST /orders` with `gatewayId: "flutterwave"` returns `400 unknown_gateway` today — the
module's own KDoc claim that "the backend resolves the real `checkout_url` (genuine Flutterwave
payment link, or the mock fallback)" describes the *intended* design, not the current wiring. Closing
this needs either a dedicated `FlutterwaveAdapter` or registering it through the same
`mockHostedAdapter("flutterwave", "Flutterwave", …)` helper every Tier-3 gateway uses.

## Test coverage

**`FlutterwaveGatewayTest`** (pure-JVM, 4 tests) covers only the client-side outcome mapping, which
is fully reachable without the backend: a `HostedReturnOutcome.Success` maps its `paymentId` into
`verification`, a `Failure` maps to `GATEWAY_DECLINED`, `Cancelled` maps through, and the advertised
capability set (`ONE_TIME_PAYMENT`, `CARDS`, `WALLET`) is asserted. None of these tests exercise
`prepare()` or the missing backend path — there is no test proving (or able to prove, given the gap
above) that a real order round-trip works.

## Failure modes

`mapOutcome` normalizes `HostedReturnOutcome.Failure` to `FailureCode.GATEWAY_DECLINED` regardless of
`reason` (no finer-grained mapping the way Razorpay's `mapErrorCode` has); `Cancelled` maps straight
through to `PaymentResult.Cancelled`. Because `verify` on the (missing) backend side would ride the
same `HostedWebViewAdapter.verify` pattern as every Tier-3 gateway — always `PENDING`, resolved only
by a mock webhook/momo-flip — a client-reported success here is, same as everywhere else in this
catalog, a hint only, never trusted without server confirmation once the gap above is closed.
