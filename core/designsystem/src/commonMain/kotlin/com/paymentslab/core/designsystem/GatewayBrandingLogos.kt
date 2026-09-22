package com.paymentslab.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Real bundled brand marks: 8 gateway logos, all sourced from `simple-icons` (CC0 1.0) — see the
 * attribution comment above each path constant for the exact source. `upi_intent` has no curated
 * logo (no rights-cleared source was available) and falls back to its monogram badge via
 * [GatewayBranding.forId].
 *
 * Each [LogoPath] is one `<path>`'s `d`/`pathData` string plus its own fill color, parsed via
 * [PathParser] into a Compose [ImageVector] path — this works identically on Android and iOS since
 * none of this is Android-specific. Multi-path logos (Paytm) just supply more than one [LogoPath];
 * single-path logos (everything else) supply exactly one.
 *
 * API note: [ImageVector.Builder] has no `path { addPath(nodes) }` DSL — `PathBuilder` (the `path {
 * }` lambda receiver) only exposes discrete `moveTo`/`lineTo`/`curveTo`-style calls, not a way to
 * splice in a pre-parsed node list. The actual bridge is [ImageVector.Builder.addPath], which takes
 * a `List<PathNode>` directly — that's what [PathParser.toNodes] produces, so no lambda is needed.
 */
internal fun registerCuratedGatewayLogos() {
    registerCuratedLogo("stripe", LogoPath(BrandStripe, PathStripe))
    registerCuratedLogo("paypal", LogoPath(BrandPaypal, PathPaypal))
    registerCuratedLogo("googlepay", LogoPath(BrandGooglePay, PathGooglePay))
    registerCuratedLogo("square", LogoPath(BrandSquare, PathSquare))
    registerCuratedLogo("razorpay", LogoPath(BrandRazorpay, PathRazorpay))
    registerCuratedLogo("phonepe", LogoPath(BrandPhonePe, PathPhonePe))
    registerCuratedLogo("xendit", LogoPath(BrandXendit, PathXendit))
    // Exact `d` attribute from simple-icons/icons/paytm.svg (CC0 1.0).
    registerCuratedLogo("paytmaio", LogoPath(BrandPaytm, PathPaytm))
}

// Each brand's official mark color, taken verbatim from the `hex` field of the matching
// simple-icons entry (CC0 1.0) that also supplied the path below. These are the brands' own
// colors, not design-system tokens — they must NOT be themed, dark-mode-adapted or reused for
// anything but the logo they belong to, which is why they live here rather than in DesignTokens.
private const val BrandStripe = 0xFF635BFF
private const val BrandPaypal = 0xFF003087
private const val BrandGooglePay = 0xFF4285F4
private const val BrandSquare = 0xFF000000
private const val BrandRazorpay = 0xFF0C2451
private const val BrandPhonePe = 0xFF5F259F
private const val BrandXendit = 0xFF4573F6
private const val BrandPaytm = 0xFF002E6E

/** simple-icons publishes every mark on a `viewBox="0 0 24 24"`, so all 8 paths share this. */
private const val SimpleIconsViewportPx = 24f

/** Intrinsic size of a curated mark: Material's standard icon box, so it drops into any IconButton. */
private val LogoIntrinsicSize = 24.dp

/** One `<path>`: its fill color (as Long ARGB, matching the source file's hex) and its path data. */
private data class LogoPath(
    val colorArgb: Long,
    val pathData: String,
)

private fun registerCuratedLogo(
    id: String,
    vararg paths: LogoPath,
) {
    val builder =
        ImageVector.Builder(
            name = id,
            defaultWidth = LogoIntrinsicSize,
            defaultHeight = LogoIntrinsicSize,
            viewportWidth = SimpleIconsViewportPx,
            viewportHeight = SimpleIconsViewportPx,
        )
    paths.forEach { logoPath ->
        builder.addPath(
            pathData = PathParser().parsePathString(logoPath.pathData).toNodes(),
            // Verified, not copy-pasted: rendered all 9 curated logos (incl. hole letterforms like
            // Paytm's "a"/"p" and the Square cutout) under EvenOdd vs NonZero — pixel-identical in
            // every case, since the source paths wind holes opposite to their outer contour. Don't
            // assume this holds for a new gateway added here — re-verify before reusing EvenOdd.
            pathFillType = PathFillType.EvenOdd,
            fill = SolidColor(Color(logoPath.colorArgb)),
        )
    }
    GatewayBranding.curatedLogos[id] = builder.build()
}

