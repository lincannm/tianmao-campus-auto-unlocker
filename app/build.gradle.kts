plugins {
    alias(libs.plugins.android.application)
    // Kotlin 本体由 AGP 内置，这里只挂 Compose 编译器插件（见根 build.gradle.kts）。
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.fucktmall.unlockassistant"
    // 必须是 37：androidx.core 1.19 / compose 1.12.1 的 AAR 元数据**硬性要求**编译目标 ≥ 37，
    // 用 36 会在 :app:checkDebugAarMetadata 直接失败（本机装的是 android-37.0）。
    compileSdk = 37

    defaultConfig {
        applicationId = "com.fucktmall.unlockassistant"
        minSdk = 24
        // targetSdk 留在 36：compileSdk 只决定"能用到哪些 API"，
        // targetSdk 决定"系统对 App 采用哪套新行为"。目标机是 Android 16 / SDK 36，
        // 没有理由为了编译顺手把运行时行为也一起换掉。
        targetSdk = 36
        // versionCode / versionName 只影响「关于」页显示和覆盖安装，跟行为无关。
        // ⚠️ 这两个值同时决定 CI 出包的 Release 名（tag = `v<versionName>`）：
        // 同名 Release 已存在时那条流水线会直接失败，所以每次要出包都得往上抬一格。
        versionCode = 8
        versionName = "3.6"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // ⚠️ 签名必须继续用原来那把自签名密钥（alias `unlockassistant`）：
    //   换密钥会让覆盖安装失败（INSTALL_FAILED_UPDATE_INCOMPATIBLE），
    //   而卸载重装会丢掉无障碍授权与偏好，HyperOS 还会把首次安装拦下来要用户手点。
    //   同一个密钥有两份副本：本仓库的 keystore/unlockassistant.jks（随源码进 Git，保证 clone 即可构建）
    //   与工作区的 work/unlockassistant.jks（老路径，仍可解析），下面优先取仓库内那份。
    //
    // ⚠️ 三个签名方案**必须显式全开**（AGP 在 minSdk>=24 时默认只签 v2）：
    //   v1（JAR 签名）是 HyperOS 安装器解析 APK 时要用的；只签 v2 时 `adb install` 能过，
    //   但在手机上点 APK 安装会失败，而那个失败与「首次安装被 HyperOS 拦」的报错一模一样，会让人误判。
    signingConfigs {
        create("shared") {
            val bundled = rootProject.file("keystore/unlockassistant.jks")
            storeFile = if (bundled.exists()) bundled else rootProject.file("../work/unlockassistant.jks")
            storePassword = "android"
            keyAlias = "unlockassistant"
            keyPassword = "android"
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("shared")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

// 不写 kotlin { compilerOptions { jvmTarget = ... } }：Kotlin 由 AGP 内置，
// AGP 会把 Kotlin 的 jvmTarget 对上上面 compileOptions 的 Java 目标，手写反而容易两边不一致。

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    // 用 BOM 统管 Compose 各组件版本：下面这些都不写版本号。
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
