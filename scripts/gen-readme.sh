#!/usr/bin/env bash
# Regenerates ONLY the <!-- AUTOGEN:x --> spans in README.md from source-of-truth in code.
# Hand-written prose outside the markers is never touched. See the Doori twin for notes.
set -euo pipefail
cd "$(dirname "$0")/.."

README="README.md"
SETTINGS="settings.gradle.kts"
CATALOG="gradle/libs.versions.toml"
SHOTS_DIR="docs/screenshots"

# grep -c exits 1 (not 0) when a pattern matches zero lines — under `set -e` that aborts the whole
# script. `|| true` keeps the "0" grep already prints on stdout while swallowing the non-zero exit,
# so a module class dropping to zero (e.g. the last :provider: include removed) no longer breaks it.

# --- local modules: `include(...)` in this repo's settings ---
local_total=$(grep -c '^include(' "$SETTINGS" || true)
local_features=$(grep -c '^include(":feature:' "$SETTINGS" || true)
local_cores=$(grep -c '^include(":core:' "$SETTINGS" || true)
local_providers=$(grep -c '^include(":provider:' "$SETTINGS" || true)
# whatever is left (app, iosApp, backend/server, …)
local_other=$(( local_total - local_features - local_cores - local_providers ))

# --- composed modules: substituted from includeBuild(external/kmp-toolkit) ---
# Each `substitute(module("com.siddharth.kmp:X")).using(project(...))` is one composed module.
# Those mapped to a `:provider:` project are payment gateways; the rest are shared core libraries.
composed_total=$(grep -cE 'substitute\(module\("com\.siddharth\.kmp:' "$SETTINGS" || true)
composed_providers=$(grep -cE 'using\(project\(":provider:' "$SETTINGS" || true)
composed_cores=$(( composed_total - composed_providers ))

grand_total=$(( local_total + composed_total ))
shots=$(find "$SHOTS_DIR" -maxdepth 1 -name '*.png' | wc -l | tr -d ' ')

# --- toolchain versions: read from the version catalog, not typed by hand ---
# Hand-typed version badges are the least checkable claim in a README: they look authoritative and
# nothing compares them to the build. These three had drifted to Kotlin 2.4.20-RC, Compose MP
# 1.12.0-rc01 and Ktor 3.5.1 while the catalog said 2.4.20, 1.13.0-alpha01 and 3.6.0.
# shields.io escaping: a literal '-' in a badge's message must be doubled.
catalog_version() { # $1 = TOML key at the start of a line in [versions]
  sed -n "s/^$1[[:space:]]*=[[:space:]]*\"\([^\"]*\)\".*/\1/p" "$CATALOG" | head -1
}
shield() { printf '%s' "${1//-/--}"; }

kotlin_v=$(shield "$(catalog_version kotlin)")
cmp_v=$(shield "$(catalog_version compose-multiplatform)")
ktor_v=$(shield "$(catalog_version ktor)")

versions="<!-- AUTOGEN:versions -->
![Kotlin](https://img.shields.io/badge/Kotlin-${kotlin_v}-7F52FF?logo=kotlin&logoColor=white)
![Compose Multiplatform](https://img.shields.io/badge/Compose%20MP-${cmp_v}-4285F4?logo=jetpackcompose&logoColor=white)
![Platforms](https://img.shields.io/badge/platforms-Android%20%7C%20iOS%20%7C%20Web-3DDC84)
![Ktor](https://img.shields.io/badge/Ktor-${ktor_v}-087CFA?logo=ktor&logoColor=white)
<!-- /AUTOGEN:versions -->"

badge="<!-- AUTOGEN:badge -->
![Modules](https://img.shields.io/badge/modules-${grand_total}-success)
<!-- /AUTOGEN:badge -->"

stats="<!-- AUTOGEN:stats -->
> **At a glance**, **${grand_total}-module** KMP architecture: **${local_total} local** (${local_cores} core · ${local_features} feature · ${local_other} app/iOS/backend) + **${composed_total} composed** via \`includeBuild(external/kmp-toolkit)\` (${composed_cores} shared core · ${composed_providers} payment-provider gateways), **${shots}** deterministic Roborazzi screenshots. *Numbers auto-generated from \`settings.gradle.kts\` by \`scripts/gen-readme.sh\`.*
<!-- /AUTOGEN:stats -->"

replace_block() {   # $1=tag  $2=replacement (marker lines included)
  TAG="$1" REPL="$2" perl -0777 -i -pe '
    s/<!-- AUTOGEN:\Q$ENV{TAG}\E -->.*?<!-- \/AUTOGEN:\Q$ENV{TAG}\E -->/$ENV{REPL}/s;
  ' "$README"
}

replace_block "versions" "$versions"
replace_block "badge" "$badge"
replace_block "stats" "$stats"
echo "[gen-readme] kotlin=$kotlin_v cmp=$cmp_v ktor=$ktor_v"
echo "[gen-readme] total=$grand_total (local=$local_total: ${local_cores}c/${local_features}f/${local_providers}p/${local_other}o + composed=$composed_total: ${composed_cores}c/${composed_providers}p) shots=$shots"