// Exact `d` attribute from simple-icons/icons/stripe.svg (CC0 1.0) — fetched live via Step 1.
private const val PathStripe =
    "M13.976 9.15c-2.172-.806-3.356-1.426-3.356-2.409 0-.831.683-1.305 1.901-1.305 2.227 0 " +
        "4.515.858 6.09 1.631l.89-5.494C18.252.975 15.697 0 12.165 0 9.667 0 7.589.654 6.104 " +
        "1.872 4.56 3.147 3.757 4.992 3.757 7.218c0 4.039 2.467 5.76 6.476 7.219 2.585.92 " +
        "3.445 1.574 3.445 2.583 0 .98-.84 1.545-2.354 1.545-1.875 0-4.965-.921-6.99-2.109l-.9 " +
        "5.555C5.175 22.99 8.385 24 11.714 24c2.641 0 4.843-.624 6.328-1.813 1.664-1.305 " +
        "2.525-3.236 2.525-5.732 0-4.128-2.524-5.851-6.594-7.305h.003z"

// Exact `d` attribute from simple-icons/icons/paypal.svg (CC0 1.0) — fetched live via Step 1.
private const val PathPaypal =
    "M15.607 4.653H8.941L6.645 19.251H1.82L4.862 0h7.995c3.754 0 6.375 2.294 6.473 5.513-.648-.478" +
        "-2.105-.86-3.722-.86m6.57 5.546c0 3.41-3.01 6.853-6.958 6.853h-2.493L11.595 24H6.74l1.845" +
        "-11.538h3.592c4.208 0 7.346-3.634 7.153-6.949a5.24 5.24 0 0 1 2.848 4.686M9.653 5.546h6.408" +
        "c.907 0 1.942.222 2.363.541-.195 2.741-2.655 5.483-6.441 5.483H8.714Z"

// Exact `d` attribute from simple-icons/icons/googlepay.svg (CC0 1.0) — fetched live via Step 1.
private const val PathGooglePay =
    "M3.963 7.235A3.963 3.963 0 00.422 9.419a3.963 3.963 0 000 3.559 3.963 3.963 0 003.541 " +
        "2.184c1.07 0 1.97-.352 2.627-.957.748-.69 1.18-1.71 1.18-2.916a4.722 4.722 0 00-.07-.806H3" +
        ".964v1.526h2.14a1.835 1.835 0 01-.79 1.205c-.356.241-.814.379-1.35.379-1.034 0-1.911-.697" +
        "-2.225-1.636a2.375 2.375 0 010-1.517c.314-.94 1.191-1.636 2.225-1.636a2.152 2.152 0 011.52" +
        ".594l1.132-1.13a3.808 3.808 0 00-2.652-1.033zm6.501.55v6.9h.886V11.89h1.465c.603 0 1.11" +
        "-.196 1.522-.588a1.911 1.911 0 00.635-1.464 1.92 1.92 0 00-.635-1.456 2.125 2.125 0 00" +
        "-1.522-.598zm2.427.85a1.156 1.156 0 01.823.365 1.176 1.176 0 010 1.686 1.171 1.171 0 01" +
        "-.877.357H11.35V8.635h1.487a1.156 1.156 0 01.054 0zm4.124 1.175c-.842 0-1.477.308-1.907" +
        ".925l.781.491c.288-.417.68-.626 1.175-.626a1.255 1.255 0 01.856.323 1.009 1.009 0 01.366" +
        ".785v.202c-.34-.193-.774-.289-1.3-.289-.617 0-1.11.145-1.479.434-.37.288-.554.677-.554 " +
        "1.165a1.476 1.476 0 00.525 1.156c.35.308.785.463 1.305.463.61 0 1.098-.27 1.465-.81h.038v" +
        ".655h.848v-2.909c0-.61-.19-1.09-.568-1.44-.38-.35-.896-.525-1.551-.525zm2.263.154l1.946 " +
        "4.422-1.098 2.38h.915L24 9.963h-.965l-1.368 3.391h-.02l-1.406-3.39zm-2.146 2.368c.494 0 " +
        ".88.11 1.156.33 0 .372-.147.696-.44.973a1.413 1.413 0 01-.997.414 1.081 1.081 0 01-.69" +
        "-.232.708.708 0 01-.293-.578c0-.257.12-.47.363-.647.24-.173.54-.26.9-.26Z"

