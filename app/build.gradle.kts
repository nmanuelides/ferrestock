import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// En CI los valores llegan como variables de entorno (secrets del repo).
fun secret(key: String): String = "\"${(localProps.getProperty(key) ?: System.getenv(key) ?: "").trim()}\""

// La versión sale de git: versionCode = cantidad de commits (CI publica cada build como release
// `build-<versionCode>` y la app compara contra eso para saber si hay una versión nueva).
fun git(vararg args: String): String = runCatching {
    providers.exec { commandLine("git", *args) }.standardOutput.asText.get().trim()
}.getOrDefault("")

val commitCount: Int = git("rev-list", "--count", "HEAD").toIntOrNull() ?: 1
val gitSha: String = git("rev-parse", "--short=10", "HEAD")
val appVersion = "1.$commitCount"

android {
    namespace = "com.ferrestock.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ferrestock.app"
        minSdk = 26
        targetSdk = 35
        versionCode = commitCount
        versionName = appVersion

        buildConfigField("String", "SUPABASE_URL", secret("SUPABASE_URL"))
        buildConfigField("String", "SUPABASE_ANON_KEY", secret("SUPABASE_ANON_KEY"))
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", secret("GOOGLE_WEB_CLIENT_ID"))
        buildConfigField("String", "UPDATE_REPO", "\"nmanuelides/ferrestock\"")
        buildConfigField("String", "GIT_SHA", "\"$gitSha\"")
    }

    signingConfigs {
        // Las actualizaciones solo se instalan encima si vienen firmadas con la misma clave.
        // CI la recibe por secrets; en local sale de local.properties. Sin ninguna, firma con debug.
        create("release") {
            fun signing(key: String): String? = System.getenv(key) ?: localProps.getProperty(key)?.trim()
            val keystore = signing("RELEASE_KEYSTORE")?.let { rootProject.file(it) }
            if (keystore != null && keystore.exists()) {
                storeFile = keystore
                storePassword = signing("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = signing("RELEASE_KEY_ALIAS")
                keyPassword = signing("RELEASE_KEY_PASSWORD")
            } else {
                initWith(getByName("debug"))
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.12.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.navigation:navigation-compose:2.9.6")
    implementation("androidx.exifinterface:exifinterface:1.4.1")

    // Supabase
    implementation(platform("io.github.jan-tennert.supabase:bom:3.2.6"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:storage-kt")
    implementation("io.ktor:ktor-client-okhttp:3.3.1")

    // Google sign-in (Credential Manager)
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Imágenes
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")
}

// Cada build release queda también en dist/ferrestock-v<versión>.apk (lo que CI publica).
val distRelease by tasks.registering(Copy::class) {
    from(layout.buildDirectory.file("outputs/apk/release/app-release.apk"))
    into(rootProject.layout.projectDirectory.dir("dist"))
    rename { "ferrestock-v$appVersion.apk" }
}
tasks.matching { it.name == "assembleRelease" }.configureEach { finalizedBy(distRelease) }
