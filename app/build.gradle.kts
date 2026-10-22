import com.android.build.api.artifact.ArtifactTransformationRequest
import com.android.build.api.artifact.SingleArtifact
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Re-evaluate when the Beijing hour changes, including with configuration caching.
abstract class BuildVersionCode : ValueSource<Int, ValueSourceParameters.None> {
    override fun obtain(): Int = at(Instant.now())

    companion object {
        fun at(instant: Instant): Int = DateTimeFormatter
            .ofPattern("yyMMddHH", Locale.ROOT)
            .withZone(ZoneId.of("Asia/Shanghai"))
            .format(instant).toInt()
    }
}

abstract class VerifyBuildVersionCode : DefaultTask() {
    @TaskAction fun verify() {
        fun code(value: String) = BuildVersionCode.at(Instant.parse(value))
        check(code("2026-09-05T05:00:00Z") == 26090513)
        check(code("2026-09-05T05:59:59Z") == 26090513)
        check(code("2026-09-05T06:00:00Z") == 26090514)
        check(code("2026-09-05T16:00:00Z") == 26090600)
        check(code("2026-12-31T16:00:00Z") == 27010100)
        logger.lifecycle("Beijing version-code checks passed (same hour, next hour, next day, next year).")
    }
}

tasks.register<VerifyBuildVersionCode>("verifyBuildVersionCode") { group = "verification" }

// Android Studio may restore an emulator snapshot with a different timezone.
tasks.register("setConnectedEmulatorTimeZones") {
    group = "verification"
    description = "Sets all connected Android emulators to Asia/Shanghai."
    outputs.upToDateWhen { false }

    doLast {
        val localProperties = Properties().apply {
            val file = rootProject.file("local.properties")
            if (file.isFile) file.inputStream().use { load(it) }
        }
        val sdkRoot = localProperties.getProperty("sdk.dir")
            ?: System.getenv("ANDROID_SDK_ROOT")
            ?: System.getenv("ANDROID_HOME")
            ?: System.getenv("LOCALAPPDATA")?.let { "$it/Android/Sdk" }
            ?: error("Android SDK location is unavailable")
        val adb = File(sdkRoot, "platform-tools/adb.exe")
        check(adb.isFile) { "adb.exe was not found at ${adb.absolutePath}" }

        fun runAdb(vararg arguments: String): Pair<Int, String> {
            val process = ProcessBuilder(adb.absolutePath, *arguments)
                .redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            return process.waitFor() to output
        }

        val (devicesCode, devicesOutput) = runAdb("devices")
        check(devicesCode == 0) { "Could not list Android devices: $devicesOutput" }
        val serials = devicesOutput.lineSequence().mapNotNull { line ->
            Regex("^(emulator-\\d+)\\s+device$").matchEntire(line.trim())?.groupValues?.get(1)
        }.toList()
        if (serials.isEmpty()) logger.lifecycle("No connected Android emulator needs a timezone update.")
        serials.forEach { serial ->
            val (settingsCode, settingsOutput) = runAdb(
                "-s", serial, "shell", "settings", "put", "global", "auto_time_zone", "0"
            )
            check(settingsCode == 0) { "Failed to configure timezone on $serial: $settingsOutput" }
            val (exitCode, output) = runAdb(
                "-s", serial, "shell", "cmd", "alarm", "set-timezone", "Asia/Shanghai"
            )
            check(exitCode == 0) { "Failed to set timezone on $serial: $output" }
            logger.lifecycle("$serial timezone set to Asia/Shanghai.")
        }
    }
}

val versionFile = rootProject.layout.projectDirectory.file("version.properties")
val appVersionName = Properties().apply {
    providers.fileContents(versionFile).asText.get().reader().use { load(it) }
}.getProperty("VERSION_NAME") ?: error("version.properties must define VERSION_NAME")
require(appVersionName.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+"))) {
    "VERSION_NAME must have the form major.minor.patch"
}
val buildVersionCode = providers.of(BuildVersionCode::class) {}.get()
require(buildVersionCode in 10000000..99999999) { "Version code must be eight digits" }

