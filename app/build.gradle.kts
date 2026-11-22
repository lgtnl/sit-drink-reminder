import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// ---------------------------------------------------------------------------
// 可选：release 签名。
// 在工程根目录放一个 keystore.properties（已在 .gitignore 中忽略），内容示例：
//
//   storeFile=release.keystore
//   storePassword=你的密码
//   keyAlias=sitdrink
//   keyPassword=你的密码
//
// 文件不存在时 release 会产出未签名 APK（app-release-unsigned.apk），
// 也可以直接用 Android Studio 的 Build > Generate Signed App Bundle / APK。
// ---------------------------------------------------------------------------
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    FileInputStream(keystorePropertiesFile).use { keystoreProperties.load(it) }
}

android {
    namespace = "com.example.sitdrinkreminder"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.sitdrinkreminder"
        minSdk = 26      // Android 8.0：NotificationChannel / java.time 都原生可用
        targetSdk = 34   // 适配 Android 14
        versionCode = 3
        versionName = "1.1.0"

        // 本项目只有中文界面，且中文放在 values/ 默认目录里，
        // 因此不需要额外声明 resourceConfigurations。
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile", "release.keystore"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // 调试包不混淆，方便断点与看类名
            isMinifyEnabled = false
        }
        release {
            // 体积优化：代码压缩 + 资源压缩
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        // 个人项目：lint 报错不阻塞打包（仍可在 IDE 里查看全部警告）
        abortOnError = false
    }
}

dependencies {
    // 唯一依赖。
    // - NotificationCompat / NotificationManagerCompat：统一通知写法，符合需求约束
    // - ContextCompat / ActivityCompat：权限与兼容工具
    // 不引入 appcompat / material / WorkManager / 协程，保持包体最小。
    implementation("androidx.core:core-ktx:1.13.1")

    // 纯 JVM 单元测试（不会打进 APK）
    testImplementation("junit:junit:4.13.2")
}
