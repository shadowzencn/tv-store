plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/** 把 installtest 模块的三个测试 APK 复制成 internal 版商店的 assets/installtest/。 */
abstract class CopyInstallTestApks : DefaultTask() {
    @get:InputFiles
    abstract val apkDirectory: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val target = outputDir.get().asFile.resolve("installtest")
        target.deleteRecursively()
        target.mkdirs()
        val apks = apkDirectory.asFileTree.matching { include("**/*.apk") }.files
        check(apks.size == 3) { "预期 3 个安装测试 APK，实际找到 ${apks.size} 个" }
        apks.forEach { apk ->
            val name = apk.name.removePrefix("installtest-").removeSuffix("-release.apk") + ".apk"
            apk.copyTo(target.resolve(name), overwrite = true)
        }
    }
}

val copyInstallTestApks = tasks.register<CopyInstallTestApks>("copyInstallTestApks") {
    dependsOn(":installtest:assembleRelease")
    apkDirectory.from(rootDir.resolve("installtest/build/outputs/apk"))
}

android {
    namespace = "com.shadowzen.tvstore"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.shadowzen.tvstore"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
        // 内测包：与 release 相同的 R8 优化和基线配置文件，但用本机调试证书签名以便直接安装。
        // 只用于实机测试，正式发布必须使用 release 和正式签名证书。
        create("internal") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
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

androidComponents {
    // 安装测试包只进入 internal 版，debug 和 release 版都不包含
    onVariants(selector().withBuildType("internal")) { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(copyInstallTestApks, CopyInstallTestApks::outputDir)
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.tv.material)

    testImplementation(libs.junit)
}
