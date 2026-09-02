plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.tomatodo"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tomatodo"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "1.6.0"
    }

    signingConfigs {
        create("release") {
            storeFile = file("tomatodo-release.jks")
            storePassword = "tomatodo2026"
            keyAlias = "tomatodo"
            keyPassword = "tomatodo2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
    }
}

// Room schema 导出（迁移测试与 schema 演进追踪）
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // 数据层
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    // 图片加载
    implementation(libs.coil.compose)

    // Markdown / LaTeX 渲染（KMS v1.2）：Markwon + JLatexMath；图片插件自研（官方 image-coil 绑定 Coil 1.x，与项目 Coil 2.7 类冲突）
    implementation(libs.markwon.core)
    implementation(libs.markwon.ext.latex)
    implementation(libs.markwon.ext.tables)
    implementation(libs.markwon.ext.strikethrough)
    implementation(libs.markwon.ext.tasklist)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
}
