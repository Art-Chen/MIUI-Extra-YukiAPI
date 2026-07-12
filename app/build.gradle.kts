import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.*

plugins {
    alias(libs.plugins.agp.app)
    alias(libs.plugins.kotlin)
    alias(libs.plugins.ksp)
    alias(libs.plugins.lsplugin.apksign)
    alias(libs.plugins.lsplugin.resopt)
}

apksign {
    storeFileProperty = "KEYSTORE_FILE"
    storePasswordProperty = "KEYSTORE_PASSWORD"
    keyAliasProperty = "KEY_ALIAS"
    keyPasswordProperty = "KEY_PASSWORD"
}

val getGitCommitCount: () -> Int = {
    val output = ByteArrayOutputStream()
    ProcessBuilder("git", "rev-list", "--count", "HEAD").start().apply {
        inputStream.copyTo(output)
        waitFor()
    }
    output.toString().trim().toInt()
}

val getVersionCode: () -> Int = {
    val commitCount = getGitCommitCount()
    val major = 5
    major + commitCount
}

fun getGitHash(): String {
    val process = ProcessBuilder("git", "rev-parse", "--short", "HEAD").start()
    val output = process.inputStream.bufferedReader().use { it.readText().trim() }
    return output
}

fun getGitHashLong(): String {
    val process = ProcessBuilder("git", "rev-parse", "HEAD").start()
    val output = process.inputStream.bufferedReader().use { it.readText().trim() }
    return output
}

android {
    compileSdk = 35

    defaultConfig {
        applicationId = "moe.chenxy.miuiextra"
        minSdk = 31
        targetSdk = 35
        versionCode = 20
        versionName = "2.7.1-V-HyperOS3"
    }

    val gitCode = getVersionCode()
    val gitHash = getGitHash()
    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
            buildConfigField("String", "GIT_HASH", "\"$gitHash\"")
            buildConfigField("String", "GIT_CODE", "\"$gitCode\"")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            versionNameSuffix = "_${gitHash}_r${gitCode}"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            multiDexEnabled = true
            buildConfigField("String", "GIT_HASH", "\"$gitHash\"")
            buildConfigField("String", "GIT_CODE", "\"$gitCode\"")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            versionNameSuffix = "_${gitHash}_r${gitCode}"
        }
    }

    dependenciesInfo.includeInApk = false

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(JavaVersion.VERSION_22.majorVersion)
        }
    }

    kotlin {
        jvmToolchain(JavaVersion.VERSION_22.majorVersion.toInt())
        compilerOptions {
            jvmTarget = JvmTarget.JVM_22
        }
    }

    namespace = "moe.chenxy.miuiextra"

    buildFeatures {
        buildConfig = true
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/**.version"
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            excludes += "okhttp3/**"
            excludes += "kotlin/**"
            excludes += "org/**"
            excludes += "**.properties"
            excludes += "**.bin"
            excludes += "kotlin-tooling-metadata.json"
        }
    }
}

configurations.configureEach {
    exclude(group = "androidx.appcompat", module = "appcompat")
    exclude(group = "androidx.lifecycle", module = "lifecycle-viewmodel-ktx")
}

dependencies {
    implementation(libs.coordinatorlayout)
    implementation(libs.coreKtx)
    implementation(libs.material)
    implementation(libs.preferenceKtx)
    implementation(libs.rikkaxAppcompat)
    implementation(libs.rikkaxCore)
    implementation(libs.rikkaxInsets)
    implementation(libs.rikkaxMaterial)
    implementation(libs.rikkaxMaterialPreference)
    implementation(libs.rikkaxSimplemenuPreference)
    implementation(libs.rikkaxRecyclerviewKtx)
    implementation(libs.rikkaxBorderview)
    implementation(libs.rikkaxMainswitchbar)
    implementation(libs.rikkaxLayoutinflater)
    compileOnly(libs.xposedApi)
    implementation(libs.yukihookApi)
    ksp(libs.yukihookKsp)
}