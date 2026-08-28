package com.movtery.zalithlauncher.game.cloudsync

import com.movtery.zalithlauncher.path.GLOBAL_CLIENT
import com.movtery.zalithlauncher.path.SUPABASE_API_KEY
import com.movtery.zalithlauncher.path.URL_SUPABASE_AUTH_BASE
import com.movtery.zalithlauncher.setting.AllSettings
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val CLOUD_JSON = Json { ignoreUnknownKeys = true }

@Serializable
private data class AuthRequestBody(
    val email: String,
    val password: String
)

@Serializable
private data class AuthUser(
    val id: String,
    val email: String? = null
)

@Serializable
private data class AuthSuccessResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    val user: AuthUser
)

@Serializable
private data class AuthErrorResponse(
    @SerialName("error_description") val errorDescription: String? = null,
    val msg: String? = null,
    val message: String? = null
)

sealed class CloudAuthResult {
    data class Success(val email: String, val accessToken: String, val refreshToken: String) : CloudAuthResult()
    data class Failure(val reason: String) : CloudAuthResult()
}

/**
 * Cloud Sync - Supabase Auth ke saath baat karne ka layer.
 * Ye Minecraft account se bilkul alag identity system hai.
 */
object CloudAuthApi {

    suspend fun signUp(email: String, password: String): CloudAuthResult {
        return runCatching {
            val response = GLOBAL_CLIENT.post("${URL_SUPABASE_AUTH_BASE}signup") {
                header("apikey", SUPABASE_API_KEY)
                contentType(ContentType.Application.Json)
                setBody(AuthRequestBody(email, password))
            }
            parseAuthResponse(response)
        }.getOrElse { CloudAuthResult.Failure(it.message ?: "Unknown error") }
    }

    suspend fun signIn(email: String, password: String): CloudAuthResult {
        return runCatching {
            val response = GLOBAL_CLIENT.post("${URL_SUPABASE_AUTH_BASE}token?grant_type=password") {
                header("apikey", SUPABASE_API_KEY)
                contentType(ContentType.Application.Json)
                setBody(AuthRequestBody(email, password))
            }
            parseAuthResponse(response)
        }.getOrElse { CloudAuthResult.Failure(it.message ?: "Unknown error") }
    }

    fun signOut() {
        AllSettings.cloudSyncEmail.save("")
        AllSettings.cloudSyncAccessToken.save("")
        AllSettings.cloudSyncRefreshToken.save("")
    }

    fun isLoggedIn(): Boolean = AllSettings.cloudSyncAccessToken.getValue().isNotBlank()

    private suspend fun parseAuthResponse(response: HttpResponse): CloudAuthResult {
        val bodyText = response.bodyAsText()
        return if (response.status.isSuccess()) {
            val parsed = CLOUD_JSON.decodeFromString(AuthSuccessResponse.serializer(), bodyText)
            AllSettings.cloudSyncEmail.save(parsed.user.email ?: email())
            AllSettings.cloudSyncAccessToken.save(parsed.accessToken)
            AllSettings.cloudSyncRefreshToken.save(parsed.refreshToken)
            CloudAuthResult.Success(
                email = parsed.user.email ?: "",
                accessToken = parsed.accessToken,
                refreshToken = parsed.refreshToken
            )
        } else {
            val error = runCatching {
                CLOUD_JSON.decodeFromString(AuthErrorResponse.serializer(), bodyText)
            }.getOrNull()
            val reason = error?.errorDescription ?: error?.msg ?: error?.message ?: "Login/Signup failed"
            CloudAuthResult.Failure(reason)
        }
    }

    private fun email() = AllSettings.cloudSyncEmail.getValue()
}
