import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val appVersion = "1.0.0" // major.minor.patch
val (major, minor, patch) = appVersion.split(".").map { it.toInt() }

val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.reader()?.use(::load)
}
fun signingProp(key: String, env: String): String? = localProps.getProperty("release.$key") ?: System.getenv(env)

android {
    namespace = "de.munichspot"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "de.publicpoint"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = major * 10000 + minor * 100 + patch
        versionName = appVersion
    }
    signingConfigs {
        val storeFile = signingProp("storeFile", "MUNICHSPOT_STORE_FILE")
        if (storeFile != null) {
            create("release") {
                this.storeFile = file(storeFile)
                storePassword = signingProp("storePassword", "MUNICHSPOT_STORE_PASSWORD")
                keyAlias = signingProp("keyAlias", "MUNICHSPOT_KEY_ALIAS")
                keyPassword = signingProp("keyPassword", "MUNICHSPOT_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug").also {
                logger.warn("No release signing config (release.* in local.properties or MUNICHSPOT_* env); signing with the debug key.")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
}
