import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
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

/**
 * 商店服务端基础地址（以 / 结尾），按构建类型读取：
 * Gradle 属性或 local.properties 中的 tvstore.serverUrl.<构建类型>，其次 tvstore.serverUrl，都没有时使用占位地址。
 * local.properties 不进 Git，适合填写自己的服务器地址。
 */
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}

fun storeServerUrl(buildType: String): String {
    val keys = listOf("tvstore.serverUrl.$buildType", "tvstore.serverUrl")
    val url = keys.firstNotNullOfOrNull { key ->
        providers.gradleProperty(key).orNull ?: localProperties.getProperty(key)
    }?.trim() ?: "https://tvstore.example.invalid/"
    require(url.startsWith("https://") || url.startsWith("http://")) { "服务端地址必须以 http(s):// 开头：$url" }
    require(url.endsWith("/")) { "服务端地址必须以 / 结尾：$url" }
    require('"' !in url && '\\' !in url) { "服务端地址含有非法字符：$url" }
    return url
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
        debug {
            buildConfigField("String", "STORE_SERVER_URL", "\"${storeServerUrl("debug")}\"")
        }
        release {
            buildConfigField("String", "STORE_SERVER_URL", "\"${storeServerUrl("release")}\"")
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
            buildConfigField("String", "STORE_SERVER_URL", "\"${storeServerUrl("internal")}\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        // Robolectric 测试需要读取合并后的清单和资源
        unitTests.isIncludeAndroidResources = true
    }
}

room {
    // 导出数据库结构，供以后编写和校验迁移
    schemaDirectory("$projectDir/schemas")
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
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime)
    implementation(libs.okhttp)
    // 应用图标加载：Compose 图片库，内存/磁盘缓存开箱即用，网络层复用 OkHttp
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.work.testing)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    // Compose 界面测试需要的空 Activity，只进入 debug 版
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.kotlinx.coroutines.test)
}
