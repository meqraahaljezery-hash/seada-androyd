
package com.example.data.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

object AuthManager {

    private fun getWebClientId(context: Context): String? {
        val resId = context.resources.getIdentifier(
            "default_web_client_id",
            "string",
            context.packageName
        )
        return if (resId != 0) context.getString(resId) else null
    }

    private fun getFirestoreDb(context: Context): FirebaseFirestore {
        val dbIdRes = context.resources.getIdentifier(
            "firestore_database_id",
            "string",
            context.packageName
        )
        val dbId = if (dbIdRes != 0) context.getString(dbIdRes) else null

        return if (!dbId.isNullOrBlank()) {
            FirebaseFirestore.getInstance(dbId)
        } else {
            FirebaseFirestore.getInstance()
        }
    }

    private suspend fun saveUserMetadataToFirestore(
        context: Context,
        firebaseUser: com.google.firebase.auth.FirebaseUser
    ) {
        val uid = firebaseUser.uid
        val db = getFirestoreDb(context)
        val userRef = db.collection("users").document(uid)

        val userData = mapOf(
            "uid" to uid,
            "name" to (firebaseUser.displayName ?: "طالب مقرأة الإمام الجزري"),
            "email" to (firebaseUser.email ?: "student@maqraah.org"),
            "photoUrl" to (firebaseUser.photoUrl?.toString() ?: ""),
            "role" to "student",
            "status" to "active",
            "studentCode" to "IM-STU-${uid.take(6).uppercase()}",
            "minuteBalance" to 100L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        try {
            userRef.set(userData, SetOptions.merge()).await()
            Log.d(
                "AuthManager",
                "User metadata saved successfully for $uid"
            )
        } catch (e: Exception) {
            Log.e(
                "AuthManager",
                "Failed to save user metadata to Firestore",
                e
            )
        }
    }

    fun attemptAutoSignIn(
        context: Context,
        credentialManager: CredentialManager,
        onAuthSuccess: () -> Unit,
        onUnauthenticated: () -> Unit,
        scope: CoroutineScope
    ) {
        if (Firebase.auth.currentUser != null) {
            scope.launch {
                Firebase.auth.currentUser?.let {
                    saveUserMetadataToFirestore(context, it)
                }
                onAuthSuccess()
            }
            return
        }

        val clientId = getWebClientId(context)

        if (clientId.isNullOrBlank()) {
            onUnauthenticated()
            return
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        scope.launch {
            try {
                val result = credentialManager.getCredential(
                    context,
                    request
                )

                val credential = result.credential

                if (
                    credential is CustomCredential &&
                    credential.type ==
                    GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleIdToken =
                        GoogleIdTokenCredential
                            .createFrom(credential.data)
                            .idToken

                    val authCredential =
                        GoogleAuthProvider.getCredential(
                            googleIdToken,
                            null
                        )

                    val authResult =
                        Firebase.auth
                            .signInWithCredential(authCredential)
                            .await()

                    authResult.user?.let {
                        saveUserMetadataToFirestore(context, it)
                    }

                    onAuthSuccess()
                } else {
                    onUnauthenticated()
                }
            } catch (e: Exception) {
                Log.e("Auth", "Automatic Google sign-in failed", e)
                onUnauthenticated()
            }
        }
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
                val clientId = getWebClientId(context)

                if (clientId.isNullOrBlank()) {
                    onAuthError(
                        "Google Sign-In configuration missing: " +
                        "default_web_client_id not found"
                    )
                    return@launch
                }

                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(clientId)
                    .setAutoSelectEnabled(false)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val activity = context as? Activity

                if (activity == null) {
                    onAuthError(
                        "Activity context is required for Google Sign-In"
                    )
                    return@launch
                }

                val result = credentialManager.getCredential(
                    activity,
                    request
                )

                val credential = result.credential

                if (
                    credential is CustomCredential &&
                    credential.type ==
                    GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleIdToken =
                        GoogleIdTokenCredential
                            .createFrom(credential.data)
                            .idToken

                    val authCredential =
                        GoogleAuthProvider.getCredential(
                            googleIdToken,
                            null
                        )

                    val authResult =
                        Firebase.auth
                            .signInWithCredential(authCredential)
                            .await()

                    authResult.user?.let {
                        saveUserMetadataToFirestore(context, it)
                    }

                    onAuthSuccess()
                } else {
                    onAuthError("Unexpected Google credential type")
                }

            } catch (e: GetCredentialCancellationException) {
                Log.w(
                    "Auth",
                    "Google Sign-In cancelled: ${e.message}",
                    e
                )
                onAuthCancelled()

            } catch (e: Exception) {
                val details = buildString {
                    append("Error type: ${e.javaClass.name}")
                    append("\nMessage: ${e.message ?: "No message"}")

                    e.cause?.let {
                        append("\nCause: ${it.javaClass.name}")
                        append("\nCause message: ${it.message ?: "No message"}")
                    }
                }

                Log.e(
                    "AuthManager",
                    "Google Sign-In diagnostic: $details",
                    e
                )

                onAuthError(details)
            }
        }
    }

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
            } catch (e: Exception) {
                Log.e(
                    "Auth",
                    "Failed to clear credential state",
                    e
                )
            } finally {
                onSignOutComplete()
            }
        }
    }
}
