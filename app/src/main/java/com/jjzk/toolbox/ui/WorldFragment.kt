package com.jjzk.toolbox.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.jjzk.toolbox.databinding.FragmentWorldBinding
import com.jjzk.toolbox.world.WorldEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WorldFragment : Fragment() {

    private var _b: FragmentWorldBinding? = null
    private val b get() = _b!!
    private var snapshot: WorldEditor.Snapshot? = null

    private val timeOfDay = linkedMapOf(
        "Sunrise" to 0L, "Day" to 1000L, "Noon" to 6000L,
        "Sunset" to 12000L, "Night" to 13000L, "Midnight" to 18000L
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _b = FragmentWorldBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        reload()
        b.save.setOnClickListener { save() }
    }

    override fun onResume() { super.onResume(); reload() }

    override fun onDestroyView() { super.onDestroyView(); _b = null }

    private fun reload() {
        val world = CurrentWorld.world ?: run {
            b.empty.visibility = View.VISIBLE
            b.form.visibility = View.GONE
            return
        }
        b.empty.visibility = View.GONE
        b.form.visibility = View.VISIBLE
        lifecycleScope.launch {
            val snap = withContext(Dispatchers.IO) { WorldEditor.read(world) }
            snapshot = snap
            bind(snap)
        }
    }

    private fun bind(s: WorldEditor.Snapshot) {
        b.nameInput.setText(s.name)
        b.modeGroup.check(when (s.mode) {
            WorldEditor.GameMode.SURVIVAL -> com.jjzk.toolbox.R.id.modeSurvival
            WorldEditor.GameMode.CREATIVE -> com.jjzk.toolbox.R.id.modeCreative
            WorldEditor.GameMode.ADVENTURE -> com.jjzk.toolbox.R.id.modeAdventure
            WorldEditor.GameMode.SPECTATOR -> com.jjzk.toolbox.R.id.modeSpectator
        })
        b.diffGroup.check(when (s.difficulty) {
            WorldEditor.Difficulty.PEACEFUL -> com.jjzk.toolbox.R.id.diffPeaceful
            WorldEditor.Difficulty.EASY     -> com.jjzk.toolbox.R.id.diffEasy
            WorldEditor.Difficulty.NORMAL   -> com.jjzk.toolbox.R.id.diffNormal
            WorldEditor.Difficulty.HARD     -> com.jjzk.toolbox.R.id.diffHard
        })
        b.weatherGroup.check(when (s.weather) {
            WorldEditor.Weather.CLEAR   -> com.jjzk.toolbox.R.id.weatherClear
            WorldEditor.Weather.RAIN    -> com.jjzk.toolbox.R.id.weatherRain
            WorldEditor.Weather.THUNDER -> com.jjzk.toolbox.R.id.weatherThunder
        })
        b.cheats.isChecked = s.cheats
        b.keepInventory.isChecked = s.keepInventory
        b.mobGriefing.isChecked = s.mobGriefing
        b.spawnX.setText(s.spawnX.toString())
        b.spawnY.setText(s.spawnY.toString())
        b.spawnZ.setText(s.spawnZ.toString())
        b.timeLabel.text = "Time: ${s.time} ticks"
    }

    private fun save() {
        val world = CurrentWorld.world ?: return
        val base = snapshot ?: return
        val mode = when (b.modeGroup.checkedRadioButtonId) {
            com.jjzk.toolbox.R.id.modeCreative  -> WorldEditor.GameMode.CREATIVE
            com.jjzk.toolbox.R.id.modeAdventure -> WorldEditor.GameMode.ADVENTURE
            com.jjzk.toolbox.R.id.modeSpectator -> WorldEditor.GameMode.SPECTATOR
            else                                 -> WorldEditor.GameMode.SURVIVAL
        }
        val diff = when (b.diffGroup.checkedRadioButtonId) {
            com.jjzk.toolbox.R.id.diffPeaceful -> WorldEditor.Difficulty.PEACEFUL
            com.jjzk.toolbox.R.id.diffEasy     -> WorldEditor.Difficulty.EASY
            com.jjzk.toolbox.R.id.diffHard     -> WorldEditor.Difficulty.HARD
            else                                -> WorldEditor.Difficulty.NORMAL
        }
        val weather = when (b.weatherGroup.checkedRadioButtonId) {
            com.jjzk.toolbox.R.id.weatherRain    -> WorldEditor.Weather.RAIN
            com.jjzk.toolbox.R.id.weatherThunder -> WorldEditor.Weather.THUNDER
            else                                  -> WorldEditor.Weather.CLEAR
        }
        val next = base.copy(
            name = b.nameInput.text.toString().ifBlank { base.name },
            mode = mode, difficulty = diff, weather = weather,
            time = parseTime(),
            cheats = b.cheats.isChecked,
            keepInventory = b.keepInventory.isChecked,
            mobGriefing = b.mobGriefing.isChecked,
            spawnX = b.spawnX.text.toString().toIntOrNull() ?: base.spawnX,
            spawnY = b.spawnY.text.toString().toIntOrNull() ?: base.spawnY,
            spawnZ = b.spawnZ.text.toString().toIntOrNull() ?: base.spawnZ
        )
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { WorldEditor.apply(world, next) }
            snapshot = next
            Toast.makeText(requireContext(), "Saved. Launch the world to apply.", Toast.LENGTH_LONG).show()
        }
    }

    private fun parseTime(): Long {
        val id = b.timeGroup.checkedRadioButtonId
        return when (id) {
            com.jjzk.toolbox.R.id.timeSunrise  -> 0L
            com.jjzk.toolbox.R.id.timeNoon     -> 6000L
            com.jjzk.toolbox.R.id.timeSunset   -> 12000L
            com.jjzk.toolbox.R.id.timeNight    -> 13000L
            com.jjzk.toolbox.R.id.timeMidnight -> 18000L
            else                                -> snapshot?.time ?: 0L
        }
    }
}
