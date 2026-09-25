package com.nevoit.xdnext.data.session

import com.nevoit.xdnext.data.ids.IdsReAuthCancelledException
import com.nevoit.xdnext.data.ids.IdsReAuthClient
import com.nevoit.xdnext.data.ids.IdsReAuthHandler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bridges a suspended login to a second-factor dialog.
 *
 * The repository is a suspend flow and the dialog is a composition; this class is the handoff. The
 * original used a mutable process-global `activeIDSReAuthHandler` that the signed-in UI installed, so
 * whether a challenge could be answered depended on which screen happened to be mounted. Here the
 * challenge is published as state and the awaiting coroutine is parked on a [CompletableDeferred].
 *
 * Ordering is load-bearing: [pending] is assigned before the challenge is published, so a UI that
 * reacts to the state can never observe a challenge whose result has nowhere to go.
 */
class IdsReAuthBroker : IdsReAuthHandler {

    private var pending: CompletableDeferred<String>? = null

    private val _challenge = MutableStateFlow<IdsReAuthClient?>(null)

    /** The challenge awaiting input, or null. Drives whether the dialog is shown. */
    val challenge: StateFlow<IdsReAuthClient?> = _challenge.asStateFlow()

    override suspend fun handle(client: IdsReAuthClient): String {
        val deferred = CompletableDeferred<String>()
        pending = deferred
        _challenge.value = client
        try {
            return deferred.await()
        } finally {
            _challenge.value = null
            pending = null
        }
    }

    /** Reports a successful challenge. [location] is where the login flow resumes. */
    fun resolve(location: String) {
        pending?.complete(location)
    }

    /** Reports a failure inside the dialog, e.g. an expired challenge. */
    fun fail(error: Throwable) {
        pending?.completeExceptionally(error)
    }

    /** The user dismissed the prompt. */
    fun cancel() {
        pending?.completeExceptionally(IdsReAuthCancelledException())
    }
}
