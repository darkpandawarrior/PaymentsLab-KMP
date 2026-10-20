# Decisions log

Settled choices for this repo, one line each, so a new session does not re-open them. Read before proposing an architectural change, and append a line when a decision is made.

- 2026-07-07: The server is the source of truth for payment state, and a client-side Success is only a hint that moves the orchestrator to Verifying. Why: price and HMAC signatures are checked server-side with webhook reconciliation, so a tampered or replayed client result changes nothing. (source: ec3a4ba, README.md trade-offs table)
- 2026-07-07: Payment lifecycle logic is a pure (State, Event) to Effects reducer, and PaymentOrchestrator only executes the effects. Why: the same event log then yields byte-identical effects, which gives the replayable audit trail money movement needs. (source: ec3a4ba, README.md trade-offs table)
- 2026-07-07: A gateway with no sandbox credentials degrades to an honest MOCK_MODE badge instead of faking success. Why: the demo must never silently lie about what is actually wired. (source: ec3a4ba, README.md trade-offs table)
- 2026-07-14: Shared code comes from the single kmp-toolkit monorepo submodule, replacing the three per-leaf submodules (kmp-common, kmp-mvi-core, kmp-security). Why: one includeBuild with plain coordinate substitutions replaces three blocks with path-collision workarounds, and the published artifact coordinates are unchanged. (source: c50b237)
- 2026-08-14: detekt scans all of src rather than an enumerated list of source sets. Why: the list omitted wasmJsMain so :web production code was never analysed, and an enumerated list shrinks coverage silently when a target is added. (source: 60f3322)
- 2026-08-14: Release APKs ship arm64 and armeabi-v7a only, with the emulator x86 and x86_64 ABIs dropped. Why: size hygiene for the release build. (source: 82e74f1)
- 2026-09-21: Gradle configure-on-demand is turned off. Why: the Kotlin Multiplatform plugin does not support it (KT-52074), and the configuration cache already gives the speedup it existed for. Measured cold configuration showed no meaningful difference. (source: 6df2e45)
