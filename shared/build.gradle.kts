plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.sqldelight)
}

kotlin {
    // Android 目标
    androidTarget {
        compilations.all {
            kotlin {
                jvmTarget = "17"
            }
        }
    }

    // iOS 目标 (需要 XCode 编译，此处声明 skeleton)
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }

    // ── 源集 ──
    sourceSets {
        commonMain.dependencies {
            // Compose 共享 UI (这是双端共享的核心)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)

            // SQLDelight 共享 + Flow
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines.ext)
            implementation(libs.sqldelight.primitive.adapters)
            implementation(libs.coroutines.core)

            // Lifecycle ViewModel 共享
            implementation(libs.compose.lifecycle.runtime)
        }

        androidMain.dependencies {
            // SQLDelight Android 驱动
            implementation(libs.sqldelight.android.driver)

            // CameraX 三件套
            implementation(libs.camerax.core)
            implementation(libs.camerax.camera2)
            implementation(libs.camerax.lifecycle)
            implementation(libs.camerax.view)

            // MediaPipe 人脸/Android 视觉任务
            implementation(libs.mediapipe.tasks.vision)

            // Android WorkManager 后台任务
            implementation(libs.workmanager.runtime)

            // Compose Android 控件 + Activity 宿主
            implementation(libs.compose.activity)
            implementation(libs.compose.ui.tooling)
            debugImplementation(libs.compose.ui.tooling.preview)
        }

        iosMain.dependencies {
            // SQLDelight iOS 驱动
            implementation(libs.sqldelight.native.driver)
            // iOS 端需要接入 MediaPipe C 库，此处占位
            // XCode 工程里直接 pod 'MediaPipeTasksVision' 或 SPM MediaPipeTasksVision
        }

        // 共享 tests (占位，双端共用测试)
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

sqldelight {
    databases {
        // 自定义 database name (数据库 package path: com.ykz200.smiletracker.db)
        create("SmileDatabase") {
            packageName.set("com.ykz200.smiletracker.db")
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/schema"))
            // 可选: 生成同步挂起函数 (suspend fun) 让 coroutines 直接消费
            generateAsync.set(true)
        }
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=androidx.compose.ui.ExperimentalComposeUiApi")
        freeCompilerArgs.add("-opt-in=androidx.compose.foundation.ExperimentalFoundationApi")
        freeCompilerArgs.add("-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi")
    }
}
