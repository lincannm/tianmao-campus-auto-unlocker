plugins {
    alias(libs.plugins.android.application)
    // Kotlin 本体由 AGP 9 内置（见根 build.gradle.kts 的说明），这里只挂 Compose 编译器插件。
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.fucktmall.unlockassistant"
    // 必须是 37：androidx.core 1.19 / compose 1.12.1 的 AAR 元数据**硬性要求**编译目标 ≥ 37，
    // 用 36 会在 :app:checkDebugAarMetadata 直接失败。
    // 本机装的是 android-37.0（API 37 + minor 0），目录名就是这个格式。
    compileSdk = 37

    defaultConfig {
        applicationId = "com.fucktmall.unlockassistant"
        minSdk = 24
        // targetSdk 留在 36：compileSdk 只决定"能用到哪些 API"，
        // targetSdk 决定"系统对 App 采用哪套新行为"。目标机是 Android 16 / SDK 36，
        // 没有理由为了编译顺手把运行时行为也一起换掉。
        targetSdk = 36
        // UI 换成 Jetpack Compose + Material 3 之后升到 2.0；2.1 加了「授权被系统清掉后自愈」
        // （A11yGuard + WRITE_SECURE_SETTINGS，通道与根因见 docs/80-historical-2.0-2.1.md §1）；
        // 2.2 把无障碍声明改成 feedbackAllMask —— 让 HyperOS 的「划掉卡片」不再强停本App
        // （docs/20-core-mechanisms-and-invariants.md §1，硬不变量）；
        // 3.0 = 这一轮的交互改造：改名「开锁」、去掉授权自动恢复（连 A11yGuard 一起删）、
        //       时间窗 90 秒并换掉「会话」说法、首次打开只弹 toast、向导搬到长按图标→设置、
        //       向导完成即退出、红色主题、去掉自检按钮、设置顶部卡片加「了解本App」。
        // 3.1 = 桌面小部件（四张独立卡 + 可缩放）+ 图标换成双色锁（锁体 #E53935 / 锁梁 #EF5350）
        //       + 设置里两个「代按开锁」开关（小部件默认开、App 图标默认关）。
        // 3.2 = 界面收敛：日志加 HH:mm:ss 时间戳、向导删掉「允许后台运行」那一步（3 步 → 2 步）、
        //       删掉「打开本App自动跳到开锁界面」开关（自动跳转固定常开）、两个代按开关换序
        //       （打开 App 在前）、界面上不再交代开关默认值、去掉「代按会真的开门」那行。
        versionCode = 7
        versionName = "3.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 刻意继续用原来那个自签名密钥：
    //   换密钥会让覆盖安装失败（INSTALL_FAILED_UPDATE_INCOMPATIBLE），
    //   而卸载重装会丢掉无障碍授权与偏好，HyperOS 还会把首次安装拦下来要用户手点。
    //
    // 三个签名方案**必须显式全开**（AGP 在 minSdk>=24 时默认只签 v2）：
    //   v1（JAR 签名）是 HyperOS 安装器解析 APK 时要用的 —— 1.0 的离线链路就是因此显式开了 v1；
    //   只签 v2 时 `adb install` 能过，但在手机上点 APK 安装会失败，
    //   而那个失败与「首次安装被 HyperOS 拦」的报错一模一样，会让人误判成老问题。
    //   实测过一次：只签 v2 的包 `apksigner verify --min-sdk-version 21` 报
    //   `Missing META-INF/MANIFEST.MF`。
    //
    // 密钥路径：**优先用本仓库自带的 `UnlockAssistant/keystore/unlockassistant.jks`**。
    // 历史上它只放在工作区的 `work/unlockassistant.jks`，而 App 源码这次要单独成库（GitHub），
    // clone 下来的人没有 work/ 这一层，签名配置就会找不到文件而构建失败。
    // 所以现在两份并存，且是**同一个密钥**（SHA-256 指纹
    // B2:5D:D5:66:ED:1A:E3:B5:95:C4:FC:D1:70:7E:19:19:7D:EF:35:31:13:E2:00:61:32:71:80:F7:9F:E4:43:93）：
    // 本仓库那份保证 clone 即可构建，工作区那份保证 `rootProject.file` 的老路径仍能解析。
    // 属于同一个自签名密钥，两条路径产出同一签名的 APK，覆盖安装不受影响。
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

// 不写 kotlin { compilerOptions { jvmTarget = ... } }：
// Kotlin 由 AGP 内置，AGP 会把 Kotlin 的 jvmTarget 对上上面 compileOptions 的 Java 目标
// （AGP 里的 KotlinJvmToolchain.wireJvmTargetToJvm 就是干这个的），
// 手写反而容易两边不一致。

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    // BOM 在这里：下面 compose 各组件都不用写版本号。
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
