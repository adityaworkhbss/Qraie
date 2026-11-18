plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)

    id("com.google.gms.google-services")
}

android {

    namespace = "com.qryde.qraie"

    compileSdk = 35

    defaultConfig {

        applicationId = "com.qryde.qraie"

        minSdk = 24
        targetSdk = 35

        versionCode = 1
        versionName = "1.0.2"
    }

    flavorDimensions += "env"

    productFlavors {

        create("stg") {
            dimension = "env"
            versionName = "2.0.1"
            resValue("string", "app_name", "QRaie STG")
            buildConfigField("String", "WEB_URL", "\"https://hbss-bridgestg.qraie.ai/workplace/\"")
        }

        create("live") {
            dimension = "env"
            versionName = "1.0.2"
            resValue("string", "app_name", "QRaie")
            buildConfigField("String", "WEB_URL", "\"https://hbss-bridge.qraie.ai/workplace/en/hbss\"")
        }
    }

    buildTypes {

        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        getByName("debug") {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {

        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    applicationVariants.all {
        val flavor = flavorName
        val version = versionName
        val buildType = buildType.name
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output.outputFileName = if (buildType == "release") {
                "qraie-$flavor-$version.apk"
            } else {
                "qraie-$flavor-$version-$buildType.apk"
            }
        }
    }
}

afterEvaluate {
    android.applicationVariants.all {
        val flavor = flavorName
        val version = versionName
        val buildTypeName = buildType.name
        val variantName = name

        tasks.findByName("bundle${variantName.replaceFirstChar { it.uppercase() }}")?.doLast {
            val aabDir = project.layout.buildDirectory
                .dir("outputs/bundle/$variantName")
                .get().asFile
            aabDir.listFiles { f -> f.extension == "aab" }?.forEach { aab ->
                val newName = if (buildTypeName == "release") {
                    "qraie-$flavor-$version.aab"
                } else {
                    "qraie-$flavor-$version-$buildTypeName.aab"
                }
                aab.renameTo(File(aab.parent, newName))
            }
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)

    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.compose.ui)

    implementation(libs.androidx.compose.material3)

    implementation(platform(libs.firebase.bom))

    implementation(libs.firebase.messaging)
    implementation(libs.firebase.analytics)

    implementation(libs.material)

    implementation(libs.androidx.webkit)
    implementation(libs.androidx.security.crypto)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}