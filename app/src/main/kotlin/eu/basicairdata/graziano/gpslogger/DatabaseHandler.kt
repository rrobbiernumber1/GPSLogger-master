package eu.basicairdata.graziano.gpslogger

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.location.Location
import android.util.Log

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Java 동작 및 API를 그대로 유지한 Kotlin 버전
 */
class DatabaseHandler(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_VERSION = 3

        private const val LOCATION_TYPE_LOCATION = 1
        private const val LOCATION_TYPE_PLACEMARK = 2

        private const val DATABASE_NAME = "GPSLogger"

        private const val TABLE_LOCATIONS = "locations"
        private const val TABLE_TRACKS = "tracks"
        private const val TABLE_PLACEMARKS = "placemarks"

        private const val KEY_ID = "id"
        private const val KEY_TRACK_ID = "track_id"

        private const val KEY_LOCATION_NUMBER = "nr"
        private const val KEY_LOCATION_LATITUDE = "latitude"
        private const val KEY_LOCATION_LONGITUDE = "longitude"
        private const val KEY_LOCATION_ALTITUDE = "altitude"
        private const val KEY_LOCATION_SPEED = "speed"
        private const val KEY_LOCATION_ACCURACY = "accuracy"
        private const val KEY_LOCATION_BEARING = "bearing"
        private const val KEY_LOCATION_TIME = "time"
        private const val KEY_LOCATION_NUMBEROFSATELLITES = "number_of_satellites"
        private const val KEY_LOCATION_TYPE = "type"
        private const val KEY_LOCATION_NUMBEROFSATELLITESUSEDINFIX = "number_of_satellites_used_in_fix"

        private const val KEY_LOCATION_NAME = "name"

        private const val KEY_TRACK_NAME = "name"
        private const val KEY_TRACK_FROM = "location_from"
        private const val KEY_TRACK_TO = "location_to"

        private const val KEY_TRACK_START_LATITUDE = "start_latitude"
        private const val KEY_TRACK_START_LONGITUDE = "start_longitude"
        private const val KEY_TRACK_START_ALTITUDE = "start_altitude"
        private const val KEY_TRACK_START_ACCURACY = "start_accuracy"
        private const val KEY_TRACK_START_SPEED = "start_speed"
        private const val KEY_TRACK_START_TIME = "start_time"

        private const val KEY_TRACK_LASTFIX_TIME = "lastfix_time"

        private const val KEY_TRACK_END_LATITUDE = "end_latitude"
        private const val KEY_TRACK_END_LONGITUDE = "end_longitude"
        private const val KEY_TRACK_END_ALTITUDE = "end_altitude"
        private const val KEY_TRACK_END_ACCURACY = "end_accuracy"
        private const val KEY_TRACK_END_SPEED = "end_speed"
        private const val KEY_TRACK_END_TIME = "end_time"

        private const val KEY_TRACK_LASTSTEPDST_LATITUDE = "laststepdst_latitude"
        private const val KEY_TRACK_LASTSTEPDST_LONGITUDE = "laststepdst_longitude"
        private const val KEY_TRACK_LASTSTEPDST_ACCURACY = "laststepdst_accuracy"

        private const val KEY_TRACK_LASTSTEPALT_ALTITUDE = "laststepalt_altitude"
        private const val KEY_TRACK_LASTSTEPALT_ACCURACY = "laststepalt_accuracy"

        private const val KEY_TRACK_MIN_LATITUDE = "min_latitude"
        private const val KEY_TRACK_MIN_LONGITUDE = "min_longitude"
        private const val KEY_TRACK_MAX_LATITUDE = "max_latitude"
        private const val KEY_TRACK_MAX_LONGITUDE = "max_longitude"

        private const val KEY_TRACK_DURATION = "duration"
        private const val KEY_TRACK_DURATION_MOVING = "duration_moving"
        private const val KEY_TRACK_DISTANCE = "distance"
        private const val KEY_TRACK_DISTANCE_INPROGRESS = "distance_in_progress"
        private const val KEY_TRACK_DISTANCE_LASTALTITUDE = "distance_last_altitude"
        private const val KEY_TRACK_ALTITUDE_UP = "altitude_up"
        private const val KEY_TRACK_ALTITUDE_DOWN = "altitude_down"
        private const val KEY_TRACK_ALTITUDE_INPROGRESS = "altitude_in_progress"
        private const val KEY_TRACK_SPEED_MAX = "speed_max"
        private const val KEY_TRACK_SPEED_AVERAGE = "speed_average"
        private const val KEY_TRACK_SPEED_AVERAGEMOVING = "speed_average_moving"
        private const val KEY_TRACK_NUMBEROFLOCATIONS = "number_of_locations"
        private const val KEY_TRACK_NUMBEROFPLACEMARKS = "number_of_placemarks"
        private const val KEY_TRACK_TYPE = "type"
        private const val KEY_TRACK_VALIDMAP = "validmap"
        private const val KEY_TRACK_DESCRIPTION = "description"

        private val DATABASE_ALTER_TABLE_LOCATIONS_TO_V2 = "ALTER TABLE $TABLE_LOCATIONS ADD COLUMN $KEY_LOCATION_NUMBEROFSATELLITESUSEDINFIX INTEGER DEFAULT ${GPSApplication.NOT_AVAILABLE};"
        private val DATABASE_ALTER_TABLE_PLACEMARKS_TO_V2 = "ALTER TABLE $TABLE_PLACEMARKS ADD COLUMN $KEY_LOCATION_NUMBEROFSATELLITESUSEDINFIX INTEGER DEFAULT ${GPSApplication.NOT_AVAILABLE};"
        private const val DATABASE_ALTER_TABLE_TRACKS_TO_V3 = "ALTER TABLE $TABLE_TRACKS ADD COLUMN $KEY_TRACK_DESCRIPTION TEXT DEFAULT \"\";"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val CREATE_TRACKS_TABLE = "CREATE TABLE $TABLE_TRACKS(" +
            "$KEY_ID INTEGER PRIMARY KEY AUTOINCREMENT," +
            "$KEY_TRACK_NAME TEXT," +
            "$KEY_TRACK_FROM TEXT," +
            "$KEY_TRACK_TO TEXT," +
            "$KEY_TRACK_START_LATITUDE REAL," +
            "$KEY_TRACK_START_LONGITUDE REAL," +
            "$KEY_TRACK_START_ALTITUDE REAL," +
            "$KEY_TRACK_START_ACCURACY REAL," +
            "$KEY_TRACK_START_SPEED REAL," +
            "$KEY_TRACK_START_TIME REAL," +
            "$KEY_TRACK_LASTFIX_TIME REAL," +
            "$KEY_TRACK_END_LATITUDE REAL," +
            "$KEY_TRACK_END_LONGITUDE REAL," +
            "$KEY_TRACK_END_ALTITUDE REAL," +
            "$KEY_TRACK_END_ACCURACY REAL," +
            "$KEY_TRACK_END_SPEED REAL," +
            "$KEY_TRACK_END_TIME REAL," +
            "$KEY_TRACK_LASTSTEPDST_LATITUDE REAL," +
            "$KEY_TRACK_LASTSTEPDST_LONGITUDE REAL," +
            "$KEY_TRACK_LASTSTEPDST_ACCURACY REAL," +
            "$KEY_TRACK_LASTSTEPALT_ALTITUDE REAL," +
            "$KEY_TRACK_LASTSTEPALT_ACCURACY REAL," +
            "$KEY_TRACK_MIN_LATITUDE REAL," +
            "$KEY_TRACK_MIN_LONGITUDE REAL," +
            "$KEY_TRACK_MAX_LATITUDE REAL," +
            "$KEY_TRACK_MAX_LONGITUDE REAL," +
            "$KEY_TRACK_DURATION REAL," +
            "$KEY_TRACK_DURATION_MOVING REAL," +
            "$KEY_TRACK_DISTANCE REAL," +
            "$KEY_TRACK_DISTANCE_INPROGRESS REAL," +
            "$KEY_TRACK_DISTANCE_LASTALTITUDE REAL," +
            "$KEY_TRACK_ALTITUDE_UP REAL," +
            "$KEY_TRACK_ALTITUDE_DOWN REAL," +
            "$KEY_TRACK_ALTITUDE_INPROGRESS REAL," +
            "$KEY_TRACK_SPEED_MAX REAL," +
            "$KEY_TRACK_SPEED_AVERAGE REAL," +
            "$KEY_TRACK_SPEED_AVERAGEMOVING REAL," +
            "$KEY_TRACK_NUMBEROFLOCATIONS INTEGER," +
            "$KEY_TRACK_NUMBEROFPLACEMARKS INTEGER," +
            "$KEY_TRACK_VALIDMAP INTEGER," +
            "$KEY_TRACK_TYPE INTEGER," +
            "$KEY_TRACK_DESCRIPTION TEXT)"
        db.execSQL(CREATE_TRACKS_TABLE)

        val CREATE_LOCATIONS_TABLE = "CREATE TABLE $TABLE_LOCATIONS(" +
            "$KEY_ID INTEGER PRIMARY KEY AUTOINCREMENT," +
            "$KEY_TRACK_ID INTEGER," +
            "$KEY_LOCATION_NUMBER INTEGER," +
            "$KEY_LOCATION_LATITUDE REAL," +
            "$KEY_LOCATION_LONGITUDE REAL," +
            "$KEY_LOCATION_ALTITUDE REAL," +
            "$KEY_LOCATION_SPEED REAL," +
            "$KEY_LOCATION_ACCURACY REAL," +
            "$KEY_LOCATION_BEARING REAL," +
            "$KEY_LOCATION_TIME REAL," +
            "$KEY_LOCATION_NUMBEROFSATELLITES INTEGER," +
            "$KEY_LOCATION_TYPE INTEGER," +
            "$KEY_LOCATION_NUMBEROFSATELLITESUSEDINFIX INTEGER)"
        db.execSQL(CREATE_LOCATIONS_TABLE)

        val CREATE_PLACEMARKS_TABLE = "CREATE TABLE $TABLE_PLACEMARKS(" +
            "$KEY_ID INTEGER PRIMARY KEY AUTOINCREMENT," +
            "$KEY_TRACK_ID INTEGER," +
            "$KEY_LOCATION_NUMBER INTEGER," +
            "$KEY_LOCATION_LATITUDE REAL," +
            "$KEY_LOCATION_LONGITUDE REAL," +
            "$KEY_LOCATION_ALTITUDE REAL," +
            "$KEY_LOCATION_SPEED REAL," +
            "$KEY_LOCATION_ACCURACY REAL," +
            "$KEY_LOCATION_BEARING REAL," +
            "$KEY_LOCATION_TIME REAL," +
            "$KEY_LOCATION_NUMBEROFSATELLITES INTEGER," +
            "$KEY_LOCATION_TYPE INTEGER," +
            "$KEY_LOCATION_NAME TEXT," +
            "$KEY_LOCATION_NUMBEROFSATELLITESUSEDINFIX INTEGER)"
        db.execSQL(CREATE_PLACEMARKS_TABLE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        when (oldVersion) {
            1 -> {
                db.execSQL(DATABASE_ALTER_TABLE_LOCATIONS_TO_V2)
                db.execSQL(DATABASE_ALTER_TABLE_PLACEMARKS_TO_V2)
            }
            2 -> {
                db.execSQL(DATABASE_ALTER_TABLE_TRACKS_TO_V3)
            }
        }
    }

    fun updateTrack(track: Track) {
        val db = writableDatabase
        val trkvalues = ContentValues().apply {
            put(KEY_TRACK_NAME, track.name)
            put(KEY_TRACK_FROM, "")
            put(KEY_TRACK_TO, "")
            put(KEY_TRACK_START_LATITUDE, track.latitudeStart)
            put(KEY_TRACK_START_LONGITUDE, track.longitudeStart)
            put(KEY_TRACK_START_ALTITUDE, track.altitudeStart)
            put(KEY_TRACK_START_ACCURACY, track.accuracyStart)
            put(KEY_TRACK_START_SPEED, track.speedStart)
            put(KEY_TRACK_START_TIME, track.timeStart)
            put(KEY_TRACK_LASTFIX_TIME, track.timeLastFix)
            put(KEY_TRACK_END_LATITUDE, track.latitudeEnd)
            put(KEY_TRACK_END_LONGITUDE, track.longitudeEnd)
            put(KEY_TRACK_END_ALTITUDE, track.altitudeEnd)
            put(KEY_TRACK_END_ACCURACY, track.accuracyEnd)
            put(KEY_TRACK_END_SPEED, track.speedEnd)
            put(KEY_TRACK_END_TIME, track.timeEnd)
            put(KEY_TRACK_LASTSTEPDST_LATITUDE, track.latitudeLastStepDistance)
            put(KEY_TRACK_LASTSTEPDST_LONGITUDE, track.longitudeLastStepDistance)
            put(KEY_TRACK_LASTSTEPDST_ACCURACY, track.accuracyLastStepDistance)
            put(KEY_TRACK_LASTSTEPALT_ALTITUDE, track.altitudeLastStepAltitude)
            put(KEY_TRACK_LASTSTEPALT_ACCURACY, track.accuracyLastStepAltitude)
            put(KEY_TRACK_MIN_LATITUDE, track.latitudeMin)
            put(KEY_TRACK_MIN_LONGITUDE, track.longitudeMin)
            put(KEY_TRACK_MAX_LATITUDE, track.latitudeMax)
            put(KEY_TRACK_MAX_LONGITUDE, track.longitudeMax)
            put(KEY_TRACK_DURATION, track.duration)
            put(KEY_TRACK_DURATION_MOVING, track.durationMoving)
            put(KEY_TRACK_DISTANCE, track.distance)
            put(KEY_TRACK_DISTANCE_INPROGRESS, track.distanceInProgress)
            put(KEY_TRACK_DISTANCE_LASTALTITUDE, track.distanceLastAltitude)
            put(KEY_TRACK_ALTITUDE_UP, track.altitudeUp)
            put(KEY_TRACK_ALTITUDE_DOWN, track.altitudeDown)
            put(KEY_TRACK_ALTITUDE_INPROGRESS, track.altitudeInProgress)
            put(KEY_TRACK_SPEED_MAX, track.speedMax)
            put(KEY_TRACK_SPEED_AVERAGE, track.speedAverage)
            put(KEY_TRACK_SPEED_AVERAGEMOVING, track.speedAverageMoving)
            put(KEY_TRACK_NUMBEROFLOCATIONS, track.numberOfLocations)
            put(KEY_TRACK_NUMBEROFPLACEMARKS, track.numberOfPlacemarks)
            put(KEY_TRACK_TYPE, track.type)
            put(KEY_TRACK_VALIDMAP, track.validMap)
            put(KEY_TRACK_DESCRIPTION, track.description)
        }
        try {
            db.beginTransaction()
            db.update(TABLE_TRACKS, trkvalues, "$KEY_ID = ?", arrayOf(track.id.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun addLocationToTrack(location: LocationExtended, track: Track) {
        val db = writableDatabase
        val loc = location.location
        val locvalues = ContentValues().apply {
            put(KEY_TRACK_ID, track.id)
            put(KEY_LOCATION_NUMBER, track.numberOfLocations)
            put(KEY_LOCATION_LATITUDE, loc.latitude)
            put(KEY_LOCATION_LONGITUDE, loc.longitude)
            put(KEY_LOCATION_ALTITUDE, if (loc.hasAltitude()) loc.altitude else GPSApplication.NOT_AVAILABLE.toDouble())
            put(KEY_LOCATION_SPEED, if (loc.hasSpeed()) loc.speed else GPSApplication.NOT_AVAILABLE.toFloat())
            put(KEY_LOCATION_ACCURACY, if (loc.hasAccuracy()) loc.accuracy else GPSApplication.NOT_AVAILABLE.toFloat())
            put(KEY_LOCATION_BEARING, if (loc.hasBearing()) loc.bearing else GPSApplication.NOT_AVAILABLE.toFloat())
            put(KEY_LOCATION_TIME, loc.time)
            put(KEY_LOCATION_NUMBEROFSATELLITES, location.numberOfSatellites)
            put(KEY_LOCATION_TYPE, LOCATION_TYPE_LOCATION)
            put(KEY_LOCATION_NUMBEROFSATELLITESUSEDINFIX, location.numberOfSatellitesUsedInFix)
        }

        val trkvalues = buildTrackContentValues(track)

        try {
            db.beginTransaction()
            db.insert(TABLE_LOCATIONS, null, locvalues)
            db.update(TABLE_TRACKS, trkvalues, "$KEY_ID = ?", arrayOf(track.id.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun addPlacemarkToTrack(placemark: LocationExtended, track: Track) {
        val db = writableDatabase
        val loc = placemark.location
        val locvalues = ContentValues().apply {
            put(KEY_TRACK_ID, track.id)
            put(KEY_LOCATION_NUMBER, track.numberOfPlacemarks)
            put(KEY_LOCATION_LATITUDE, loc.latitude)
            put(KEY_LOCATION_LONGITUDE, loc.longitude)
            put(KEY_LOCATION_ALTITUDE, if (loc.hasAltitude()) loc.altitude else GPSApplication.NOT_AVAILABLE.toDouble())
            put(KEY_LOCATION_SPEED, if (loc.hasSpeed()) loc.speed else GPSApplication.NOT_AVAILABLE.toFloat())
            put(KEY_LOCATION_ACCURACY, if (loc.hasAccuracy()) loc.accuracy else GPSApplication.NOT_AVAILABLE.toFloat())
            put(KEY_LOCATION_BEARING, if (loc.hasBearing()) loc.bearing else GPSApplication.NOT_AVAILABLE.toFloat())
            put(KEY_LOCATION_TIME, loc.time)
            put(KEY_LOCATION_NUMBEROFSATELLITES, placemark.numberOfSatellites)
            put(KEY_LOCATION_TYPE, LOCATION_TYPE_PLACEMARK)
            put(KEY_LOCATION_NAME, placemark.description)
            put(KEY_LOCATION_NUMBEROFSATELLITESUSEDINFIX, placemark.numberOfSatellitesUsedInFix)
        }

        val trkvalues = buildTrackContentValues(track)

        try {
            db.beginTransaction()
            db.insert(TABLE_PLACEMARKS, null, locvalues)
            db.update(TABLE_TRACKS, trkvalues, "$KEY_ID = ?", arrayOf(track.id.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun buildTrackContentValues(track: Track): ContentValues = ContentValues().apply {
        put(KEY_TRACK_NAME, track.name)
        put(KEY_TRACK_FROM, "")
        put(KEY_TRACK_TO, "")
        put(KEY_TRACK_START_LATITUDE, track.latitudeStart)
        put(KEY_TRACK_START_LONGITUDE, track.longitudeStart)
        put(KEY_TRACK_START_ALTITUDE, track.altitudeStart)
        put(KEY_TRACK_START_ACCURACY, track.accuracyStart)
        put(KEY_TRACK_START_SPEED, track.speedStart)
        put(KEY_TRACK_START_TIME, track.timeStart)
        put(KEY_TRACK_LASTFIX_TIME, track.timeLastFix)
        put(KEY_TRACK_END_LATITUDE, track.latitudeEnd)
        put(KEY_TRACK_END_LONGITUDE, track.longitudeEnd)
        put(KEY_TRACK_END_ALTITUDE, track.altitudeEnd)
        put(KEY_TRACK_END_ACCURACY, track.accuracyEnd)
        put(KEY_TRACK_END_SPEED, track.speedEnd)
        put(KEY_TRACK_END_TIME, track.timeEnd)
        put(KEY_TRACK_LASTSTEPDST_LATITUDE, track.latitudeLastStepDistance)
        put(KEY_TRACK_LASTSTEPDST_LONGITUDE, track.longitudeLastStepDistance)
        put(KEY_TRACK_LASTSTEPDST_ACCURACY, track.accuracyLastStepDistance)
        put(KEY_TRACK_LASTSTEPALT_ALTITUDE, track.altitudeLastStepAltitude)
        put(KEY_TRACK_LASTSTEPALT_ACCURACY, track.accuracyLastStepAltitude)
        put(KEY_TRACK_MIN_LATITUDE, track.latitudeMin)
        put(KEY_TRACK_MIN_LONGITUDE, track.longitudeMin)
        put(KEY_TRACK_MAX_LATITUDE, track.latitudeMax)
        put(KEY_TRACK_MAX_LONGITUDE, track.longitudeMax)
        put(KEY_TRACK_DURATION, track.duration)
        put(KEY_TRACK_DURATION_MOVING, track.durationMoving)
        put(KEY_TRACK_DISTANCE, track.distance)
        put(KEY_TRACK_DISTANCE_INPROGRESS, track.distanceInProgress)
        put(KEY_TRACK_DISTANCE_LASTALTITUDE, track.distanceLastAltitude)
        put(KEY_TRACK_ALTITUDE_UP, track.altitudeUp)
        put(KEY_TRACK_ALTITUDE_DOWN, track.altitudeDown)
        put(KEY_TRACK_ALTITUDE_INPROGRESS, track.altitudeInProgress)
        put(KEY_TRACK_SPEED_MAX, track.speedMax)
        put(KEY_TRACK_SPEED_AVERAGE, track.speedAverage)
        put(KEY_TRACK_SPEED_AVERAGEMOVING, track.speedAverageMoving)
        put(KEY_TRACK_NUMBEROFLOCATIONS, track.numberOfLocations)
        put(KEY_TRACK_NUMBEROFPLACEMARKS, track.numberOfPlacemarks)
        put(KEY_TRACK_TYPE, track.type)
        put(KEY_TRACK_VALIDMAP, track.validMap)
        put(KEY_TRACK_DESCRIPTION, track.description)
    }

    fun getLocationsList(trackID: Long, startNumber: Long, endNumber: Long): List<LocationExtended> {
        val locationList: MutableList<LocationExtended> = ArrayList()
        val selectQuery = "SELECT  * FROM $TABLE_LOCATIONS WHERE $KEY_TRACK_ID = $trackID AND $KEY_LOCATION_NUMBER BETWEEN $startNumber AND $endNumber ORDER BY $KEY_LOCATION_NUMBER"
        val db = writableDatabase
        val cursor: Cursor = db.rawQuery(selectQuery, null)
        if (cursor != null) {
            val lc = Location("DB")
            if (cursor.moveToFirst()) {
                do {
                    val loc = Location("DB")
                    loc.latitude = cursor.getDouble(3)
                    loc.longitude = cursor.getDouble(4)
                    var lcdataDouble = cursor.getDouble(5)
                    if (lcdataDouble != GPSApplication.NOT_AVAILABLE.toDouble()) loc.altitude = lcdataDouble
                    var lcdataFloat = cursor.getFloat(6)
                    if (lcdataFloat != GPSApplication.NOT_AVAILABLE.toFloat()) loc.speed = lcdataFloat
                    lcdataFloat = cursor.getFloat(7)
                    if (lcdataFloat != GPSApplication.NOT_AVAILABLE.toFloat()) loc.accuracy = lcdataFloat
                    lcdataFloat = cursor.getFloat(8)
                    if (lcdataFloat != GPSApplication.NOT_AVAILABLE.toFloat()) loc.bearing = lcdataFloat
                    loc.time = cursor.getLong(9)
                    val extd = LocationExtended(loc)
                    extd.numberOfSatellites = cursor.getInt(10)
                    extd.numberOfSatellitesUsedInFix = cursor.getInt(12)
                    locationList.add(extd)
                } while (cursor.moveToNext())
            }
            cursor.close()
        }
        return locationList
    }

    fun getPlacemarksList(trackID: Long, startNumber: Long, endNumber: Long): List<LocationExtended> {
        val placemarkList: MutableList<LocationExtended> = ArrayList()
        val selectQuery = "SELECT  * FROM $TABLE_PLACEMARKS WHERE $KEY_TRACK_ID = $trackID AND $KEY_LOCATION_NUMBER BETWEEN $startNumber AND $endNumber ORDER BY $KEY_LOCATION_NUMBER"
        val db = writableDatabase
        val cursor: Cursor = db.rawQuery(selectQuery, null)
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    val loc = Location("DB")
                    loc.latitude = cursor.getDouble(3)
                    loc.longitude = cursor.getDouble(4)
                    var lcdataDouble = cursor.getDouble(5)
                    if (lcdataDouble != GPSApplication.NOT_AVAILABLE.toDouble()) loc.altitude = lcdataDouble
                    var lcdataFloat = cursor.getFloat(6)
                    if (lcdataFloat != GPSApplication.NOT_AVAILABLE.toFloat()) loc.speed = lcdataFloat
                    lcdataFloat = cursor.getFloat(7)
                    if (lcdataFloat != GPSApplication.NOT_AVAILABLE.toFloat()) loc.accuracy = lcdataFloat
                    lcdataFloat = cursor.getFloat(8)
                    if (lcdataFloat != GPSApplication.NOT_AVAILABLE.toFloat()) loc.bearing = lcdataFloat
                    loc.time = cursor.getLong(9)
                    val extd = LocationExtended(loc)
                    extd.numberOfSatellites = cursor.getInt(10)
                    extd.numberOfSatellitesUsedInFix = cursor.getInt(13)
                    extd.description = cursor.getString(12)
                    placemarkList.add(extd)
                } while (cursor.moveToNext())
            }
            cursor.close()
        }
        return placemarkList
    }

    fun getLatLngList(trackID: Long, startNumber: Long, endNumber: Long): List<LatLng> {
        val latlngList: MutableList<LatLng> = ArrayList()
        val selectQuery = "SELECT $KEY_TRACK_ID,$KEY_LOCATION_LATITUDE,$KEY_LOCATION_LONGITUDE,$KEY_LOCATION_NUMBER FROM $TABLE_LOCATIONS WHERE $KEY_TRACK_ID = $trackID AND $KEY_LOCATION_NUMBER BETWEEN $startNumber AND $endNumber ORDER BY $KEY_LOCATION_NUMBER"
        val db = writableDatabase
        val cursor: Cursor = db.rawQuery(selectQuery, null)
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    val latlng = LatLng()
                    latlng.latitude = cursor.getDouble(1)
                    latlng.longitude = cursor.getDouble(2)
                    latlngList.add(latlng)
                } while (cursor.moveToNext())
            }
            cursor.close()
        }
        return latlngList
    }

    fun DeleteTrack(trackID: Long) {
        val db = writableDatabase
        try {
            db.beginTransaction()
            db.delete(TABLE_PLACEMARKS, "$KEY_TRACK_ID = ?", arrayOf(trackID.toString()))
            db.delete(TABLE_LOCATIONS, "$KEY_TRACK_ID = ?", arrayOf(trackID.toString()))
            db.delete(TABLE_TRACKS, "$KEY_ID = ?", arrayOf(trackID.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun addTrack(track: Track): Long {
        val db = writableDatabase
        val trkvalues = buildTrackContentValues(track)
        return db.insert(TABLE_TRACKS, null, trkvalues)
    }

    fun getTrack(TrackID: Long): Track {
        var track: Track? = null
        val selectQuery = "SELECT  * FROM $TABLE_TRACKS WHERE $KEY_ID = $TrackID"
        val db = writableDatabase
        val cursor = db.rawQuery(selectQuery, null)
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                track = Track()
                track.fromDB(
                    cursor.getLong(0),
                    cursor.getString(1),
                    cursor.getString(2),
                    cursor.getString(3),
                    cursor.getDouble(4),
                    cursor.getDouble(5),
                    cursor.getDouble(6),
                    cursor.getFloat(7),
                    cursor.getFloat(8),
                    cursor.getLong(9),
                    cursor.getLong(10),
                    cursor.getDouble(11),
                    cursor.getDouble(12),
                    cursor.getDouble(13),
                    cursor.getFloat(14),
                    cursor.getFloat(15),
                    cursor.getLong(16),
                    cursor.getDouble(17),
                    cursor.getDouble(18),
                    cursor.getFloat(19),
                    cursor.getDouble(20),
                    cursor.getFloat(21),
                    cursor.getDouble(22),
                    cursor.getDouble(23),
                    cursor.getDouble(24),
                    cursor.getDouble(25),
                    cursor.getLong(26),
                    cursor.getLong(27),
                    cursor.getFloat(28),
                    cursor.getFloat(29),
                    cursor.getLong(30),
                    cursor.getDouble(31),
                    cursor.getDouble(32),
                    cursor.getDouble(33),
                    cursor.getFloat(34),
                    cursor.getFloat(35),
                    cursor.getFloat(36),
                    cursor.getLong(37),
                    cursor.getLong(38),
                    cursor.getInt(39),
                    cursor.getInt(40),
                    cursor.getString(41)
                )
            }
            cursor.close()
        }
        return track ?: Track()
    }

    fun getLastTrackID(): Long {
        val db = writableDatabase
        var result: Long = 0
        val query = "SELECT $KEY_ID FROM $TABLE_TRACKS ORDER BY $KEY_ID DESC LIMIT 1"
        val cursor = db.rawQuery(query, null)
        if (cursor != null) {
            if (cursor.moveToFirst()) result = cursor.getLong(0)
            cursor.close()
        }
        return result
    }

    fun getLastTrack(): Track = getTrack(getLastTrackID())

    fun getTracksList(startNumber: Long, endNumber: Long): List<Track> {
        val trackList: MutableList<Track> = ArrayList()
        val selectQuery = "SELECT  * FROM $TABLE_TRACKS WHERE $KEY_ID BETWEEN $startNumber AND $endNumber ORDER BY $KEY_ID DESC"
        val db = writableDatabase
        val cursor = db.rawQuery(selectQuery, null)
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    val trk = Track()
                    trk.fromDB(
                        cursor.getLong(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getString(3),
                        cursor.getDouble(4),
                        cursor.getDouble(5),
                        cursor.getDouble(6),
                        cursor.getFloat(7),
                        cursor.getFloat(8),
                        cursor.getLong(9),
                        cursor.getLong(10),
                        cursor.getDouble(11),
                        cursor.getDouble(12),
                        cursor.getDouble(13),
                        cursor.getFloat(14),
                        cursor.getFloat(15),
                        cursor.getLong(16),
                        cursor.getDouble(17),
                        cursor.getDouble(18),
                        cursor.getFloat(19),
                        cursor.getDouble(20),
                        cursor.getFloat(21),
                        cursor.getDouble(22),
                        cursor.getDouble(23),
                        cursor.getDouble(24),
                        cursor.getDouble(25),
                        cursor.getLong(26),
                        cursor.getLong(27),
                        cursor.getFloat(28),
                        cursor.getFloat(29),
                        cursor.getLong(30),
                        cursor.getDouble(31),
                        cursor.getDouble(32),
                        cursor.getDouble(33),
                        cursor.getFloat(34),
                        cursor.getFloat(35),
                        cursor.getFloat(36),
                        cursor.getLong(37),
                        cursor.getLong(38),
                        cursor.getInt(39),
                        cursor.getInt(40),
                        cursor.getString(41)
                    )
                    trackList.add(trk)
                } while (cursor.moveToNext())
            }
            cursor.close()
        }
        return trackList
    }

    fun CorrectGPSWeekRollover() {
        val CorrectLocationsQuery = "UPDATE $TABLE_LOCATIONS SET $KEY_LOCATION_TIME = $KEY_LOCATION_TIME + 619315200000 WHERE $KEY_LOCATION_TIME <= 1388534400000"
        val CorrectPlacemarksQuery = "UPDATE $TABLE_PLACEMARKS SET $KEY_LOCATION_TIME = $KEY_LOCATION_TIME + 619315200000 WHERE $KEY_LOCATION_TIME <= 1388534400000"
        val CorrectNamesQuery = "SELECT $KEY_ID,$KEY_TRACK_NAME FROM $TABLE_TRACKS WHERE $KEY_TRACK_NAME LIKE '199%'"

        data class IdAndName(var id: Long, var Name: String)
        val Names = ArrayList<IdAndName>()

        val db = writableDatabase
        db.execSQL(CorrectLocationsQuery)
        db.execSQL(CorrectPlacemarksQuery)

        val cursor = db.rawQuery(CorrectNamesQuery, null)
        if (cursor != null) {
            var i = 0
            if (cursor.moveToFirst()) {
                do {
                    val SDF = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
                    SDF.timeZone = TimeZone.getTimeZone("GMT")
                    try {
                        val d: Date = SDF.parse(cursor.getString(1))
                        val timeInMilliseconds = d.time
                        val timeInMilliseconds_Corrected = timeInMilliseconds + 619315200000L
                        val Name_Corrected = SDF.format(timeInMilliseconds_Corrected)
                        Names.add(IdAndName(cursor.getLong(0), Name_Corrected))
                    } catch (ex: ParseException) {
                        Log.v("Exception", ex.localizedMessage)
                    }
                    i++
                } while (cursor.moveToNext())
            }
            Log.w("myApp", "[#] DatabaseHandler.kt - CorrectGPSWeekRollover NAMES = $i")
            cursor.close()
        }
        for (N in Names) {
            Log.w("myApp", "[#] GPSApplication.kt - CORRECTING TRACK ${N.id} = ${N.Name}")
            db.execSQL("UPDATE $TABLE_TRACKS SET $KEY_TRACK_NAME = \"${N.Name}\" WHERE $KEY_ID = ${N.id}")
        }
    }
}


