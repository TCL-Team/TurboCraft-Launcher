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
import com.google.gson.JsonObject
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

    fun offlineUuid(name: String): String =
        UUID.nameUUIDFromBytes("OfflinePlayer:$name".toByteArray()).toString()
}

/**
 * Microsoft sign-in — **mobile edition jaisa hi setup** (Android ka
 * `MicrosoftAuthenticator.kt` isi flow par chalta hai):
 *
 *  1. Live Connect device code:  POST login.live.com/oauth20_connect.srf
 *     client_id = 00000000402b5328, response_type = device_code,
 *     scope = service::user.auth.xboxlive.com::MBI_SSL
 *     → user code + verification URL (koi redirect URI nahi chahiye, isliye
 *       "redirect_uri is not valid" error kabhi nahi aata)
 *  2. Poll: POST login.live.com/oauth20_token.srf (device_code grant)
 *  3. XBL (raw RpsTicket, 400 par "d=" prefix retry) → XSTS → Minecraft services
 *  4. Ownership check → account
 *
 * Agar Settings me apna Entra app id daala ho to Entra v2 device code use hota hai.
 */
object MicrosoftAuth {
    /** Mobile ka public Minecraft client id — sirf Live Connect ke saath valid */
    const val DEFAULT_CLIENT_ID = "00000000402b5328"

    private const val LIVE_AUTH = "https://login.live.com"
    private const val ENTRA_AUTH = "https://login.microsoftonline.com/consumers/oauth2/v2.0"
    private const val LIVE_SCOPE = "service::user.auth.xboxlive.com::MBI_SSL"
    private const val ENTRA_SCOPE = "XboxLive.signin offline_access openid profile email"
    private const val XBL_AUTH = "https://user.auth.xboxlive.com"
    private const val XSTS_AUTH = "https://xsts.auth.xboxlive.com"
    private const val MC_SERVICES = "https://api.minecraftservices.com"

    data class DeviceCodeInfo(
        val userCode: String,
        val verificationUri: String,
        val expiresIn: Int,
        val interval: Int,
        val deviceCode: String,
    )

    private fun isLegacy(clientId: String) =
        clientId.isBlank() || clientId == DEFAULT_CLIENT_ID

    // ── 1. Device code ───────────────────────────────────────────────────────

    suspend fun requestDeviceCode(clientId: String = DEFAULT_CLIENT_ID): DeviceCodeInfo =
        withContext(Dispatchers.IO) {
            val legacy = isLegacy(clientId)
            val body = if (legacy) {
                Net.postForm(
                    "$LIVE_AUTH/oauth20_connect.srf",
                    mapOf(
                        "client_id" to DEFAULT_CLIENT_ID,
                        "response_type" to "device_code",
                        "scope" to LIVE_SCOPE,
                    )
                )
            } else {
                Net.postForm(
                    "$ENTRA_AUTH/devicecode",
                    mapOf("client_id" to clientId, "scope" to ENTRA_SCOPE)
                )
            }
            val json = JsonParser.parseString(body).asJsonObject
            json.get("error")?.let {
                error("Microsoft: ${json.get("error_description")?.asString ?: it.asString}")
            }
            DeviceCodeInfo(
                userCode = json.get("user_code")?.asString ?: error("No user code returned"),
                verificationUri = json.get("verification_uri")?.asString
                    ?: json.get("verification_url")?.asString
                    ?: "https://www.microsoft.com/link",
                expiresIn = json.get("expires_in")?.asInt ?: 900,
                interval = json.get("interval")?.asInt ?: 5,
                deviceCode = json.get("device_code")?.asString ?: error("No device code returned"),
            )
        }

    // ── 2. Poll + full sign-in ───────────────────────────────────────────────

    suspend fun signIn(
        clientId: String = DEFAULT_CLIENT_ID,
        onCode: (DeviceCodeInfo) -> Unit = {},
        onStatus: (String) -> Unit = {},
    ): Account = withContext(Dispatchers.IO) {
        val info = requestDeviceCode(clientId)
        onCode(info)
        onStatus("Enter the code in your browser")
        runCatching {
            java.awt.Desktop.getDesktop().browse(java.net.URI.create(info.verificationUri))
        }

        val token = pollForToken(info, clientId, onStatus)
        onStatus("Signing in with Xbox Live…")
        toAccount(token.accessToken, token.refreshToken, onStatus)
    }

