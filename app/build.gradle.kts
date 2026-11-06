plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "org.arcmediaserver.android"
    // 37: the current Compose, Navigation, Coil, Core and OkHttp libraries need it to compile
    // against; the app still targets 36 (runtime behaviour) and runs from 26.
    compileSdk = 37

    defaultConfig {
        applicationId = "org.arcmediaserver.android"
        // Android 8.0: older Android TV sets and boxes (Mi Box S, Sony and Philips TVs from 2018,
        // first Chromecast with Google TV is 10) are still in use; 8.0 brings Display.Mode and
        // adaptive icons, which the app relies on, and Media3 and Compose support it.
        minSdk = 26
        targetSdk = 36
        // GitHub's Build workflow passes its run number (-ParcVersionCode=N), so each build
        // installs over the one before; local builds stay at 1.
        versionCode = (project.findProperty("arcVersionCode") as String?)?.toIntOrNull() ?: 1
        versionName = "0.1.0"
    }

    // Arc's own key, when one is provided (ARC_KEYSTORE_FILE plus its passwords and alias, from
    // the Build workflow's repository secrets): builds signed with it install over each other on
    // every machine. Without it, release builds use this machine's debug key, as before.
    val arcKeystore = System.getenv("ARC_KEYSTORE_FILE")?.let(::file)?.takeIf { it.isFile }
    signingConfigs {
        if (arcKeystore != null) {
            create("arc") {
                storeFile = arcKeystore
                storePassword = System.getenv("ARC_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ARC_KEY_ALIAS")
                keyPassword = System.getenv("ARC_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8 shrinks and optimises the code: Compose is several times faster than in a
            // debug build, which matters on slow TV boxes (Chromecast with Google TV).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // No keys in git: Arc's key from the environment when there is one, else the debug key.
            signingConfig = signingConfigs.findByName("arc") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // Robolectric reaches into these on JDK 17 and later.
                it.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.base/java.io=ALL-UNNAMED")
                it.maxHeapSize = "2g"
            }
        }
    }
}

kotlin {
    jvmToolchain(21)
}

roborazzi {
    outputDir.set(layout.projectDirectory.dir("screenshots"))
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.tv.material)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.ui)
    implementation(libs.ass.media)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.coil.test)
    debugImplementation(libs.compose.ui.test.manifest)
}
