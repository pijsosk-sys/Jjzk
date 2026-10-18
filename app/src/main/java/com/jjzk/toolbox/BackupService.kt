package com.jjzk.toolbox

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.CompressionLevel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Round-trip backup for a world: pack folder into an .mcworld (plain zip),
 * restore by delegating to PackInstaller. Also supports full snapshot of
 * every pack directory for the detected game install.
 */
object BackupService {

    suspend fun backupWorld(ctx: Context, world: WorldSummary): File = withContext(Dispatchers.IO) {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val safeName = world.name.replace(Regex("[^A-Za-z0-9_\\- ]"), "_").trim()
        val out = File(WorldRepository.exportDirInApp(ctx), "$safeName-$stamp.mcworld")
        if (out.exists()) out.delete()
        val zip = ZipFile(out)
        val params = ZipParameters().apply {
            compressionLevel = CompressionLevel.NORMAL
            isIncludeRootFolder = false
        }
        world.folder.listFiles()?.forEach { f ->
            if (f.isDirectory) zip.addFolder(f, params) else zip.addFile(f, params)
        }
        out
    }

    suspend fun snapshotEverything(ctx: Context, install: GameInstall): File =
        withContext(Dispatchers.IO) {
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val out = File(WorldRepository.exportDirInApp(ctx), "snapshot-$stamp.zip")
            if (out.exists()) out.delete()
            val zip = ZipFile(out)
            val params = ZipParameters().apply {
                compressionLevel = CompressionLevel.NORMAL
                isIncludeRootFolder = true
            }
            for (kind in PackKind.values()) {
                val src = install.subdir(kind)
                if (src.exists() && (src.listFiles()?.isNotEmpty() == true))
                    zip.addFolder(src, params)
            }
            out
        }

    suspend fun restore(install: GameInstall, backup: File): PackInstaller.Result =
        PackInstaller.install(install, PackInstaller.Source.LocalFile(backup))
}
