// 顶层构建文件：声明插件版本（通过 version catalog），apply false 供子模块按需应用
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
