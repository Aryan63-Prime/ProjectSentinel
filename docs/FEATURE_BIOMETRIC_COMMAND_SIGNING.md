# Feature Specification & Code: Biometric Security & Cryptographic Command Signing

## 1. Overview & Problem Statement
In surveillance and remote management platforms, high-privilege air commands (e.g., executing remote shell scripts, capturing screenshots, downloading private documents) represent significant security risks. If an unauthorized individual gains physical access to the administrator's unlocked smartphone, they could issue destructive commands.

This feature establishes an **Asymmetric Cryptographic Command Gatekeeper**:
1. **Biometric Gatekeeper:** Requires biometric authentication (`BiometricPrompt` via Fingerprint or Face Unlock) on the Admin app prior to unlocking high-privilege air commands.
2. **Hardware-Backed Signature:** The Admin app signs the command payload using an Elliptic Curve (EC) private key generated inside the **Android Keystore System** (`SHA256withECDSA`).
3. **Host-Side Verification:** The Host verifies the cryptographic signature against the registered Admin public key before executing the command, providing a tamper-proof audit trail.

---

## 2. Protocol Specification (`shared/`)

Add signature metadata to the air command payload:

```json
{
  "type": "COMMAND",
  "version": 1,
  "timestamp": 1727500400,
  "sequence": 204,
  "data": {
    "command": "EXECUTE_SHELL",
    "params": {
      "cmd": "uptime"
    },
    "signature": "MEUCIQDZ7xP...",
    "adminKeyAlias": "sentinel_admin_ec_key"
  }
}
```

---

## 3. Admin-App Implementation

### File: `admin-app/data/src/main/java/com/sentinel/admin/data/security/CryptoCommandSigner.kt`

```kotlin
package com.sentinel.admin.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CryptoCommandSigner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "Sentinel:Signer"
        private const val KEY_ALIAS = "sentinel_admin_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }

    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    init {
        ensureKeyExists()
    }

    private fun ensureKeyExists() {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val kpg = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                ANDROID_KEYSTORE
            )
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(false) // Set to true if BiometricPrompt unlocks key directly
                .build()

            kpg.initialize(spec)
            kpg.generateKeyPair()
            Log.i(TAG, "Generated hardware-backed EC keypair: $KEY_ALIAS")
        }
    }

    fun signCommand(canonicalPayload: String): String {
        val privateKey = keyStore.getKey(KEY_ALIAS, null) as java.security.PrivateKey
        val signature = Signature.getInstance("SHA256withECDSA").apply {
            initSign(privateKey)
            update(canonicalPayload.toByteArray(Charsets.UTF_8))
        }
        val sigBytes = signature.sign()
        return Base64.encodeToString(sigBytes, Base64.NO_WRAP)
    }

    fun getPublicKeyBase64(): String {
        val pubKey = keyStore.getCertificate(KEY_ALIAS).publicKey
        return Base64.encodeToString(pubKey.encoded, Base64.NO_WRAP)
    }
}
```

### File: `admin-app/app/src/main/java/com/sentinel/admin/ui/security/BiometricPromptHelper.kt`

```kotlin
package com.sentinel.admin.ui.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object BiometricPromptHelper {

    fun authenticate(
        activity: FragmentActivity,
        title: String = "Authorize Critical Command",
        subtitle: String = "Verify identity to execute high-privilege operation",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onError(errString.toString())
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onError("Biometric authentication failed")
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        prompt.authenticate(promptInfo)
    }
}
```

---

## 4. Host-App Verification

In [`CommandProcessor.kt`](file:///Users/ayush/Desktop/Servillance/host-app/service/src/main/java/com/sentinel/host/service/CommandProcessor.kt):

```kotlin
class CommandProcessor @Inject constructor(...) {

    private fun verifyAdminSignature(
        commandJsonString: String,
        signatureBase64: String,
        adminPublicKey: PublicKey
    ): Boolean {
        return try {
            val sigBytes = Base64.decode(signatureBase64, Base64.DEFAULT)
            val signature = Signature.getInstance("SHA256withECDSA").apply {
                initVerify(adminPublicKey)
                update(commandJsonString.toByteArray(Charsets.UTF_8))
            }
            signature.verify(sigBytes)
        } catch (e: Exception) {
            Log.e("Sentinel:Verifier", "Signature verification error: ${e.message}")
            false
        }
    }
}
```
