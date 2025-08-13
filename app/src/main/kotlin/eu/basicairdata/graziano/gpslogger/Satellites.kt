package eu.basicairdata.graziano.gpslogger

import android.location.GnssStatus
import android.location.GpsSatellite
import android.location.GpsStatus
import android.os.Build
import androidx.annotation.RequiresApi
import eu.basicairdata.graziano.gpslogger.GPSApplication

/**
 * Stores and manages the updating of the status of the satellites constellations.
 */
class Satellites {
    var numSatsTotal: Int = GPSApplication.NOT_AVAILABLE
        private set
    var numSatsUsedInFix: Int = GPSApplication.NOT_AVAILABLE
        private set

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
            numSatsTotal = GPSApplication.NOT_AVAILABLE
            numSatsUsedInFix = GPSApplication.NOT_AVAILABLE
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
            numSatsTotal = GPSApplication.NOT_AVAILABLE
            numSatsUsedInFix = GPSApplication.NOT_AVAILABLE
        }
    }
}


