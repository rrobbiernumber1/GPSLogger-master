package eu.basicairdata.graziano.gpslogger

import android.location.Location

import eu.basicairdata.graziano.gpslogger.GPSApplication.NOT_AVAILABLE

/**
 * The Location Class, including some extra stuff in order to manage the orthometric
 * height using the EGM Correction.
 */
class LocationExtended(val location: Location) {
    var description: String = ""
    private var altitudeEGM96Correction: Double = NOT_AVAILABLE.toDouble()
    private var numberOfSatellites: Int = NOT_AVAILABLE
    private var numberOfSatellitesUsedInFix: Int = NOT_AVAILABLE

    init {
        val egm96 = EGM96.getInstance()
        if (egm96 != null && egm96.isLoaded) {
            altitudeEGM96Correction = egm96.getEGMCorrection(location.latitude, location.longitude)
        }
    }

    val latitude: Double get() = location.latitude
    val longitude: Double get() = location.longitude
    val altitude: Double get() = if (location.hasAltitude()) location.altitude else NOT_AVAILABLE.toDouble()
    val speed: Float get() = if (location.hasSpeed()) location.speed else NOT_AVAILABLE.toFloat()
    val accuracy: Float get() = if (location.hasAccuracy()) location.accuracy else NOT_AVAILABLE.toFloat()
    val bearing: Float get() = if (location.hasBearing()) location.bearing else NOT_AVAILABLE.toFloat()
    val time: Long get() = location.time

    fun setNumberOfSatellites(n: Int) { numberOfSatellites = n }
    fun getNumberOfSatellites(): Int = numberOfSatellites
    fun setNumberOfSatellitesUsedInFix(n: Int) { numberOfSatellitesUsedInFix = n }
    fun getNumberOfSatellitesUsedInFix(): Int = numberOfSatellitesUsedInFix

    /**
     * @return the altitude correction, in meters, based on EGM96
     */
    fun getAltitudeEGM96Correction(): Double {
        if (altitudeEGM96Correction == NOT_AVAILABLE.toDouble()) {
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
        if (!location.hasAltitude()) return NOT_AVAILABLE.toDouble()
        return if (egmCorrection && getAltitudeEGM96Correction() != NOT_AVAILABLE.toDouble())
            location.altitude - getAltitudeEGM96Correction() + altitudeManualCorrection
        else location.altitude + altitudeManualCorrection
    }
}


