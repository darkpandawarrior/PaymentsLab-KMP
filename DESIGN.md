# PaymentsLab-KMP DESIGN.md

> Inherits: house design standard (AgentHarness skill `design-md`). This file wins on conflict.
> Also inherits the shared kmp-toolkit `DESIGN.md` (present in `external/kmp-toolkit` once its pin
> includes it) for the toolkit pieces this app uses (`StepTimeline`, `AnimatedCounter`). This module
> has its own `DesignTokens`, which differ from the toolkit scale; this file wins for them.
> Agents: read this before creating or changing UI. Values live in
> `core/designsystem/src/commonMain/kotlin/com/paymentslab/core/designsystem/` (`Theme.kt`,
> `DesignTokens.kt`, `StatusColors.kt`, `Typography.kt`, `Motion.kt`).
> Dial: ENERGY 4 / RHYTHM 4 / MOTION 4

## Overview

PaymentsLab-KMP is a payments integration lab: a catalog of gateways, a checkout flow, status
timelines and a region coverage map, running mock or sandbox. The identity is a calm, credible
fintech surface: a deep indigo anchor ("Ledger Indigo"), an electric teal interactive accent
("Settlement Teal"), and a warm amber for gated or attention states. High contrast, AA on both
schemes, no neon. Light and dark follow the system.

A single "hero" accent (violet to pink gradient) is allowed for rare moments only: the Home hero
card, the centre Pay FAB area and success celebrations. Everything else stays on indigo and teal.

## Colors

Source: `Theme.kt` (`PaymentsLabLightColors`, `PaymentsLabDarkColors`).

| M3 role | Light | Dark |
|---|---|---|
| primary | #4F46E5 | #B9BBFF |
| onPrimary | #FFFFFF | #1E1B6B |
| primaryContainer | #E0E1FF | #37348F |
| onPrimaryContainer | #12106B | #E0E1FF |
| secondary | #0E9F8E | #64DDCB |
| onSecondary | #FFFFFF | #003731 |
| secondaryContainer | #B9F1E6 | #00504A |
| onSecondaryContainer | #00201C | #B9F1E6 |
| tertiary | #B57900 | #F7C066 |
| onTertiary | #FFFFFF | #3A2600 |
| tertiaryContainer | #FFDEA6 | #5A3D00 |
| onTertiaryContainer | #3A2600 | #FFDEA6 |
| background / surface | #FBFAFF | #121218 |
| onBackground / onSurface | #1B1B23 | #E4E1E9 |
| surfaceVariant | #E4E1EC | #47464F |
| onSurfaceVariant | #47464F | #C8C5D0 |
| surfaceContainer | #F1EFF7 | #1E1E26 |
| surfaceContainerHigh | #EBE9F3 | #292931 |
| outline | #787680 | #928F9A |
| outlineVariant | #C8C5D0 | #47464F |
| error | #BA1A1A | #FFB4AB |
| onError | #FFFFFF | #690005 |
| errorContainer | #FFDAD6 | #93000A |
| onErrorContainer | #410002 | #FFDAD6 |

Status tones (`StatusColors`, theme-independent, internal): Success #1E9E6A (settlement done,
sandbox ready), Warning #B57900 (KYC gated), Danger #CE3B3B (failed), Neutral #8A8894 (pending,
coming soon), Info #3B6FCE (mock mode: real code, simulated end to end).

Hero gradient (`PaymentsLabHeroGradient`): #7C3AED to #EC4899, left to right, same in both themes.
Gateway monograms hash to a fixed ten-colour palette in `GatewayBranding.kt` (violet #6D28D9, sky
#0EA5E9, red #DC2626, emerald #059669, amber #D97706, pink #DB2777, cyan #0891B2, lime #65A30D,
rose #E11D48, purple #7C3AED) with white letters. Real marks come from `GatewayBrandingLogos.kt`.

## Typography

Display face is Space Grotesk (OFL), supplied by the host app, not this module
(`app/src/main/kotlin/com/paymentslab/app/BrandTypeface.kt`; weights Regular, Medium, SemiBold,
Bold). `PaymentsLabTheme(displayFontFamily = ...)` patches it into displayLarge/Medium/Small,
headlineLarge/Medium/Small and titleLarge only. Body, title-medium/small and label slots stay on the
platform default at Material 3 stock metrics, because small text reads better in the system font.
A null font gives stock Material typography, a supported state. Do not bundle a font inside this
library module (it broke `Res` generation on Compose Multiplatform beta02 and beta03).

## Layout

`DesignTokens.Spacing` (4dp grid): xs 4, sm 8, md 12, lg 16, xl 24, xxl 32. Shell chrome
(`AppShell.kt`): bottom `NavigationBar` with a centre "Pay" FAB (24dp card icon, primary
container). Screens sit in `LabScaffold`. Money is formatted by `MoneyFormat.kt` and animates with
`AnimatedAmount`; do not format amounts by hand.

## Elevation & Depth

`DesignTokens.Elevation`: flat 0, raised 2 (alias `card`), floating 6 (hero card, FAB), overlay 12
(hosted webview overlay). Cards are `ElevatedCard`s (per the `DesignTokens` doc), with the
`card` elevation alias, unlike the toolkit's outlined cards.

## Shapes

`DesignTokens.Radius`: sm 8, md 12, lg 16 dp. `PrimaryButton` uses `Radius.md`. Material shape
defaults apply elsewhere; there is no custom `Shapes` scheme.

## Components

`PrimaryButton`, `AppShell` / `AppShellDestination`, `LabScaffold`, `SectionHeader`,
`GatewayStatusBadge` (shimmers in mock mode), `PaymentFlowDiagram`, `RegionCoverageMap`,
`ShieldPulse`, `AnimatedAmount`, `TerminalFeedback`, and `StepMapper` which maps domain steps to the
toolkit `StepTimeline`. Previews are in `Previews.kt`; app screenshots in `docs/screenshots`.
Landing page: `site/index.html`.

## Motion

`DesignTokens.Motion`: SHORT 150, MEDIUM 400, LONG 900 ms, `FastOutSlowInEasing`. `shimmer()` is a
1.4 s sweep that marks mock mode. Every animated component must check `LocalReducedMotion` first
(`Motion.kt`); the host wires it to the platform setting.

## Do's and Don'ts

- Do take colour from `MaterialTheme.colorScheme` or `StatusColors`; state is never colour alone,
  pair it with a label or icon.
- Do route spacing, radius, elevation and duration through `DesignTokens`.
- Do honour `LocalReducedMotion` in any new animation.
- Do use teal for interactive accents and amber only for gated or attention states.
- Don't use the hero gradient outside the hero moments listed above.
- Don't add a font to this module; take it from the host.
- Don't hard-code a hex in a feature module.
- Don't hand-format money; use `MoneyFormat` and `AnimatedAmount`.

## Known gaps

The hero gradient is a violet-to-pink sweep, a pattern the `antislop` filter is likely to flag.
It is a documented, rare accent, but treat any new use as needing a reason. `StatusColors` is
internal and duplicated in the toolkit `StepTimeline`; the two success and danger values match.

## Agent notes

- The code wins on any value here. If a hex differs, fix this file.
- No docs style guide exists beyond this file; `docs/RELEASE.md` covers release only.
- Run the `antislop` skill as the filter on any UI diff and report its Delivery Gate result.
