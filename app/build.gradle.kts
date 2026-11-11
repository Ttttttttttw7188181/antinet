plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.antinet.vpn"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.antinet.vpn"
        minSdk = 24
        targetSdk = 37
        versionCode = 2
        versionName = "1.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val abiFilters = (project.findProperty("ABI_FILTERS") as? String)
            ?.split(';')
            ?.filter { it.isNotBlank() }
            .orEmpty()
        splits {
            abi {
                isEnable = abiFilters.isNotEmpty()
                reset()
                if (abiFilters.isNotEmpty()) {
                    include(*abiFilters.toTypedArray())
                }
                isUniversalApk = abiFilters.isEmpty()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/DEPENDENCIES",
        )
    }

    androidResources {
        noCompress += listOf("dat")
    }
}

dependencies {
    implementation(files("libs/libv2ray.aar"))
    implementation(files("libs/libbox.aar"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.okhttp)
    implementation(libs.gson)

    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.hiddenapibypass)

    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.zxing.core)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
