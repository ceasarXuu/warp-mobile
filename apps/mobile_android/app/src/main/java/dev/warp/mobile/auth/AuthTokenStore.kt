package dev.warp.mobile.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

class AuthTokenStore(context: Context) : AuthHandoffProvider {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)

    @Volatile
    private var cachedRefreshToken: String? = null

    override fun refreshTokenForHandoff(): String? = refreshToken()

    override fun saveRefreshTokenFromHandoff(token: String) {
        saveRefreshToken(token)
    }

    fun hasRefreshToken(): Boolean = refreshToken() != null

    fun refreshToken(): String? {
        cachedRefreshToken?.let { return it }
        val payload = prefs.getString(RefreshTokenKey, null) ?: return null
        val token = runCatching { decrypt(payload) }.getOrNull()
        cachedRefreshToken = token
        return token
    }

    fun saveRefreshToken(token: String) {
        cachedRefreshToken = token
        prefs.edit().putString(RefreshTokenKey, encrypt(token)).apply()
    }

    fun clear() {
        cachedRefreshToken = null
        prefs.edit().remove(RefreshTokenKey).apply()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(Transformation)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val cipherText = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return JSONObject()
            .put("v", 1)
            .put("iv", cipher.iv.base64())
            .put("value", cipherText.base64())
            .toString()
    }

    private fun decrypt(payload: String): String {
        val json = JSONObject(payload)
        val cipher = Cipher.getInstance(Transformation)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(GcmTagLengthBits, json.getString("iv").fromBase64()),
        )
        val plainText = cipher.doFinal(json.getString("value").fromBase64())
        return String(plainText, StandardCharsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(AndroidKeyStore).apply { load(null) }
        (keyStore.getEntry(KeyAlias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, AndroidKeyStore)
        val spec = KeyGenParameterSpec.Builder(
            KeyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun ByteArray.base64(): String = Base64.encodeToString(this, Base64.NO_WRAP)

    private fun String.fromBase64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)

    private companion object {
        const val PrefsName = "warp_mobile_auth"
        const val RefreshTokenKey = "refresh_token"
        const val AndroidKeyStore = "AndroidKeyStore"
        const val KeyAlias = "warp_mobile_refresh_token"
        const val Transformation = "AES/GCM/NoPadding"
        const val GcmTagLengthBits = 128
    }
}
