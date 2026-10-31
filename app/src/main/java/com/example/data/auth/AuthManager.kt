
package com.example.data.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.BuildConfig
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
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

        require(clientId.isNotBlank()) {
            "Google Web Client ID is empty."
        }

        log("STEP 1: Web Client ID loaded.")
        return clientId
    }

    /**
     * Save or update the authenticated user's profile.
     * Existing role and balance are preserved.
     */
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
        require(googleIdToken.isNotBlank()) {
            "Google ID token is empty."
        }

        log("STEP 4: Google ID token received. Length=${googleIdToken.length}")

        val firebaseCredential =
            GoogleAuthProvider.getCredential(googleIdToken, null)

        log("STEP 5: Sending Google credential to Firebase.")

        val result = auth.signInWithCredential(firebaseCredential).await()

        val user = result.user
            ?: throw IllegalStateException("Firebase returned no user.")

        log("STEP 5 SUCCESS: Firebase authentication succeeded. UID=${user.uid}")

        saveUserMetadataToFirestore(user)

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
                    ?: throw IllegalStateException(
                        "Activity context is required for Google sign-in."
                    )

                val serverClientId = getWebClientId(context)

                val googleOption = GetGoogleIdOption.Builder()
                    .setServerClientId(serverClientId)
                    .setFilterByAuthorizedAccounts(false)
                    .setAutoSelectEnabled(false)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleOption)
                    .build()

                log("STEP 2: Opening Google account chooser.")

                val credential = try {
                    credentialManager.getCredential(
                        context = activity,
                        request = request
                    ).credential
                } catch (e: NoCredentialException) {
                    log("NoCredentialException. Trying explicit Google sign-in.")

                    val signInOption =
                        GetSignInWithGoogleOption.Builder(serverClientId)
                            .build()

                    val fallbackRequest =
                        GetCredentialRequest.Builder()
                            .addCredentialOption(signInOption)
                            .build()

                    credentialManager.getCredential(
                        context = activity,
                        request = fallbackRequest
                    ).credential
                }

                log("STEP 3: Google credential returned. Type=${credential.type}")

                if (credential !is CustomCredential ||
                    credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    throw IllegalStateException(
                        "Unexpected Google credential type: ${credential.type}"
                    )
                }

                val googleCredential =
                    GoogleIdTokenCredential.createFrom(credential.data)

                val token = googleCredential.idToken

                authenticateWithGoogleToken(token)

                log("STEP 8: Sign-in and Firestore profile completed.")

                onAuthSuccess()

            } catch (e: CancellationException) {
                log("Google sign-in cancelled.")
                onAuthCancelled()
                throw e

            } catch (e: GetCredentialException) {
                log("CREDENTIAL ERROR: ${e.javaClass.simpleName}: ${e.message}")
                onAuthError(
                    "Google sign-in error: ${e.javaClass.simpleName}: ${e.message}"
                )

            } catch (e: Exception) {
                Log.e(TAG, "SIGN-IN FAILED", e)
                onAuthError(
                    "${e.javaClass.simpleName}: ${e.message ?: "Unknown error"}"
                )
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

            scopeForSignOut(context, credentialManager, onComplete)

        } catch (e: Exception) {
            Log.e(TAG, "SIGN OUT FAILED", e)
            onComplete()
        }
    }

    private fun scopeForSignOut(
        context: Context,
        credentialManager: CredentialManager,
        onComplete: () -> Unit
    ) {
        val activity = context as? Activity

        if (activity == null) {
            onComplete()
            return
        }

        CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            try {
                credentialManager.clearCredentialState(
                    androidx.credentials.ClearCredentialStateRequest()
                )
                log("Credential state cleared.")
            } catch (e: Exception) {
                Log.e(TAG, "CLEAR CREDENTIAL STATE FAILED", e)
            } finally {
                onComplete()
            }
        }
    }
}
