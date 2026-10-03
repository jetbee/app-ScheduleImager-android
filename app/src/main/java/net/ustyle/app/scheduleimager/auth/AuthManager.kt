package net.ustyle.app.scheduleimager.auth

import android.app.Activity
import android.content.Context
import com.microsoft.identity.client.AcquireTokenSilentParameters
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAccount
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.IPublicClientApplication
import com.microsoft.identity.client.ISingleAccountPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.SignInParameters
import com.microsoft.identity.client.SilentAuthenticationCallback
import com.microsoft.identity.client.exception.MsalException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import net.ustyle.app.scheduleimager.Config
import net.ustyle.app.scheduleimager.R
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed class AuthError(message: String) : Exception(message) {
    data object NotConfigured : AuthError("res/raw/msal_config.json にクライアントIDを設定してください。詳しくはREADME.mdを参照してください。")
    data object NoAccount : AuthError("サインインしているアカウントがありません。")
    data object Cancelled : AuthError("サインインがキャンセルされました。")
}

/**
 * Thin wrapper around MSAL's single-account public client application. Safe
 * to call from the UI (interactive sign-in) and from the background worker
 * (silent token acquisition only) alike — MSAL Android doesn't need an
 * Activity for silent calls.
 */
class AuthManager private constructor(context: Context) {
    private val appDeferred = CompletableDeferred<ISingleAccountPublicClientApplication?>()

    /** False until res/raw/msal_config.json has a real client id filled in. */
    val isConfigured: Boolean = context.resources.openRawResource(R.raw.msal_config)
        .bufferedReader()
        .use { it.readText() }
        .let { !it.contains("YOUR-CLIENT-ID-HERE") }

    init {
        if (isConfigured) {
            PublicClientApplication.createSingleAccountPublicClientApplication(
                context,
                R.raw.msal_config,
                object : IPublicClientApplication.ISingleAccountApplicationCreatedListener {
                    override fun onCreated(application: ISingleAccountPublicClientApplication) {
                        appDeferred.complete(application)
                    }

                    override fun onError(exception: MsalException) {
                        appDeferred.complete(null)
                    }
                },
            )
        } else {
            appDeferred.complete(null)
        }
    }

    suspend fun isSignedIn(): Boolean = currentAccount() != null

    suspend fun currentAccount(): IAccount? {
        val app = appDeferred.await() ?: return null
        return suspendCancellableCoroutine { cont ->
            app.getCurrentAccountAsync(object : ISingleAccountPublicClientApplication.CurrentAccountCallback {
                override fun onAccountLoaded(activeAccount: IAccount?) {
                    cont.resume(activeAccount)
                }

                override fun onAccountChanged(priorAccount: IAccount?, currentAccount: IAccount?) {
                    // Only the eventual onAccountLoaded call matters for this query.
                }

                override fun onError(exception: MsalException) {
                    cont.resume(null)
                }
            })
        }
    }

    /** Silent-only token fetch; throws [AuthError.NotConfigured] or [AuthError.NoAccount] if it can't. */
    suspend fun acquireTokenSilently(): String {
        val app = appDeferred.await() ?: throw AuthError.NotConfigured
        val account = currentAccount() ?: throw AuthError.NoAccount
        val authority = app.configuration.defaultAuthority.authorityURL.toString()
        return suspendCancellableCoroutine { cont ->
            val parameters = AcquireTokenSilentParameters.Builder()
                .withScopes(listOf(Config.GRAPH_SCOPE))
                .forAccount(account)
                .fromAuthority(authority)
                .withCallback(object : SilentAuthenticationCallback {
                    override fun onSuccess(authenticationResult: IAuthenticationResult) {
                        cont.resume(authenticationResult.accessToken)
                    }

                    override fun onError(exception: MsalException) {
                        cont.resumeWithException(exception)
                    }
                })
                .build()
            app.acquireTokenSilentAsync(parameters)
        }
    }

    /** Shows the Microsoft sign-in UI in a browser / custom tab launched from [activity]. */
    suspend fun signIn(activity: Activity): String {
        val app = appDeferred.await() ?: throw AuthError.NotConfigured
        return suspendCancellableCoroutine { cont ->
            val parameters = SignInParameters.builder()
                .withActivity(activity)
                .withScopes(listOf(Config.GRAPH_SCOPE))
                .withCallback(object : AuthenticationCallback {
                    override fun onSuccess(authenticationResult: IAuthenticationResult) {
                        cont.resume(authenticationResult.accessToken)
                    }

                    override fun onError(exception: MsalException) {
                        cont.resumeWithException(exception)
                    }

                    override fun onCancel() {
                        cont.resumeWithException(AuthError.Cancelled)
                    }
                })
                .build()
            app.signIn(parameters)
        }
    }

    suspend fun signOut() {
        val app = appDeferred.await() ?: return
        suspendCancellableCoroutine { cont ->
            app.signOut(object : ISingleAccountPublicClientApplication.SignOutCallback {
                override fun onSignOut() {
                    cont.resume(Unit)
                }

                override fun onError(exception: MsalException) {
                    cont.resumeWithException(exception)
                }
            })
        }
    }

    companion object {
        @Volatile
        private var instance: AuthManager? = null

        fun getInstance(context: Context): AuthManager =
            instance ?: synchronized(this) {
                instance ?: AuthManager(context.applicationContext).also { instance = it }
            }
    }
}
