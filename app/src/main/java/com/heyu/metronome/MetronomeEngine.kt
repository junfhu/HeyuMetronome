package com.heyu.metronome

import android.os.Handler
import android.os.Looper
import kotlin.math.roundToInt

class MetronomeEngine {

    interface MetronomeCallback {
        fun onTick(beatNumber: Int, isAccent: Boolean)
        fun onPendulumUpdate(phase: Float)
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isPlaying = false
    private var currentBeat = 0
    private var bpm = 120
    private var beatsPerMeasure = 4
    private var callback: MetronomeCallback? = null
    
    private var nextTickTime = 0L
    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!isPlaying) return
            
            val currentTime = System.currentTimeMillis()
            val delay = nextTickTime - currentTime
            
            if (delay <= 0) {
                currentBeat = (currentBeat % beatsPerMeasure) + 1
                val isAccent = currentBeat == 1
                callback?.onTick(currentBeat, isAccent)
                
                val interval = 60000 / bpm
                nextTickTime = currentTime + interval
                handler.postDelayed(this, interval.toLong())
            } else {
                handler.postDelayed(this, delay)
            }
        }
    }

    private val pendulumRunnable = object : Runnable {
        override fun run() {
            if (!isPlaying) return
            
            val interval = 60000f / bpm
            val currentTime = System.currentTimeMillis()
            val phase = (currentTime % (interval * 2).toLong()) / (interval * 2)
            callback?.onPendulumUpdate(phase)
            
            handler.postDelayed(this, 16)
        }
    }

    fun setCallback(callback: MetronomeCallback) {
        this.callback = callback
    }

    fun setBPM(bpm: Int) {
        this.bpm = bpm.coerceIn(40, 208)
    }

    fun getBPM(): Int = bpm

    fun setTimeSignature(beatsPerMeasure: Int) {
        this.beatsPerMeasure = beatsPerMeasure
        if (currentBeat > beatsPerMeasure) {
            currentBeat = 0
        }
    }

    fun start() {
        if (isPlaying) return
        isPlaying = true
        currentBeat = 0
        nextTickTime = System.currentTimeMillis()
        handler.post(tickRunnable)
        handler.post(pendulumRunnable)
    }

    fun stop() {
        isPlaying = false
        handler.removeCallbacks(tickRunnable)
        handler.removeCallbacks(pendulumRunnable)
        currentBeat = 0
    }

    fun isPlaying(): Boolean = isPlaying

    fun getCurrentBeat(): Int = currentBeat

    fun getBeatsPerMeasure(): Int = beatsPerMeasure

    fun getTempoTerm(): String {
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

    fun destroy() {
        stop()
        callback = null
    }
}
