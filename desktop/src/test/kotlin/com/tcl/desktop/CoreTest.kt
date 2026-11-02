package com.tcl.desktop

import com.tcl.desktop.core.Account
import com.tcl.desktop.core.AccountType
import com.tcl.desktop.core.GameLauncher
import com.tcl.desktop.core.JavaManager
import com.tcl.desktop.core.JavaRuntime
import com.tcl.desktop.core.Library
import com.tcl.desktop.core.MojangApi
import com.tcl.desktop.core.Rule
import com.tcl.desktop.core.OsRule
import com.tcl.desktop.core.Settings
import com.tcl.desktop.core.VersionResolver
import com.tcl.desktop.core.allows
import com.tcl.desktop.core.libraryNameToPath
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoreTest {

    private val account = Account(
        type = AccountType.OFFLINE,
        name = "Steve",
        uuid = "00000000-0000-0000-0000-000000000001",
        accessToken = "0"
    )
    private val java = JavaRuntime(File("/java/bin/java"), 21, "test")

    @Test
    fun `library name to path`() {
        assertEquals(
            "org/ow2/asm/asm/9.7/asm-9.7.jar",
            libraryNameToPath("org.ow2.asm:asm:9.7")
        )
        assertEquals(
            "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-windows.jar",
            libraryNameToPath("org.lwjgl:lwjgl:3.3.3:natives-windows")
        )
    }

    @Test
    fun `rules engine`() {
        val windowsOnly = Rule(
            action = "allow",
            os = OsRule(name = "windows")
        )
        val allExceptWindows = listOf(
            Rule(action = "allow"),
            Rule(action = "disallow", os = OsRule(name = "windows"))
        )

        if (com.tcl.desktop.core.OsInfo.isWindows) {
            assertTrue(listOf(windowsOnly).allows())
            assertFalse(allExceptWindows.allows())
        } else {
            assertFalse(listOf(windowsOnly).allows())
            assertTrue(allExceptWindows.allows())
        }
        // feature rule (demo mode) off hoti hai to arg skip
        val demoRule = listOf(Rule(action = "allow", features = mapOf("is_demo_user" to true)))
        assertFalse(demoRule.allows())
    }

    @Test
    fun `java major version mapping`() {
        assertEquals(8, JavaManager.requiredMajor("1.8.9"))
        assertEquals(8, JavaManager.requiredMajor("1.7.10"))
        assertEquals(17, JavaManager.requiredMajor("1.19.2"))
        assertEquals(21, JavaManager.requiredMajor("1.20.4"))
        assertEquals(21, JavaManager.requiredMajor("1.21.6"))
        assertEquals(21, JavaManager.requiredMajor("1.21.6", manifestMajor = 21))
        assertEquals(8, JavaManager.requiredMajor("whatever", manifestMajor = 8))
    }

    @Test
    fun `argument placeholders and jvm args`() {
        val json = """
            {
              "id": "1.21.6-test",
              "type": "release",
              "mainClass": "net.minecraft.client.main.Main",
              "assets": "17",
              "assetIndex": { "id": "17", "url": "https://example.invalid/assets.json" },
              "javaVersion": { "majorVersion": 21 },
              "libraries": [
                { "name": "org.example:lib:1.0" },
                { "name": "org.example:badlib:1.0",
                  "rules": [ { "action": "allow", "os": { "name": "plan9" } } ] }
              ],
              "arguments": {
                "jvm": [
                  "-Djava.library.path=${'$'}{natives_directory}",
                  "-cp", "${'$'}{classpath}",
                  { "rules": [ { "action": "allow", "os": { "name": "plan9" } } ], "value": "-Xbogus" }
                ],
                "game": [
                  "--username", "${'$'}{auth_player_name}",
                  "--version", "${'$'}{version_name}",
                  "--accessToken", "${'$'}{auth_access_token}",
                  { "rules": [ { "action": "allow", "features": { "is_demo_user": true } } ], "value": "--demo" }
                ]
              }
            }
        """.trimIndent()

        val version = MojangApi.parse(json)
        val command = GameLauncher.buildCommand(
            "1.21.6-test", VersionResolver.fromJson("1.21.6-test", version), account, Settings(memoryMb = 2048), java
        )

        assertEquals("/java/bin/java", command.first())
        assertContains(command, "-Xmx2048M")
        assertContains(command, "net.minecraft.client.main.Main")
        assertContains(command, "--username")
        assertContains(command, "Steve")
        assertContains(command, "--accessToken")
        assertFalse(command.contains("--demo"))
        assertFalse(command.contains("-Xbogus"))
        assertTrue(command.any { it.startsWith("-Djava.library.path=") })
        // rules ke hisaab se bogus library classpath me nahi honi chahiye
        assertTrue(command.none { it.contains("badlib") })
    }

    @Test
    fun `legacy minecraftArguments`() {
        val json = """
            {
              "id": "1.8.9-test",
              "mainClass": "net.minecraft.client.main.Main",
              "minecraftArguments": "--username ${'$'}{auth_player_name} --uuid ${'$'}{auth_uuid}",
              "assets": "1.8",
              "libraries": []
            }
        """.trimIndent()
        val version = MojangApi.parse(json)
        val command = GameLauncher.buildCommand(
            "1.8.9-test", VersionResolver.fromJson("1.8.9-test", version), account, Settings(memoryMb = 1024), java
        )
        assertTrue(command.contains("-cp"))
        assertContains(command, "--uuid")
        assertContains(command, "00000000000000000000000000000001")
    }

    @Test
    fun `split args respects quotes`() {
        assertEquals(
            listOf("-Dfoo=bar", "-XX:+UseG1GC", "-Dname=a b"),
            GameLauncher.splitArgs("""-Dfoo=bar -XX:+UseG1GC "-Dname=a b"""")
        )
    }

    @Test
    fun `loader profile merges with parent (inheritsFrom)`() {
        val vanilla = MojangApi.parse(
            """
            {
              "id": "1.21.6",
              "type": "release",
              "mainClass": "net.minecraft.client.main.Main",
              "assetIndex": { "id": "21" },
              "libraries": [ { "name": "com.mojang:authlib:6.0" }, { "name": "org.lwjgl:lwjgl:3.3.3" } ],
              "arguments": {
                "jvm": [ "-Djava.library.path=placeholder" ],
                "game": [ "--username", "name" ]
              }
            }
            """.trimIndent()
        )
        val parent = VersionResolver.fromJson("1.21.6", vanilla)

        val fabric = MojangApi.parse(
            """
            {
              "id": "fabric-loader-0.16.5-1.21.6",
              "inheritsFrom": "1.21.6",
              "mainClass": "net.fabricmc.loader.impl.launch.knot.KnotClient",
              "libraries": [
                { "name": "net.fabricmc:fabric-loader:0.16.5" },
                { "name": "org.lwjgl:lwjgl:3.3.3" }
              ],
              "arguments": { "game": [ "--fabricTest" ] }
            }
            """.trimIndent()
        )

        val merged = VersionResolver.merge("fabric-loader-0.16.5-1.21.6", fabric, parent)
        assertEquals("net.fabricmc.loader.impl.launch.knot.KnotClient", merged.mainClass)
        assertEquals("1.21.6", merged.jarId)
        assertEquals("21", merged.assetIndex)
        // dup library hata, loader library shaamil
        assertEquals(3, merged.libraries.size)
        assertEquals(
            "net.fabricmc:fabric-loader:0.16.5",
            merged.libraries.first().name
        )
        assertContains(merged.gameArguments.map { it.asString }, "--fabricTest")
    }

    @Test
    fun `library rules allow empty`() {
        val library = Library(name = "a:b:1")
        assertTrue(library.rules.allows())
    }
}
