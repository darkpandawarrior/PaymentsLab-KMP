import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Convention plugin for a plain (non-KMP) Android Compose library — used for modules that are
 * inherently Android-only, such as `core:security` (Keystore, FLAG_SECURE, device-integrity checks
 * are all `android.*` APIs). Applies AGP library + Compose compiler and the shared android config.
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
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
