package com.jjzk.toolbox.hacks

import com.jjzk.toolbox.GameInstall
import com.jjzk.toolbox.PackKind
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import java.io.File
import java.util.UUID

/**
 * Generates and installs an X-Ray resource pack. Works on any current
 * Bedrock version without root: the pack ships textures that render
 * common hider blocks (stone, deepslate, dirt, netherrack, basalt, end
 * stone, blackstone, tuff, andesite, diorite, granite) as transparent,
 * leaving ores fully opaque. Pack is written to the install's
 * resource_packs directory; the player must enable it on the world's
 * Resource Packs page once.
 */
object XrayPack {

    private val hiders = listOf(
        "stone", "deepslate", "cobbled_deepslate", "dirt", "grass_block",
        "netherrack", "basalt", "end_stone", "blackstone", "tuff",
        "andesite", "diorite", "granite", "sand", "gravel", "sandstone"
    )

    fun install(game: GameInstall): File {
        val root = game.subdir(PackKind.RESOURCE)
        val packDir = File(root, "jjzk_xray").apply {
            if (exists()) deleteRecursively(); mkdirs()
        }
        writeManifest(packDir)
        writeTextures(packDir)
        writeBlockMap(packDir)
        return packDir
    }

    private fun writeManifest(dir: File) {
        val headerUuid = UUID.nameUUIDFromBytes("jjzk_xray_header".toByteArray()).toString()
        val moduleUuid = UUID.nameUUIDFromBytes("jjzk_xray_module".toByteArray()).toString()
        File(dir, "manifest.json").writeText(
            """
            {
              "format_version": 2,
              "header": {
                "description": "Jjzk Toolbox X-Ray",
                "name": "§eJjzk §cX-Ray",
                "uuid": "$headerUuid",
                "version": [1, 0, 0],
                "min_engine_version": [1, 20, 0]
              },
              "modules": [
                {
                  "description": "Transparent hiders, opaque ores.",
                  "type": "resources",
                  "uuid": "$moduleUuid",
                  "version": [1, 0, 0]
                }
              ]
            }
            """.trimIndent()
        )
    }

    private fun writeTextures(dir: File) {
        val tx = File(dir, "textures/blocks").apply { mkdirs() }
        // 16x16 transparent PNG — one byte of nothing. Minecraft tiles it.
        val transparent = Png.transparent16()
        hiders.forEach { name ->
            File(tx, "$name.png").writeBytes(transparent)
        }
    }

    private fun writeBlockMap(dir: File) {
        // terrain_texture.json tells MCPE which image serves which block.
        val sb = StringBuilder()
        sb.append("""{"resource_pack_name":"jjzk_xray","texture_name":"atlas.terrain","texture_data":{""")
        hiders.forEachIndexed { i, n ->
            if (i > 0) sb.append(',')
            sb.append(""""$n":{"textures":"textures/blocks/$n"}""")
        }
        sb.append("}}")
        File(dir, "textures").mkdirs()
        File(dir, "textures/terrain_texture.json").writeText(sb.toString())
    }

    fun remove(game: GameInstall) {
        File(game.subdir(PackKind.RESOURCE), "jjzk_xray").deleteRecursively()
    }

    fun isInstalled(game: GameInstall): Boolean =
        File(game.subdir(PackKind.RESOURCE), "jjzk_xray/manifest.json").exists()

    fun exportMcpack(game: GameInstall, outFile: File): File {
        if (!isInstalled(game)) install(game)
        val src = File(game.subdir(PackKind.RESOURCE), "jjzk_xray")
        if (outFile.exists()) outFile.delete()
        val zip = ZipFile(outFile)
        val p = ZipParameters().apply { isIncludeRootFolder = false }
        src.listFiles()?.forEach { if (it.isDirectory) zip.addFolder(it, p) else zip.addFile(it, p) }
        return outFile
    }
}
