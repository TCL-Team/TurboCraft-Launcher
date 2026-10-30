/*
 * TurboCraft Launcher
 * Copyright (C) 2025-2026 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.movtery.zalithlauncher.discord

import android.app.Activity
import android.util.Log
import com.discord.socialsdk.DiscordSocialSdkInit

/**
 * Thin JNI wrapper around the Discord Social SDK's token-free Rich Presence RPC
 * (`Client::SetApplicationId` + `Client::UpdateRichPresence`, no `Client::Connect()`).
 *
 * No Discord account token, OAuth session, or Gateway connection is involved: this only
 * reaches the Discord app installed and signed in on the same device, over the SDK's native
 * Android RPC channel. All calls are fire-and-forget; the native side
 * (discord_rpc_bridge.cpp) owns a dedicated thread that pumps `discordpp::RunCallbacks()`,
 * so nothing here blocks the caller.
 */
object DiscordPresenceBridge {
    private const val TAG = "TurboCraft-Discord"

    @Volatile
    private var libraryLoaded = false

    /**
     * Loads the SDK and its native bridge. Must be called on the main thread with the
     * Activity that owns the process.
     * @return true when presence calls can be made
     */
    fun prepare(activity: Activity): Boolean {
        return runCatching {
            DiscordSocialSdkInit.setEngineActivity(activity)
            if (!libraryLoaded) System.loadLibrary("tcdiscordrpc")
            libraryLoaded = true
            true
        }.onFailure { Log.w(TAG, "Discord SDK initialization unavailable", it) }.getOrDefault(false)
    }

    fun available(): Boolean = libraryLoaded

    /** Idempotent: only the first call for a given process spins up the SDK client. */
    fun init(applicationId: Long): Boolean =
        libraryLoaded && runCatching { nativeInit(applicationId) }.getOrDefault(false)

    fun updatePresence(
        name: String,
        details: String,
        state: String,
        activityType: Int,
        startTimestampMs: Long?,
        largeImage: String,
        largeText: String,
        smallImage: String,
        smallText: String,
        buttonLabels: Array<String>,
        buttonUrls: Array<String>,
    ) {
        if (!libraryLoaded) return
        runCatching {
            nativeUpdatePresence(
                name, details, state, activityType,
                startTimestampMs != null, startTimestampMs ?: 0L,
                largeImage, largeText, smallImage, smallText,
                buttonLabels, buttonUrls,
            )
        }
    }

    fun clear() {
        if (libraryLoaded) runCatching { nativeClear() }
    }

    fun shutdown() {
        if (libraryLoaded) runCatching { nativeShutdown() }
    }

    /** Best-effort: true once the last UpdateRichPresence call reported success. */
    fun lastResultOk(): Boolean = libraryLoaded && runCatching { nativeLastResultOk() }.getOrDefault(false)

    @JvmStatic
    private external fun nativeInit(applicationId: Long): Boolean

    @JvmStatic
    private external fun nativeUpdatePresence(
        name: String,
        details: String,
        state: String,
        activityType: Int,
        hasTimestamp: Boolean,
        startTimestampMs: Long,
        largeImage: String,
        largeText: String,
        smallImage: String,
        smallText: String,
        buttonLabels: Array<String>,
        buttonUrls: Array<String>,
    )

    @JvmStatic
    private external fun nativeClear()

    @JvmStatic
    private external fun nativeShutdown()

    @JvmStatic
    private external fun nativeLastResultOk(): Boolean
}
