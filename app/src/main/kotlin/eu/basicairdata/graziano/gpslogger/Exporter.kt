package eu.basicairdata.graziano.gpslogger

import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.BufferedWriter
import java.io.IOException
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ArrayBlockingQueue

/**
 * A Thread that performs the exportation of a Track in KML, GPX, and/or TXT format.
 * The files exported and the destination folder depend on the input parameters.
 */
class Exporter(
    private val exportingTask: ExportingTask,
    private val exportKML: Boolean,
    private val exportGPX: Boolean,
    private val exportTXT: Boolean,
    private val saveIntoFolder: String
) : Thread() {

    private val gpsApp: GPSApplication = GPSApplication.getInstance()

    private val track: Track = gpsApp.gpsDataBase.getTrack(exportingTask.id)
    private val altitudeManualCorrection: Double = gpsApp.prefAltitudeCorrection
    private val egmAltitudeCorrection: Boolean = gpsApp.prefEGM96AltitudeCorrection
    private val prefKMLAltitudeMode: Int = gpsApp.prefKMLAltitudeMode
    private val prefGPXVersion: Int = gpsApp.prefGPXVersion

    private var txtFirstTrackpointFlag = true

    private var kmlFile: DocumentFile? = null
    private var gpxFile: DocumentFile? = null
    private var txtFile: DocumentFile? = null

    // Reads and writes location grouped by this number
    private var groupOfLocations: Int = 1500

    private val arrayGeopoints = ArrayBlockingQueue<LocationExtended>(3500)
    private val asyncGeopointsLoader = AsyncGeopointsLoader()

    init {
        exportingTask.numberOfPoints_Processed = 0
        exportingTask.status = ExportingTask.STATUS_RUNNING

        var formats = 0
        if (exportKML) formats++
        if (exportGPX) formats++
        if (exportTXT) formats++
        groupOfLocations = if (formats == 1) 1500 else 1900
        if (exportKML) groupOfLocations -= 200 // KML is a light format, less time to write file
        if (exportTXT) groupOfLocations -= 800 //
        if (exportGPX) groupOfLocations -= 600 // GPX is the heavier format, more time to write the file
    }

    /**
     * Converts a String in a format suitable for GPX/KML files,
     * by replacing the invalid characters with the corresponding HTML sequences.
     */
    private fun stringToXML(str: String?): String {
        if (str == null) return ""
        return str.replace("<", "&lt;")
            .replace("&", "&amp;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    /**
     * Converts a String in a format suitable for the CDATA fields on KML files.
     */
    private fun stringToCDATA(str: String?): String {
        if (str == null) return ""
        return str.replace("[", "(")
            .replace("]", ")")
            .replace("<", "(")
            .replace(">", ")")
    }

    /**
     * Creates the files that will be written by the Exporter. The method tries to assign the
     * name specified as input.
     * @param fName The file name (without path and .extension)
     * @return true if the operation succeeds
     */
    private fun tryToInitFiles(fName: String): Boolean {
        return try {
            val pickedDir: DocumentFile? = if (saveIntoFolder.startsWith("content")) {
                val uri = Uri.parse(saveIntoFolder)
                DocumentFile.fromTreeUri(GPSApplication.getInstance(), uri!!)
            } else {
                DocumentFile.fromFile(java.io.File(saveIntoFolder))
            }

            if (pickedDir == null || !pickedDir.exists()) {
                Log.w("myApp", "[#] Exporter.kt - UNABLE TO CREATE THE FOLDER")
                exportingTask.status = ExportingTask.STATUS_ENDED_FAILED
                return false
            }

            if (exportKML) {
                kmlFile = pickedDir.findFile("$fName.kml")
                if (kmlFile != null && kmlFile!!.exists()) kmlFile!!.delete()
                kmlFile = pickedDir.createFile("", "$fName.kml")
                Log.w("myApp", "[#] Exporter.kt - Export ${kmlFile?.uri}")
            }
            if (exportGPX) {
                gpxFile = pickedDir.findFile("$fName.gpx")
                if (gpxFile != null && gpxFile!!.exists()) gpxFile!!.delete()
                gpxFile = pickedDir.createFile("", "$fName.gpx")
                Log.w("myApp", "[#] Exporter.kt - Export ${gpxFile?.uri}")
            }
            if (exportTXT) {
                txtFile = pickedDir.findFile("$fName.txt")
                if (txtFile != null && txtFile!!.exists()) txtFile!!.delete()
                txtFile = pickedDir.createFile("", "$fName.txt")
                Log.w("myApp", "[#] Exporter.kt - Export ${txtFile?.uri}")
            }
            true
        } catch (e: SecurityException) {
            Log.w("myApp", "[#] Exporter.kt - Unable to write the file: SecurityException")
            exportingTask.status = ExportingTask.STATUS_ENDED_FAILED
            false
        } catch (e: NullPointerException) {
            Log.w("myApp", "[#] Exporter.kt - Unable to write the file: IOException")
            exportingTask.status = ExportingTask.STATUS_ENDED_FAILED
            false
        }
    }

    override fun run() {
        currentThread().priority = Thread.MIN_PRIORITY
        Log.w("myApp", "[#] Exporter.kt - STARTED")

        kmlFile = null
        gpxFile = null
        txtFile = null

        val GPX1_0 = 100
        val GPX1_1 = 110

        val versionName = BuildConfig.VERSION_NAME
        if (gpsApp == null) return

        val elementsTotal = track.numberOfLocations + track.numberOfPlacemarks
        val startTime = System.currentTimeMillis()

        if (track == null) {
            exportingTask.status = ExportingTask.STATUS_ENDED_FAILED
            return
        }
        if (track.numberOfLocations + track.numberOfPlacemarks == 0L) {
            exportingTask.status = ExportingTask.STATUS_ENDED_FAILED
            return
        }

        if (egmAltitudeCorrection && EGM96.getInstance().isLoading) {
            try {
                Log.w("myApp", "[#] Exporter.kt - Wait, EGMGrid is loading")
                do {
                    sleep(200)
                } while (EGM96.getInstance().isLoading)
            } catch (e: InterruptedException) {
                Log.w("myApp", "[#] Exporter.kt - Cannot wait!!")
            }
        }

        val dfdtGPX = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }
        val dfdtGPX_NoMillis = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }
        val dfdtTXT = SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss.SSS", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }
        val dfdtTXT_NoMillis = SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }

        val newLine = "\r\n"

        // If the file is not writable abort exportation:
        val fileWritable = tryToInitFiles(gpsApp.getFileName(track))
        if (!fileWritable) {
            Log.w("myApp", "[#] Exporter.kt - Unable to write the file!!")
            exportingTask.status = ExportingTask.STATUS_ENDED_FAILED
            return
        }

        var kmlBW: BufferedWriter? = null
        var gpxBW: BufferedWriter? = null
        var txtBW: BufferedWriter? = null

        asyncGeopointsLoader.start()

        try {
            if (exportKML) {
                val outputStream = gpsApp.contentResolver.openOutputStream(kmlFile!!.uri, "rw")
                kmlBW = BufferedWriter(OutputStreamWriter(outputStream))
            }
            if (exportGPX) {
                val outputStream = gpsApp.contentResolver.openOutputStream(gpxFile!!.uri, "rw")
                gpxBW = BufferedWriter(OutputStreamWriter(outputStream))
            }
            if (exportTXT) {
                val outputStream = gpsApp.contentResolver.openOutputStream(txtFile!!.uri, "rw")
                txtBW = BufferedWriter(OutputStreamWriter(outputStream))
            }

            val creationTime = Calendar.getInstance().time

            // ---------------------------- Writing Heads
            Log.w("myApp", "[#] Exporter.kt - Writing Heads")

            if (exportKML) {
                kmlBW!!.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + newLine)
                kmlBW.write("<!-- Created with BasicAirData GPS Logger for Android - ver. $versionName -->" + newLine)
                kmlBW.write("<!-- Track ${track.id} = ${track.numberOfLocations} TrackPoints + ${track.numberOfPlacemarks} Placemarks -->" + newLine)
                kmlBW.write("<kml xmlns=\"http://www.opengis.net/kml/2.2\">" + newLine)
                kmlBW.write(" <Document>" + newLine)
                kmlBW.write("  <name>GPS Logger ${track.name}</name>" + newLine)
                kmlBW.write("  <description><![CDATA[" + (if (track.description.isEmpty()) "" else stringToCDATA(track.description) + newLine))
                kmlBW.write("${track.numberOfLocations} Trackpoints + ${track.numberOfPlacemarks} Placemarks]]></description>" + newLine)
                if (track.numberOfLocations > 0) {
                    kmlBW.write("  <Style id=\"TrackStyle\">" + newLine)
                    kmlBW.write("   <LineStyle>" + newLine)
                    kmlBW.write("    <color>ff0000ff</color>" + newLine)
                    kmlBW.write("    <width>3</width>" + newLine)
                    kmlBW.write("   </LineStyle>" + newLine)
                    kmlBW.write("   <PolyStyle>" + newLine)
                    kmlBW.write("    <color>7f0000ff</color>" + newLine)
                    kmlBW.write("   </PolyStyle>" + newLine)
                    kmlBW.write("   <BalloonStyle>" + newLine)
                    kmlBW.write("    <text><![CDATA[<p style=\"color:red;font-weight:bold\">$[name]</p><p style=\"font-size:11px\">$[description]</p><p style=\"font-size:7px\">" +
                            gpsApp.getString(R.string.pref_track_stats) + ": " +
                            gpsApp.getString(R.string.pref_track_stats_totaltime) + " | " +
                            gpsApp.getString(R.string.pref_track_stats_movingtime) + "</p>]]></text>" + newLine)
                    kmlBW.write("   </BalloonStyle>" + newLine)
                    kmlBW.write("  </Style>" + newLine)
                }
                if (track.numberOfPlacemarks > 0) {
                    kmlBW.write("  <Style id=\"PlacemarkStyle\">" + newLine)
                    kmlBW.write("   <IconStyle>" + newLine)
                    kmlBW.write("    <Icon><href>http://maps.google.com/mapfiles/kml/shapes/placemark_circle_highlight.png</href></Icon>" + newLine)
                    kmlBW.write("   </IconStyle>" + newLine)
                    kmlBW.write("  </Style>" + newLine)
                }
                kmlBW.write(newLine)
            }

            if (exportGPX) {
                gpxBW!!.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + newLine)
                gpxBW.write("<!-- Created with BasicAirData GPS Logger for Android - ver. $versionName -->" + newLine)
                gpxBW.write("<!-- Track ${track.id} = ${track.numberOfLocations} TrackPoints + ${track.numberOfPlacemarks} Placemarks -->" + newLine + newLine)

                if (track.numberOfLocations > 0) {
                    gpxBW.write("<!-- Track Statistics (based on Total Time | Time in Movement): -->" + newLine)
                    val phdformatter = PhysicalDataFormatter()
                    val phdDuration = phdformatter.format(track.duration, PhysicalDataFormatter.FORMAT_DURATION)
                    val phdDurationMoving = phdformatter.format(track.durationMoving, PhysicalDataFormatter.FORMAT_DURATION)
                    val phdSpeedMax = phdformatter.format(track.speedMax, PhysicalDataFormatter.FORMAT_SPEED)
                    val phdSpeedAvg = phdformatter.format(track.speedAverage, PhysicalDataFormatter.FORMAT_SPEED_AVG)
                    val phdSpeedAvgMoving = phdformatter.format(track.speedAverageMoving, PhysicalDataFormatter.FORMAT_SPEED_AVG)
                    val phdDistance = phdformatter.format(track.estimatedDistance, PhysicalDataFormatter.FORMAT_DISTANCE)
                    val phdAltitudeGap = phdformatter.format(track.getEstimatedAltitudeGap(gpsApp.prefEGM96AltitudeCorrection), PhysicalDataFormatter.FORMAT_ALTITUDE)
                    val phdOverallDirection = phdformatter.format(track.bearing.toDouble(), PhysicalDataFormatter.FORMAT_BEARING)

                    if (phdDistance.value.isNotEmpty())
                        gpxBW.write("<!--  Distance = ${phdDistance.value} ${phdDistance.um} -->" + newLine)
                    if (phdDuration.value.isNotEmpty())
                        gpxBW.write("<!--  Duration = ${phdDuration.value} | ${phdDurationMoving.value} -->" + newLine)
                    if (phdAltitudeGap.value.isNotEmpty())
                        gpxBW.write("<!--  Altitude Gap = ${phdAltitudeGap.value} ${phdAltitudeGap.um} -->" + newLine)
                    if (phdSpeedMax.value.isNotEmpty())
                        gpxBW.write("<!--  Max Speed = ${phdSpeedMax.value} ${phdSpeedMax.um} -->" + newLine)
                    if (phdSpeedAvg.value.isNotEmpty())
                        gpxBW.write("<!--  Avg Speed = ${phdSpeedAvg.value} | ${phdSpeedAvgMoving.value} ${phdSpeedAvg.um} -->" + newLine)
                    if (phdOverallDirection.value.isNotEmpty())
                        gpxBW.write("<!--  Direction = ${phdOverallDirection.value}${phdOverallDirection.um} -->" + newLine)
                    if (track.estimatedTrackType != GPSApplication.NOT_AVAILABLE)
                        gpxBW.write("<!--  Activity = ${Track.ACTIVITY_DESCRIPTION[track.estimatedTrackType]} -->" + newLine)

                    gpxBW.write("<!--  Altitudes = " +
                            (if (egmAltitudeCorrection) "Corrected using EGM96 grid (bilinear interpolation)" else "Raw") +
                            (if (altitudeManualCorrection == 0.0) "" else (", " + String.format(Locale.US, "%+.3f", altitudeManualCorrection) + "m of manual offset")) +
                            " -->" + newLine)

                    gpxBW.write(newLine)
                }

                if (prefGPXVersion == GPX1_0) {
                    gpxBW.write("<gpx version=\"1.0\"" + newLine
                            + "     creator=\"BasicAirData GPS Logger $versionName\"" + newLine
                            + "     xmlns=\"http://www.topografix.com/GPX/1/0\"" + newLine
                            + "     xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"" + newLine
                            + "     xsi:schemaLocation=\"http://www.topografix.com/GPX/1/0 http://www.topografix.com/GPX/1/0/gpx.xsd\">" + newLine)
                    gpxBW.write("<name>GPS Logger ${track.name}</name>" + newLine)
                    if (track.description.isNotEmpty()) gpxBW.write("<desc>${stringToXML(track.description)}</desc>" + newLine)
                    gpxBW.write("<time>${dfdtGPX_NoMillis.format(creationTime)}</time>" + newLine)
                    if (track.estimatedTrackType != GPSApplication.NOT_AVAILABLE) gpxBW.write("<keywords>${Track.ACTIVITY_DESCRIPTION[track.estimatedTrackType]}</keywords>" + newLine)
                    if (track.validMap != 0
                        && track.latitudeMin != GPSApplication.NOT_AVAILABLE.toDouble()
                        && track.longitudeMin != GPSApplication.NOT_AVAILABLE.toDouble()
                        && track.latitudeMax != GPSApplication.NOT_AVAILABLE.toDouble()
                        && track.longitudeMax != GPSApplication.NOT_AVAILABLE.toDouble()
                    ) {
                        gpxBW.write("<bounds minlat=\"${String.format(Locale.US, "%.8f", track.latitudeMin)}\" minlon=\"${String.format(Locale.US, "%.8f", track.longitudeMin)}\" maxlat=\"${String.format(Locale.US, "%.8f", track.latitudeMax)}\" maxlon=\"${String.format(Locale.US, "%.8f", track.longitudeMax)}\" />" + newLine)
                    }
                    gpxBW.write(newLine)
                }

                if (prefGPXVersion == GPX1_1) {
                    gpxBW.write("<gpx version=\"1.1\"" + newLine
                            + "     creator=\"BasicAirData GPS Logger $versionName\"" + newLine
                            + "     xmlns=\"http://www.topografix.com/GPX/1/1\"" + newLine
                            + "     xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"" + newLine
                            + "     xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd\">" + newLine)
                    gpxBW.write("<metadata> " + newLine)
                    gpxBW.write(" <name>GPS Logger ${track.name}</name>" + newLine)
                    if (track.description.isNotEmpty()) gpxBW.write(" <desc>${stringToXML(track.description)}</desc>" + newLine)
                    gpxBW.write(" <time>${dfdtGPX_NoMillis.format(creationTime)}</time>" + newLine)
                    if (track.estimatedTrackType != GPSApplication.NOT_AVAILABLE) gpxBW.write(" <keywords>${Track.ACTIVITY_DESCRIPTION[track.estimatedTrackType]}</keywords>" + newLine)
                    if (track.validMap != 0
                        && track.latitudeMin != GPSApplication.NOT_AVAILABLE.toDouble()
                        && track.longitudeMin != GPSApplication.NOT_AVAILABLE.toDouble()
                        && track.latitudeMax != GPSApplication.NOT_AVAILABLE.toDouble()
                        && track.longitudeMax != GPSApplication.NOT_AVAILABLE.toDouble()
                    ) {
                        gpxBW.write(" <bounds minlat=\"${String.format(Locale.US, "%.8f", track.latitudeMin)}\" minlon=\"${String.format(Locale.US, "%.8f", track.longitudeMin)}\" maxlat=\"${String.format(Locale.US, "%.8f", track.latitudeMax)}\" maxlon=\"${String.format(Locale.US, "%.8f", track.longitudeMax)}\" />" + newLine)
                    }
                    gpxBW.write("</metadata>" + newLine + newLine)
                }
            }

            if (exportTXT) {
                txtBW!!.write("type,date time,latitude,longitude,accuracy(m),altitude(m),geoid_height(m),speed(m/s),bearing(deg),sat_used,sat_inview,name,desc" + newLine)
            }

            var formattedLatitude = ""
            var formattedLongitude = ""
            var formattedAltitude = ""
            var formattedSpeed = ""

            // ---------------------------- Writing Placemarks
            Log.w("myApp", "[#] Exporter.kt - Writing Placemarks")

            if (track.numberOfPlacemarks > 0) {
                var placemarkId = 1
                val placemarkList = ArrayList<LocationExtended>(groupOfLocations)
                var i = 0
                while (i <= track.numberOfPlacemarks) {
                    placemarkList.addAll(gpsApp.gpsDataBase.getPlacemarksList(track.id, i.toLong(), (i + groupOfLocations - 1).toLong()))

                    if (placemarkList.isNotEmpty()) {
                        for (loc in placemarkList) {
                            formattedLatitude = String.format(Locale.US, "%.8f", loc.location.latitude)
                            formattedLongitude = String.format(Locale.US, "%.8f", loc.location.longitude)
                            if (loc.location.hasAltitude()) formattedAltitude = String.format(
                                Locale.US,
                                "%.3f",
                                loc.location.altitude + altitudeManualCorrection - (
                                    if ((loc.getAltitudeEGM96Correction() == GPSApplication.NOT_AVAILABLE.toDouble()) || (!egmAltitudeCorrection)) 0.0
                                    else loc.getAltitudeEGM96Correction()
                                )
                            )
                            if (exportGPX || exportTXT) {
                                if (loc.location.hasSpeed()) formattedSpeed = String.format(Locale.US, "%.3f", loc.location.speed)
                            }

                            if (exportKML) {
                                kmlBW!!.write("  <Placemark id=\"$placemarkId\">" + newLine)
                                kmlBW.write("   <name>")
                                kmlBW.write(stringToXML(loc.description))
                                kmlBW.write("</name>" + newLine)
                                kmlBW.write("   <styleUrl>#PlacemarkStyle</styleUrl>" + newLine)
                                kmlBW.write("   <Point>" + newLine)
                                kmlBW.write("    <altitudeMode>" + (if (prefKMLAltitudeMode == 1) "clampToGround" else "absolute") + "</altitudeMode>" + newLine)
                                kmlBW.write("    <coordinates>")
                                if (loc.location.hasAltitude()) {
                                    kmlBW.write("$formattedLongitude,$formattedLatitude,$formattedAltitude")
                                } else {
                                    kmlBW.write("$formattedLongitude,$formattedLatitude,0")
                                }
                                kmlBW.write("</coordinates>" + newLine)
                                kmlBW.write("    <extrude>1</extrude>" + newLine)
                                kmlBW.write("   </Point>" + newLine)
                                kmlBW.write("  </Placemark>" + newLine + newLine)
                            }

                            if (exportGPX) {
                                gpxBW!!.write("<wpt lat=\"$formattedLatitude\" lon=\"$formattedLongitude\">")
                                if (loc.location.hasAltitude()) {
                                    gpxBW.write("<ele>")
                                    gpxBW.write(formattedAltitude)
                                    gpxBW.write("</ele>")
                                }
                                gpxBW.write("<time>")
                                gpxBW.write(if ((loc.location.time % 1000L) == 0L) dfdtGPX_NoMillis.format(loc.location.time) else dfdtGPX.format(loc.location.time))
                                gpxBW.write("</time>")
                                gpxBW.write("<name>")
                                gpxBW.write(stringToXML(loc.description))
                                gpxBW.write("</name>")
                                if (loc.getNumberOfSatellitesUsedInFix() > 0) {
                                    gpxBW.write("<sat>")
                                    gpxBW.write(loc.getNumberOfSatellitesUsedInFix().toString())
                                    gpxBW.write("</sat>")
                                }
                                gpxBW.write("</wpt>" + newLine + newLine)
                            }

                            if (exportTXT) {
                                txtBW!!.write("W," + (if ((loc.location.time % 1000L) == 0L) dfdtTXT_NoMillis.format(loc.location.time) else dfdtTXT.format(loc.location.time)) + "," +
                                        "$formattedLatitude,$formattedLongitude,")
                                if (loc.location.hasAccuracy()) txtBW.write(String.format(Locale.US, "%.2f", loc.location.accuracy))
                                txtBW.write(",")
                                if (loc.location.hasAltitude()) txtBW.write(formattedAltitude)
                                txtBW.write(",")
                                run {
                                    val egmCorr = loc.getAltitudeEGM96Correction()
                                    if ((egmCorr != GPSApplication.NOT_AVAILABLE.toDouble()) && egmAltitudeCorrection) {
                                        txtBW.write(String.format(Locale.US, "%.3f", egmCorr))
                                    }
                                }
                                txtBW.write(",")
                                if (loc.location.hasSpeed()) txtBW.write(formattedSpeed)
                                txtBW.write(",")
                                if (loc.location.hasBearing()) txtBW.write(String.format(Locale.US, "%.0f", loc.location.bearing))
                                txtBW.write(",")
                                if (loc.getNumberOfSatellitesUsedInFix() > 0) txtBW.write(loc.getNumberOfSatellitesUsedInFix().toString())
                                txtBW.write(",")
                                if (loc.getNumberOfSatellites() > 0) txtBW.write(loc.getNumberOfSatellites().toString())
                                txtBW.write(",")
                                txtBW.write(",") // Name is an empty field
                                txtBW.write(loc.description.replace(",", "_"))
                                txtBW.write(newLine)
                            }

                            placemarkId++
                            exportingTask.numberOfPoints_Processed = exportingTask.numberOfPoints_Processed + 1
                        }
                        placemarkList.clear()
                    }
                    i += groupOfLocations
                }
                exportingTask.numberOfPoints_Processed = track.numberOfPlacemarks
            }

            // ---------------------------- Writing Track
            Log.w("myApp", "[#] Exporter.kt - Writing Trackpoints")
            if (track.numberOfLocations > 0) {
                if (exportKML) {
                    val phdformatter = PhysicalDataFormatter()
                    val phdDuration = phdformatter.format(track.duration, PhysicalDataFormatter.FORMAT_DURATION)
                    val phdDurationMoving = phdformatter.format(track.durationMoving, PhysicalDataFormatter.FORMAT_DURATION)
                    val phdSpeedMax = phdformatter.format(track.speedMax, PhysicalDataFormatter.FORMAT_SPEED)
                    val phdSpeedAvg = phdformatter.format(track.speedAverage, PhysicalDataFormatter.FORMAT_SPEED_AVG)
                    val phdSpeedAvgMoving = phdformatter.format(track.speedAverageMoving, PhysicalDataFormatter.FORMAT_SPEED_AVG)
                    val phdDistance = phdformatter.format(track.estimatedDistance, PhysicalDataFormatter.FORMAT_DISTANCE)
                    val phdAltitudeGap = phdformatter.format(track.getEstimatedAltitudeGap(gpsApp.prefEGM96AltitudeCorrection), PhysicalDataFormatter.FORMAT_ALTITUDE)
                    val phdOverallDirection = phdformatter.format(track.bearing.toDouble(), PhysicalDataFormatter.FORMAT_BEARING)

                    val trackDesc = (if (track.description.isEmpty()) "" else "<b>" + stringToCDATA(track.description) + "</b><br><br>") +
                            gpsApp.getString(R.string.distance) + " = ${phdDistance.value} ${phdDistance.um}" +
                            "<br>" + gpsApp.getString(R.string.duration) + " = ${phdDuration.value} | ${phdDurationMoving.value}" +
                            "<br>" + gpsApp.getString(R.string.altitude_gap) + " = ${phdAltitudeGap.value} ${phdAltitudeGap.um}" +
                            "<br>" + gpsApp.getString(R.string.max_speed) + " = ${phdSpeedMax.value} ${phdSpeedMax.um}" +
                            "<br>" + gpsApp.getString(R.string.average_speed) + " = ${phdSpeedAvg.value} | ${phdSpeedAvgMoving.value} ${phdSpeedAvg.um}" +
                            "<br>" + gpsApp.getString(R.string.direction) + " = ${phdOverallDirection.value} ${phdOverallDirection.um}" +
                            "<br><br><i>${track.numberOfLocations} ${gpsApp.getString(R.string.trackpoints)}</i>"

                    kmlBW!!.write("  <Placemark id=\"${track.name}\">" + newLine)
                    kmlBW.write("   <name>" + gpsApp.getString(R.string.tab_track) + " " + track.name + "</name>" + newLine)
                    kmlBW.write("   <description><![CDATA[" + trackDesc + "]]></description>" + newLine)
                    kmlBW.write("   <styleUrl>#TrackStyle</styleUrl>" + newLine)
                    kmlBW.write("   <LineString>" + newLine)
                    kmlBW.write("    <extrude>0</extrude>" + newLine)
                    kmlBW.write("    <tessellate>0</tessellate>" + newLine)
                    kmlBW.write("    <altitudeMode>" + (if (prefKMLAltitudeMode == 1) "clampToGround" else "absolute") + "</altitudeMode>" + newLine)
                    kmlBW.write("    <coordinates>" + newLine)
                }
                if (exportGPX) {
                    gpxBW!!.write("<trk>" + newLine)
                    gpxBW.write(" <name>" + gpsApp.getString(R.string.tab_track) + " " + track.name + "</name>" + newLine)
                    gpxBW.write(" <trkseg>" + newLine)
                }

                var i = 0
                while (i < track.numberOfLocations) {
                    val loc = arrayGeopoints.take()

                    formattedLatitude = String.format(Locale.US, "%.8f", loc.location.latitude)
                    formattedLongitude = String.format(Locale.US, "%.8f", loc.location.longitude)
                    if (loc.location.hasAltitude()) formattedAltitude = String.format(
                        Locale.US,
                        "%.3f",
                        loc.location.altitude + altitudeManualCorrection - (
                            if ((loc.getAltitudeEGM96Correction() == GPSApplication.NOT_AVAILABLE.toDouble()) || (!egmAltitudeCorrection)) 0.0
                            else loc.getAltitudeEGM96Correction()
                        )
                    )
                    if (exportGPX || exportTXT) {
                        if (loc.location.hasSpeed()) formattedSpeed = String.format(Locale.US, "%.3f", loc.location.speed)
                    }

                    if (exportKML) {
                        if (loc.location.hasAltitude()) kmlBW!!.write("     $formattedLongitude,$formattedLatitude,$formattedAltitude" + newLine)
                        else kmlBW!!.write("     $formattedLongitude,$formattedLatitude,0" + newLine)
                    }

                    if (exportGPX) {
                        gpxBW!!.write("  <trkpt lat=\"$formattedLatitude\" lon=\"$formattedLongitude\">")
                        if (loc.location.hasAltitude()) {
                            gpxBW.write("<ele>")
                            gpxBW.write(formattedAltitude)
                            gpxBW.write("</ele>")
                        }
                        gpxBW.write("<time>")
                        gpxBW.write(if ((loc.location.time % 1000L) == 0L) dfdtGPX_NoMillis.format(loc.location.time) else dfdtGPX.format(loc.location.time))
                        gpxBW.write("</time>")
                        if (prefGPXVersion == GPX1_0) {
                            if (loc.location.hasSpeed()) {
                                gpxBW.write("<speed>")
                                gpxBW.write(formattedSpeed)
                                gpxBW.write("</speed>")
                            }
                        }
                        if (loc.getNumberOfSatellitesUsedInFix() > 0) {
                            gpxBW.write("<sat>")
                            gpxBW.write(loc.getNumberOfSatellitesUsedInFix().toString())
                            gpxBW.write("</sat>")
                        }
                        gpxBW.write("</trkpt>" + newLine)
                    }

                    if (exportTXT) {
                        txtBW!!.write("T," + (if ((loc.location.time % 1000L) == 0L) dfdtTXT_NoMillis.format(loc.location.time) else dfdtTXT.format(loc.location.time)) + "," + "$formattedLatitude,$formattedLongitude,")
                        if (loc.location.hasAccuracy()) txtBW.write(String.format(Locale.US, "%.2f", loc.location.accuracy))
                        txtBW.write(",")
                        if (loc.location.hasAltitude()) txtBW.write(formattedAltitude)
                        txtBW.write(",")
                        run {
                            val egmCorr = loc.getAltitudeEGM96Correction()
                            if ((egmCorr != GPSApplication.NOT_AVAILABLE.toDouble()) && egmAltitudeCorrection) {
                                txtBW.write(String.format(Locale.US, "%.3f", egmCorr))
                            }
                        }
                        txtBW.write(",")
                        if (loc.location.hasSpeed()) txtBW.write(formattedSpeed)
                        txtBW.write(",")
                        if (loc.location.hasBearing()) txtBW.write(String.format(Locale.US, "%.0f", loc.location.bearing))
                        txtBW.write(",")
                        if (loc.getNumberOfSatellitesUsedInFix() > 0) txtBW.write(loc.getNumberOfSatellitesUsedInFix().toString())
                        txtBW.write(",")
                        if (loc.getNumberOfSatellites() > 0) txtBW.write(loc.getNumberOfSatellites().toString())
                        txtBW.write(",")
                        if (txtFirstTrackpointFlag) {
                            if (track.description.isEmpty()) txtBW.write(track.name + ",GPS Logger: " + track.name)
                            else txtBW.write(track.name + ",GPS Logger: " + track.name + " - " + track.description.replace(",", "_"))
                            txtFirstTrackpointFlag = false
                        } else txtBW.write(",")
                        txtBW.write(newLine)
                    }

                    exportingTask.numberOfPoints_Processed = exportingTask.numberOfPoints_Processed + 1
                    i++
                }

                exportingTask.numberOfPoints_Processed = track.numberOfPlacemarks + track.numberOfLocations
                arrayGeopoints.clear()

                if (exportKML) {
                    kmlBW!!.write("    </coordinates>" + newLine)
                    kmlBW.write("   </LineString>" + newLine)
                    kmlBW.write("  </Placemark>" + newLine + newLine)
                }
                if (exportGPX) {
                    gpxBW!!.write(" </trkseg>" + newLine)
                    gpxBW.write("</trk>" + newLine + newLine)
                }
            }

            // ---------------------------- Writing tails and close
            Log.w("myApp", "[#] Exporter.kt - Writing Tails and close files")
            if (exportKML) {
                kmlBW!!.write(" </Document>" + newLine)
                kmlBW.write("</kml>" + newLine + " ")
                kmlBW.flush()
                kmlBW.close()
            }
            if (exportGPX) {
                gpxBW!!.write("</gpx>" + newLine + " ")
                gpxBW.flush()
                gpxBW.close()
            }
            if (exportTXT) {
                txtBW!!.flush()
                txtBW.close()
            }

            Log.w(
                "myApp",
                "[#] Exporter.kt - Track ${track.id} exported in ${System.currentTimeMillis() - startTime} ms ($elementsTotal pts @ ${(1000L * elementsTotal) / (System.currentTimeMillis() - startTime)} pts/s)"
            )
            exportingTask.status = ExportingTask.STATUS_ENDED_SUCCESS
        } catch (e: IOException) {
            exportingTask.status = ExportingTask.STATUS_ENDED_FAILED
            asyncGeopointsLoader.interrupt()
            Log.w("myApp", "[#] Exporter.kt - Unable to write the file: $e")
        } catch (e: InterruptedException) {
            exportingTask.status = ExportingTask.STATUS_ENDED_FAILED
            asyncGeopointsLoader.interrupt()
            Log.w("myApp", "[#] Exporter.kt - Interrupted: $e")
        }
    }

    /**
     * This Thread feeds the arrayGeopoints list with the GeoPoints by querying small blocks of
     * points from the DB and keeping the list as full as possible.
     * The Database reading and the file writing are decoupled to optimise performance.
     */
    private inner class AsyncGeopointsLoader : Thread() {
        override fun run() {
            currentThread().priority = Thread.MIN_PRIORITY
            val lList = ArrayList<LocationExtended>(groupOfLocations)
            var i = 0
            while (i <= track.numberOfLocations) {
                lList.addAll(GPSApplication.getInstance().gpsDataBase.getLocationsList(track.id, i.toLong(), (i + groupOfLocations - 1).toLong()))
                if (lList.isNotEmpty()) {
                    for (loc in lList) {
                        try {
                            arrayGeopoints.put(loc)
                        } catch (e: InterruptedException) {
                            Log.w("myApp", "[#] Exporter.kt - Interrupted: $e")
                        }
                    }
                    lList.clear()
                }
                i += groupOfLocations
            }
        }
    }
}


