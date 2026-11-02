import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.streamshell"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.streamshell"
        minSdk = 26
        targetSdk = 35
        versionCode = 54
        versionName = "0.6.4"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Signing: never embed credentials. A release signing config is only created
    // when keystore.properties (git-ignored) exists next to the root build file.
    val keystoreProps = rootProject.file("keystore.properties")
    if (keystoreProps.exists()) {
        val props = Properties().apply { keystoreProps.inputStream().use { stream -> load(stream) } }
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
                // v2 for Android 7–8, v3 additionally (Android 9+): same key, but a later key change (rotation) stays possible.
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // Not minified: R8 problems would only show at run time, and this build is not device-tested yet.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystoreProps.exists()) signingConfig = signingConfigs.getByName("release")
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

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/LICENSE*")
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.window.size)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.window)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.car.app)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.org.json)
    testImplementation(libs.robolectric)
    // Renders Compose screens to PNG under Robolectric native graphics; used for the visual QA pass only.
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.ext.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}

// Robolectric (JVM UI tests) downloads its Android runtime jar at test time. Resolve it through Gradle
// instead, so it comes from the repositories declared in settings.gradle.kts, then run Robolectric offline.
// One configuration per SDK jar: in a single configuration Gradle's conflict resolution would keep only the newest version.
val robolectricRuntime34: Configuration by configurations.creating
val robolectricRuntime35: Configuration by configurations.creating

dependencies {
    robolectricRuntime34("org.robolectric:android-all-instrumented:14-robolectric-10818077-i7")
    robolectricRuntime35("org.robolectric:android-all-instrumented:15-robolectric-12650502-i7")
}

val robolectricDepsDir = layout.buildDirectory.dir("robolectric-deps")
val prepareRobolectricRuntime = tasks.register<Copy>("prepareRobolectricRuntime") {
    from(robolectricRuntime34, robolectricRuntime35)
    into(robolectricDepsDir)
}

tasks.withType<Test>().configureEach {
    dependsOn(prepareRobolectricRuntime)
    systemProperty("roborazzi.test.record", "true")
    systemProperty("roborazzi.output.dir", layout.buildDirectory.dir("outputs/screenshots").get().asFile.absolutePath)
    systemProperty("robolectric.offline", "true")
    systemProperty("robolectric.dependency.dir", robolectricDepsDir.get().asFile.absolutePath)
}
