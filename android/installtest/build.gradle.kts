plugins {
    alias(libs.plugins.android.application)
}

// 签名冲突测试专用的一次性证书：构建时生成到 build/ 下，不进 Git，不承载任何安全保证，
// 因此密码可以像 Android 调试证书一样公开写在这里。
val otherSigningKeystore: File = layout.buildDirectory.file("other-signing/installtest-other.jks").get().asFile
val otherSigningPassword = "installtest-only"

val generateOtherSigningKeystore = tasks.register<Exec>("generateOtherSigningKeystore") {
    val keystore = otherSigningKeystore
    val keytool = File(System.getProperty("java.home"), "bin/keytool").path
    outputs.file(keystore)
    onlyIf { !keystore.exists() }
    doFirst { keystore.parentFile.mkdirs() }
    commandLine(
        keytool, "-genkeypair", "-noprompt",
        "-keystore", keystore.path,
        "-storepass", otherSigningPassword,
        "-keypass", otherSigningPassword,
        "-alias", "installtest-other",
        "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000",
        "-dname", "CN=TV Store Install Test Other Signer",
    )
}

android {
    namespace = "com.shadowzen.tvstore.installtest"
    compileSdk = 37

    signingConfigs {
        create("other") {
            storeFile = otherSigningKeystore
            storePassword = otherSigningPassword
            keyAlias = "installtest-other"
            keyPassword = otherSigningPassword
        }
    }

    defaultConfig {
        applicationId = "com.shadowzen.tvstore.installtest"
        minSdk = 28
        targetSdk = 36
        signingConfig = signingConfigs.getByName("debug")
    }

    flavorDimensions += "build"
    productFlavors {
        create("v1") {
            dimension = "build"
            versionCode = 1
            versionName = "1.0"
        }
        create("v2") {
            dimension = "build"
            versionCode = 2
            versionName = "2.0"
        }
        create("v2other") {
            dimension = "build"
            versionCode = 2
            versionName = "2.0"
            signingConfig = signingConfigs.getByName("other")
        }
    }

    buildTypes {
        release {
            // 去掉未使用的 Kotlin 标准库代码，避免三个测试包撑大 internal 版商店
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

androidComponents {
    // 测试包只需要不可调试的 release 产物
    beforeVariants(selector().withBuildType("debug")) { it.enable = false }
}

tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(generateOtherSigningKeystore) }
