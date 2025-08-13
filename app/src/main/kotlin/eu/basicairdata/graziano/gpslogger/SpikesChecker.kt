package eu.basicairdata.graziano.gpslogger

import eu.basicairdata.graziano.gpslogger.GPSApplication

/**
 * Checks the evolution of the altitudes with the purpose to detect the altitude spikes.
 */
class SpikesChecker(
    private val maxAcceleration: Float,
    private val stabilizationTime: Int
) {
    private var goodTime: Long = GPSApplication.NOT_AVAILABLE.toLong()

    private var prevAltitude: Double = GPSApplication.NOT_AVAILABLE.toDouble()
    private var prevTime: Long = GPSApplication.NOT_AVAILABLE.toLong()
    private var prevVerticalSpeed: Float = GPSApplication.NOT_AVAILABLE.toFloat()

    private var newAltitude: Double = GPSApplication.NOT_AVAILABLE.toDouble()
    private var newTime: Long = GPSApplication.NOT_AVAILABLE.toLong()
    private var newVerticalSpeed: Float = GPSApplication.NOT_AVAILABLE.toFloat()

    private var timeInterval: Long = GPSApplication.NOT_AVAILABLE.toLong()
    private var verticalAcceleration: Float = 0f

    fun load(time: Long, altitude: Double) {
        if (time > newTime) {
            prevTime = newTime
            newTime = time
            prevAltitude = newAltitude
            prevVerticalSpeed = newVerticalSpeed
        }
        timeInterval = if (prevTime != GPSApplication.NOT_AVAILABLE.toLong()) (newTime - prevTime) / 1000 else GPSApplication.NOT_AVAILABLE.toLong()
        newAltitude = altitude
        if (timeInterval > 0 && prevAltitude != GPSApplication.NOT_AVAILABLE.toDouble()) {
            newVerticalSpeed = ((newAltitude - prevAltitude) / timeInterval).toFloat()
            if (prevVerticalSpeed != GPSApplication.NOT_AVAILABLE.toFloat()) {
                verticalAcceleration = if (timeInterval > 1000) GPSApplication.NOT_AVAILABLE.toFloat()
                else (2 * (-prevVerticalSpeed * timeInterval + (newAltitude - prevAltitude)).toFloat() / (timeInterval * timeInterval))
            }
        }
        if (kotlin.math.abs(verticalAcceleration) >= maxAcceleration) goodTime = newTime
    }

    fun isValid(): Boolean = (newTime - goodTime) / 1000 >= stabilizationTime
}


