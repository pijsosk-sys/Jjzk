package com.jjzk.toolbox.overlay

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.jjzk.toolbox.R
import com.jjzk.toolbox.hacks.KillAuraPack
import com.jjzk.toolbox.hacks.XrayPack
import com.jjzk.toolbox.ui.CurrentWorld
import com.jjzk.toolbox.world.WorldEditor

/**
 * Binds the inflated overlay panel to the four section buttons on the left
 * (WORLD / RENDER / COMMAND / COMBAT), each one swapping the right-hand
 * body in place like the Jony Toolbox UI.
 */
class OverlayController(private val ctx: Context) {

    private enum class Section(val label: String, val toggles: List<OverlayState.Toggle>) {
        WORLD  ("WORLD",  listOf(OverlayState.Toggle.KEEP_INV, OverlayState.Toggle.CREATIVE,
                                 OverlayState.Toggle.DAY_LOCK, OverlayState.Toggle.WEATHER_OFF,
                                 OverlayState.Toggle.FULLBRIGHT, OverlayState.Toggle.NO_FALL)),
        RENDER ("RENDER", listOf(OverlayState.Toggle.X_RAY, OverlayState.Toggle.CHEST_ESP,
                                 OverlayState.Toggle.PLAYER_ESP, OverlayState.Toggle.FULLBRIGHT)),
        COMMAND("COMMAND",listOf(OverlayState.Toggle.FAST_PLACE, OverlayState.Toggle.FAST_BREAK,
                                 OverlayState.Toggle.STEP, OverlayState.Toggle.JETPACK,
                                 OverlayState.Toggle.SPEED)),
        COMBAT ("COMBAT", listOf(OverlayState.Toggle.KILLAURA, OverlayState.Toggle.REACH,
                                 OverlayState.Toggle.INSTAKILL, OverlayState.Toggle.GOD))
    }

    fun bind(root: View, onClose: () -> Unit) {
        val sideCol: LinearLayout = root.findViewById(R.id.sections)
        val body:    LinearLayout = root.findViewById(R.id.body)
        val title:   TextView     = root.findViewById(R.id.sectionTitle)
        root.findViewById<View>(R.id.closeOverlay).setOnClickListener { onClose() }

        Section.values().forEach { s ->
            val btn = TextView(ctx).apply {
                text = s.label
                setPadding(32, 24, 32, 24)
                setTextColor(0xFFFFFFFF.toInt())
                setBackgroundResource(R.drawable.bg_overlay_pill)
            }
            btn.setOnClickListener { renderSection(s, body, title) }
            sideCol.addView(btn, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8 })
        }
        renderSection(Section.WORLD, body, title)
    }

    private fun renderSection(s: Section, body: LinearLayout, title: TextView) {
        title.text = s.label
        body.removeAllViews()
        val inflater = LayoutInflater.from(ctx)
        for (t in s.toggles) {
            val row = inflater.inflate(R.layout.overlay_row, body, false)
            val label: TextView = row.findViewById(R.id.rowLabel)
            val sub:   TextView = row.findViewById(R.id.rowSub)
            val sw:    android.widget.Switch = row.findViewById(R.id.rowSwitch)
            label.text = t.label
            sub.text = when (t) {
                OverlayState.Toggle.X_RAY    -> "Installs a resource pack"
                OverlayState.Toggle.KILLAURA -> "Installs a behavior pack · chat: .ka on"
                else -> if (t.needsRoot) "Needs ptrace (rooted MCPE)" else "Writes to level.dat"
            }
            sw.isChecked = OverlayState.isOn(t)
            sw.setOnCheckedChangeListener { _, checked ->
                OverlayState.set(t, checked)
                applyToggle(t, checked)
            }
            body.addView(row, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
    }

    private fun applyToggle(t: OverlayState.Toggle, on: Boolean) {
        val install = CurrentWorld.install
        try {
            when (t) {
                OverlayState.Toggle.X_RAY -> {
                    if (install == null) { toast("Open Toolbox once to detect Minecraft"); return }
                    if (on) XrayPack.install(install) else XrayPack.remove(install)
                    toast(if (on)
                        "X-Ray pack installed. Enable it on the world's Resource Packs page."
                    else "X-Ray pack removed.")
                    return
                }
                OverlayState.Toggle.KILLAURA -> {
                    if (install == null) { toast("Open Toolbox once to detect Minecraft"); return }
                    if (on) KillAuraPack.install(install) else KillAuraPack.remove(install)
                    toast(if (on)
                        "KillAura pack installed. Enable it (behavior pack + Beta APIs experiment); in-game type .ka on"
                    else "KillAura pack removed.")
                    return
                }
                else -> Unit
            }
        } catch (e: Throwable) {
            toast("Pack op failed: ${e.message}"); return
        }

        val world = CurrentWorld.world ?: run {
            if (!t.needsRoot) toast("Pick a world in Toolbox first")
            return
        }
        if (t.needsRoot) {
            toast("${t.label}: hook stub — requires rooted MCPE")
            return
        }
        try {
            val snap = WorldEditor.read(world)
            val next = when (t) {
                OverlayState.Toggle.KEEP_INV    -> snap.copy(keepInventory = on)
                OverlayState.Toggle.CREATIVE    -> snap.copy(mode = if (on) WorldEditor.GameMode.CREATIVE else WorldEditor.GameMode.SURVIVAL)
                OverlayState.Toggle.DAY_LOCK    -> snap.copy(time = if (on) 6000L else snap.time)
                OverlayState.Toggle.WEATHER_OFF -> snap.copy(weather = WorldEditor.Weather.CLEAR)
                else -> snap
            }
            WorldEditor.apply(world, next)
            toast("${t.label}: ${if (on) "on" else "off"}. Re-enter the world to apply.")
        } catch (e: Throwable) {
            toast("Edit failed: ${e.message}")
        }
    }

    private fun toast(s: String) {
        Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()
    }
}
