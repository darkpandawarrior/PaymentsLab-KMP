# UPI (raw intent)

- **Region:** India
- **Archetype:** no SDK, no partner onboarding — a raw NPCI `upi://pay` deep link handed to the
  system's UPI-app chooser. This does not fit the A/C/D lettered taxonomy the rest of the catalog
  uses (native SDK / hosted checkout / async mobile-money): there is no SDK to integrate and no
  hosted checkout page, just an Android `Intent`.
- **Status shipped:** `SANDBOX_READY` — but see the unverifiability warning below; "sandbox" here
  means "runs against real UPI apps," not "backed by a provider test environment."
- **Docs:** NPCI's `upi://pay` deep-link spec is not publicly hosted as a single canonical page;
  the parameter shape (`pa`/`pn`/`tr`/`am`/`cu`/`mc`) is the same one every UPI-intent integration
  guide (e.g. Razorpay's, PhonePe's) documents identically, since it is an NPCI-mandated format, not
  provider-specific.

## The unverifiability warning — the whole point of this gateway

`UpiIntentGateway`'s own KDoc calls this out as the single most important thing it teaches: the
`response` extra a UPI app returns to the calling Activity is **client-side and completely
unverifiable**. Any app (or a repackaged/malicious one) can return `Status=SUCCESS` without a rupee
moving — there is no client-side signature, and for a personal VPA (`pa=` pointing at an individual,
not a registered merchant) there is no transaction-status API to confirm against. A `Success` from
this gateway is a *hint only*; `UpiIntentAdapter.verify` on the backend always returns `PENDING`
regardless of what the client claims, and only an out-of-band bank/PSP webhook can resolve the truth.

## Flow

`prepare()` builds the `upi://pay` parameter map entirely from backend-supplied, trust-sensitive
fields — payee address (`pa`), payee name (`pn`), merchant category code (`mc`) — so the client can
never redirect the payment to itself; only the amount is derived from the server-set order amount.
NPCI mandates the generic Android chooser (`Intent.createChooser`) for this intent — targeting a
specific UPI package or pre-selecting one is not allowed — so `pay()` always shows every installed
UPI app. The result is parsed from the `response` extra's `&`-delimited key=value string
(`txnId=...&responseCode=...&Status=SUCCESS|FAILURE|SUBMITTED&txnRef=...`).

## Test coverage

**`UpiIntentGatewayTest`** (pure-JVM, 5 tests, Android `Intent`/`ActivityResult` mocked via MockK so
no emulator is needed): `Status=SUCCESS` maps to `Success` with `txnId` as the payment id;
`Status=SUBMITTED` maps to `Pending(UPI_SUBMITTED)`; `Status=FAILURE` maps to `Failure`; a
null/blank `response` (many UPI apps return no extras at all when the user backs out) maps to
`Cancelled`, not a failure; and an unrecognized/empty `Status` also maps to `Cancelled` rather than a
false success. On the backend, `BackendTest.kt` exercises UPI-intent order creation as part of its
idempotency tests, but `UpiIntentAdapter.verify`'s always-`PENDING` behavior needs no dedicated test
beyond that — it has no branch to get wrong.

## Failure modes

`SUBMITTED` is the interesting middle state this gateway introduces beyond plain success/failure:
some UPI apps report the request was sent to the payer's PSP but not yet resolved, which maps to
`PaymentResult.Pending(PendingReason.UPI_SUBMITTED)` rather than either terminal state — the
orchestrator's reconciliation path (the same one that recovers a process death mid-payment) is what
eventually resolves it via the backend webhook.

## What is mocked

Nothing client-side is mocked — this gateway genuinely launches whatever real UPI apps are installed
on the device. What's "mock" is the merchant identity: `payeeVpa`/`payeeName` are hardcoded demo
values (`UpiIntentAdapter`'s constructor args in `Application.module`) rather than a real registered
merchant VPA, and `merchantCategoryCode` is a fixed `"5411"` placeholder.
