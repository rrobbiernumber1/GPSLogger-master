package eu.basicairdata.graziano.gpslogger

import android.location.Location
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * A helper Class for the formatting of the physical data.
 * It returns the data formatted basing on the given criteria and on the Preferences.
 */
class PhysicalDataFormatter {

    companion object {
        const val UM_METRIC = 0
        const val UM_IMPERIAL = 8
        const val UM_NAUTICAL = 16

        const val UM_SPEED_MS = 0
        const val UM_SPEED_KMH = 1
        const val UM_SPEED_FPS = 2
        const val UM_SPEED_MPH = 3
        const val UM_SPEED_KN = 4

        const val FORMAT_LATITUDE: Byte = 1
        const val FORMAT_LONGITUDE: Byte = 2
        const val FORMAT_ALTITUDE: Byte = 3
        const val FORMAT_SPEED: Byte = 4
        const val FORMAT_ACCURACY: Byte = 5
        const val FORMAT_BEARING: Byte = 6
        const val FORMAT_DURATION: Byte = 7
        const val FORMAT_SPEED_AVG: Byte = 8
        const val FORMAT_DISTANCE: Byte = 9
        const val FORMAT_TIME: Byte = 10

        const val M_TO_FT = 3.280839895f
        const val M_TO_NM = 0.000539957f
        const val MS_TO_MPH = 2.2369363f
        const val MS_TO_KMH = 3.6f
        const val MS_TO_KN = 1.943844491f
        const val KM_TO_MI = 0.621371192237f
    }

    private val gpsApp = GPSApplication.getInstance()

    fun format(number: Float, format: Byte): PhysicalData {
        val physicalData = PhysicalData()
        if (number == GPSApplication.NOT_AVAILABLE.toFloat()) return physicalData
        when (format) {
            FORMAT_SPEED -> when (gpsApp.prefUMOfSpeed) {
                UM_SPEED_KMH -> {
                    physicalData.value = (number * MS_TO_KMH).toInt().toString()
                    physicalData.um = gpsApp.getString(R.string.UM_km_h)
                }
                UM_SPEED_MS -> {
                    physicalData.value = number.toInt().toString()
                    physicalData.um = gpsApp.getString(R.string.UM_m_s)
                }
                UM_SPEED_MPH -> {
                    physicalData.value = (number * MS_TO_MPH).toInt().toString()
                    physicalData.um = gpsApp.getString(R.string.UM_mph)
                }
                UM_SPEED_FPS -> {
                    physicalData.value = (number * M_TO_FT).toInt().toString()
                    physicalData.um = gpsApp.getString(R.string.UM_fps)
                }
                UM_SPEED_KN -> {
                    physicalData.value = (number * MS_TO_KN).toInt().toString()
                    physicalData.um = gpsApp.getString(R.string.UM_kn)
                }
            }
            FORMAT_SPEED_AVG -> when (gpsApp.prefUMOfSpeed) {
                UM_SPEED_KMH -> {
                    physicalData.value = String.format(Locale.getDefault(), "%.1f", number * MS_TO_KMH)
                    physicalData.um = gpsApp.getString(R.string.UM_km_h)
                }
                UM_SPEED_MS -> {
                    physicalData.value = String.format(Locale.getDefault(), "%.1f", number)
                    physicalData.um = gpsApp.getString(R.string.UM_m_s)
                }
                UM_SPEED_MPH -> {
                    physicalData.value = String.format(Locale.getDefault(), "%.1f", number * MS_TO_MPH)
                    physicalData.um = gpsApp.getString(R.string.UM_mph)
                }
                UM_SPEED_FPS -> {
                    physicalData.value = String.format(Locale.getDefault(), "%.1f", number * M_TO_FT)
                    physicalData.um = gpsApp.getString(R.string.UM_fps)
                }
                UM_SPEED_KN -> {
                    physicalData.value = String.format(Locale.getDefault(), "%.1f", number * MS_TO_KN)
                    physicalData.um = gpsApp.getString(R.string.UM_kn)
                }
            }
            FORMAT_ACCURACY -> when (gpsApp.prefUM) {
                UM_METRIC -> {
                    physicalData.value = if (GPSApplication.getInstance().isAccuracyDecimal()) {
                        when {
                            Math.round(number) >= 10 -> Math.round(number).toString()
                            Math.round(number * 10) >= 10 -> String.format(Locale.getDefault(), "%.1f", (Math.round(number * 10.0f)) / 10.0f)
                            else -> String.format(Locale.getDefault(), "%.2f", (Math.floor((number * 100.0).toDouble()) / 100.0))
                        }
                    } else Math.round(number).toString()
                    physicalData.um = gpsApp.getString(R.string.UM_m)
                }
                UM_IMPERIAL, UM_NAUTICAL -> {
                    physicalData.value = if (GPSApplication.getInstance().isAccuracyDecimal()) {
                        when {
                            Math.round(number * M_TO_FT) >= 10 -> Math.round(number * M_TO_FT).toString()
                            Math.round(number * M_TO_FT * 10) >= 10 -> String.format(Locale.getDefault(), "%.1f", (Math.round(number * M_TO_FT * 10.0f)) / 10.0f)
                            else -> String.format(Locale.getDefault(), "%.2f", (Math.floor((number * M_TO_FT * 100.0).toDouble()) / 100.0))
                        }
                    } else Math.round(number * M_TO_FT).toString()
                    physicalData.um = gpsApp.getString(R.string.UM_ft)
                }
            }
        }
        return physicalData
    }

