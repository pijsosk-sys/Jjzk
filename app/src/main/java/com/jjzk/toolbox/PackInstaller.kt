package com.jjzk.toolbox

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import java.io.File

/**
 * Handles .mcpack, .mcaddon, .mcworld, .mctemplate.
 *   mcpack      -> single resource/behavior/skin pack, routed by manifest 'modules[0].type'
 *   mcaddon     -> container zip with multiple packs
 *   mcworld     -> level folder, dropped into minecraftWorlds/<uuid or stem>
 *   mctemplate  -> dropped into world_templates/
 *
 * Routing matches every Mojang shipping version from 1.0 forward and
 * degrades to legacy name heuristics for pre-manifest 0.x content.
 */
object PackInstaller {

    data class Result(val installed: List<File>, val skipped: List<String>)

    sealed class Source {
        data class LocalFile(val file: File) : Source()
        data class ContentUri(val ctx: Context, val uri: Uri, val displayName: String) : Source()
    }

    suspend fun install(install: GameInstall, src: Source): Result = withContext(Dispatchers.IO) {
        val workDir = File(install.root, ".jjzk_tmp/${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val local = materialize(src, workDir)
            val ext = local.extension.lowercase()
            when (ext) {
                "mcpack"     -> installPack(install, local)
                "mcaddon"    -> installAddon(install, local, workDir)
                "mcworld"    -> installWorld(install, local, PackKind.WORLD)
                "mctemplate" -> installWorld(install, local, PackKind.TEMPLATE)
                else         -> Result(emptyList(), listOf("Unsupported type: $ext"))
            }
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun materialize(src: Source, workDir: File): File = when (src) {
        is Source.LocalFile -> src.file
        is Source.ContentUri -> {
            val out = File(workDir, src.displayName)
            src.ctx.contentResolver.openInputStream(src.uri)!!.use { input ->
                out.outputStream().use { input.copyTo(it) }
            }
            out
        }
    }

    private fun installPack(install: GameInstall, zipFile: File): Result {
        val staging = File(zipFile.parentFile, "stage_${zipFile.nameWithoutExtension}").apply { mkdirs() }
        ZipFile(zipFile).extractAll(staging.absolutePath)
        val packRoot = findManifestRoot(staging) ?: return Result(emptyList(), listOf("manifest.json missing"))
        val kind = classify(packRoot)
        val targetName = packRoot.name.ifBlank { zipFile.nameWithoutExtension }
        val target = File(install.subdir(kind), targetName)
        if (target.exists()) target.deleteRecursively()
        packRoot.copyRecursively(target, overwrite = true)
        return Result(listOf(target), emptyList())
    }

    private fun installAddon(install: GameInstall, addon: File, work: File): Result {
        val staging = File(work, "addon").apply { mkdirs() }
        ZipFile(addon).extractAll(staging.absolutePath)
        val installed = mutableListOf<File>()
        val skipped = mutableListOf<String>()
        // Each inner pack is either a nested .mcpack or a directory with manifest.json
        staging.walkTopDown().forEach { f ->
            if (f.isFile && f.extension.equals("mcpack", true)) {
                val r = installPack(install, f); installed += r.installed; skipped += r.skipped
            }
        }
        staging.listFiles()?.forEach { child ->
            if (child.isDirectory && File(child, "manifest.json").exists()) {
                val kind = classify(child)
                val target = File(install.subdir(kind), child.name)
                if (target.exists()) target.deleteRecursively()
                child.copyRecursively(target, overwrite = true)
                installed += target
            }
        }
        return Result(installed, skipped)
    }

    private fun installWorld(install: GameInstall, zipFile: File, kind: PackKind): Result {
        val target = File(install.subdir(kind), zipFile.nameWithoutExtension)
        if (target.exists()) target.deleteRecursively()
        target.mkdirs()
        ZipFile(zipFile).extractAll(target.absolutePath)
        // mcworld archives sometimes nest everything in a single subfolder — flatten.
        val entries = target.listFiles().orEmpty()
        if (entries.size == 1 && entries[0].isDirectory && !File(target, "level.dat").exists()) {
            val nested = entries[0]
            nested.listFiles()?.forEach { it.renameTo(File(target, it.name)) }
            nested.deleteRecursively()
        }
        return Result(listOf(target), emptyList())
    }

    private fun findManifestRoot(dir: File): File? {
        if (File(dir, "manifest.json").exists()) return dir
        dir.listFiles()?.forEach { child ->
            if (child.isDirectory && File(child, "manifest.json").exists()) return child
        }
        // One extra level of nesting (common for *.mcpack zips containing a single folder).
        dir.listFiles()?.forEach { child ->
            if (child.isDirectory) findManifestRoot(child)?.let { return it }
        }
        return null
    }

    private fun classify(packRoot: File): PackKind {
        val manifest = File(packRoot, "manifest.json")
        if (manifest.exists()) {
            val root = Gson().fromJson(manifest.readText(), JsonObject::class.java)
            val modules = root.getAsJsonArray("modules")
            val type = modules?.firstOrNull()?.asJsonObject?.get("type")?.asString?.lowercase()
            return when (type) {
                "data", "script"     -> PackKind.BEHAVIOR
                "resources"          -> PackKind.RESOURCE
                "skin_pack"          -> PackKind.SKIN
                "world_template"     -> PackKind.TEMPLATE
                else                 -> heuristic(packRoot)
            }
        }
        return heuristic(packRoot)
    }

    private fun heuristic(dir: File): PackKind {
        val names = dir.list().orEmpty().map { it.lowercase() }.toSet()
        return when {
            "skins.json" in names                              -> PackKind.SKIN
            "textures" in names || "ui" in names               -> PackKind.RESOURCE
            "entities" in names || "scripts" in names          -> PackKind.BEHAVIOR
            "level.dat" in names                               -> PackKind.WORLD
            else                                                -> PackKind.RESOURCE
        }
    }
}
