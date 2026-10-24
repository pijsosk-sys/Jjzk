package com.boddy.os

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** App-wide state shared between the service and the screens. */
object Boddy {
    enum class Phase { NEEDS_INSTALL, INSTALLING, READY, RUNNING, ERROR }

    val phase = MutableStateFlow(Phase.NEEDS_INSTALL)
    val status = MutableStateFlow("")
    /** 0-100, or -1 when there's no meaningful percentage. */
    val progress = MutableStateFlow(-1)

    /** Things Linux asked the phone to do (see [PhoneBridge]); handled by the desktop screen. */
    val phoneRequests = MutableSharedFlow<PhoneRequest>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val _log = MutableStateFlow("")
    val log: StateFlow<String> = _log

    private const val MAX_LOG = 20_000

    fun log(line: String) {
        _log.update { (it + line + "\n").takeLast(MAX_LOG) }
    }

    fun clearLog() {
        _log.value = ""
    }
}
