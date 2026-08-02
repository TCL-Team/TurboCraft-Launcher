package com.movtery.zalithlauncher.game.marketplace

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Setup Marketplace - ek publish ki gayi launcher-setup entry
 */
@Serializable
data class SetupData(
    val id: String? = null,
    val title: String,
    val author: String,
    val fps: Int,
    val downloads: Int = 0,
    val rating: Double = 0.0,
    @SerialName("settings_json") val settingsJson: String
)

/**
 * Ek setup ke andar bundle hone wali actual launcher settings.
 * Ye JSON string ke form mein SetupData.settingsJson field mein store hoti hai.
 */
@Serializable
data class LauncherSetupBundle(
    val renderer: String,
    val resolutionRatio: Int,
    val autoRamAllocation: Boolean,
    val ramAllocation: Int?,
    val fpsLimitEnabled: Boolean,
    val fpsLimit: Int,
    val jvmArgs: String
)
