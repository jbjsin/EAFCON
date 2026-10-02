plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.sphc.eafcon"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.sphc.eafcon"
        minSdk = 26
        targetSdk = 36
        versionCode = 11
        versionName = "1.1.6"

    }

    flavorDimensions += "distribution"
    productFlavors {
        create("dev") {
            dimension = "distribution"
            applicationIdSuffix = ".dev"
            resValue("string", "app_name", "EAFCON Dev")
        }
        create("prod") {
            dimension = "distribution"
        }
    }

    buildFeatures {
        compose = true
    }

    bundle {
        language {
            enableSplit = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

android.applicationVariants.all {
    outputs.all {
        val filePrefix = if (flavorName == "dev") "EAFCON_Dev" else "EAFCON"
        (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
            "${filePrefix}_${versionName}.apk"
    }
}

androidComponents {
    beforeVariants(selector().all()) { variantBuilder ->
        val distribution = variantBuilder.productFlavors
            .firstOrNull { it.first == "distribution" }
            ?.second
        variantBuilder.enable = when (distribution) {
            "dev" -> variantBuilder.buildType == "debug"
            "prod" -> variantBuilder.buildType == "release"
            else -> false
        }
    }
}

tasks.register<Copy>("archiveProdReleaseBundle") {
    dependsOn("bundleProdRelease")
    from(layout.buildDirectory.file("outputs/bundle/prodRelease/app-prod-release.aab"))
    into(layout.buildDirectory.dir("outputs/distribution"))
    rename { "EAFCON_${android.defaultConfig.versionName}_Play.aab" }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.usb.serial)

    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.json)
}
