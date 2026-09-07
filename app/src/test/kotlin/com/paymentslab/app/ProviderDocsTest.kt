package com.paymentslab.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every gateway registered in [PaymentsLabApplication] carries a `docsPath` the Explore tab's "view
 * docs" link resolves. Nothing enforces that the referenced file actually exists — a renamed/deleted
 * doc, or a typo in a `docsPath`, breaks that link silently with no compile or runtime error. This
 * test asserts every registered `docsPath` resolves to a real file, checked two ways:
 *
 *  - The four config-driven categories ([allHostedGatewayConfigs], [allMobileMoneyConfigs],
 *    [allStubGatewayConfigs], [walletConfig]) expose `docsPath` directly on each config object, so
 *    those are read straight off the same lists [ReadmeGatewayCountTest] counts.
 *  - The 15 single-instance native-SDK modules set a literal `docsPath = "docs/providers/…"` in
 *    their own source (or, for `xendit`/`mpesa`, a config default of the same literal shape) —
 *    several of those gateway classes need an Android host/relay this plain-JVM test can't
 *    construct, so those are read by scanning `external/kmp-toolkit/provider`'s modules' source for that
 *    literal instead of instantiating each gateway.
 */
class ProviderDocsTest {
    private val repoRoot = File("..")

    private fun assertDocExists(
        docsPath: String,
        source: String,
    ) {
        assertTrue(
            "docsPath \"$docsPath\" (from $source) does not resolve to an existing file under $repoRoot",
            File(repoRoot, docsPath).isFile,
        )
    }

    @Test
    fun configDrivenGatewaysDocsPathsResolve() {
        allHostedGatewayConfigs.forEach { assertDocExists(it.docsPath, "hosted:${it.gatewayId.value}") }
        allMobileMoneyConfigs.forEach { assertDocExists(it.docsPath, "mobile-money:${it.gatewayId.value}") }
        allStubGatewayConfigs.forEach { assertDocExists(it.docsPath, "stub:${it.id.value}") }
        assertDocExists(walletConfig.docsPath, "wallet")
    }

    @Test
    fun singleInstanceGatewaysDocsPathsResolve() {
        val toolkitProviderRoot = File(repoRoot, "external/kmp-toolkit/provider")
        val sourceDirs =
            toolkitProviderRoot
                .listFiles { f -> f.isDirectory }
                .orEmpty()
                .flatMap { listOf(File(it, "src/main/kotlin"), File(it, "src/commonMain/kotlin")) }
                .filter { it.exists() }
        // Matches a literal docsPath assignment (including a Config class's default parameter value,
        // e.g. XenditConfig/MpesaConfig) but not a template like "docs/providers/$id.md" — the `$`
        // exclusion keeps the hosted/mobile-money/stub factories' interpolated paths out of this scan;
        // those are covered by configDrivenGatewaysDocsPathsResolve instead.
        val docsPathRegex = Regex("docsPath\\s*(?::\\s*String)?\\s*=\\s*\"(docs/providers/[^\"\$]+)\"")

        val found = mutableSetOf<String>()
        sourceDirs.forEach { dir ->
            dir
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file -> docsPathRegex.findAll(file.readText()).forEach { m -> found += m.groupValues[1] } }
        }

        assertTrue("expected to find literal docsPath declarations under kmp-toolkit/provider", found.isNotEmpty())
        found.forEach { assertDocExists(it, "kmp-toolkit provider source") }
    }
}
