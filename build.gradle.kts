plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.androidKmpLibrary) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.ktor) apply false
    alias(libs.plugins.dependency.guard) apply false
    alias(libs.plugins.roborazzi) apply false
    // Code quality — applied to root, propagated to subprojects below
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
    alias(libs.plugins.ktlint)
}

detekt {
    config.setFrom(files("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    allRules = false
}

tasks.withType<dev.detekt.gradle.Detekt>().configureEach { jvmTarget = "21" }

dependencies {
    // Aggregate coverage from the logic-bearing modules (skip UI-only + generated-heavy ones,
    // which would just dilute the number). Guarded so the project still configures if a module moves.
    listOf(
        ":core:orchestration",
        ":core:network",
        ":core:data",
        ":backend",
    ).forEach { path -> findProject(path)?.let { kover(it) } }
}

kover {
    reports {
        filters {
            excludes {
                packages("*.BuildConfig", "*.R")
                classes("*Fake*", "*Test*")
            }
        }
        verify {
            rule {
                // Coverage floor across the aggregated logic modules — fails the build on regression.
                minBound(50)
            }
        }
    }
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "dev.detekt")
    apply(plugin = "org.jetbrains.kotlinx.kover")
    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        // Never lint generated code (Room KSP, etc.).
        filter { exclude { entry -> entry.file.path.contains("${"/build/"}") } }
    }
    extensions.configure<dev.detekt.gradle.extensions.DetektExtension> {
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        buildUponDefaultConfig = true
        baseline = file("detekt-baseline.xml")
        // detekt's default `detekt` task only scans src/main, which misses KMP entirely — hence
        // pointing it elsewhere. But the enumerated list this replaced omitted wasmJsMain, so
        // :web's production code was never scanned: a `// TODO:` in web/src/wasmJsMain passed clean.
        //
        // A list cannot hold. Adding a target adds a source set, and nothing fails when the list is
        // not updated to match — coverage shrinks silently while the build stays green. `src` covers
        // what exists now and what gets added later; the ktlint filter above already drops build/,
        // and detekt only reads .kt.
        source.setFrom(layout.projectDirectory.dir("src"))
    }
    tasks.withType<dev.detekt.gradle.Detekt>().configureEach { jvmTarget = "21" }
    tasks.withType<dev.detekt.gradle.DetektCreateBaselineTask>().configureEach { jvmTarget = "21" }
}

// ── Workflow task aliases: the local dev + CI verification loop ──────────────
tasks.register("fastGate") {
    description = "ktlint + detekt + unit tests + coverage floor + dependency lock: the fast CI gate."
    dependsOn("ktlintCheck", "detekt", "koverVerify")
    // `dependsOn("ktlintCheck", "detekt", ...)` above only binds the ROOT project's tasks — every
    // subproject applies both plugins (see the `subprojects` block) but its tasks never ran in CI
    // until they're wired in here too.
    subprojects.forEach { sub -> dependsOn("${sub.path}:ktlintCheck", "${sub.path}:detekt") }
    findProject(":app")?.let {
        dependsOn(":app:dependencyGuard")
        // verifyRoborazziDebug runs :app's unit tests (incl. ScreenshotCatalogTest) AND fails the
        // build if a rendered screen no longer matches its committed docs/screenshots/ baseline —
        // the actual verification recordRoborazziDebug's "diffs cleanly in PRs" claim depends on.
        dependsOn(":app:verifyRoborazziDebug")
    }
    findProject(":backend")?.let { dependsOn(":backend:test") }
    listOf(
        ":core:orchestration",
        ":core:data",
        ":core:network",
        ":feature:lab",
        ":feature:history",
        ":feature:checkout-demo",
        ":feature:home",
    ).forEach { path -> findProject(path)?.let { dependsOn("$path:testAndroidHostTest") } }
}

// iOS *test* binary linkage — a workaround for an UPSTREAM PACKAGING BUG, not for anything in this
// repo. `org.jetbrains.compose.ui:ui-uikit-<target>:1.13.0-alpha01` publishes its cinterop klib with
// JetBrains' own CI machine's Swift runtime path baked into `default/manifest`:
//   linkerOpts=… -L/Applications/Xcode_26.4.app/…/usr/lib/swift/iphonesimulator … -lswiftCompatibility51 …
// That directory exists on no other machine, so `ld` fails to find libswiftCompatibility51 and every
// iOS test binary that links Compose UI dies (:designsystem, :provider:hosted-webview). Appending the
// *real* toolchain's Swift lib dir restores the search path while keeping the version pin and without
// mutating the environment (symlinking a fake /Applications/Xcode_26.4.app would go green here and
// fail on CI and every other machine). Resolved from the active toolchain at configuration time on
// purpose: a second hardcoded absolute path is precisely the upstream bug.
// DELETE once that artifact stops shipping an absolute Xcode path — do not "tidy" it away before.
val swiftRuntimeLibDir: String? =
    if (org.gradle.internal.os.OperatingSystem
            .current()
            .isMacOsX
    ) {
        runCatching {
            // …/usr/bin/swift -> …/usr/lib/swift
            providers
                .exec { commandLine("xcrun", "--find", "swift") }
                .standardOutput.asText
                .get()
                .trim()
                .substringBeforeLast("/bin/swift") + "/lib/swift"
        }.getOrNull()
    } else {
        null
    }

if (swiftRuntimeLibDir != null) {
    subprojects {
        plugins.withId("org.jetbrains.kotlin.multiplatform") {
            extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension> {
                targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
                    val sdk =
                        when {
                            name.startsWith("iosSimulator") -> "iphonesimulator"
                            name.startsWith("ios") -> "iphoneos"
                            else -> return@configureEach
                        }
                    binaries.withType<org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable>().configureEach {
                        linkerOpts("-L$swiftRuntimeLibDir/$sdk")
                    }
                }
            }
        }
    }
}
