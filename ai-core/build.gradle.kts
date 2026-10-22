import java.util.Properties

plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":game-core"))
    testImplementation(kotlin("test"))
}

val hostIsWindows = System.getProperty("os.name").startsWith("Windows")
val hostIsMac = System.getProperty("os.name").startsWith("Mac")
val localSdkProperties = Properties().apply {
    val source = rootProject.file("local.properties")
    if (source.isFile) source.inputStream().use { load(it) }
}
val localSdk = localSdkProperties.getProperty("sdk.dir")
    ?: System.getenv("ANDROID_SDK_ROOT") ?: System.getenv("ANDROID_HOME")
    ?: error("Set sdk.dir or ANDROID_SDK_ROOT to build the native engine")
val cmakeBin = File(localSdk, "cmake/3.22.1/bin")
val cmakeCommand = providers.gradleProperty("threes.cmake").getOrElse(
    File(cmakeBin, if (hostIsWindows) "cmake.exe" else "cmake").absolutePath,
)
val ninjaCommand = File(cmakeBin, if (hostIsWindows) "ninja.exe" else "ninja").absolutePath
val cxxCommand = providers.gradleProperty("threes.cxx").getOrElse(
    System.getenv("CXX") ?: if (hostIsWindows) "g++" else "c++",
)
val nativeHostDirectory = layout.buildDirectory.dir("native/host")
val nativeHostLibrary = nativeHostDirectory.map {
    it.file(when {
        hostIsWindows -> "nneonneo_threes.dll"
        hostIsMac -> "libnneonneo_threes.dylib"
        else -> "libnneonneo_threes.so"
    })
}

val configureNneonneoHost by tasks.registering(Exec::class) {
    group = "build"
    description = "Configures the original nneonneo C++ engine for desktop tests."
    inputs.dir("src/main/cpp")
    outputs.file(nativeHostDirectory.map { it.file("build.ninja") })
    commandLine(
        cmakeCommand, "-S", file("src/main/cpp").absolutePath,
        "-B", nativeHostDirectory.get().asFile.absolutePath, "-G", "Ninja",
        "-DCMAKE_MAKE_PROGRAM=$ninjaCommand", "-DCMAKE_CXX_COMPILER=$cxxCommand",
        "-DCMAKE_BUILD_TYPE=Release", "-DTHREES_HOST_JAVA_HOME=${System.getProperty("java.home")}",
    )
}

val buildNneonneoHost by tasks.registering(Exec::class) {
    group = "build"
    dependsOn(configureNneonneoHost)
    inputs.dir("src/main/cpp")
    outputs.file(nativeHostLibrary)
    commandLine(cmakeCommand, "--build", nativeHostDirectory.get().asFile.absolutePath)
}

tasks.test {
    useJUnitPlatform()
    dependsOn(buildNneonneoHost)
    systemProperty("threes.nneonneo.library", nativeHostLibrary.get().asFile.absolutePath)
}

tasks.register<JavaExec>("benchmark") {
    group = "verification"
    description = "Runs complete seeded games with the timed AI without Android or a UI."
    dependsOn(buildNneonneoHost, tasks.classes)
    classpath = sourceSets.main.get().runtimeClasspath
    workingDir(rootProject.projectDir)
    mainClass.set("com.zerocode.threes.ai.benchmark.BenchmarkKt")
    maxHeapSize = "2g"
    systemProperty("threes.nneonneo.library", nativeHostLibrary.get().asFile.absolutePath)
    args(
        "--games=${providers.gradleProperty("benchmarkGames").getOrElse("3")}",
        "--seed=${providers.gradleProperty("benchmarkSeed").getOrElse("42")}",
        "--millis=${providers.gradleProperty("benchmarkMillis").getOrElse("200")}",
        "--nodes=${providers.gradleProperty("benchmarkNodes").getOrElse("9223372036854775807")}",
        "--max-moves=${providers.gradleProperty("benchmarkMaxMoves").getOrElse("20000")}",
        "--max-game-seconds=${providers.gradleProperty("benchmarkMaxGameSeconds").getOrElse("0")}",
        "--algorithms=${providers.gradleProperty("benchmarkAlgorithms").getOrElse("nneonneo-timed")}",
        "--output=${providers.gradleProperty("benchmarkOutput").getOrElse(rootProject.file("reports/benchmarks/latest").absolutePath)}",
    )
}

