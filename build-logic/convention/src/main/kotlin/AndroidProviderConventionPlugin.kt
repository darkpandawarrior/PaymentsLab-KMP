import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Convention plugin for a `provider:*` module — a plain Android library that hosts one payment
 * provider's Android-only SDK integration and implements the `core:payments-api` `PaymentGateway`
 * contract (consumed via that KMP module's android target).
 *
 * Providers are deliberately NOT KMP: every gateway SDK (Razorpay, Cashfree, Stripe) is
 * Android-only. The commonMain contract stays platform-agnostic; only the impl is androidMain-shaped.
 * Compose is enabled so providers can supply small glue composables (e.g. Stripe PaymentSheet host).
 */
class AndroidProviderConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            // AGP 9 provides built-in Kotlin support — applying kotlin.android is no longer needed.
            apply("com.android.library")
            apply("org.jetbrains.kotlin.plugin.compose")
        }
        configureComposeCompilerMetrics()
        extensions.configure<LibraryExtension> {
            compileSdk = 37
            defaultConfig { minSdk = 24 }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_25
                targetCompatibility = JavaVersion.VERSION_25
            }
            buildFeatures {
                compose = true
                buildConfig = false
            }
        }
    }
}
