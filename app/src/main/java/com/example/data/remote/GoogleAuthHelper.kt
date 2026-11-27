package com.example.data.remote

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

sealed class FirebaseGoogleAuthOutcome {
    data class Success(
        val firebaseUser: FirebaseUser,
        val firebaseIdToken: String
    ) : FirebaseGoogleAuthOutcome()

    data object Cancelled : FirebaseGoogleAuthOutcome()
    data class Failure(val message: String) : FirebaseGoogleAuthOutcome()
}

object GoogleAuthHelper {
    private const val TAG = "Auth"

    suspend fun attemptAutoSignIn(context: Context): FirebaseGoogleAuthOutcome? {
        val current = runCatching { Firebase.auth.currentUser }.getOrNull()
        if (current != null) {
            val token = getFreshFirebaseIdToken()
            if (!token.isNullOrBlank()) {
                return FirebaseGoogleAuthOutcome.Success(current, token)
            }
        }
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            return null
        }

        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(true)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val credentialManager = CredentialManager.create(context)
            val cred = credentialManager.getCredential(context, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(cred.data).idToken
                val authResult = Firebase.auth
                    .signInWithCredential(GoogleAuthProvider.getCredential(googleIdToken, null))
                    .await()
                val user = authResult.user ?: return null
                val firebaseToken = user.getIdToken(false).await().token ?: return null
                FirebaseGoogleAuthOutcome.Success(user, firebaseToken)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun signInWithGoogle(context: Context): FirebaseGoogleAuthOutcome {
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            return FirebaseGoogleAuthOutcome.Failure(
                "Configuração Google Sign-In em falta: default_web_client_id não encontrado."
            )
        }

        return try {
            val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()
            val credentialManager = CredentialManager.create(context)
            val cred = credentialManager.getCredential(context, request).credential

            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(cred.data).idToken
                val authResult = Firebase.auth
                    .signInWithCredential(GoogleAuthProvider.getCredential(googleIdToken, null))
                    .await()
                val user = authResult.user
                    ?: return FirebaseGoogleAuthOutcome.Failure("Utilizador Firebase nulo após autenticação.")
                val firebaseToken = user.getIdToken(true).await().token
                    ?: return FirebaseGoogleAuthOutcome.Failure("Não foi possível obter o Firebase ID Token.")
                FirebaseGoogleAuthOutcome.Success(user, firebaseToken)
            } else {
                FirebaseGoogleAuthOutcome.Failure("Tipo de credencial Google inesperado.")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            FirebaseGoogleAuthOutcome.Cancelled
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Google Sign-In failed: ${e.type} - ${e.errorMessage}", e)
            FirebaseGoogleAuthOutcome.Failure(e.localizedMessage ?: "Falha na autenticação Google.")
        } catch (e: Exception) {
            Log.e(TAG, "Firebase Google Sign-In failed", e)
            FirebaseGoogleAuthOutcome.Failure(e.localizedMessage ?: "Erro inesperado ao entrar com Google.")
        }
    }

    suspend fun signInWithEmailPassword(email: String, password: String): FirebaseGoogleAuthOutcome {
        return try {
            val authResult = Firebase.auth
                .signInWithEmailAndPassword(email.trim(), password)
                .await()
            val user = authResult.user
                ?: return FirebaseGoogleAuthOutcome.Failure("Não foi possível autenticar este utilizador.")
            val firebaseToken = user.getIdToken(true).await().token
                ?: return FirebaseGoogleAuthOutcome.Failure("Não foi possível obter o token da sessão.")
            FirebaseGoogleAuthOutcome.Success(user, firebaseToken)
        } catch (e: Exception) {
            Log.e(TAG, "Email/Password sign-in failed", e)
            val msg = e.localizedMessage.orEmpty()
            val friendly = when {
                msg.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true) ||
                    msg.contains("wrong-password", ignoreCase = true) ||
                    msg.contains("invalid-credential", ignoreCase = true) ->
                    "E-mail ou senha incorretos."
                msg.contains("USER_NOT_FOUND", ignoreCase = true) ||
                    msg.contains("user-not-found", ignoreCase = true) ->
                    "Nenhuma conta encontrada com este e-mail."
                msg.contains("TOO_MANY_REQUESTS", ignoreCase = true) ->
                    "Demasiadas tentativas. Aguarda um momento e tenta novamente."
                else -> e.localizedMessage ?: "Não foi possível entrar com e-mail e senha."
            }
            FirebaseGoogleAuthOutcome.Failure(friendly)
        }
    }

    suspend fun getFreshFirebaseIdToken(forceRefresh: Boolean = false): String? {
        val user = runCatching { Firebase.auth.currentUser }.getOrNull() ?: return null
        return runCatching { user.getIdToken(forceRefresh).await().token }.getOrNull()
    }

    suspend fun signOut(context: Context) {
        withContext(NonCancellable) {
            try {
                withTimeoutOrNull(1_000L) {
                    CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear credential state: ${e.message}")
            } finally {
                runCatching { Firebase.auth.signOut() }
            }
        }
    }
}
