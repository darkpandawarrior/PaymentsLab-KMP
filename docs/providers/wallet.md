# Wallet

- **Region:** Global
- **Archetype:** Archetype-E ("internal rail," per `WalletGateway`'s own KDoc) — backed entirely by
  the consumer's own backend double-entry ledger. No external SDK, no WebView, no 3DS, no webhook.
  Unlike every other archetype in this catalog, there is no external sandbox to be "ready" for: the
  ledger *is* the whole implementation, so `WalletConfig.status` defaults to `SANDBOX_READY`
  unconditionally.
- **Status shipped:** `SANDBOX_READY`.
- **Docs:** none — this is not a third-party integration; it is one of the app's five internal
  money-movement rails (payouts, mandates, vault, Connect, and this wallet ledger), documented
  alongside them in the top-level README rather than by an external provider's own docs.

## Flow — deliberately bypasses `/orders`

Unlike every gateway registered through `GatewayRegistry`/`POST /orders`, `WalletGateway` never goes
through the create-order/adapter-registry path at all:

- `prepare()` calls `GET /wallet/{accountId}/balance` directly (`WalletLedgerRoutes.kt`) and throws
  a `PaymentPreparationException` if the balance is insufficient — this is the pre-flight "balance
  race" check the catalog uses as its showcase for a provider that can fail before `pay()` even
  starts.
- `pay()` posts `POST /wallet/{accountId}/debit`, idempotency-keyed as `pay_${orderId}` so replaying
  the same order id never double-charges, and reports the ledger's own `txnId` as the payment id.
- There is no `WalletAdapter` in `Application.module`'s `GatewayRegistry` — a `gatewayId: "wallet"`
  order through `POST /orders` would 400 `unknown_gateway`, the same as Flutterwave (see
  `docs/providers/flutterwave.md`), except here that's by design, not a gap: the wallet rail's own
  `/wallet/*` routes are the real entry point, not `/orders`.

## Refund lives at the ledger layer, not on the gateway

Refund is deliberately **not** a method on `WalletGateway` or on the base `PaymentGateway` interface
— the interface has no refund method by design, so a reversing credit is exposed only via
`POST /wallet/{accountId}/refund` (`WalletLedgerRoutes.kt`), idempotent the same way debit is, and
netting against the same `WALLET_HOLDING_ACCOUNT_ID` holding account so the ledger always balances.

## Test coverage

- **`WalletGatewayTest`** (`commonTest`, 3 tests): `prepare` fails on insufficient balance, `prepare`
  succeeds when balance covers the order amount, and `pay` debits and returns `Success` with the
  ledger's txn ref.
- **`BackendTest.kt`**'s `wallet debit route moves balance and rejects overdraft` test exercises the
  real `/wallet/{accountId}/seed` → `/balance` → `/debit` round-trip end-to-end via Ktor
  `testApplication`, including the overdraft-rejection path, so this rail's ledger arithmetic is
  covered at both the gateway-unit and HTTP-integration level — unlike Flutterwave/Stripe/Cashfree,
  nothing here is stubbed.

## Failure modes

Insufficient balance is a `prepare()`-time failure (`PaymentPreparationException`), never surfaced as
a `pay()`-time `PaymentResult.Failure` — the pre-flight check exists specifically so the app can show
"insufficient balance" before ever attempting the debit. Any other HTTP/ledger error during `pay()`
(network failure, ledger rejecting the debit for a reason other than balance) maps to
`FailureCode.GATEWAY_DECLINED`; a `CancellationException` is deliberately rethrown rather than mapped,
so cancelling the coroutine (e.g. the screen going away) doesn't get misreported as a declined
payment.

## What is mocked

Nothing is mocked in the usual "waiting on a real sandbox key" sense — there is no external processor
to point this at. The ledger itself is real code (`LedgerStore`, double-entry, idempotent), just
scoped to one demo account (`wallet_demo_user`) seeded via `POST /wallet/{accountId}/seed`, which
exists purely for the demo and would not exist in a production ledger (real money would arrive via a
funding rail, not a seed endpoint).
