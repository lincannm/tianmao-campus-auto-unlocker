// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    // 注意：**不要**加 org.jetbrains.kotlin.android。
    // AGP 9 起 Kotlin 支持内置，再挂那个插件 AGP 会直接报错：
    //   "The 'org.jetbrains.kotlin.android' plugin is no longer required for Kotlin support since AGP 9.0."
    // Kotlin 编译器版本由 AGP 决定（AGP 9.4.1 内置 2.2.10）。
    //
    // Compose 编译器插件仍然要显式挂：@Composable 靠它编译。
    alias(libs.plugins.kotlin.compose) apply false
}
