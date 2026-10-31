package com.example.data.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

object AuthManager {

    private const val TAG = "MaqraahAuth"

    private val auth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    private val firestore: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    private fun log(message: String) {
        Log.d(TAG, message)
    }

    private fun getWebClientId(context: Context): String {
        val clientId = context.getString(R.string.default_web_client_id)
        require(clientId.isNotBlank()) { "Google Web Client ID is empty." }
        log("STEP 1: Web Client ID loaded.")
        return clientId
    }

    private suspend fun saveUserMetadataToFirestore(
        user: com.google.firebase.auth.FirebaseUser
    ) {
        log("STEP 6: Saving user profile to Firestore.")
        val uid = user.uid
        val userRef = firestore.collection("users").document(uid)
        val snapshot = userRef.get().await()

        val commonData = hashMapOf<String, Any>(
            "uid" to uid,
            "name" to (user.displayName ?: ""),
            "email" to (user.email ?: ""),
            "photoUrl" to (user.photoUrl?.toString() ?: ""),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        if (snapshot.exists()) {
            userRef.update(commonData).await()
            log("STEP 7: Existing user profile updated.")
        } else {
            val newUserData = hashMapOf<String, Any>(
                "uid" to uid,
                "name" to (user.displayName ?: ""),
                "email" to (user.email ?: ""),
                "photoUrl" to (user.photoUrl?.toString() ?: ""),
                "role" to "student",
                "status" to "active",
                "studentCode" to "IM-STU-${uid.take(6).uppercase()}",
                "minuteBalance" to 100L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            userRef.set(newUserData).await()
            log("STEP 7: New user profile created.")
        }
    }

    private suspend fun authenticateWithGoogleToken(
        googleIdToken: String
    ): com.google.firebase.auth.FirebaseUser {
        require(googleIdToken.isNotBlank()) { "Google ID token is empty." }
        log("STEP 4: Google ID token received. Length=${googleIdToken.length}")

        val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
        log("STEP 5: Sending Google credential to Firebase.")

        val result = auth.signInWithCredential(firebaseCredential).await()
        val user = result.user ?: throw IllegalStateException("Firebase returned no user.")
        log("STEP 5 SUCCESS: Firebase authentication succeeded. UID=${user.uid}")

        // تحصين عملية Firestore: إذا فشلت لن يُفشل تسجيل الدخول بأكمله
        try {
            saveUserMetadataToFirestore(user)
        } catch (e: Exception) {
            Log.e(TAG, "FIRESTORE PROFILE SAVE FAILED (non-blocking)", e)
        }

        return user
    }

    fun signInWithGoogle(
        context: Context,
        credentialManager: CredentialManager,
        onAuthSuccess: () -> Unit,
        onAuthError: (String) -> Unit,
        onAuthCancelled: () -> Unit,
        scope: CoroutineScope
    ) {
        scope.launch {
            try {
                log("STEP 0: Google sign-in started.")
                val activity = context as? Activity
                    ?: throw IllegalStateException("Activity context is required.")

                val serverClientId = getWebClientId(context)

                // استخدام GetSignInWithGoogleOption مباشرة (أكثر استقراراً على Android 14+)
                val signInOption = GetSignInWithGoogleOption.Builder(serverClientId)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(signInOption)
                    .build()

                log("STEP 2: Opening Google account chooser.")
                val credential = credentialManager.getCredential(
                    context = activity,
                    request = request
                ).credential

                log("STEP 3: Google credential returned. Type=${credential.type}")

                if (credential !is CustomCredential ||
                    credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    throw IllegalStateException("Unexpected Google credential type: ${credential.type}")
                }

                val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val token = googleCredential.idToken

                authenticateWithGoogleToken(token)

                log("STEP 8: Sign-in and Firestore profile completed.")
                onAuthSuccess()

            } catch (e: GetCredentialCancellationException) {
                // إلغاء من المستخدم (أغلق النافذة) -> لا نعرض رسالة خطأ
                log("USER CANCELLED: ${e.message}")
                onAuthCancelled()
                
            } catch (e: GetCredentialException) {
                log("CREDENTIAL ERROR: ${e.javaClass.simpleName}: ${e.message}")
                onAuthError("خطأ في Google Credentials: ${e.javaClass.simpleName}")
                
            } catch (e: CancellationException) {
                log("Coroutine cancelled.")
                throw e
                
            } catch (e: Exception) {
                Log.e(TAG, "SIGN-IN FAILED", e)
                onAuthError(describeFirebaseError(e))
            }
        }
    }

    fun attemptAutoSignIn(
        context: Context,
        onAuthSuccess: () -> Unit,
        onUnauthenticated: () -> Unit,
        scope: CoroutineScope
    ) {
        scope.launch {
            try {
                val user = auth.currentUser
                if (user == null) {
                    log("AUTO SIGN-IN: No Firebase user.")
                    onUnauthenticated()
                    return@launch
                }
                log("AUTO SIGN-IN: Existing Firebase user found.")
                saveUserMetadataToFirestore(user)
                onAuthSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AUTO SIGN-IN FAILED", e)
                onUnauthenticated()
            }
        }
    }

    fun signOut(
        context: Context,
        credentialManager: CredentialManager,
        onComplete: () -> Unit
    ) {
        try {
            auth.signOut()
            val activity = context as? Activity
            if (activity == null) {
                onComplete()
                return
            }

            CoroutineScope(Dispatchers.Main).launch {
                try {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                    log("Credential state cleared.")
                } catch (e: Exception) {
                    Log.e(TAG, "CLEAR CREDENTIAL STATE FAILED", e)
                } finally {
                    onComplete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "SIGN OUT FAILED", e)
            onComplete()
        }
    }

    // دالة مساعدة لترجمة أخطاء Firebase إلى رسائل مفهومة
    private fun describeFirebaseError(e: Exception): String {
        val msg = e.message ?: ""
        return when {
            msg.contains("ERROR_INVALID_CREDENTIAL") || msg.contains("10:") -> 
                "فشل التحقق: تأكد من إضافة SHA-1 في Firebase Console"
            msg.contains("ERROR_INVALID_IDP_RESPONSE") -> 
                "خطأ في Web Client ID، تأكد من strings.xml"
            msg.contains("16:") -> 
                "google-services.json لا يحتوي Web Client ID"
            msg.contains("PERMISSION_DENIED") -> 
                "قواعد Firestore تمنع الحفظ (تم تجاهله، الحساب يعمل)"
            msg.contains("NETWORK_ERROR") -> 
                "مشكلة في الاتصال بالإنترنت"
            msg.contains("DEVELOPER_ERROR") -> 
                "إعدادات OAuth في Google Cloud Console ناقصة"
            else -> "${e.javaClass.simpleName}: $msg"
        }
    }
}