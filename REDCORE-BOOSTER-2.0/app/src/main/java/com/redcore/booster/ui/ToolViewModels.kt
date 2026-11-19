package com.redcore.booster.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.redcore.booster.app
import com.redcore.booster.data.AppSize
import com.redcore.booster.data.FileScan
import com.redcore.booster.data.NetInfo
import com.redcore.booster.data.PingResult
import com.redcore.booster.data.SpeedTestException
import com.redcore.booster.data.StorageSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.UnknownHostException

enum class SpeedPhase { IDLE, PING, DOWNLOAD, UPLOAD, DONE, ERROR }

data class SpeedState(
    val phase: SpeedPhase = SpeedPhase.IDLE,
    val liveBps: Double = 0.0,
    val downBps: Double? = null,
    val upBps: Double? = null,
    val pingMs: Double? = null,
    val error: String? = null,
    /** On mobile data the first tap only asks for confirmation; the second starts the test. */
    val confirmMetered: Boolean = false,
)

class NetworkViewModel(app: Application) : AndroidViewModel(app) {
    private val net = app.app.network
    private val refreshTick = MutableStateFlow(0)

    /** Connection details, re-read every 3 s only while the Network screen is collecting them. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val info: StateFlow<NetInfo?> = refreshTick.flatMapLatest {
        flow { while (true) { emit(net.info()); delay(3000) } }
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), null)

    private val _ping = MutableStateFlow<PingResult?>(null)
    val ping = _ping.asStateFlow()
    private val _pinging = MutableStateFlow(false)
    val pinging = _pinging.asStateFlow()
    private val _speed = MutableStateFlow(SpeedState())
    val speed = _speed.asStateFlow()
    private var speedJob: Job? = null

    fun refresh() { refreshTick.value++ }

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
        if (info.value?.metered == true && !_speed.value.confirmMetered) { _speed.value = SpeedState(confirmMetered = true); return }
        speedJob = viewModelScope.launch {
            try {
                _speed.value = SpeedState(SpeedPhase.PING)
                val p = net.ping("speed.cloudflare.com", 443, 5)
                if (p.ok.isEmpty()) throw SpeedTestException("The test server didn't answer. Check that you're online.")
                _speed.value = _speed.value.copy(phase = SpeedPhase.DOWNLOAD, pingMs = p.avg)
                val down = net.downloadTest(8) { _speed.value = _speed.value.copy(liveBps = it) }
                _speed.value = _speed.value.copy(phase = SpeedPhase.UPLOAD, downBps = down, liveBps = 0.0)
                val up = net.uploadTest(6) { _speed.value = _speed.value.copy(liveBps = it) }
                _speed.value = _speed.value.copy(phase = SpeedPhase.DONE, upBps = up, liveBps = 0.0)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: SpeedTestException) {
                _speed.value = _speed.value.copy(phase = SpeedPhase.ERROR, error = e.message)
            } catch (e: UnknownHostException) {
                _speed.value = _speed.value.copy(phase = SpeedPhase.ERROR, error = "No internet connection.")
            } catch (e: Exception) {
                _speed.value = _speed.value.copy(phase = SpeedPhase.ERROR, error = "Network error: ${e.message ?: e.javaClass.simpleName}")
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
    private val _files = MutableStateFlow<FileScan?>(null)
    val files = _files.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private var job: Job? = null

    fun load() {
        job?.cancel()
        job = viewModelScope.launch {
            _loading.value = true
            kotlinx.coroutines.coroutineScope {
                launch { _summary.value = st.summary() }
                launch { _files.value = st.largeFiles() }
                launch { _apps.value = st.largestApps() }
            }
            _loading.value = false
        }
    }
}
