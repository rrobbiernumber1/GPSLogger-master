package eu.basicairdata.graziano.gpslogger

import android.location.GnssStatus
import android.location.GpsSatellite
import android.location.GpsStatus
import android.os.Build
import androidx.annotation.RequiresApi
import eu.basicairdata.graziano.gpslogger.GPSApplication.NOT_AVAILABLE

/**
 * Stores and manages the updating of the status of the satellites constellations.
 */
class Satellites {
    private var numSatsTotal: Int = NOT_AVAILABLE
    private var numSatsUsedInFix: Int = NOT_AVAILABLE

    fun getNumSatsTotal(): Int = numSatsTotal
    fun getNumSatsUsedInFix(): Int = numSatsUsedInFix

    fun updateStatus(gpsStatus: GpsStatus?) {
        if (gpsStatus != null) {
            var satsTotal = 0
            var satsUsed = 0
            val sats: Iterable<GpsSatellite> = gpsStatus.satellites
            for (sat in sats) {
                satsTotal++
                if (sat.usedInFix()) satsUsed++
            }
            numSatsTotal = satsTotal
            numSatsUsedInFix = satsUsed
        } else {
            numSatsTotal = NOT_AVAILABLE
            numSatsUsedInFix = NOT_AVAILABLE
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    fun updateStatus(gnssStatus: GnssStatus?) {
        if (gnssStatus != null) {
            data class Satellite(var svid: Int, var constellationType: Int, var used: Boolean)
            val list = ArrayList<Satellite>()
            for (i in 0 until gnssStatus.satelliteCount) {
                val sat = Satellite(
                    gnssStatus.getSvid(i),
                    gnssStatus.getConstellationType(i),
                    gnssStatus.usedInFix(i)
                )
                var found = false
                for (s in list) {
                    if (s.svid == sat.svid && s.constellationType == sat.constellationType) {
                        found = true
                        if (sat.used) s.used = true
                    }
                }
                if (!found) list.add(sat)
            }
            numSatsTotal = list.size
            numSatsUsedInFix = list.count { it.used }
        } else {
            numSatsTotal = NOT_AVAILABLE
            numSatsUsedInFix = NOT_AVAILABLE
        }
    }
}


