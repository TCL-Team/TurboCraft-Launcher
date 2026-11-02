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
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetSocketAddress
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Base64
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

    /** Classic offline UUID — offline mode jaisa hi */
    fun offlineUuid(name: String): String =
        UUID.nameUUIDFromBytes("OfflinePlayer:$name".toByteArray()).toString()
}

/**
 * Microsoft sign-in.
 *
 * Do raste, dono me koi embedded WebView nahi:
 *  1. **Browser + localhost** (recommended): system browser khulta hai, login ke baad
 *     `http://localhost:PORT/` par wapas aata hai, launcher code khud le leta hai (PKCE).
 *  2. **Device code**: browser me code type karna hota hai. Fallback ke liye.
 *
 * Client ID: Microsoft har third-party launcher se apna app registration maangta hai
 * (free, 2 min). Settings me paste kar sakte hain; na ho to default use hota hai.
 */
object MicrosoftAuth {
    /**
     * Purana "Minecraft" public client id — device-code fallback ke liye.
     * Apna client id settings me daalo to browser flow bhi chalega.
     */
    const val DEFAULT_CLIENT_ID = "00000000402b5328"
    private const val AUTHORITY = "https://login.microsoftonline.com/consumers/oauth2/v2.0"
    private const val SCOPE = "XboxLive.signin offline_access"

    /** Xbox Live / Minecraft services ke liye zaroori scopes */
    private val XBL_SCOPES = listOf("XboxLive.signin", "offline_access")

    data class DeviceCodeInfo(
        val userCode: String,
        val verificationUri: String,
        val expiresIn: Int,
        val interval: Int,
        val deviceCode: String,
    )

    // ── Flow 1: browser + loopback (PKCE) ─────────────────────────────────────

    /**
     * System browser kholta hai. Jab user login kar leta hai, Microsoft
     * `http://localhost:<port>/` par redirect karta hai aur hum code le lete hain.
     */
    suspend fun signInWithBrowser(
        clientId: String = DEFAULT_CLIENT_ID,
        onStatus: (String) -> Unit = {},
        onUrl: (String) -> Unit = {},
    ): Account = withContext(Dispatchers.IO) {
        val server = try {
            HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        } catch (error: Throwable) {
            throw NetworkException("Couldn't open a local port for sign-in: ${error.message}", error)
        }

        val port = server.address.port
        val redirectUri = "http://localhost:$port/"
        val verifier = randomUrlSafe(64)
        val challenge = base64Url(sha256(verifier))
        val state = randomUrlSafe(16)

        val codeFuture = java.util.concurrent.CompletableFuture<String>()

        server.createContext("/") { exchange ->
            try {
                val params = exchange.requestURI.rawQuery.orEmpty()
                    .split("&")
                    .mapNotNull { part ->
                        val index = part.indexOf('=')
                        if (index <= 0) {
                            null
                        } else {
                            part.substring(0, index) to
                                java.net.URLDecoder.decode(part.substring(index + 1), Charsets.UTF_8)
                        }
                    }
                    .toMap()

                val body = when {
                    params["error"] != null ->
                        "Sign-in cancelled or failed: ${params["error"]} ${params["error_description"] ?: ""}"
                    params["state"] != state -> "State mismatch — try again."
                    params["code"] != null -> "Signed in. You can close this tab and return to the launcher."
                    else -> "Waiting for Microsoft…"
                }
                val bytes = ("<html><head><meta charset='utf-8'><title>TurboCraft</title></head>" +
                    "<body style='font-family:sans-serif;background:#101418;color:#E0E2E8;padding:48px'>" +
                    "<h2>$body</h2></body></html>").toByteArray()
                exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }

                params["code"]?.let { codeFuture.complete(it) }
                params["error"]?.let { codeFuture.completeExceptionally(RuntimeException(it)) }
            } catch (error: Throwable) {
                codeFuture.completeExceptionally(error)
            }
        }
        server.start()

        val url = buildString {
            append("$AUTHORITY/authorize")
            append("?client_id=").append(URLEncoder.encode(clientId))
            append("&response_type=code")
            append("&redirect_uri=").append(URLEncoder.encode(redirectUri))
            append("&response_mode=query")
            append("&scope=").append(URLEncoder.encode(XBL_SCOPES.joinToString(" ")))
            append("&prompt=select_account")
            append("&state=").append(state)
            append("&code_challenge=").append(challenge)
            append("&code_challenge_method=S256")
        }

