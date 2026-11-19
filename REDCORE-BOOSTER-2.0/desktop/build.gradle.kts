import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    // Cross-platform hardware & OS information (CPU, memory, sensors, disks, network, processes).
    implementation("com.github.oshi:oshi-core:6.6.5")
    implementation("org.slf4j:slf4j-nop:2.0.16")
    testImplementation("junit:junit:4.13.2")
}

val appVersion = "2.0.0"

compose.desktop {
    application {
        mainClass = "com.redcore.booster.desktop.MainKt"
        jvmArgs += listOf("-Dfile.encoding=UTF-8")
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb, TargetFormat.Dmg)
            packageName = "REDCORE Booster"
            packageVersion = appVersion
            description = "REDCORE Booster for PC: game library, verified boost modes, live hardware monitor"
            vendor = "REDCORE"
            copyright = "REDCORE Booster"
            modules("java.management", "jdk.unsupported", "java.net.http")
            windows {
                menuGroup = "REDCORE"
                shortcut = true
                dirChooser = true
                upgradeUuid = "6b6c2c3e-5d0a-4a7e-9b1d-2f7c1f0f2a20"
            }
            linux { shortcut = true }
        }
    }
}

/**
 * Portable, cross-platform JAR for PCs that already have Java 17+: contains the Windows, Linux and macOS
 * graphics runtimes, so the same file runs everywhere (`java -jar REDCORE-Booster-PC-2.0.jar`).
 */
val portableRuntime: Configuration by configurations.creating {
    extendsFrom(configurations.runtimeClasspath.get())
}
dependencies {
    portableRuntime(compose.desktop.windows_x64)
    portableRuntime(compose.desktop.linux_x64)
    portableRuntime(compose.desktop.macos_x64)
    portableRuntime(compose.desktop.macos_arm64)
}
tasks.register<Jar>("portableJar") {
    group = "distribution"
    description = "Builds a single JAR that runs on Windows, Linux and macOS with Java 17+."
    archiveBaseName.set("REDCORE-Booster-PC")
    archiveVersion.set("2.0")
    archiveClassifier.set("")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest { attributes("Main-Class" to "com.redcore.booster.desktop.MainKt", "Implementation-Version" to appVersion) }
    from(sourceSets.main.get().output)
    dependsOn(portableRuntime)
    from({ portableRuntime.filter { it.name.endsWith(".jar") }.map { zipTree(it) } }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.EC", "META-INF/versions/9/module-info.class", "module-info.class")
    }
}
