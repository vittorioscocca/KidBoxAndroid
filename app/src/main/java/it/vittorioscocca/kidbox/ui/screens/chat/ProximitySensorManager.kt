package it.vittorioscocca.kidbox.ui.screens.chat

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager

class ProximitySensorManager(
    context: Context,
) : SensorEventListener {
    private val appContext = context.applicationContext
    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val proximitySensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    private var playbackActive = false
    private var recordingActive = false
    private var near = false
    private var observing = false

    fun setPlaybackActive(active: Boolean) {
        playbackActive = active
        refresh()
    }

    fun setRecordingActive(active: Boolean) {
        recordingActive = active
        refresh()
    }

    fun start() {
        if (observing || proximitySensor == null) return
        sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL)
        observing = true
    }

    fun stop() {
        if (!observing) return
        sensorManager.unregisterListener(this)
        observing = false
        restoreAudioRoute()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val value = event?.values?.firstOrNull() ?: return
        val maxRange = proximitySensor?.maximumRange ?: return
        near = value < maxRange
        refresh()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun refresh() {
        val routeToEarpiece = playbackActive && !recordingActive && near
        if (routeToEarpiece) {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = false
        } else {
            restoreAudioRoute()
        }
    }

    private fun restoreAudioRoute() {
        audioManager.isSpeakerphoneOn = true
        audioManager.mode = AudioManager.MODE_NORMAL
    }
}

