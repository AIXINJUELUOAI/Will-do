package com.antgskds.calendarassistant.shared.api

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.shared.operation.WillDoAgentContract

/**
 * Shell-friendly transport for Agent API calls.
 *
 * The receiver only adapts broadcast extras and results. Request validation,
 * access-switch checks, and method dispatch remain in [WillDoAgentProvider].
 */
class WillDoAgentReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_AGENT_CALL) {
            resultCode = Activity.RESULT_CANCELED
            return
        }

        val method = intent.getStringExtra(METHOD_KEY).orEmpty()
        val request = intent.getStringExtra(WillDoAgentContract.REQUEST_KEY).orEmpty()
        val requestId = runCatching { AgentProtocolJson.decodeRequest(request).requestId }
            .getOrDefault("unknown")
        val settings = (context.applicationContext as? App)?.settingsQueryApi?.settings?.value

        if (settings == null) {
            respond(AgentProtocolJson.encodeFailure(requestId, "INTERNAL_ERROR", "WillDo is not initialized"))
            return
        }
        if (!settings.agentApiEnabled) {
            respond(AgentProtocolJson.encodeFailure(requestId, "API_DISABLED", "Agent API is disabled"))
            return
        }
        if (!settings.agentThirdPartyAccessEnabled) {
            respond(
                AgentProtocolJson.encodeFailure(
                    requestId,
                    "THIRD_PARTY_DISABLED",
                    "Third-party Agent access is disabled"
                )
            )
            return
        }

        val result = context.contentResolver.call(
            Uri.parse(WillDoAgentContract.BASE_URI),
            method,
            null,
            Bundle().apply {
                putString(WillDoAgentContract.REQUEST_KEY, request)
                putBoolean(WillDoAgentContract.THIRD_PARTY_TRANSPORT_KEY, true)
            }
        )

        respond(result?.getString(WillDoAgentContract.RESPONSE_KEY).orEmpty())
    }

    private fun respond(response: String) {
        resultCode = Activity.RESULT_OK
        resultData = response
    }

    companion object {
        const val ACTION_AGENT_CALL = "com.antgskds.calendarassistant.AGENT_CALL"
        const val METHOD_KEY = "method"
    }
}
