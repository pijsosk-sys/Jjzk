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

    /**
     * The last [MAX_LOG_LINES] log lines. Kept as a small ring of lines rather than one big
     * string, so a chatty desktop session can't use more and more memory over time.
     */
    private val logLines = ArrayDeque<String>()
    private val _logVersion = MutableStateFlow(0L)
    /** Changes whenever the log does; read the text with [logText]. */
    val logVersion: StateFlow<Long> = _logVersion

    private const val MAX_LOG_LINES = 400

    fun log(line: String) {
        synchronized(logLines) {
            logLines.addLast(line.take(500))
            while (logLines.size > MAX_LOG_LINES) logLines.removeFirst()
        }
        _logVersion.update { it + 1 }
    }

    fun logText(): String = synchronized(logLines) { logLines.joinToString("\n") }

    fun clearLog() {
        synchronized(logLines) { logLines.clear() }
        _logVersion.update { it + 1 }
    }
}
