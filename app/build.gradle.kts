import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.expandProjection", "true")
}

android {
    namespace = "com.pixelpals.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pixelpals.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 15
        versionName = "2.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // AdMob: IDs de PRUEBA de Google por defecto. Para ads reales define
        // (por orden de prioridad) pixelpals.admob.appId/bannerId en gradle.properties
        // o las variables de entorno PIXELPALS_ADMOB_APP_ID / PIXELPALS_ADMOB_BANNER_ID.
        val configuredAdMobAppId = (project.findProperty("pixelpals.admob.appId") as String?)
            ?: System.getenv("PIXELPALS_ADMOB_APP_ID")
        val configuredAdMobBannerId = (project.findProperty("pixelpals.admob.bannerId") as String?)
            ?: System.getenv("PIXELPALS_ADMOB_BANNER_ID")
        val adMobAppId = configuredAdMobAppId
            ?: "ca-app-pub-3940256099942544~3347511713"
        val adMobBannerId = configuredAdMobBannerId
            ?: "ca-app-pub-3940256099942544/9214589741"
        val adMobConfigured = !configuredAdMobAppId.isNullOrBlank() &&
            !configuredAdMobBannerId.isNullOrBlank()
        val umpTestDeviceId = (project.findProperty("pixelpals.ump.testDeviceId") as String?)
            ?: System.getenv("PIXELPALS_UMP_TEST_DEVICE_ID")
            ?: ""
        val umpDebugEea = (project.findProperty("pixelpals.ump.debugEea") as String?)
            ?.toBooleanStrictOrNull()
            ?: System.getenv("PIXELPALS_UMP_DEBUG_EEA")?.toBooleanStrictOrNull()
            ?: false
        val escapedUmpTestDeviceId = umpTestDeviceId
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        manifestPlaceholders["adMobAppId"] = adMobAppId
        buildConfigField("String", "ADMOB_BANNER_AD_UNIT_ID", "\"$adMobBannerId\"")
        buildConfigField("boolean", "ADS_ENABLED", if (adMobConfigured) "true" else "false")
        buildConfigField("String", "UMP_TEST_DEVICE_ID", "\"$escapedUmpTestDeviceId\"")
        buildConfigField("boolean", "UMP_DEBUG_EEA", umpDebugEea.toString())
    }

    signingConfigs {
        create("release") {
            val ksFile = System.getenv("PIXELPALS_KEYSTORE_FILE")
                ?: project.findProperty("pixelpals.ks.file") as String?
            if (!ksFile.isNullOrBlank()) {
                storeFile = file(ksFile)
                storePassword = System.getenv("PIXELPALS_KEYSTORE_PASSWORD")
                    ?: project.findProperty("pixelpals.ks.password") as String?
                keyAlias = System.getenv("PIXELPALS_KEYSTORE_ALIAS")
                    ?: project.findProperty("pixelpals.ks.alias") as String?
                keyPassword = System.getenv("PIXELPALS_KEY_PASSWORD")
                    ?: project.findProperty("pixelpals.key.password") as String?
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            // Los anuncios de prueba de Google funcionan sin cuenta AdMob.
            buildConfigField("boolean", "ADS_ENABLED", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // CI and local validation may exercise the full R8/release package
            // without production keystore credentials. Keep the release
            // artifact unsigned until a keystore is explicitly configured.
            signingConfig = signingConfigs.getByName("release")
                .takeIf { it.storeFile != null }
            // Sin IDs reales configurados, el release NO muestra anuncios.
            buildConfigField(
                "boolean",
                "ADS_ENABLED",
                if (
                    !((project.findProperty("pixelpals.admob.appId") as String?)
                        ?: System.getenv("PIXELPALS_ADMOB_APP_ID"))
                        .isNullOrBlank() &&
                    !((project.findProperty("pixelpals.admob.bannerId") as String?)
                        ?: System.getenv("PIXELPALS_ADMOB_BANNER_ID"))
                        .isNullOrBlank()
                ) {
                    "true"
                } else {
                    "false"
                }
            )
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    // AndroidX Core
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.appcompat:appcompat:1.7.1")

    // Material Design 3
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")

    // ConstraintLayout
    implementation("androidx.constraintlayout:constraintlayout:2.2.2")

    // Room Database
    val roomVersion = "2.8.4"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("androidx.test:runner:1.7.0")
    testImplementation("androidx.room:room-testing:$roomVersion")
    testImplementation("org.json:json:20240303")

    androidTestImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.room:room-testing:$roomVersion")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // Google Play Billing
    implementation("com.android.billingclient:billing-ktx:9.1.0")

    // AdMob (banner adaptativo en la tienda)
    implementation("com.google.android.gms:play-services-ads:25.4.0")

    // User Messaging Platform (consent before requesting ads)
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")
}
