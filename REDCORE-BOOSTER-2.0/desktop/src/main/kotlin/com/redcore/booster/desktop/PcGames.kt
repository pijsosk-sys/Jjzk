package com.redcore.booster.desktop

import java.awt.Desktop
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption

enum class Store(val title: String) { STEAM("Steam"), EPIC("Epic Games"), MANUAL("Added by you") }

/** PC performance modes. Each one maps to real, reversible OS settings (see [PcBooster]). */
enum class PcMode(val title: String, val tagline: String) {
    ECO("Eco", "Power saver plan — quieter and cooler"),
    BALANCED("Balance", "Windows' balanced plan"),
    BEAST("Beast", "High performance plan + game priority"),
    LIMIT("Limit", "Highest plan available + Game Mode + high priority"),
}

data class PcGame(
    val id: String,
    val name: String,
    val store: Store,
    val installDir: String?,
    /** steam://, com.epicgames.launcher:// or an executable path. */
    val launch: String,
    val mode: PcMode = PcMode.BEAST,
    val hidden: Boolean = false,
)

object Paths {
    val os: String = System.getProperty("os.name").lowercase()
    val isWindows = os.contains("win")
    val isMac = os.contains("mac")
    val home = File(System.getProperty("user.home"))

    /** %APPDATA%\REDCORE on Windows, ~/Library/Application Support/REDCORE on macOS, ~/.config/redcore elsewhere. */
    val configDir: File by lazy {
        val dir = when {
            isWindows -> File(System.getenv("APPDATA") ?: home.path, "REDCORE")
            isMac -> File(home, "Library/Application Support/REDCORE")
            else -> File(System.getenv("XDG_CONFIG_HOME") ?: File(home, ".config").path, "redcore")
        }
        dir.also { it.mkdirs() }
    }
}

/** Writes a file atomically (temp file + rename), so a crash never leaves a half-written file. */
fun writeAtomically(target: File, text: String) {
    target.parentFile?.mkdirs()
    val tmp = File(target.parentFile, target.name + ".tmp")
    tmp.writeText(text)
    runCatching { Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
        .onFailure { Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING) }
}

/** Valve KeyValues (VDF/ACF) reading — just what's needed for library folders and app manifests. */
object Vdf {
    private val pair = Regex("\"([^\"]+)\"\\s+\"((?:[^\"\\\\]|\\\\.)*)\"")

    fun unescape(s: String) = s.replace("\\\\", "\\").replace("\\\"", "\"")

    /** Library folders from libraryfolders.vdf (new "path" format and the old numbered format). */
    fun libraryFolders(vdf: String): List<String> {
        val out = LinkedHashSet<String>()
        for (m in pair.findAll(vdf)) {
            val (k, v) = m.destructured
            if (k == "path" || (k.all { it.isDigit() } && (v.contains(":\\\\") || v.startsWith("/")))) out += unescape(v)
        }
        return out.toList()
    }

    /** Top-level fields of an appmanifest_*.acf file. */
    fun fields(acf: String): Map<String, String> = pair.findAll(acf).associate { it.groupValues[1].lowercase() to unescape(it.groupValues[2]) }
}

object SteamScanner {
    /** Steam tools that aren't games. */
    private val toolIds = setOf("228980", "1070560", "1391110", "1628350", "1493710", "2180100", "1887720", "1580130", "961940", "1245040")
    private val toolNames = Regex("^(Proton|Steam Linux Runtime|Steamworks Common|SteamVR|Steam Audio)", RegexOption.IGNORE_CASE)

    fun roots(): List<File> = buildList {
        if (Paths.isWindows) {
            registrySteamPath()?.let { add(File(it)) }
            listOf(System.getenv("ProgramFiles(x86)"), System.getenv("ProgramFiles")).filterNotNull().forEach { add(File(it, "Steam")) }
        } else if (Paths.isMac) add(File(Paths.home, "Library/Application Support/Steam"))
        else {
            add(File(Paths.home, ".steam/steam")); add(File(Paths.home, ".local/share/Steam"))
            add(File(Paths.home, ".var/app/com.valvesoftware.Steam/.local/share/Steam"))
        }
    }.filter { File(it, "steamapps").isDirectory }.distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.path) }

    private fun registrySteamPath(): String? = runCatching {
        val out = runCommand(listOf("reg", "query", "HKCU\\Software\\Valve\\Steam", "/v", "SteamPath"), 3_000) ?: return null
        Regex("SteamPath\\s+REG_SZ\\s+(.+)").find(out)?.groupValues?.get(1)?.trim()?.replace('/', '\\')
    }.getOrNull()

    fun parseManifest(text: String, library: File): PcGame? {
        val f = Vdf.fields(text)
        val id = f["appid"] ?: return null
        val name = f["name"] ?: return null
        if (id in toolIds || toolNames.containsMatchIn(name)) return null
        val dir = f["installdir"]?.let { File(library, "steamapps/common/$it").path }
        return PcGame("steam:$id", name, Store.STEAM, dir, "steam://rungameid/$id")
    }

    fun scan(): List<PcGame> = roots().flatMap { root ->
        val vdf = File(root, "steamapps/libraryfolders.vdf").takeIf { it.isFile }?.readText()
        val libs = (listOf(root.path) + (vdf?.let { Vdf.libraryFolders(it) } ?: emptyList())).map { File(it) }.distinctBy { it.path.lowercase() }
        libs.flatMap { lib ->
            File(lib, "steamapps").listFiles { f -> f.name.startsWith("appmanifest_") && f.name.endsWith(".acf") }.orEmpty()
                .mapNotNull { runCatching { parseManifest(it.readText(), lib) }.getOrNull() }
        }
    }.distinctBy { it.id }
}

