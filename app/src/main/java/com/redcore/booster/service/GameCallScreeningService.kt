package com.redcore.booster.service

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import androidx.annotation.RequiresApi
import com.redcore.booster.app
import com.redcore.booster.data.CallMode

/**
 * "No calls" during games. Requires the user to give REDCORE the call-screening role (Android 10+).
 * Only acts while a game session is running; every other call is allowed untouched.
 */
@RequiresApi(29)
class GameCallScreeningService : CallScreeningService() {
    override fun onScreenCall(details: Call.Details) {
        val session = app.boost.session.value
        val mode = if (session == null) CallMode.ALLOW else (session.spec.calls ?: CallPolicy.mode)
        val incoming = details.callDirection == Call.Details.DIRECTION_INCOMING
        val r = CallResponse.Builder()
        if (incoming && mode == CallMode.DECLINE) {
            r.setDisallowCall(true).setRejectCall(true).setSkipCallLog(false).setSkipNotification(false)
        } else if (incoming && mode == CallMode.SILENCE && Build.VERSION.SDK_INT >= 29) {
            r.setSilenceCall(true)
        }
        respondToCall(details, r.build())
    }
}

/** Mirrors the user's "Calls during games" preference in memory for fast screening (safe on every API level). */
object CallPolicy {
    @Volatile var mode: CallMode = CallMode.ALLOW
}