// Exact `d` attribute from simple-icons/icons/square.svg (CC0 1.0) — fetched live via Step 1.
private const val PathSquare =
    "M4.01 0A4.01 4.01 0 000 4.01v15.98c0 2.21 1.8 4 4.01 4.01h15.98C22.2 24 24 22.2 24 19.99V4" +
        "A4.01 4.01 0 0019.99 0H4zm1.62 4.36h12.74c.7 0 1.26.57 1.26 1.27v12.74c0 .7-.56 1.27-1.26" +
        " 1.27H5.63c-.7 0-1.26-.57-1.26-1.27V5.63a1.27 1.27 0 011.26-1.27zm3.83 4.35a.73.73 0 00" +
        "-.73.73v5.09c0 .4.32.72.72.72h5.1a.73.73 0 00.73-.72V9.44a.73.73 0 00-.73-.73h-5.1Z"

// Exact `d` attribute from simple-icons/icons/razorpay.svg (CC0 1.0) — fetched live via Step 1.
private const val PathRazorpay =
    "M22.436 0l-11.91 7.773-1.174 4.276 6.625-4.297L11.65 24h4.391l6.395-24zM14.26 10.098L3.389 " +
        "17.166 1.564 24h9.008l3.688-13.902Z"

// Exact `d` attribute from simple-icons/icons/phonepe.svg (CC0 1.0) — fetched live via Step 1.
private const val PathPhonePe =
    "M10.206 9.941h2.949v4.692c-.402.201-.938.268-1.34.268-1.072 0-1.609-.536-1.609-1.743V9.941zm" +
        "13.47 4.816c-1.523 6.449-7.985 10.442-14.433 8.919C2.794 22.154-1.199 15.691.324 9.243 " +
        "1.847 2.794 8.309-1.199 14.757.324c6.449 1.523 10.442 7.985 8.919 14.433zm-6.231-5.888a" +
        ".887.887 0 0 0-.871-.871h-1.609l-3.686-4.222c-.335-.402-.871-.536-1.407-.402l-1.274.401" +
        "c-.201.067-.268.335-.134.469l4.021 3.82H6.386c-.201 0-.335.134-.335.335v.67c0 .469.402" +
        ".871.871.871h.938v3.217c0 2.413 1.273 3.82 3.418 3.82.67 0 1.206-.067 1.877-.335v2.145c0" +
        " .603.469 1.072 1.072 1.072h.938a.432.432 0 0 0 .402-.402V9.874h1.542c.201 0 .335-.134" +
        ".335-.335v-.67z"

// Exact `d` attribute from simple-icons/icons/xendit.svg (CC0 1.0) — fetched live via Step 1.
private const val PathXendit =
    "M11.781 2.743H7.965l-5.341 9.264 5.341 9.263-1.312 2.266L0 12.007 6.653.464h6.454l-1.326 " +
        "2.279Zm-5.128 2.28 1.312-2.28L9.873 6.03 8.561 8.296 6.653 5.023Zm9.382-2.28 1.312 2.28" +
        "L7.965 21.27l-1.312-2.279 9.382-16.248Zm-5.128 20.793 1.298-2.279h3.83L14.1 17.931l1.312" +
        "-2.267 1.926 3.337 4.038-6.994-5.341-9.264L17.347.464 24 12.007l-6.653 11.529h-6.44Z"

