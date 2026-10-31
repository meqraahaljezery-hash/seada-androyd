
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

object AuthManager {

    private const val TAG = "AuthManager"

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

    // Use the default Firestore database.
    private fun getFirestoreDb(): FirebaseFirestore {
        return FirebaseFirestore.getInstance()
    }

    // Save user profile without resetting existing role or balance.
    private suspend fun saveUserMetadataToFirestore(
        firebaseUser: FirebaseUser
    ) {
        val uid = firebaseUser.uid

        val userRef = getFirestoreDb()
            .collection("users")
            .document(uid)

        try {
            val snapshot = userRef.get().await()

            if (snapshot.exists()) {
                val updates = mutableMapOf<String, Any>(
                    "updatedAt" to FieldValue.serverTimestamp()
                )

                firebaseUser.displayName?.let {
                    updates["name"] = it
                }

                firebaseUser.email?.let {
                    updates["email"] = it
                }

                firebaseUser.photoUrl?.toString()?.let {
                    updates["photoUrl"] = it
                }

                userRef.update(updates).await()

                Log.d(TAG, "Existing user profile updated: $uid")

            } else {
                val userData = hashMapOf<String, Any>(
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
                    "studentCode" to
                        "IM-STU-${uid.take(6).uppercase()}",
                    "minuteBalance" to 100L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )

                userRef.set(userData).await()

                Log.d(TAG, "New user profile created: $uid")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Firestore profile operation failed", e)
            throw e
        }
    }

    // Convert Google credential into Firebase authentication.
    private suspend fun authenticateWithFirebase(
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

        val user = result.user
            ?: throw IllegalStateException(
                "Firebase authentication returned no user"
            )

        saveUserMetadataToFirestore(user)
    }

    // Automatic sign-in.
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
                try {
                    saveUserMetadataToFirestore(currentUser)
                    onAuthSuccess()
                } catch (e: Exception) {
                    Log.e(TAG, "Automatic profile sync failed", e)
                    onUnauthenticated()
                }
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

        val request = androidx.credentials.GetCredentialRequest.Builder()
            .addCredentialOption(googleOption)
            .build()

        scope.launch {
            try {
                val result = credentialManager.getCredential(
                    context,
                    request
                )

                authenticateWithFirebase(result.credential)
                onAuthSuccess()

            } catch (e: Exception) {
                Log.w(TAG, "Automatic sign-in unavailable", e)
                onUnauthenticated()
            }
        }
    }

    // Manual Google sign-in.
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
                    val googleOption = GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId(clientId)
                        .setAutoSelectEnabled(false)
                        .build()

                    val request =
                        androidx.credentials.GetCredentialRequest.Builder()
                            .addCredentialOption(googleOption)
                            .build()

                    credentialManager.getCredential(
                        activity,
                        request
                    ).credential

                } catch (e: NoCredentialException) {
                    Log.w(
                        TAG,
                        "No credential available. Trying explicit sign-in.",
                        e
                    )

                    val signInOption =
                        GetSignInWithGoogleOption.Builder(clientId)
                            .build()

                    val fallbackRequest =
                        androidx.credentials.GetCredentialRequest.Builder()
                            .addCredentialOption(signInOption)
                            .build()

                    credentialManager.getCredential(
                        activity,
                        fallbackRequest
                    ).credential
                }

                authenticateWithFirebase(credential)

                onAuthSuccess()

            } catch (e: GetCredentialCancellationException) {
                Log.w(TAG, "Google sign-in cancelled", e)
                onAuthCancelled()

            } catch (e: Exception) {
                val details = buildString {
                    appendLine("Error type: ${e.javaClass.name}")
                    appendLine("Message: ${e.message ?: "No message"}")

                    e.cause?.let {
                        appendLine("Cause: ${it.javaClass.name}")
                        appendLine("Cause message: ${it.message}")
                    }
                }

                Log.e(TAG, "Google sign-in failed: $details", e)
                onAuthError(details)
            }
        }
    }

    // Sign out.
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
                Log.e(TAG, "Failed to clear credential state", e)

            } finally {
                onSignOutComplete()
            }
        }
    }
}
