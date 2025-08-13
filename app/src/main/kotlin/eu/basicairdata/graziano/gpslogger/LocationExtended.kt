package eu.basicairdata.graziano.gpslogger

import android.location.Location

import eu.basicairdata.graziano.gpslogger.GPSApplication

/**
 * The Location Class, including some extra stuff in order to manage the orthometric
 * height using the EGM Correction.
 */
class LocationExtended(val location: Location) {
    var description: String = ""
    private var altitudeEGM96Correction: Double = GPSApplication.NOT_AVAILABLE.toDouble()
    var numberOfSatellites: Int = GPSApplication.NOT_AVAILABLE
    var numberOfSatellitesUsedInFix: Int = GPSApplication.NOT_AVAILABLE

    init {
        val egm96 = EGM96.getInstance()
        if (egm96 != null && egm96.isLoaded) {
            altitudeEGM96Correction = egm96.getEGMCorrection(location.latitude, location.longitude)
        }
    }

    val latitude: Double get() = location.latitude
    val longitude: Double get() = location.longitude
    val altitude: Double get() = if (location.hasAltitude()) location.altitude else GPSApplication.NOT_AVAILABLE.toDouble()
    val speed: Float get() = if (location.hasSpeed()) location.speed else GPSApplication.NOT_AVAILABLE.toFloat()
    val accuracy: Float get() = if (location.hasAccuracy()) location.accuracy else GPSApplication.NOT_AVAILABLE.toFloat()
    val bearing: Float get() = if (location.hasBearing()) location.bearing else GPSApplication.NOT_AVAILABLE.toFloat()
    val time: Long get() = location.time

    // Use Kotlin property accessors; explicit duplicate getters/setters removed to avoid JVM signature clashes

    /**
     * @return the altitude correction, in meters, based on EGM96
     */
    fun getAltitudeEGM96Correction(): Double {
        if (altitudeEGM96Correction == GPSApplication.NOT_AVAILABLE.toDouble()) {
            val egm96 = EGM96.getInstance()
            if (egm96 != null && egm96.isLoaded) {
                altitudeEGM96Correction = egm96.getEGMCorrection(location.latitude, location.longitude)
            }
        }
        return altitudeEGM96Correction
    }

    /**
     * @return the orthometric altitude in meters
     */
    fun getAltitudeCorrected(altitudeManualCorrection: Double, egmCorrection: Boolean): Double {
        if (!location.hasAltitude()) return GPSApplication.NOT_AVAILABLE.toDouble()
        return if (egmCorrection && getAltitudeEGM96Correction() != GPSApplication.NOT_AVAILABLE.toDouble())
            location.altitude - getAltitudeEGM96Correction() + altitudeManualCorrection
        else location.altitude + altitudeManualCorrection
    }
}


