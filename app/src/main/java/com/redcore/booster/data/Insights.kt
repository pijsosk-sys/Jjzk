package com.redcore.booster.data

enum class Severity { GOOD, INFO, WARN, CRITICAL }

data class Insight(val title: String, val detail: String, val severity: Severity)

/** Recommendations derived only from real readings. */
object Insights {
    fun performance(s: LiveStats, batterySaver: Boolean, usageGranted: Boolean): List<Insight> = buildList {
        if (batterySaver) add(Insight("Battery Saver is on", "Android limits CPU speed and background work in Battery Saver. Turn it off for maximum gaming performance.", Severity.WARN))
        when (s.thermalStatus) {
            in 3..6 -> add(Insight("Phone is throttling", "Android reports ${SystemMonitor.thermalName(s.thermalStatus).lowercase()} thermal status — performance is being reduced to cool down. Take a short break or remove the case.", Severity.CRITICAL))
            2 -> add(Insight("Getting warm", "Thermal status is moderate. Lower graphics settings or frame-rate cap to avoid throttling.", Severity.WARN))
            else -> {}
        }
        if (s.lowMemory) add(Insight("Memory pressure", "Android reports low memory. Close apps you aren't using before launching heavy games.", Severity.WARN))
        else if (s.ramFraction > 0.85f) add(Insight("RAM nearly full", "${(s.ramFraction * 100).toInt()}% of RAM is in use. Closing background apps can reduce game reloads.", Severity.INFO))
        s.batteryPct?.let { if (it <= 15 && !s.charging) add(Insight("Low battery", "At $it% many phones start limiting performance. Plug in or switch to Eco mode.", Severity.WARN)) }
        if (s.charging && (s.batteryTempC ?: 0f) >= 40f) add(Insight("Charging while hot", "Gaming while charging at ${"%.0f".format(s.batteryTempC)}°C stresses the battery. Consider unplugging or using a cooler.", Severity.WARN))
        if (!usageGranted) add(Insight("Enable Usage access", "Needed to find recently used background apps and track play time.", Severity.INFO))
        if (isEmpty()) add(Insight("Ready to play", "No issues detected. Temperature, memory and battery all look good.", Severity.GOOD))
    }

    fun cooling(s: LiveStats): List<Insight> = buildList {
        val t = s.hottestC
        when {
            t == null -> add(Insight("No temperature data", "This phone doesn't share temperature readings with apps.", Severity.INFO))
            t >= 45 -> add(Insight("Very hot", "Stop playing for a few minutes, remove the case and keep the phone out of direct sunlight.", Severity.CRITICAL))
            t >= 40 -> add(Insight("Warm", "Lower graphics quality or cap frame rate to 60 FPS. Avoid charging while gaming.", Severity.WARN))
            else -> add(Insight("Temperature is healthy", "No cooling action needed right now.", Severity.GOOD))
        }
        if (s.charging) add(Insight("Charging adds heat", "Fast charging during play is one of the biggest heat sources. A lower-wattage charger runs cooler.", Severity.INFO))
        add(Insight("Physical cooling works best", "A clip-on phone cooler, removing thick cases and holding the phone by its edges all reduce temperature measurably.", Severity.INFO))
    }

    fun battery(s: LiveStats, health: String?, batterySaver: Boolean): List<Insight> = buildList {
        if (health != null && health != "Good") add(Insight("Battery health: $health", "Android reports a battery condition other than Good. Have the battery checked.", Severity.CRITICAL))
        s.batteryTempC?.let { if (it >= 42) add(Insight("Battery is hot", "Heat above 40°C speeds up battery wear. Let it cool before charging.", Severity.WARN)) }
        s.batteryPct?.let {
            if (it < 20 && !s.charging) add(Insight("Charge soon", "Keeping the battery between 20% and 80% extends its lifespan.", Severity.INFO))
            if (it >= 100 && s.charging) add(Insight("Fully charged", "Unplugging at full charge reduces long-term wear. Many phones offer an 80–85% charge limit in battery settings.", Severity.INFO))
        }
        if (!batterySaver && (s.batteryPct ?: 100) < 30) add(Insight("Try Eco mode", "Eco mode lowers brightness and refresh rate (where allowed) to stretch play time.", Severity.INFO))
        add(Insight("Biggest power users while gaming", "Screen brightness, high refresh rate, 5G data and high frame-rate settings in games use the most power.", Severity.INFO))
    }

    fun network(info: NetInfo?, ping: PingResult?): List<Insight> = buildList {
        if (info == null || !info.connected) { add(Insight("Offline", "Connect to Wi-Fi or mobile data.", Severity.CRITICAL)); return@buildList }
        if (!info.validated) add(Insight("No internet access", "Connected to ${info.type} but Android couldn't reach the internet. Check router or captive portal sign-in.", Severity.CRITICAL))
        if (info.type == "Wi-Fi") {
            info.rssi?.let { if (it < -70) add(Insight("Weak Wi-Fi signal", "Signal is $it dBm. Move closer to the router or remove obstacles.", Severity.WARN)) }
            info.frequencyMhz?.let { if (it < 3000) add(Insight("Using 2.4 GHz", "5 GHz or 6 GHz Wi-Fi has lower latency and less interference if your router supports it.", Severity.INFO)) }
        }
        if (info.vpn) add(Insight("VPN active", "VPNs add a hop and usually increase ping. Disable it for online games unless you need it.", Severity.INFO))
        ping?.let {
            if (it.loss > 0.1f) add(Insight("Packet loss", "${(it.loss * 100).toInt()}% of connection attempts failed. Expect rubber-banding in online games.", Severity.WARN))
            if ((it.jitter ?: 0.0) > 20) add(Insight("High jitter", "Latency varies by ${"%.0f".format(it.jitter)} ms. Other devices streaming or downloading on your network often cause this.", Severity.WARN))
        }
        add(Insight("Close bandwidth-heavy apps", "Cloud backups, streaming and app updates compete with your game. Pause them before ranked matches.", Severity.INFO))
    }
}
