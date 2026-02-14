package com.heyu.metronome

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.os.PowerManager
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.heyu.metronome.databinding.ActivityMainBinding
import com.google.android.material.chip.Chip
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var metronomeEngine: MetronomeEngine
    private lateinit var soundPool: SoundPool
    private var tickAccentId: Int = 0
    private var tickWeakId: Int = 0
    private var wakeLock: PowerManager.WakeLock? = null
    
    private var isPendulumMode = true
    private var currentBPM = 60
    private var currentBeats = 4
    private val PRESETS_KEY = "metronome_presets"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initWakeLock()
        initSoundPool()
        initMetronomeEngine()
        setupUI()
        loadPresets()
        updateDisplay()
    }

    private fun initWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE,
            "Metronome::WakeLock"
        )
    }

    private fun initSoundPool() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        
        soundPool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(audioAttributes)
            .build()
        
        tickAccentId = soundPool.load(this, R.raw.tick_accent, 1)
        tickWeakId = soundPool.load(this, R.raw.tick_weak, 1)
    }

    private fun initMetronomeEngine() {
        metronomeEngine = MetronomeEngine()
        metronomeEngine.setBPM(currentBPM)
        metronomeEngine.setTimeSignature(currentBeats)
        metronomeEngine.setCallback(object : MetronomeEngine.MetronomeCallback {
            override fun onTick(beatNumber: Int, isAccent: Boolean) {
                runOnUiThread {
                    playClick(isAccent)
                    updateBeatDisplay(beatNumber)
                    highlightBeatIndicator(beatNumber, isAccent)
                }
            }

            override fun onPendulumUpdate(phase: Float) {
                runOnUiThread {
                    updatePendulumPosition(phase)
                }
            }
        })
    }

    private fun setupUI() {
        setupTimeSignatureSelector()
        setupBPMControls()
        setupViewToggle()
        setupPresetControls()
        setupStartStopButton()
        setupHelpButton()
    }

    private fun setupHelpButton() {
        binding.btnHelp.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.help_title)
                .setMessage(R.string.help_message)
                .setPositiveButton(R.string.confirm, null)
                .show()
        }
    }

    private fun setupTimeSignatureSelector() {
        updateTimeSignatureDisplay()
        binding.tvTimeSignature.setOnClickListener {
            showTimeSignatureDialog()
        }
    }

    private fun showTimeSignatureDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_time_signature, null)
        val pickerBeats = dialogView.findViewById<android.widget.NumberPicker>(R.id.pickerBeats)
        val pickerNoteValue = dialogView.findViewById<android.widget.NumberPicker>(R.id.pickerNoteValue)
        
        // 设置分子（拍数）：1-16
        pickerBeats.minValue = 1
        pickerBeats.maxValue = 16
        pickerBeats.value = currentBeats
        
        // 设置分母（音符值）：2, 4, 6, 8, 10, 12, 16
        val noteValues = arrayOf("2", "4", "6", "8", "10", "12", "16")
        pickerNoteValue.minValue = 0
        pickerNoteValue.maxValue = noteValues.size - 1
        pickerNoteValue.displayedValues = noteValues
        pickerNoteValue.value = 1 // 默认选择4
        
        AlertDialog.Builder(this)
            .setTitle(R.string.select_time_signature)
            .setView(dialogView)
            .setPositiveButton(R.string.confirm) { _, _ ->
                currentBeats = pickerBeats.value
                // 分母仅用于显示，实际拍数由分子决定
                metronomeEngine.setTimeSignature(currentBeats)
                createBeatIndicators()
                updateTimeSignatureDisplay()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updateTimeSignatureDisplay() {
        binding.tvTimeSignature.text = "$currentBeats/4"
    }

    private fun setupBPMControls() {
        binding.sliderBPM.value = currentBPM.toFloat()
        binding.sliderBPM.addOnChangeListener { _, value, _ ->
            currentBPM = value.roundToInt()
            metronomeEngine.setBPM(currentBPM)
            updateDisplay()
        }

        // 减少按钮
        binding.btnDecrease1.setOnClickListener { adjustBPM(-1) }
        binding.btnDecrease5.setOnClickListener { adjustBPM(-5) }
        binding.btnDecrease10.setOnClickListener { adjustBPM(-10) }

        // 增加按钮
        binding.btnIncrease1.setOnClickListener { adjustBPM(1) }
        binding.btnIncrease5.setOnClickListener { adjustBPM(5) }
        binding.btnIncrease10.setOnClickListener { adjustBPM(10) }
    }

    private fun adjustBPM(delta: Int) {
        val newBPM = (currentBPM + delta).coerceIn(40, 208)
        if (newBPM != currentBPM) {
            currentBPM = newBPM
            binding.sliderBPM.value = currentBPM.toFloat()
            metronomeEngine.setBPM(currentBPM)
            updateDisplay()
        }
    }

    private fun setupViewToggle() {
        binding.btnToggleView.setOnClickListener {
            isPendulumMode = !isPendulumMode
            if (isPendulumMode) {
                binding.pendulumView.visibility = View.VISIBLE
                binding.dotsContainer.visibility = View.GONE
            } else {
                binding.pendulumView.visibility = View.GONE
                binding.dotsContainer.visibility = View.VISIBLE
                createBeatIndicators()
            }
        }
    }

    private fun createBeatIndicators() {
        binding.dotsContainer.removeAllViews()
        for (i in 1..currentBeats) {
            val dot = ImageView(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(16, 16).apply {
                    marginStart = 8
                    marginEnd = 8
                }
                setBackgroundResource(if (i == 1) R.drawable.beat_indicator_accent else R.drawable.beat_indicator_inactive)
                tag = i
            }
            binding.dotsContainer.addView(dot)
        }
    }

    private fun highlightBeatIndicator(beatNumber: Int, isAccent: Boolean) {
        if (!isPendulumMode) {
            for (i in 0 until binding.dotsContainer.childCount) {
                val dot = binding.dotsContainer.getChildAt(i) as ImageView
                val dotBeat = dot.tag as Int
                if (dotBeat == beatNumber) {
                    dot.setBackgroundResource(if (isAccent) R.drawable.beat_indicator_accent else R.drawable.beat_indicator_active)
                } else {
                    dot.setBackgroundResource(if (dotBeat == 1) R.drawable.beat_indicator_accent else R.drawable.beat_indicator_inactive)
                }
            }
        }
    }

    private fun updatePendulumPosition(phase: Float) {
        val angle = if (phase < 0.5f) {
            -20f + (phase * 2 * 40f)
        } else {
            20f - ((phase - 0.5f) * 2 * 40f)
        }
        binding.pendulumView.rotation = angle
    }

    private fun setupPresetControls() {
        binding.btnSavePreset.setOnClickListener {
            showSavePresetDialog()
        }
    }

    private fun showSavePresetDialog() {
        val editText = android.widget.EditText(this)
        // 默认名称格式：BPM-拍数-分母，如 "60-4-4"
        val defaultName = "$currentBPM-$currentBeats-4"
        editText.setText(defaultName)
        editText.setSelection(defaultName.length) // 将光标移到末尾，方便用户修改
        
        AlertDialog.Builder(this)
            .setTitle(R.string.save_current_tempo)
            .setView(editText)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = editText.text.toString()
                if (name.isNotEmpty()) {
                    savePreset(name, currentBPM, currentBeats)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun savePreset(name: String, bpm: Int, beats: Int) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val presetsJson = prefs.getString(PRESETS_KEY, "[]")
        val presets = JSONArray(presetsJson)
        
        val preset = JSONObject().apply {
            put("name", name)
            put("bpm", bpm)
            put("beats", beats)
        }
        presets.put(preset)
        
        prefs.edit().putString(PRESETS_KEY, presets.toString()).apply()
        loadPresets()
        Toast.makeText(this, getString(R.string.preset_saved) + ": $name", Toast.LENGTH_SHORT).show()
    }

    private fun loadPresets() {
        binding.presetChipGroup.removeAllViews()
        
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val presetsJson = prefs.getString(PRESETS_KEY, "[]")
        val presets = JSONArray(presetsJson)
        
        for (i in 0 until presets.length()) {
            val preset = presets.getJSONObject(i)
            val name = preset.getString("name")
            val bpm = preset.getInt("bpm")
            val beats = preset.getInt("beats")
            
            val chip = Chip(this).apply {
                text = "$name ($bpm BPM)"
                isClickable = true
                isCheckable = false
                setOnClickListener {
                    loadPreset(bpm, beats)
                }
                setOnLongClickListener {
                    showDeletePresetDialog(i)
                    true
                }
            }
            binding.presetChipGroup.addView(chip)
        }
    }

    private fun showDeletePresetDialog(index: Int) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_preset)
            .setMessage(R.string.delete_preset_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                deletePreset(index)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deletePreset(index: Int) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val presetsJson = prefs.getString(PRESETS_KEY, "[]")
        val presets = JSONArray(presetsJson)
        
        val newPresets = JSONArray()
        for (i in 0 until presets.length()) {
            if (i != index) {
                newPresets.put(presets.getJSONObject(i))
            }
        }
        
        prefs.edit().putString(PRESETS_KEY, newPresets.toString()).apply()
        loadPresets()
    }

    private fun loadPreset(bpm: Int, beats: Int) {
        currentBPM = bpm
        currentBeats = beats
        metronomeEngine.setBPM(bpm)
        metronomeEngine.setTimeSignature(beats)
        binding.sliderBPM.value = bpm.toFloat()
        
        updateTimeSignatureDisplay()
        createBeatIndicators()
        updateDisplay()
    }

    private fun setupStartStopButton() {
        binding.btnStartStop.setOnClickListener {
            if (metronomeEngine.isPlaying()) {
                stopMetronome()
            } else {
                startMetronome()
            }
        }
    }

    private fun startMetronome() {
        metronomeEngine.start()
        wakeLock?.acquire(10*60*1000L)
        binding.btnStartStop.text = getString(R.string.stop)
        binding.btnStartStop.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.holo_red_light)
    }

    private fun stopMetronome() {
        metronomeEngine.stop()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        binding.btnStartStop.text = getString(R.string.start)
        binding.btnStartStop.backgroundTintList = ContextCompat.getColorStateList(this, R.color.purple_700)
        binding.pendulumView.rotation = 0f
        resetBeatIndicators()
    }

    private fun resetBeatIndicators() {
        if (!isPendulumMode) {
            for (i in 0 until binding.dotsContainer.childCount) {
                val dot = binding.dotsContainer.getChildAt(i) as ImageView
                val dotBeat = dot.tag as Int
                dot.setBackgroundResource(if (dotBeat == 1) R.drawable.beat_indicator_accent else R.drawable.beat_indicator_inactive)
            }
        }
    }

    private fun playClick(isAccent: Boolean) {
        val soundId = if (isAccent) tickAccentId else tickWeakId
        soundPool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f)
    }

    private fun updateBeatDisplay(beatNumber: Int) {
        binding.tvCurrentBeat.text = "$beatNumber / $currentBeats"
    }

    private fun updateDisplay() {
        binding.tvBPM.text = currentBPM.toString()
        binding.tvTempoTerm.text = getTempoTerm(currentBPM)
        binding.tvCurrentBeat.text = "1 / $currentBeats"
    }

    private fun getTempoTerm(bpm: Int): String {
        return when (bpm) {
            in 0..44 -> "Grave"
            in 45..54 -> "Largo"
            in 55..64 -> "Lento"
            in 65..76 -> "Adagio"
            in 77..108 -> "Andante"
            in 109..120 -> "Moderato"
            in 121..140 -> "Allegretto"
            in 141..168 -> "Allegro"
            in 169..200 -> "Vivace"
            else -> "Presto"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        metronomeEngine.destroy()
        soundPool.release()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    override fun onPause() {
        super.onPause()
        if (metronomeEngine.isPlaying()) {
            stopMetronome()
        }
    }
}
