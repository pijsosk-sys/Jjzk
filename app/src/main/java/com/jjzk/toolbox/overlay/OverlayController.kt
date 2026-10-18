package com.jjzk.toolbox.overlay

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.jjzk.toolbox.R
import com.jjzk.toolbox.hacks.ToolboxPack
import com.jjzk.toolbox.hacks.XrayPack
import com.jjzk.toolbox.ui.CurrentWorld

/**
 * The real Toolbox PE menu lives inside Minecraft — a behavior pack's
 * Script API form, opened by typing .tb in chat. The Android panel's
 * only job is to install or remove the two packs that make that menu
 * work (Jjzk Toolbox behavior pack + Jjzk X-Ray resource pack) and to
 * remind the player of the two in-game steps (enable the packs,
 * flip the Beta APIs experiment).
 */
class OverlayController(private val ctx: Context) {

    fun bind(root: View, onHide: () -> Unit) {
        root.findViewById<View>(R.id.closeOverlay).setOnClickListener { onHide() }

        val statusToolbox = root.findViewById<TextView>(R.id.statusToolbox)
        val statusXray    = root.findViewById<TextView>(R.id.statusXray)
        val installTb     = root.findViewById<Button>(R.id.installToolbox)
        val removeTb      = root.findViewById<Button>(R.id.removeToolbox)
        val installXr     = root.findViewById<Button>(R.id.installXray)
        val removeXr      = root.findViewById<Button>(R.id.removeXray)

        fun refresh() {
            val g = CurrentWorld.install
            statusToolbox.text = buildString {
                append("Toolbox pack: ")
                append(if (g != null && ToolboxPack.isInstalled(g)) "installed" else "not installed")
            }
            statusXray.text = buildString {
                append("X-Ray pack: ")
                append(if (g != null && XrayPack.isInstalled(g)) "installed" else "not installed")
            }
            val have = g != null
            installTb.isEnabled = have; removeTb.isEnabled = have
            installXr.isEnabled = have; removeXr.isEnabled = have
        }

        installTb.setOnClickListener {
            val g = CurrentWorld.install ?: return@setOnClickListener
            try { ToolboxPack.install(g); toast("Toolbox pack installed."); } catch (e: Throwable) { toast("Install failed: ${e.message}") }
            refresh()
        }
        removeTb.setOnClickListener {
            val g = CurrentWorld.install ?: return@setOnClickListener
            ToolboxPack.remove(g); toast("Toolbox pack removed."); refresh()
        }
        installXr.setOnClickListener {
            val g = CurrentWorld.install ?: return@setOnClickListener
            try { XrayPack.install(g); toast("X-Ray pack installed."); } catch (e: Throwable) { toast("Install failed: ${e.message}") }
            refresh()
        }
        removeXr.setOnClickListener {
            val g = CurrentWorld.install ?: return@setOnClickListener
            XrayPack.remove(g); toast("X-Ray pack removed."); refresh()
        }

        refresh()
    }

    private fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()
}
