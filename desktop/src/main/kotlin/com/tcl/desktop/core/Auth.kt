/*
 * TurboCraft Launcher — desktop (Windows) edition
 * Copyright (C) 2025-2026 TCL Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.tcl.desktop.core

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private val GSON = GsonBuilder().setPrettyPrinting().create()

enum class AccountType { OFFLINE, MICROSOFT }

data class Account(
    val type: AccountType = AccountType.OFFLINE,
    val name: String = "",
    val uuid: String = "",
    val accessToken: String = "",
    val refreshToken: String = "",
    val expiresAt: Long = 0,
) {
    val isExpired: Boolean get() = type == AccountType.MICROSOFT && System.currentTimeMillis() > expiresAt
}

data class DeviceCodeInfo(
    val userCode: String,
    val verificationUri: String,
    val expiresIn: Int,
    val interval: Int,
)

object Accounts {
    fun load(): MutableList<Account> {
        AppPaths.ensure()
        val file = AppPaths.accountsFile
        if (!file.isFile) return mutableListOf()
        return runCatching {
            GSON.fromJson(file.readText(), Array<Account>::class.java)?.toMutableList() ?: mutableListOf()
        }.getOrElse { mutableListOf() }
    }

    fun save(accounts: List<Account>) {
        runCatching {
            AppPaths.root.mkdirs()
            AppPaths.accountsFile.writeText(GSON.toJson(accounts))
        }
    }

    /** Classic offline UUID — Minecraft ke offline mode jaisa hi */
    fun offlineUuid(name: String): String =
        UUID.nameUUIDFromBytes("OfflinePlayer:$name".toByteArray()).toString()
}

/**
 * Microsoft login — device code flow (browser me code daalna, koi embedded WebView nahi).
 * Desktop ke liye yahi sabse reliable hai: loopback server, redirect URL, kuch bhi nahi chahiye.
 */
object MicrosoftAuth {
    private const val CLIENT_ID = "00000000402b5328"
    private const val SCOPE = "service::user.auth.xboxlive.com::MBI_SSL"
    private const val DEVICE_CODE_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode"
    private const val TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token"

    suspend fun requestDeviceCode(): DeviceCodeInfo = withContext(Dispatchers.IO) {
        val body = Net.postForm(DEVICE_CODE_URL, mapOf("client_id" to CLIENT_ID, "scope" to SCOPE))
        val json = JsonParser.parseString(body).asJsonObject
        if (json.has("error")) {
            error("Device code error: ${json.get("error_description")?.asString}")
        }
        DeviceCodeInfo(
            userCode = json.get("user_code").asString,
            verificationUri = json.get("verification_uri").asString,
            expiresIn = json.get("expires_in").asInt,
            interval = json.get("interval")?.asInt ?: 5,
        )
    }

    /**
     * Device code poll karta hai jab tak user browser me login na kare.
     */
    suspend fun loginWithDeviceCode(
        info: DeviceCodeInfo,
        deviceCode: String,
        onStatus: (String) -> Unit = {},
    ): Account = withContext(Dispatchers.IO) {
        val deadline = System.currentTimeMillis() + info.expiresIn * 1000L
        while (System.currentTimeMillis() < deadline) {
            delay((info.interval.coerceAtLeast(2)) * 1000L)
            onStatus("Microsoft login ka intezaar… (code: ${info.userCode})")
            val body = Net.postForm(
                TOKEN_URL,
                mapOf(
                    "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                    "client_id" to CLIENT_ID,
                    "device_code" to deviceCode,
                )
            )
            val json = JsonParser.parseString(body).asJsonObject
            val error = json.get("error")?.asString
            when (error) {
                "authorization_pending" -> continue
                "slow_down" -> {
                    delay(5000)
                    continue
                }
                "authorization_declined", "expired_token" -> error("Login cancel ho gaya ya expire ho gaya")
                null -> {
                    val accessToken = json.get("access_token")?.asString ?: continue
                    val refreshToken = json.get("refresh_token")?.asString ?: ""
                    return@withContext exchangeForMinecraft(accessToken, refreshToken)
                }
                else -> error("Microsoft login error: $error")
            }
        }
        error("Device code expire ho gaya, dobara try karo")
    }

