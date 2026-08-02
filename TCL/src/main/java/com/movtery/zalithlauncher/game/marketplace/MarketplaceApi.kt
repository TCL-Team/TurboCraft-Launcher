package com.movtery.zalithlauncher.game.marketplace

import com.movtery.zalithlauncher.path.GLOBAL_CLIENT
import com.movtery.zalithlauncher.path.SUPABASE_API_KEY
import com.movtery.zalithlauncher.path.URL_SUPABASE_BASE
import com.movtery.zalithlauncher.setting.AllSettings
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.json.Json

private val MARKETPLACE_JSON = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * Setup Marketplace - Supabase REST API ke saath baat karne ka layer
 */
object MarketplaceApi {

    private fun HttpRequestBuilder.supabaseAuth() {
        headers {
            append("apikey", SUPABASE_API_KEY)
            append(HttpHeaders.Authorization, "Bearer $SUPABASE_API_KEY")
        }
    }

    /**
     * Sabse zyada downloads wale setups sabse upar
     */
    suspend fun fetchSetups(): List<SetupData> {
        return GLOBAL_CLIENT.get("${URL_SUPABASE_BASE}setups?select=*&order=downloads.desc") {
            supabaseAuth()
        }.body()
    }

    /**
     * Current launcher settings ko ek naye setup ke roop mein publish karo
     */
    suspend fun publishCurrentSetup(title: String, author: String, fps: Int) {
        val bundle = LauncherSetupBundle(
            renderer = AllSettings.renderer.getValue(),
            resolutionRatio = AllSettings.resolutionRatio.getValue(),
            autoRamAllocation = AllSettings.autoRamAllocation.getValue(),
            ramAllocation = AllSettings.ramAllocation.getValue(),
            fpsLimitEnabled = AllSettings.fpsLimitEnabled.getValue(),
            fpsLimit = AllSettings.fpsLimit.getValue(),
            jvmArgs = AllSettings.jvmArgs.getValue()
        )
        val setup = SetupData(
            title = title,
            author = author,
            fps = fps,
            settingsJson = MARKETPLACE_JSON.encodeToString(LauncherSetupBundle.serializer(), bundle)
        )
        GLOBAL_CLIENT.post("${URL_SUPABASE_BASE}setups") {
            supabaseAuth()
            headers { append("Prefer", "return=minimal") }
            contentType(ContentType.Application.Json)
            setBody(setup)
        }
    }

    /**
     * Ek setup ko one-click install karo (settings apply) aur download counter badhao
     */
    suspend fun installSetup(setup: SetupData) {
        val bundle = MARKETPLACE_JSON.decodeFromString(LauncherSetupBundle.serializer(), setup.settingsJson)
        AllSettings.renderer.save(bundle.renderer)
        AllSettings.resolutionRatio.save(bundle.resolutionRatio)
        AllSettings.autoRamAllocation.save(bundle.autoRamAllocation)
        bundle.ramAllocation?.let { AllSettings.ramAllocation.save(it) }
        AllSettings.fpsLimitEnabled.save(bundle.fpsLimitEnabled)
        AllSettings.fpsLimit.save(bundle.fpsLimit)
        AllSettings.jvmArgs.save(bundle.jvmArgs)

        setup.id?.let { id ->
            GLOBAL_CLIENT.patch("${URL_SUPABASE_BASE}setups?id=eq.$id") {
                supabaseAuth()
                headers { append("Prefer", "return=minimal") }
                contentType(ContentType.Application.Json)
                setBody(mapOf("downloads" to setup.downloads + 1))
            }
        }
    }
}