val nativeLicenseAssets = layout.buildDirectory.dir("generated/nneonneoLicenseAssets")
val copyNneonneoLicense = tasks.register<Copy>("copyNneonneoLicense") {
    from(rootProject.file("ai-core/src/main/cpp/upstream/LICENSE"))
    into(nativeLicenseAssets.map { it.dir("licenses/nneonneo-threes-ai") })
}
tasks.named("preBuild").configure { dependsOn(copyNneonneoLicense) }

val signingPropertiesFile = rootProject.file("../_android_signing/Threes.properties")
val signingProperties = Properties().apply {
    if (signingPropertiesFile.isFile) {
        signingPropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.zerocode.threes"
    compileSdk = 36
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.zerocode.threes"
        minSdk = 28
        targetSdk = 36
        versionCode = buildVersionCode
        versionName = appVersionName
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=c++_static"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("../ai-core/src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    signingConfigs {
        create("release") {
            storeFile = signingPropertiesFile.parentFile.resolve(
                signingProperties.getProperty("storeFile", "Threes.jks")
            )
            storePassword = signingProperties.getProperty("storePassword", "")
            keyAlias = signingProperties.getProperty("keyAlias", "")
            keyPassword = signingProperties.getProperty("keyPassword", "")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            if (signingPropertiesFile.isFile) {
                signingConfig = signingConfigs.getByName("release")
            }
            applicationIdSuffix = ".debug"
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

    sourceSets.getByName("main").assets.srcDir(nativeLicenseAssets)

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

// Keep APK filenames and install metadata aligned, following MemoSeed's convention.
abstract class RenameApk : DefaultTask() {
    @get:InputDirectory abstract val inputDirectory: DirectoryProperty
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty
    @get:Input abstract val appName: Property<String>
    @get:Input abstract val buildType: Property<String>
    @get:Internal abstract val transformation: Property<ArtifactTransformationRequest<RenameApk>>

    @TaskAction fun rename() {
        val source = inputDirectory.get().asFile
        val destination = outputDirectory.get().asFile.apply { mkdirs() }
        check(source.canonicalFile != destination.canonicalFile) {
            "APK transform needs separate input and output directories"
        }
        source.walkTopDown()
            .filter { it.isFile && it.extension != "apk" && it.name != "output-metadata.json" }
            .forEach { file ->
                val target = destination.resolve(file.relativeTo(source))
                target.parentFile.mkdirs()
                file.copyTo(target, overwrite = true)
            }
        transformation.get().submit(this) { artifact ->
            check(artifact.filters.isEmpty()) { "APK splits require filter-specific filenames" }
            val target = destination.resolve(
                "${appName.get()}_v${artifact.versionName}_${artifact.versionCode}_${buildType.get()}.apk"
            )
            File(artifact.outputFile).copyTo(target, overwrite = true)
            target
        }
    }
}

androidComponents {
    onVariants(selector().all()) { variant ->
        val type = requireNotNull(variant.buildType)
        val capitalized = variant.name.replaceFirstChar { it.uppercaseChar() }
        variant.outputs.forEach {
            it.versionCode.set(buildVersionCode)
            it.versionName.set(appVersionName)
        }
        val rename = tasks.register<RenameApk>("rename${capitalized}Apk") {
            appName.set(rootProject.name)
            buildType.set(type)
            outputDirectory.set(layout.buildDirectory.dir("outputs/apk/${variant.name}"))
        }
        val transformation = variant.artifacts.use(rename)
            .wiredWithDirectories(RenameApk::inputDirectory, RenameApk::outputDirectory)
            .toTransformMany(SingleArtifact.APK)
        rename.configure { this.transformation.set(transformation) }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":game-core"))
    implementation(project(":ai-core"))

    val composeBom = platform("androidx.compose:compose-bom:2025.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

