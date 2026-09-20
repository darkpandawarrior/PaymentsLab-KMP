# iOS privacy manifest — what was declared, and the evidence for it

Apple requires `PrivacyInfo.xcprivacy` in every App Store submission. The `iosApp` target ships one
at `ios/iosApp/iosApp/PrivacyInfo.xcprivacy`, added to the target's Resources build phase.

The rule that drove every line below: **over-declaring is a false statement to Apple, and
under-declaring gets the submission rejected.** So each category was decided by scanning what this
binary actually links, not by assuming what a payments app usually touches.

## Target inventory

One native target, `iosApp` (`com.apple.product-type.application`). No widget, no share extension,
no watch app — so one manifest is the whole requirement. (Each *additional* bundle that used a
required-reason API would need its own; there are none.)

## Declared

| Category | Reason | Evidence |
|---|---|---|
| `NSPrivacyAccessedAPICategoryFileTimestamp` | `C617.1` | `nm -u libandroidXBundledSqlite.a` → `_stat`, `_fstat`, `_lstat`. Kotlin/Native `source_info_libbacktrace.bc` → `_stat`. |
| `NSPrivacyAccessedAPICategoryDiskSpace` | `E174.1` | `nm -u libandroidXBundledSqlite.a` → `_statfs`, `_fstatfs`. |

`androidx.sqlite:sqlite-bundled` statically links its own SQLite amalgamation into the
Kotlin/Native binary — `core/data/src/iosMain/.../PaymentsLabDatabaseBuilder.kt` uses
`BundledSQLiteDriver()`, with the database file under the app's own `Documents/` directory. Both
reasons are the container-scoped ones, which is exactly what SQLite does: stat its own file, and
check free space on that file's volume before growing it.

## Deliberately NOT declared

| Category | Why not |
|---|---|
| `NSPrivacyAccessedAPICategoryUserDefaults` (`CA92.1`) | Zero `NSUserDefaults` hits across `iosMain` and the Swift target. kmp-toolkit's `:settings` uses `KeychainSettings` on iOS — Keychain is not a required-reason API — and `:settings` is not on this app's link path anyway. |
| `NSPrivacyAccessedAPICategorySystemBootTime` (`35F9.1`) | No `systemUptime`, `mach_absolute_time` or `clock_gettime` anywhere. `nm -u` over the whole Kotlin/Native iOS runtime bitcode returns only `gettimeofday`, which is wall-clock and is not on Apple's list. |

## Tracking and collected data

- `NSPrivacyTracking` = `false`, `NSPrivacyTrackingDomains` = empty. No `ASIdentifierManager`, no
  `AppTrackingTransparency`, no `advertisingIdentifier`.
- `NSPrivacyCollectedDataTypes` = empty. No accounts, no analytics SDK, no crash reporter. Card
  details are typed into each vendor SDK's own UI (StripePaymentSheet, Razorpay Checkout, Cashfree
  Drop, Omise, Square card entry) and transmitted by that SDK, which carries its own
  `PrivacyInfo.xcprivacy` inside its framework — verified for `stripe-ios` 26.11.0. A third-party
  SDK's collection is declared by the SDK, not by the host app. What this app sends to its own
  backend is catalog item ids, minor-unit amounts, order ids and idempotency keys. The `customerId`
  field exists on the vault DTOs in `core:protocol` but no iOS feature calls that path.

## Re-running the scan

```bash
# the bundled SQLite static library inside the cinterop klib
unzip -q ~/.gradle/caches/modules-2/files-2.1/androidx.sqlite/sqlite-bundled-iossimulatorarm64/*/*/*Cinterop*.klib -d /tmp/k
nm -u /tmp/k/default/targets/ios_simulator_arm64/included/libandroidXBundledSqlite.a \
  | sort -u | grep -E "stat|getattr|uptime|mach_absolute|clock_gettime"

# the Kotlin/Native runtime itself
for f in ~/.konan/kotlin-native-prebuilt-macos-*/konan/targets/ios_arm64/native/*.bc; do
  nm -u "$f" | grep -E "_mach_absolute_time|_clock_gettime|_stat|_statfs" && echo "  ^ $f"
done
```