object EpicScanner {
    private fun field(json: String, key: String): String? =
        Regex("\"$key\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(json)?.groupValues?.get(1)?.replace("\\\\", "\\")?.replace("\\/", "/")

    fun parseItem(json: String): PcGame? {
        val app = field(json, "AppName") ?: return null
        val name = field(json, "DisplayName") ?: return null
        if (json.contains("\"bIsIncompleteInstall\"\\s*:\\s*true".toRegex())) return null
        val dir = field(json, "InstallLocation")
        return PcGame("epic:$app", name, Store.EPIC, dir, "com.epicgames.launcher://apps/$app?action=launch&silent=true")
    }

    fun scan(): List<PcGame> {
        if (!Paths.isWindows) return emptyList()
        val dir = File(System.getenv("ProgramData") ?: "C:\\ProgramData", "Epic\\EpicGamesLauncher\\Data\\Manifests")
        return dir.listFiles { f -> f.extension.equals("item", true) }.orEmpty().mapNotNull { runCatching { parseItem(it.readText()) }.getOrNull() }
    }
}

/** The PC game library: detected games plus games you add, each with its own mode. Saved as a small text file. */
class PcLibrary(private val file: File = File(Paths.configDir, "library.tsv")) {
    fun load(): List<PcGame> = runCatching {
        file.readLines().mapNotNull { line ->
            val p = line.split('\t')
            if (p.size < 7) return@mapNotNull null
            PcGame(p[0], p[1], runCatching { Store.valueOf(p[2]) }.getOrDefault(Store.MANUAL), p[3].ifEmpty { null }, p[4],
                runCatching { PcMode.valueOf(p[5]) }.getOrDefault(PcMode.BEAST), p[6] == "1")
        }
    }.getOrDefault(emptyList())

    fun save(games: List<PcGame>) = writeAtomically(file, games.distinctBy { it.id }.joinToString("\n") { g ->
        listOf(g.id, g.name, g.store.name, g.installDir.orEmpty(), g.launch, g.mode.name, if (g.hidden) "1" else "0").joinToString("\t") { it.replace('\t', ' ').replace('\n', ' ') }
    })

    /** Merges a fresh scan into the saved library: keeps modes and hidden flags, adds new games, keeps manual ones. */
    fun merge(saved: List<PcGame>, scanned: List<PcGame>): List<PcGame> {
        val byId = saved.associateBy { it.id }
        val merged = scanned.map { s -> byId[s.id]?.let { old -> s.copy(mode = old.mode, hidden = old.hidden) } ?: s }
        val manual = saved.filter { it.store == Store.MANUAL && merged.none { m -> m.id == it.id } }
        return (merged + manual).sortedBy { it.name.lowercase() }
    }

    fun manualGame(exe: File, mode: PcMode = PcMode.BEAST) =
        PcGame("exe:" + exe.absolutePath.lowercase(), exe.nameWithoutExtension, Store.MANUAL, exe.parentFile?.absolutePath, exe.absolutePath, mode)
}

object Launcher {
    /** Starts a game through its store (so overlays/DRM work) or directly for manually added games. */
    fun launch(g: PcGame): Result<Unit> = runCatching {
        if (g.launch.contains("://")) {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) Desktop.getDesktop().browse(URI(g.launch))
            else ProcessBuilder(if (Paths.isWindows) listOf("cmd", "/c", "start", "", g.launch) else if (Paths.isMac) listOf("open", g.launch) else listOf("xdg-open", g.launch)).start()
        } else {
            val exe = File(g.launch)
            require(exe.exists()) { "The game file no longer exists: ${exe.path}" }
            ProcessBuilder(exe.absolutePath).directory(exe.parentFile).start()
        }
    }
}

/** Runs a command with a timeout; returns its output, or null if it failed or hung. */
fun runCommand(cmd: List<String>, timeoutMs: Long = 5_000): String? = runCatching {
    val p = ProcessBuilder(cmd).redirectErrorStream(true).start()
    val out = StringBuilder()
    val t = Thread { runCatching { p.inputStream.bufferedReader().use { r -> r.forEachLine { synchronized(out) { out.appendLine(it) } } } } }.apply { isDaemon = true; start() }
    if (!p.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)) { p.destroyForcibly(); return null }
    t.join(500)
    if (p.exitValue() != 0) null else synchronized(out) { out.toString() }
}.getOrNull()