        onStatus("Opening your browser for Microsoft sign-in…")
        onUrl(url)
        runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI.create(url)) }

        val code = try {
            while (true) {
                try {
                    codeFuture.get(1, java.util.concurrent.TimeUnit.SECONDS).let { return@let }
                    break
                } catch (_: java.util.concurrent.TimeoutException) {
                    onStatus("Waiting for you to finish signing in…")
                }
            }
            codeFuture.get()
        } catch (error: java.util.concurrent.ExecutionException) {
            throw NetworkException(signInErrorMessage(error.cause?.message), error)
        } finally {
            server.stop(0)
        }

        onStatus("Exchanging token with Microsoft…")
        val tokenBody = Net.postForm(
            "$AUTHORITY/token",
            mapOf(
                "client_id" to clientId,
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to redirectUri,
                "code_verifier" to verifier,
            )
        )
        val tokenJson = parseOrThrow(tokenBody, "Microsoft did not accept the sign-in")
        exchangeForMinecraft(
            msaToken = tokenJson.get("access_token").asString,
            refreshToken = tokenJson.get("refresh_token")?.asString ?: "",
            clientId = clientId
        )
    }

    // ── Flow 2: device code ───────────────────────────────────────────────────

    suspend fun requestDeviceCode(clientId: String = DEFAULT_CLIENT_ID): DeviceCodeInfo =
        withContext(Dispatchers.IO) {
            val body = Net.postForm(
                "$AUTHORITY/devicecode",
                mapOf("client_id" to clientId, "scope" to SCOPE)
            )
            val json = parseOrThrow(body, "Device code request failed")
            DeviceCodeInfo(
                userCode = json.get("user_code").asString,
                verificationUri = json.get("verification_uri")?.asString
                    ?: "https://microsoft.com/link",
                expiresIn = json.get("expires_in")?.asInt ?: 900,
                interval = json.get("interval")?.asInt ?: 5,
                deviceCode = json.get("device_code").asString,
            )
        }

    suspend fun signInWithDeviceCode(
        info: DeviceCodeInfo,
        clientId: String = DEFAULT_CLIENT_ID,
        onStatus: (String) -> Unit = {},
    ): Account = withContext(Dispatchers.IO) {
        val deadline = System.currentTimeMillis() + info.expiresIn * 1000L
        var interval = info.interval.coerceAtLeast(2)

        while (System.currentTimeMillis() < deadline) {
            delay(interval * 1000L)
            onStatus("Waiting for sign-in… (code ${info.userCode})")
            val body = Net.postForm(
                "$AUTHORITY/token",
                mapOf(
                    "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                    "client_id" to clientId,
                    "device_code" to info.deviceCode,
                )
            )
            val json = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull()
                ?: continue
            when (json.get("error")?.asString) {
                null -> {
                    val token = json.get("access_token")?.asString ?: continue
                    return@withContext exchangeForMinecraft(
                        token,
                        json.get("refresh_token")?.asString ?: "",
                        clientId
                    )
                }
                "authorization_pending" -> continue
                "slow_down" -> interval += 3
                "expired_token" -> error("The sign-in code expired. Please try again.")
                "authorization_declined" -> error("Sign-in was cancelled.")
                "unauthorized_client", "invalid_client" -> error(
                    "Microsoft rejected this app id. Open Settings → Microsoft client id and " +
                        "paste your own free app id (see instructions there)."
                )
                else -> error("Microsoft error: ${json.get("error_description")?.asString ?: json.get("error")?.asString}")
            }
        }
        error("Sign-in timed out. Please try again.")
    }

    /** Refresh token se naya Minecraft token (launch se pehle) */
    suspend fun refresh(account: Account, clientId: String = DEFAULT_CLIENT_ID): Account =
        withContext(Dispatchers.IO) {
            if (account.refreshToken.isBlank()) error("Session expired — sign in with Microsoft again")
            val body = Net.postForm(
                "$AUTHORITY/token",
                mapOf(
                    "client_id" to clientId,
                    "grant_type" to "refresh_token",
                    "refresh_token" to account.refreshToken,
                    "scope" to SCOPE,
                )
            )
            val json = parseOrThrow(body, "Couldn't refresh the Microsoft session")
            exchangeForMinecraft(
                json.get("access_token").asString,
                json.get("refresh_token")?.asString ?: account.refreshToken,
                clientId
            )
        }

    // ── Xbox Live → Minecraft services ───────────────────────────────────────

    private fun exchangeForMinecraft(msaToken: String, refreshToken: String, clientId: String): Account {
        val xbl = postJsonOrThrow(
            "https://user.auth.xboxlive.com/user/authenticate",
            """
            {"Properties":{"AuthMethod":"RPS","SiteName":"user.auth.xboxlive.com","RpsTicket":"d=$msaToken"},
             "RelyingParty":"http://auth.xboxlive.com","TokenType":"JWT"}
            """.trimIndent(),
            "Xbox Live sign-in failed"
        )
        val xblToken = xbl.get("Token")?.asString
            ?: throw NetworkException("Xbox Live did not return a token. Check that this account has an Xbox profile.")
        val userHash = xbl.getAsJsonObject("DisplayClaims").getAsJsonArray("xui")
            .first().asJsonObject.get("uhs").asString

        val xsts = runCatching {
            postJsonOrThrow(
                "https://xsts.auth.xboxlive.com/xsts/authorize",
                """{"Properties":{"SandboxId":"RETAIL","UserTokens":["$xblToken"]},
                   "RelyingParty":"rp://api.minecraftservices.com/","TokenType":"JWT"}""",
                "Xbox Live authorization failed"
            )
        }.getOrElse { error -> throw error }

        if (xsts.has("XErr")) {
            val code = xsts.get("XErr").asLong
            error(
                when (code) {
                    2148916233L -> "This Microsoft account has no Xbox profile. Sign in at xbox.com once, then retry."
                    2148916235L -> "Xbox Live is not available in your country."
                    2148916238L -> "This is a child account — a parent must add it to a Microsoft family."
                    else -> "Xbox Live error $code"
                }
            )
        }
        val xstsToken = xsts.get("Token").asString

        val mc = postJsonOrThrow(
            "https://api.minecraftservices.com/authentication/login_with_xbox",
            """{"identityToken":"XBL3.0 x=$userHash;$xstsToken"}""",
            "Minecraft login failed"
        )
        val mcToken = mc.get("access_token")?.asString ?: throw NetworkException("Minecraft did not return a token")
        val expiresIn = mc.get("expires_in")?.asLong ?: 86400L

        val profile = runCatching {
            JsonParser.parseString(
                Net.getString(
                    "https://api.minecraftservices.com/minecraft/profile",
                    mapOf("Authorization" to "Bearer $mcToken")
                )
            ).asJsonObject
        }.getOrElse {
            throw NetworkException("This Microsoft account doesn't own Minecraft: Java Edition.")
        }
        if (!profile.has("name")) {
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

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun parseOrThrow(body: String, fallback: String): JsonObject {
        val json = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull()
            ?: throw NetworkException(fallback)
        val error = json.get("error")
        if (error != null) {
            val description = json.get("error_description")?.asString ?: error.asString
            throw NetworkException("$fallback: $description")
        }
        return json
    }

    private fun postJsonOrThrow(url: String, body: String, fallback: String): JsonObject {
        val response = Net.postJson(url, body)
        return runCatching { JsonParser.parseString(response).asJsonObject }.getOrNull()
            ?: throw NetworkException(fallback)
    }

    private fun signInErrorMessage(detail: String?): String = when {
        detail.isNullOrBlank() -> "Sign-in was cancelled."
        detail.contains("access_denied") -> "Sign-in was cancelled."
        detail.contains("invalid_client") || detail.contains("unauthorized_client") ->
            "Microsoft rejected this app id. Set your own client id in Settings."
        else -> "Sign-in failed: $detail"
    }

    private fun randomUrlSafe(bytes: Int): String {
        val buffer = ByteArray(bytes)
        java.security.SecureRandom().nextBytes(buffer)
        return base64Url(buffer)
    }

    private fun base64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun sha256(value: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray())

    fun copyAccountFile(source: File) {
        if (source.isFile) source.copyTo(AppPaths.accountsFile, overwrite = true)
    }
}