    /** Refresh token se naya Minecraft token (launch se pehle) */
    suspend fun refresh(account: Account): Account = withContext(Dispatchers.IO) {
        if (account.refreshToken.isBlank()) error("Refresh token nahi hai, dobara login karo")
        val body = Net.postForm(
            TOKEN_URL,
            mapOf(
                "grant_type" to "refresh_token",
                "client_id" to CLIENT_ID,
                "refresh_token" to account.refreshToken,
                "scope" to SCOPE,
            )
        )
        val json = JsonParser.parseString(body).asJsonObject
        val accessToken = json.get("access_token")?.asString ?: error("Token refresh fail")
        val refreshToken = json.get("refresh_token")?.asString ?: account.refreshToken
        exchangeForMinecraft(accessToken, refreshToken)
    }

    /** MSA token -> Xbox Live -> XSTS -> Minecraft services */
    private fun exchangeForMinecraft(msaToken: String, refreshToken: String): Account {
        val xblBody = """
            {"Properties":{"AuthMethod":"RPS","SiteName":"user.auth.xboxlive.com","RpsTicket":"d=$msaToken"},
            "RelyingParty":"http://auth.xboxlive.com","TokenType":"JWT"}
        """.trimIndent()
        val xbl = JsonParser.parseString(
            Net.postJson("https://user.auth.xboxlive.com/user/authenticate", xblBody)
        ).asJsonObject
        val xblToken = xbl.get("Token").asString
        val userHash = xbl.getAsJsonObject("DisplayClaims")
            .getAsJsonArray("xui").first().asJsonObject.get("uhs").asString

        val xstsBody = """
            {"Properties":{"SandboxId":"RETAIL","UserTokens":["$xblToken"]},
            "RelyingParty":"rp://api.minecraftservices.com/","TokenType":"JWT"}
        """.trimIndent()
        val xstsResponse = Net.postJson("https://xsts.auth.xboxlive.com/xsts/authorize", xstsBody)
        val xstsJson = JsonParser.parseString(xstsResponse).asJsonObject
        if (xstsJson.has("XErr")) {
            val err = xstsJson.get("XErr").asLong
            error(
                when (err) {
                    2148916233L -> "Is Microsoft account se Xbox profile nahi juda. Pehle xbox.com par login karo."
                    2148916238L -> "Ye child account hai, family settings chahiye."
                    else -> "XSTS error $err"
                }
            )
        }
        val xstsToken = xstsJson.get("Token").asString

        val mcBody = """{"identityToken":"XBL3.0 x=$userHash;$xstsToken"}"""
        val mc = JsonParser.parseString(
            Net.postJson("https://api.minecraftservices.com/authentication/login_with_xbox", mcBody)
        ).asJsonObject
        val mcToken = mc.get("access_token")?.asString ?: error("Minecraft token nahi mila")
        val expiresIn = mc.get("expires_in")?.asLong ?: 86400L

        val profile = JsonParser.parseString(
            Net.getString(
                "https://api.minecraftservices.com/minecraft/profile",
                mapOf("Authorization" to "Bearer $mcToken")
            )
        ).asJsonObject
        if (!profile.has("id")) {
            error("Is account ke paas Minecraft Java Edition nahi hai")
        }

        return Account(
            type = AccountType.MICROSOFT,
            name = profile.get("name").asString,
            uuid = profile.get("id").asString,
            accessToken = mcToken,
            refreshToken = refreshToken,
            expiresAt = System.currentTimeMillis() + (expiresIn - 300) * 1000L,
        )
    }

    /** Device code ka raw code bhi chahiye hota hai polling ke liye */
    suspend fun startLogin(onStatus: (String) -> Unit = {}): Pair<DeviceCodeInfo, String> =
        withContext(Dispatchers.IO) {
            val body = Net.postForm(DEVICE_CODE_URL, mapOf("client_id" to CLIENT_ID, "scope" to SCOPE))
            val json = JsonParser.parseString(body).asJsonObject
            val info = DeviceCodeInfo(
                userCode = json.get("user_code").asString,
                verificationUri = json.get("verification_uri").asString,
                expiresIn = json.get("expires_in").asInt,
                interval = json.get("interval")?.asInt ?: 5,
            )
            onStatus("Browser me ${info.verificationUri} kholo aur code daalo: ${info.userCode}")
            info to json.get("device_code").asString
        }

    fun parseAccounts(json: String): List<Account> = GSON.fromJson(json, Array<Account>::class.java)?.toList() ?: emptyList()

    fun serializeAccounts(accounts: List<Account>): String = GSON.toJson(accounts)

    fun copyAccountFile(source: File) {
        if (source.isFile) source.copyTo(AppPaths.accountsFile, overwrite = true)
    }
}