    fun format(number: Double, format: Byte): PhysicalData {
        val physicalData = PhysicalData()
        if (number == GPSApplication.NOT_AVAILABLE.toDouble()) return physicalData
        when (format) {
            FORMAT_LATITUDE -> {
                physicalData.value = if (gpsApp.prefShowDecimalCoordinates)
                    String.format(Locale.getDefault(), "%.9f", kotlin.math.abs(number))
                else Location.convert(kotlin.math.abs(number), Location.FORMAT_SECONDS)
                    .replaceFirst(":".toRegex(), "°")
                    .replaceFirst(":".toRegex(), "' ")
                    .plus("\"")
                physicalData.um = if (number >= 0) gpsApp.getString(R.string.north) else gpsApp.getString(R.string.south)
            }
            FORMAT_LONGITUDE -> {
                physicalData.value = if (gpsApp.prefShowDecimalCoordinates)
                    String.format(Locale.getDefault(), "%.9f", kotlin.math.abs(number))
                else Location.convert(kotlin.math.abs(number), Location.FORMAT_SECONDS)
                    .replaceFirst(":".toRegex(), "°")
                    .replaceFirst(":".toRegex(), "' ")
                    .plus("\"")
                physicalData.um = if (number >= 0) gpsApp.getString(R.string.east) else gpsApp.getString(R.string.west)
            }
            FORMAT_ALTITUDE -> when (gpsApp.prefUM) {
                UM_METRIC -> {
                    physicalData.value = Math.round(number).toString()
                    physicalData.um = gpsApp.getString(R.string.UM_m)
                }
                UM_IMPERIAL, UM_NAUTICAL -> {
                    physicalData.value = Math.round(number * M_TO_FT).toString()
                    physicalData.um = gpsApp.getString(R.string.UM_ft)
                }
            }
        }
        return physicalData
    }

    fun format(number: Long, format: Byte): PhysicalData {
        val physicalData = PhysicalData()
        if (number == GPSApplication.NOT_AVAILABLE.toLong()) return physicalData
        when (format) {
            FORMAT_DURATION -> {
                val time = number / 1000
                var seconds = ((time % 60).toInt()).toString()
                var minutes = (((time % 3600) / 60).toInt()).toString()
                var hours = (time / 3600).toInt().toString()
                repeat(2) {
                    if (seconds.length < 2) seconds = "0$seconds"
                    if (minutes.length < 2) minutes = "0$minutes"
                    if (hours.length < 2) hours = "0$hours"
                }
                physicalData.value = if (hours == "00") "$minutes:$seconds" else "$hours:$minutes:$seconds"
            }
            FORMAT_TIME -> {
                if (gpsApp.prefShowLocalTime) {
                    val dfdTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    val dfdTimeZone = SimpleDateFormat("ZZZZZ", Locale.getDefault())
                    physicalData.value = dfdTime.format(number)
                    physicalData.um = dfdTimeZone.format(number)
                } else {
                    val dfdTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    dfdTime.timeZone = TimeZone.getTimeZone("GMT")
                    physicalData.value = dfdTime.format(number)
                }
            }
            FORMAT_DISTANCE -> when (gpsApp.prefUM) {
                UM_METRIC -> {
                    if (number < 1000) {
                        physicalData.value = String.format(Locale.getDefault(), "%.0f", Math.floor(number.toDouble()))
                        physicalData.um = gpsApp.getString(R.string.UM_m)
                    } else {
                        physicalData.value = if (number < 10000) String.format(Locale.getDefault(), "%.2f", Math.floor(number / 10.0) / 100.0)
                        else String.format(Locale.getDefault(), "%.1f", Math.floor(number / 100.0) / 10.0)
                        physicalData.um = gpsApp.getString(R.string.UM_km)
                    }
                }
                UM_IMPERIAL -> {
                    if (number * M_TO_FT < 1000) {
                        physicalData.value = String.format(Locale.getDefault(), "%.0f", Math.floor(number * M_TO_FT.toDouble()))
                        physicalData.um = gpsApp.getString(R.string.UM_ft)
                    } else {
                        val miles = number * KM_TO_MI
                        physicalData.value = if (miles < 10000) String.format(Locale.getDefault(), "%.2f", Math.floor(miles / 10.0) / 100.0)
                        else String.format(Locale.getDefault(), "%.1f", Math.floor(miles / 100.0) / 10.0)
                        physicalData.um = gpsApp.getString(R.string.UM_mi)
                    }
                }
                UM_NAUTICAL -> {
                    val nm = number * M_TO_NM
                    physicalData.value = if (nm < 100) String.format(Locale.getDefault(), "%.2f", Math.floor(nm * 100.0) / 100.0)
                    else String.format(Locale.getDefault(), "%.1f", Math.floor(nm * 10.0) / 10.0)
                    physicalData.um = gpsApp.getString(R.string.UM_nm)
                }
            }
        }
        return physicalData
    }
}


