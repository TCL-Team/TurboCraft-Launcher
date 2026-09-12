import java.security.MessageDigest

plugins {
    java
}

fun writeVersion(file: File, inputs: List<File>) {
    val digest = MessageDigest.getInstance("SHA-1")
    inputs.forEach { input ->
        input.inputStream().use { stream ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
    }
    file.writeText(digest.digest().joinToString("") { "%02x".format(it) })
}

val lwjglVersion = "3.3.3"
group = "org.lwjgl.glfw"

configurations {
    create("lwjglModules") {
        isCanBeResolved = true
    }
}

dependencies {
    compileOnly(fileTree(mapOf("dir" to "../compileOnly", "include" to listOf("*.jar"))))
    implementation(fileTree(mapOf("dir" to "libs/$lwjglVersion", "include" to listOf("*.jar"))))
    val lwjglModules = fileTree("libs/$lwjglVersion") {
        include("*.jar")
        exclude("jsr305.jar")
    }
    add("lwjglModules", lwjglModules)
    add("lwjglModules", project(":LWJGL:patches"))
    implementation(project(":LWJGL:patches"))
}

tasks.jar {
    inputs.files(configurations["lwjglModules"])

    val excludedModules = arrayOf(
        "lwjgl.jar",
        "lwjgl-freetype.jar",
        "lwjgl-lwjglx.jar",
        "lwjgl-jemalloc.jar",
        "lwjgl-nanovg.jar",
        "lwjgl-openal.jar",
        "lwjgl-sdl.jar",
        "lwjgl-shaderc.jar",
        "lwjgl-spng.jar",
        "lwjgl-spvc.jar",
        "lwjgl-stb.jar",
        "lwjgl-tinyfd.jar",
        "lwjgl-vma.jar",
        "lwjgl-vulkan.jar"
    )

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveBaseName.set("lwjgl-${lwjglVersion}-merged-modules")
    destinationDirectory.set(file("$rootDir/TCL/src/main/assets/app_runtime/lwjgl/${lwjglVersion}"))

    from({
        val includedModules = configurations["lwjglModules"].filter { dep ->
            !excludedModules.any { it == dep.name }
        }
        val coreJar = includedModules.find { it.name == "lwjgl.jar" }
        val jarList =
            if (coreJar != null) listOf(coreJar) + (includedModules - coreJar) else includedModules
        println("Merging LWJGL $lwjglVersion modules in the order: ")
        jarList.map {
            println(it.name)
            if (it.isDirectory) it else zipTree(it)
        }
    })

    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true

    val versionFile = File(destinationDirectory.get().asFile, "version")
    doLast {
        val excludedModulesFileList = excludedModules.flatMap { fileName ->
            configurations["lwjglModules"].filter { it.name == fileName }
        }
        copy {
            from(excludedModulesFileList)
            into(archiveFile.get().asFile.parentFile)
        }
        writeVersion(versionFile, listOf(archiveFile.get().asFile) + excludedModulesFileList)
    }
    outputs.file(versionFile)
    outputs.files(excludedModules.map { path -> File(destinationDirectory.get().asFile, path) })
    exclude("net/java/openjdk/cacio/ctc/**")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}
