
package com.example.data.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

object AuthManager {

    private const val TAG = "AuthManager"

    // قراءة Web Client ID من google-services.json
    private fun getWebClientId(context: Context): String? {
        val resId = context.resources.getIdentifier(
            "default_web_client_id",
            "string",
            context.packageName
        )

        return if (resId != 0) {
            context.getString(resId)
        } else {
            null
        }
    }

    // الحصول على قاعدة بيانات Firestore
    private fun getFirestoreDb(context: Context): FirebaseFirestore {
        val dbIdRes = context.resources.getIdentifier(
            "firestore_database_id",
            "string",
            context.packageName
        )

        val dbId = if (dbIdRes != 0) {
            context.getString(dbIdRes)
        } else {
            null
        }

        return if (!dbId.isNullOrBlank()) {
            FirebaseFirestore.getInstance(dbId)
        } else {
            FirebaseFirestore.getInstance()
        }
    }

    // حفظ بيانات المستخدم
    private suspend fun saveUserMetadataToFirestore(
        context: Context,
        firebaseUser: FirebaseUser
    ) {
        val uid = firebaseUser.uid

        val userRef = getFirestoreDb(context)
            .collection("users")
            .document(uid)

        val userData = mapOf(
            "uid" to uid,
            "name" to (
                firebaseUser.displayName
                    ?: "طالب مقرأة الإمام الجزري"
            ),
            "email" to (
                firebaseUser.email
                    ?: "student@maqraah.org"
            ),
            "photoUrl" to (
                firebaseUser.photoUrl?.toString() ?: ""
            ),
            "role" to "student",
            "status" to "active",
            "studentCode" to "IM-STU-${uid.take(6).uppercase()}",
            "minuteBalance" to 100L,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        try {
            userRef.set(
                userData + mapOf(
                    "createdAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()

            Log.d(TAG, "User metadata saved: $uid")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to save user metadata", e)
        }
    }

    // تحويل Google ID Token إلى Firebase Credential
    private suspend fun authenticateWithFirebase(
        context: Context,
        credential: androidx.credentials.Credential
    ) {
        if (
            credential !is CustomCredential ||
            credential.type !=
            GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            throw IllegalStateException(
                "Unexpected credential type: ${credential.type}"
            )
        }

        val googleIdToken = GoogleIdTokenCredential
            .createFrom(credential.data)
            .idToken

        val firebaseCredential = GoogleAuthProvider.getCredential(
            googleIdToken,
            null
        )

        val result = Firebase.auth
            .signInWithCredential(firebaseCredential)
            .await()

        result.user?.let { user ->
            saveUserMetadataToFirestore(context, user)
        } ?: throw IllegalStateException(
            "Firebase authentication returned no user"
        )
    }

    // تسجيل الدخول التلقائي
    fun attemptAutoSignIn(
        context: Context,
        credentialManager: CredentialManager,
        onAuthSuccess: () -> Unit,
        onUnauthenticated: () -> Unit,
        scope: CoroutineScope
    ) {
        val currentUser = Firebase.auth.currentUser

        if (currentUser != null) {
            scope.launch {
                saveUserMetadataToFirestore(context, currentUser)
                onAuthSuccess()
            }
            return
        }

        val clientId = getWebClientId(context)

        if (clientId.isNullOrBlank()) {
            Log.e(TAG, "Web Client ID not found")
            onUnauthenticated()
            return
        }

        val googleOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleOption)
            .build()

        scope.launch {
            try {
                val result = credentialManager.getCredential(
                    context,
                    request
                )

                authenticateWithFirebase(
                    context,
                    result.credential
                )

                onAuthSuccess()

            } catch (e: Exception) {
                Log.w(TAG, "Automatic sign-in unavailable", e)
                onUnauthenticated()
            }
        }
    }

    // تسجيل الدخول اليدوي باستخدام Google
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
                val activity = context as? Activity

                if (activity == null) {
                    onAuthError(
                        "Activity context is required for Google Sign-In"
                    )
                    return@launch
                }

                val clientId = getWebClientId(context)

                if (clientId.isNullOrBlank()) {
                    onAuthError(
                        "default_web_client_id not found. " +
                        "Check google-services.json"
                    )
                    return@launch
                }

                val credential = try {
                    // المحاولة الأولى: Google ID Option
                    val googleOption = GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId(clientId)
                        .setAutoSelectEnabled(false)
                        .build()

                    val request = GetCredentialRequest.Builder()
                        .addCredentialOption(googleOption)
                        .build()

                    credentialManager.getCredential(
                        activity,
                        request
                    ).credential

                } catch (e: NoCredentialException) {
                    Log.w(
                        TAG,
                        "No credential from GetGoogleIdOption. " +
                        "Trying explicit Google sign-in.",
                        e
                    )

                    // المحاولة الثانية: نافذة تسجيل Google الصريحة
                    val signInOption =
                        GetSignInWithGoogleOption.Builder(clientId)
                            .build()

                    val fallbackRequest =
                        GetCredentialRequest.Builder()
                            .addCredentialOption(signInOption)
                            .build()

                    credentialManager.getCredential(
                        activity,
                        fallbackRequest
                    ).credential
                }

                authenticateWithFirebase(
                    context,
                    credential
                )

                onAuthSuccess()

            } catch (e: GetCredentialCancellationException) {
                Log.w(TAG, "User cancelled Google sign-in", e)
                onAuthCancelled()

            } catch (e: Exception) {
                val details = buildString {
                    append("Error type: ${e.javaClass.name}")
                    append("\nMessage: ${e.message ?: "No message"}")

                    e.cause?.let {
                        append("\nCause: ${it.javaClass.name}")
                        append(
                            "\nCause message: ${it.message ?: "No message"}"
                        )
                    }
                }

                Log.e(
                    TAG,
                    "Google Sign-In failed: $details",
                    e
                )

                onAuthError(details)
            }
        }
    }

    // تسجيل الخروج
    fun signOut(
        context: Context,
        credentialManager: CredentialManager,
        onSignOutComplete: () -> Unit,
        scope: CoroutineScope
    ) {
        Firebase.auth.signOut()

        scope.launch {
            try {
                credentialManager.clearCredentialState(
                    ClearCredentialStateRequest()
                )

                Log.d(TAG, "Credential state cleared")

            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Failed to clear credential state",
                    e
                )

            } finally {
                onSignOutComplete()
            }
        }
    }
}
