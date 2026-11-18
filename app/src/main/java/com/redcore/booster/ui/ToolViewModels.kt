package com.redcore.booster.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.redcore.booster.app
import com.redcore.booster.data.AppSize
import com.redcore.booster.data.BigFile
import com.redcore.booster.data.NetInfo
import com.redcore.booster.data.PingResult
import com.redcore.booster.data.StorageSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class SpeedPhase { IDLE, PING, DOWNLOAD, UPLOAD, DONE, ERROR }

data class SpeedState(
    val phase: SpeedPhase = SpeedPhase.IDLE,
    val liveBps: Double = 0.0,
    val downBps: Double? = null,
    val upBps: Double? = null,
    val pingMs: Double? = null,
    val error: String? = null,
)

class NetworkViewModel(app: Application) : AndroidViewModel(app) {
    private val net = app.app.network
    private val _info = MutableStateFlow<NetInfo?>(null)
    val info = _info.asStateFlow()
    private val _ping = MutableStateFlow<PingResult?>(null)
    val ping = _ping.asStateFlow()
    private val _pinging = MutableStateFlow(false)
    val pinging = _pinging.asStateFlow()
    private val _speed = MutableStateFlow(SpeedState())
    val speed = _speed.asStateFlow()
    private var speedJob: Job? = null

    init {
        viewModelScope.launch { while (isActive) { _info.value = net.info(); delay(3000) } }
    }

    fun refresh() { _info.value = net.info() }

    fun runPing() {
        if (_pinging.value) return
        viewModelScope.launch {
            _pinging.value = true
            _ping.value = PingResult("1.1.1.1", emptyList())
            _ping.value = net.ping("1.1.1.1", 443, 12) { _ping.value = PingResult("1.1.1.1", it) }
            _pinging.value = false
        }
    }

    fun runSpeedTest() {
        if (speedJob?.isActive == true) { speedJob?.cancel(); _speed.value = SpeedState(); return }
        speedJob = viewModelScope.launch {
            try {
                _speed.value = SpeedState(SpeedPhase.PING)
                val p = net.ping("speed.cloudflare.com", 443, 5)
                _speed.value = _speed.value.copy(phase = SpeedPhase.DOWNLOAD, pingMs = p.avg)
                val down = net.downloadTest(8) { _speed.value = _speed.value.copy(liveBps = it) }
                _speed.value = _speed.value.copy(phase = SpeedPhase.UPLOAD, downBps = down, liveBps = 0.0)
                val up = net.uploadTest(6) { _speed.value = _speed.value.copy(liveBps = it) }
                _speed.value = _speed.value.copy(phase = SpeedPhase.DONE, upBps = up, liveBps = 0.0)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _speed.value = _speed.value.copy(phase = SpeedPhase.ERROR, error = e.message ?: "Network error")
            }
        }
    }
}

class StorageViewModel(app: Application) : AndroidViewModel(app) {
    private val st = app.app.storage
    private val _summary = MutableStateFlow<StorageSummary?>(null)
    val summary = _summary.asStateFlow()
    private val _apps = MutableStateFlow<List<AppSize>?>(null)
    val apps = _apps.asStateFlow()
    private val _files = MutableStateFlow<List<BigFile>?>(null)
    val files = _files.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    fun load() = viewModelScope.launch {
        _loading.value = true
        _summary.value = st.summary()
        _files.value = st.largeFiles()
        _apps.value = st.largestApps()
        _loading.value = false
    }
}