    private data class MsaToken(val accessToken: String, val refreshToken: String)

    private fun pollForToken(
        info: DeviceCodeInfo,
        clientId: String,
        onStatus: (String) -> Unit,
    ): MsaToken {
        val legacy = isLegacy(clientId)
        val deadline = System.currentTimeMillis() + info.expiresIn * 1000L
        var interval = info.interval.coerceAtLeast(2)
        var consecutiveFailures = 0

        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(interval * 1000L)
            onStatus("Waiting for you to finish sign-in (code ${info.userCode})…")

            val body = runCatching {
                if (legacy) {
                    Net.postForm(
                        "$LIVE_AUTH/oauth20_token.srf",
                        mapOf(
                            "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                            "device_code" to info.deviceCode,
                            "client_id" to DEFAULT_CLIENT_ID,
                        )
                    )
                } else {
                    Net.postForm(
                        "$ENTRA_AUTH/token",
                        mapOf(
                            "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                            "device_code" to info.deviceCode,
                            "client_id" to clientId,
                        )
                    )
                }
            }

            if (body.isFailure) {
                // network blip — 5 tak retry, mobile jaisa behaviour
                consecutiveFailures++
                if (consecutiveFailures >= 5) {
                    throw NetworkException("Network error during sign-in: ${body.exceptionOrNull()?.message}")
                }
                continue
            }

            val json = runCatching { JsonParser.parseString(body.getOrThrow()).asJsonObject }.getOrNull()
                ?: continue
            consecutiveFailures = 0

            val access = json.get("access_token")?.asString
            if (!access.isNullOrBlank()) {
                return MsaToken(access, json.get("refresh_token")?.asString ?: "")
            }

            when (json.get("error")?.asString) {
                "authorization_pending", null -> Unit
                "slow_down" -> interval += 3
                "expired_token" -> error("The sign-in code expired. Please try again.")
                "authorization_declined", "access_denied" -> error("Sign-in was cancelled.")
                "invalid_grant" -> error("Sign-in expired. Please try again.")
                else -> error("Microsoft: ${json.get("error_description")?.asString ?: json.get("error")?.asString}")
            }
        }
        error("Sign-in timed out. Please try again.")
    }

    /** Refresh token se naya Minecraft token (launch se pehle) */
    suspend fun refresh(account: Account, clientId: String = DEFAULT_CLIENT_ID): Account =
        withContext(Dispatchers.IO) {
            if (account.refreshToken.isBlank()) error("Session expired — sign in again")
            val legacy = isLegacy(clientId)
            val body = if (legacy) {
                Net.postForm(
                    "$LIVE_AUTH/oauth20_token.srf",
                    mapOf(
                        "client_id" to DEFAULT_CLIENT_ID,
                        "refresh_token" to account.refreshToken,
                        "grant_type" to "refresh_token",
                        "scope" to LIVE_SCOPE,
                    )
                )
            } else {
                Net.postForm(
                    "$ENTRA_AUTH/token",
                    mapOf(
                        "client_id" to clientId,
                        "refresh_token" to account.refreshToken,
                        "grant_type" to "refresh_token",
                        "scope" to ENTRA_SCOPE,
                    )
                )
            }
            val json = JsonParser.parseString(body).asJsonObject
            json.get("error")?.let {
                error(
                    if (it.asString == "invalid_grant") "Session expired — sign in with Microsoft again"
                    else "Token refresh failed: ${json.get("error_description")?.asString ?: it.asString}"
                )
            }
            val access = json.get("access_token")?.asString ?: error("Token refresh failed")
            toAccount(access, json.get("refresh_token")?.asString ?: account.refreshToken) { }
        }

    // ── 3. Xbox Live → XSTS → Minecraft (mobile jaisa) ────────────────────────

    private fun toAccount(msaToken: String, refreshToken: String, onStatus: (String) -> Unit): Account {
        onStatus("Xbox Live…")
        val (xblToken, uhs) = authenticateXbl(msaToken)

        onStatus("Xbox Live security token…")
        val xstsToken = authenticateXsts(xblToken)

        onStatus("Minecraft services…")
        val mc = postJson(
            "$MC_SERVICES/authentication/login_with_xbox",
            """{"identityToken":"XBL3.0 x=$uhs;$xstsToken"}"""
        )
        val mcToken = mc.get("access_token")?.asString
            ?: throw NetworkException("Minecraft did not return a token")
        val expiresIn = mc.get("expires_in")?.asLong ?: 86400L

        onStatus("Checking game ownership…")
        val profile = runCatching {
            JsonParser.parseString(
                Net.getString("$MC_SERVICES/minecraft/profile", mapOf("Authorization" to "Bearer $mcToken"))
            ).asJsonObject
        }.getOrNull()
        if (profile == null || !profile.has("name")) {
            throw NetworkException("This Microsoft account doesn't own Minecraft: Java Edition.")
        }

        return Account(
            type = AccountType.MICROSOFT,
            name = profile.get("name").asString,
            uuid = profile.get("id").asString,
            accessToken = mcToken,
            refreshToken = refreshToken,
            expiresAt = System.currentTimeMillis() + (expiresIn - 300).coerceAtLeast(600) * 1000L,
        )
    }

    private fun authenticateXbl(accessToken: String): Pair<String, String> {
        fun request(rpsTicket: String): Pair<String, String> {
            val response = postJson(
                "$XBL_AUTH/user/authenticate",
                """
                {"Properties":{"AuthMethod":"RPS","SiteName":"user.auth.xboxlive.com","RpsTicket":"$rpsTicket"},
                 "RelyingParty":"http://auth.xboxlive.com","TokenType":"JWT"}
                """.trimIndent()
            )
            val token = response.get("Token")?.asString
                ?: throw NetworkException("Xbox Live did not return a token. Does this account have an Xbox profile?")
            val uhs = response.getAsJsonObject("DisplayClaims").getAsJsonArray("xui")
                .first().asJsonObject.get("uhs").asString
            return token to uhs
        }

        // Mobile/Pojav jaisa: raw token pehle, 400 par "d=" prefix
        return runCatching { request(accessToken) }.getOrElse { first ->
            runCatching { request("d=$accessToken") }.getOrElse { throw first }
        }
    }

    private fun authenticateXsts(xblToken: String): String {
        val body = """
            {"Properties":{"SandboxId":"RETAIL","UserTokens":["$xblToken"]},
             "RelyingParty":"rp://api.minecraftservices.com/","TokenType":"JWT"}
        """.trimIndent()
        val raw = Net.postJson("$XSTS_AUTH/xsts/authorize", body)
        val json = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull()
            ?: throw NetworkException("Xbox Live authorization failed")

        json.get("XErr")?.asLong?.let { code ->
            throw NetworkException(
                when (code) {
                    2148916227L -> "This Xbox account has been banned."
                    2148916229L -> "This Xbox account is restricted (child/limited profile)."
                    2148916233L -> "This Microsoft account has no Xbox profile. Sign in at xbox.com once, then retry."
                    2148916234L -> "Xbox Live did not accept the service terms for this account."
                    2148916235L -> "Xbox Live is not available in this country."
                    2148916236L -> "This account needs age verification (proof of age)."
                    2148916237L -> "This account has reached its play-time limit."
                    2148916238L -> "This is a child account — a parent must add it to a Microsoft family."
                    else -> "Xbox Live error $code"
                }
            )
        }

        return json.get("Token")?.asString ?: throw NetworkException("Xbox Live authorization failed")
    }

    private fun postJson(url: String, body: String): JsonObject {
        val raw = Net.postJson(url, body)
        return runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull()
            ?: throw NetworkException("Unexpected response from ${url.substringAfter("//").substringBefore("/")}")
    }

    fun copyAccountFile(source: File) {
        if (source.isFile) source.copyTo(AppPaths.accountsFile, overwrite = true)
    }
}
