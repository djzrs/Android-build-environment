import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

/**
 * 版本号策略：versionCode 跟仓库提交数走，每次 commit 必然递增，不需要人记。
 * 归档的 tag 包允许用 -PappVersionName=v1.3.0 覆盖显示版本，让「包内版本」和「Release 名」对得上。
 */
fun gitCommitCount(): Int = runCatching {
    val proc = ProcessBuilder("git", "rev-list", "--count", "HEAD")
        .directory(rootProject.projectDir)
        .redirectErrorStream(true)
        .start()
    val out = proc.inputStream.bufferedReader().use { it.readText() }.trim()
    proc.waitFor()
    out.toInt()
}.getOrDefault(0).coerceAtLeast(1)

val commitCount: Int = providers.gradleProperty("appVersionCode").orNull?.toIntOrNull()
    ?: gitCommitCount()
val appVersionName: String = providers.gradleProperty("appVersionName").orNull?.takeIf { it.isNotBlank() }
    ?: "1.2.$commitCount"

/**
 * 签名材料不进仓库。CI 从密钥仓库 imports 注入这四个变量，本地则写 .ci/signing.properties（已 gitignore）。
 * 四者缺一就不签名，产物退回 debug 签名——能装、能测，但上不了架。
 */
val signingProps = Properties().apply {
    val f = rootProject.file(".ci/signing.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun signingValue(env: String, key: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: signingProps.getProperty(key)?.takeIf { it.isNotBlank() }

val keystoreFile = signingValue("ANDROID_KEYSTORE_FILE", "keystoreFile")
val keystorePassword = signingValue("ANDROID_KEYSTORE_PASSWORD", "keystorePassword")
val keyAliasName = signingValue("ANDROID_KEY_ALIAS", "keyAlias")
val keyPasswordValue = signingValue("ANDROID_KEY_PASSWORD", "keyPassword")
val signingReady = listOf(keystoreFile, keystorePassword, keyAliasName, keyPasswordValue)
    .all { !it.isNullOrBlank() } && file(keystoreFile!!).exists()

android {
    namespace = "com.djzrs.htmlmanager"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.djzrs.htmlmanager"
        minSdk = 26
        targetSdk = 34
        versionCode = commitCount
        versionName = appVersionName
    }

    signingConfigs {
        if (signingReady) {
            create("release") {
                storeFile = file(keystoreFile!!)
                storePassword = keystorePassword
                this.keyAlias = keyAliasName
                this.keyPassword = keyPasswordValue
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signingReady) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

tasks.register("printVersion") {
    doLast {
        println("versionCode=$commitCount versionName=$appVersionName signing=${if (signingReady) "release" else "debug"}")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("com.google.zxing:core:3.5.3")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