// Exact `d` attribute from simple-icons/icons/paytm.svg (CC0 1.0).
private const val PathPaytm =
    "M15.85 8.167a.204.204 0 0 0-.04.004c-.68.19-.543 1.148-1.781 1.23h-.12a.23.23 0 0 0-.052.005h" +
        "-.001a.24.24 0 0 0-.184.235v1.09c0 .134.106.241.237.241h.645v4.623c0 .132.104.238.233.238h" +
        "1.058a.236.236 0 0 0 .233-.238v-4.623h.6c.13 0 .236-.107.236-.241v-1.09a.239.239 0 0 0" +
        "-.236-.24h-.612V8.386a.218.218 0 0 0-.216-.22zm4.225 1.17c-.398 0-.762.15-1.042.395v-.124a" +
        ".238.238 0 0 0-.234-.224h-1.07a.24.24 0 0 0-.236.242v5.92a.24.24 0 0 0 .236.242h1.07c.12 0 " +
        ".217-.091.233-.209v-4.25a.393.393 0 0 1 .371-.408h.196a.41.41 0 0 1 .226.09.405.405 0 0 1 " +
        ".145.319v4.074l.004.155a.24.24 0 0 0 .237.241h1.07a.239.239 0 0 0 .235-.23l-.001-4.246c0" +
        "-.14.062-.266.174-.34a.419.419 0 0 1 .196-.068h.198c.23.02.37.2.37.408.005 1.396.004 2.8" +
        ".004 4.224a.24.24 0 0 0 .237.241h1.07c.13 0 .236-.108.236-.241v-4.543c0-.31-.034-.442-.08" +
        "-.577a1.601 1.601 0 0 0-1.51-1.09h-.015a1.58 1.58 0 0 0-1.152.5c-.291-.308-.7-.5-1.153-.5z" +
        "M.232 9.4A.234.234 0 0 0 0 9.636v5.924c0 .132.096.238.216.241h1.09c.13 0 .237-.107.237" +
        "-.24l.004-1.658H2.57c.857 0 1.453-.605 1.453-1.481v-1.538c0-.877-.596-1.484-1.453-1.484H" +
        ".232zm9.032 0a.239.239 0 0 0-.237.241v2.47c0 .94.657 1.608 1.579 1.608h.675s.016 0 " +
        ".037.004a.253.253 0 0 1 .222.253c0 .13-.096.235-.219.251l-.018.004-.303.006H9.739a.239" +
        ".239 0 0 0-.236.24v1.09a.24.24 0 0 0 .236.242h1.75c.92 0 1.577-.669 1.577-1.608v-4.56a" +
        ".239.239 0 0 0-.236-.24h-1.07a.239.239 0 0 0-.236.24c-.005.787 0 1.525 0 2.255a.253.253 0 " +
        "0 1-.25.25h-.449a.253.253 0 0 1-.25-.255c.005-.754-.005-1.5-.005-2.25a.239.239 0 0 0" +
        "-.236-.24zm-4.004.006a.232.232 0 0 0-.238.226v1.023c0 .132.113.24.252.24h1.413c.112.017" +
        ".2.1.213.23v.14c-.013.124-.1.214-.207.224h-.7c-.93 0-1.594.63-1.594 1.515v1.269c0 .88.57" +
        " 1.506 1.495 1.506h1.94c.348 0 .63-.27.63-.6v-4.136c0-1.004-.508-1.637-1.72-1.637z" +
        "m-3.713 1.572h.678c.139 0 .25.115.25.256v.836a.253.253 0 0 1-.25.256h-.1c-.192.002" +
        "-.386 0-.578 0zm4.67 1.977h.445c.139 0 .252.108.252.24v.932a.23.23 0 0 1-.014.076.25" +
        ".25 0 0 1-.238.164h-.445a.247.247 0 0 1-.252-.24v-.933c0-.132.113-.239.252-.239Z"
