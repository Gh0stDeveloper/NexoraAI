plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android { namespace = "com.ghostnexora.ai"; compileSdk = 35
    defaultConfig { applicationId = "com.ghostnexora.ai"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "0.1.0-vps-ci" }
    buildTypes { debug { buildConfigField("String", "DEFAULT_API_BASE_URL", "\"http://10.0.2.2:3000/\"") }; release { isMinifyEnabled = false; buildConfigField("String", "DEFAULT_API_BASE_URL", "\"https://api.nexoraia.com/\"") } }
    buildFeatures { compose = true; buildConfig = true }
}
dependencies { implementation(platform("androidx.compose:compose-bom:2024.09.03")); implementation("androidx.activity:activity-compose:1.9.3"); implementation("androidx.compose.ui:ui"); implementation("androidx.compose.material3:material3"); implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6"); debugImplementation("androidx.compose.ui:ui-tooling") }
