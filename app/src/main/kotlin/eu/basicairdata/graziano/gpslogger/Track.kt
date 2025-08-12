package eu.basicairdata.graziano.gpslogger

import android.location.Location
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Java 동작/시그니처를 그대로 유지한 Kotlin 변환 버전
 */
class Track {

    // ------------------------------ 상수 (Java와 동일한 값/이름 유지)
    companion object {
        private const val MIN_ALTITUDE_STEP = 8.0
        private const val MOVEMENT_SPEED_THRESHOLD = 0.5f
        private const val STANDARD_ACCURACY = 10.0f
        private const val SECURITY_COEFFICIENT = 1.7f

        const val TRACK_TYPE_STEADY = 0
        const val TRACK_TYPE_WALK = 1
        const val TRACK_TYPE_MOUNTAIN = 2
        const val TRACK_TYPE_RUN = 3
        const val TRACK_TYPE_BICYCLE = 4
        const val TRACK_TYPE_CAR = 5
        const val TRACK_TYPE_FLIGHT = 6
        const val TRACK_TYPE_HIKING = 7
        const val TRACK_TYPE_NORDICWALKING = 8
        const val TRACK_TYPE_SWIMMING = 9
        const val TRACK_TYPE_SCUBADIVING = 10
        const val TRACK_TYPE_ROWING = 11
        const val TRACK_TYPE_KAYAKING = 12
        const val TRACK_TYPE_SURFING = 13
        const val TRACK_TYPE_KITESURFING = 14
        const val TRACK_TYPE_SAILING = 15
        const val TRACK_TYPE_BOAT = 16
        const val TRACK_TYPE_DOWNHILLSKIING = 17
        const val TRACK_TYPE_SNOWBOARDING = 18
        const val TRACK_TYPE_SLEDDING = 19
        const val TRACK_TYPE_SNOWMOBILE = 20
        const val TRACK_TYPE_SNOWSHOEING = 21
        const val TRACK_TYPE_ICESKATING = 22
        const val TRACK_TYPE_HELICOPTER = 23
        const val TRACK_TYPE_ROCKET = 24
        const val TRACK_TYPE_PARAGLIDING = 25
        const val TRACK_TYPE_AIRBALLOON = 26
        const val TRACK_TYPE_SKATEBOARDING = 27
        const val TRACK_TYPE_ROLLERSKATING = 28
        const val TRACK_TYPE_WHEELCHAIR = 29
        const val TRACK_TYPE_ELECTRICSCOOTER = 30
        const val TRACK_TYPE_MOPED = 31
        const val TRACK_TYPE_MOTORCYCLE = 32
        const val TRACK_TYPE_TRUCK = 33
        const val TRACK_TYPE_BUS = 34
        const val TRACK_TYPE_TRAIN = 35
        const val TRACK_TYPE_AGRICULTURE = 36
        const val TRACK_TYPE_CITY = 37
        const val TRACK_TYPE_FOREST = 38
        const val TRACK_TYPE_WORK = 39
        const val TRACK_TYPE_PHOTOGRAPHY = 40
        const val TRACK_TYPE_RESEARCH = 41
        const val TRACK_TYPE_SOCCER = 42
        const val TRACK_TYPE_GOLF = 43
        const val TRACK_TYPE_PETS = 44
        const val TRACK_TYPE_MAP = 45
        const val TRACK_TYPE_ND = GPSApplication.NOT_AVAILABLE

        @JvmField
        val ACTIVITY_DRAWABLE_RESOURCE: IntArray = intArrayOf(
            R.drawable.ic_tracktype_place_24dp,
            R.drawable.ic_tracktype_walk_24dp,
            R.drawable.ic_tracktype_mountain_24dp,
            R.drawable.ic_tracktype_run_24dp,
            R.drawable.ic_tracktype_bike_24dp,
            R.drawable.ic_tracktype_car_24dp,
            R.drawable.ic_tracktype_flight_24dp,
            R.drawable.ic_tracktype_hiking_24,
            R.drawable.ic_tracktype_nordic_walking_24,
            R.drawable.ic_tracktype_pool_24,
            R.drawable.ic_tracktype_scuba_diving_24,
            R.drawable.ic_tracktype_rowing_24,
            R.drawable.ic_tracktype_kayaking_24,
            R.drawable.ic_tracktype_surfing_24,
            R.drawable.ic_tracktype_kitesurfing_24,
            R.drawable.ic_tracktype_sailing_24,
            R.drawable.ic_tracktype_directions_boat_24,
            R.drawable.ic_tracktype_downhill_skiing_24,
            R.drawable.ic_tracktype_snowboarding_24,
            R.drawable.ic_tracktype_sledding_24,
            R.drawable.ic_tracktype_snowmobile_24,
            R.drawable.ic_tracktype_snowshoeing_24,
            R.drawable.ic_tracktype_ice_skating_24,
            R.drawable.ic_tracktype_helicopter_24,
            R.drawable.ic_tracktype_rocket_24,
            R.drawable.ic_tracktype_paragliding_24,
            R.drawable.ic_tracktype_airballoon_24,
            R.drawable.ic_tracktype_skateboarding_24,
            R.drawable.ic_tracktype_roller_skating_24,
            R.drawable.ic_tracktype_wheelchair_24,
            R.drawable.ic_tracktype_electric_scooter_24,
            R.drawable.ic_tracktype_moped_24,
            R.drawable.ic_tracktype_sports_motorsports_24,
            R.drawable.ic_tracktype_truck_24,
            R.drawable.ic_tracktype_directions_bus_24,
            R.drawable.ic_tracktype_train_24,
            R.drawable.ic_tracktype_agriculture_24,
            R.drawable.ic_tracktype_city_24,
            R.drawable.ic_tracktype_forest_24,
            R.drawable.ic_tracktype_work_24,
            R.drawable.ic_tracktype_camera_24,
            R.drawable.ic_tracktype_search_24,
            R.drawable.ic_tracktype_sports_soccer_24,
            R.drawable.ic_tracktype_golf_24,
            R.drawable.ic_tracktype_pets_24,
            R.drawable.ic_tracktype_map_24
        )

        @JvmField
        val ACTIVITY_DESCRIPTION: Array<String> = arrayOf(
            "steady",
            "walking",
            "mountaineering",
            "running",
            "cycling",
            "car",
            "flying",
            "hiking",
            "nordic_walking",
            "swimming",
            "scuba_diving",
            "rowing",
            "kayaking",
            "surfing",
            "kitesurfing",
            "sailing",
            "boat",
            "downhill_skiing",
            "snowboarding",
            "sledding",
            "snowmobile",
            "snowshoeing",
            "ice_skating",
            "helicopter",
            "rocket",
            "paragliding",
            "air_balloon",
            "skateboarding",
            "roller_skating",
            "wheelchair",
            "electric_scooter",
            "moped",
            "motorcycle",
            "truck",
            "bus",
            "train",
            "agriculture",
            "city",
            "forest",
            "work",
            "photography",
            "research",
            "soccer",
            "golf",
            "pets",
            "map"
        )
    }

    // ------------------------------ 필드 (이름/타입/초기값 동일 유지)
    var id: Long = 0
    var name: String = ""
    var description: String = ""

    var latitudeStart: Double = GPSApplication.NOT_AVAILABLE.toDouble()
    var longitudeStart: Double = GPSApplication.NOT_AVAILABLE.toDouble()
    var altitudeStart: Double = GPSApplication.NOT_AVAILABLE.toDouble()
    var egmAltitudeCorrectionStart: Double = GPSApplication.NOT_AVAILABLE.toDouble()
    var accuracyStart: Float = STANDARD_ACCURACY
    var speedStart: Float = GPSApplication.NOT_AVAILABLE.toFloat()
    var timeStart: Long = GPSApplication.NOT_AVAILABLE.toLong()

    var timeLastFix: Long = GPSApplication.NOT_AVAILABLE.toLong()

    var latitudeEnd: Double = GPSApplication.NOT_AVAILABLE.toDouble()
    var longitudeEnd: Double = GPSApplication.NOT_AVAILABLE.toDouble()
    var altitudeEnd: Double = GPSApplication.NOT_AVAILABLE.toDouble()
    var egmAltitudeCorrectionEnd: Double = GPSApplication.NOT_AVAILABLE.toDouble()
    var accuracyEnd: Float = STANDARD_ACCURACY
    var speedEnd: Float = GPSApplication.NOT_AVAILABLE.toFloat()
    var timeEnd: Long = GPSApplication.NOT_AVAILABLE.toLong()

    var latitudeLastStepDistance: Double = GPSApplication.NOT_AVAILABLE.toDouble()
    var longitudeLastStepDistance: Double = GPSApplication.NOT_AVAILABLE.toDouble()
    var accuracyLastStepDistance: Float = STANDARD_ACCURACY

    var altitudeLastStepAltitude: Double = GPSApplication.NOT_AVAILABLE.toDouble()
    var accuracyLastStepAltitude: Float = STANDARD_ACCURACY

    var latitudeMin: Double = GPSApplication.NOT_AVAILABLE.toDouble()
    var longitudeMin: Double = GPSApplication.NOT_AVAILABLE.toDouble()
    var latitudeMax: Double = GPSApplication.NOT_AVAILABLE.toDouble()
    var longitudeMax: Double = GPSApplication.NOT_AVAILABLE.toDouble()

    var duration: Long = GPSApplication.NOT_AVAILABLE.toLong()
    var durationMoving: Long = GPSApplication.NOT_AVAILABLE.toLong()

    var distance: Float = GPSApplication.NOT_AVAILABLE.toFloat()
    var distanceInProgress: Float = GPSApplication.NOT_AVAILABLE.toFloat()
    var distanceLastAltitude: Long = GPSApplication.NOT_AVAILABLE.toLong()

    var altitudeUp: Double = GPSApplication.NOT_AVAILABLE.toDouble()
    var altitudeDown: Double = GPSApplication.NOT_AVAILABLE.toDouble()
    var altitudeInProgress: Double = GPSApplication.NOT_AVAILABLE.toDouble()

    var speedMax: Float = GPSApplication.NOT_AVAILABLE.toFloat()
    var speedAverage: Float = GPSApplication.NOT_AVAILABLE.toFloat()
    var speedAverageMoving: Float = GPSApplication.NOT_AVAILABLE.toFloat()

    var numberOfLocations: Long = 0
    var numberOfPlacemarks: Long = 0

    var validMap: Int = 1
    var type: Int = TRACK_TYPE_ND

    var isSelected: Boolean = false

    private val altitudeFilter = SpikesChecker(12f, 4)

    // ------------------------------ 공개 메서드 (이름/시그니처 동일 유지)
    fun add(location: LocationExtended) {
        if (numberOfLocations == 0L) {
            latitudeStart = location.location.latitude
            longitudeStart = location.location.longitude
            altitudeStart = if (location.location.hasAltitude()) location.location.altitude else GPSApplication.NOT_AVAILABLE.toDouble()
            egmAltitudeCorrectionStart = location.getAltitudeEGM96Correction()
            speedStart = if (location.location.hasSpeed()) location.location.speed else GPSApplication.NOT_AVAILABLE.toFloat()
            accuracyStart = if (location.location.hasAccuracy()) location.location.accuracy else STANDARD_ACCURACY
            timeStart = location.location.time

            latitudeLastStepDistance = latitudeStart
            longitudeLastStepDistance = longitudeStart
            accuracyLastStepDistance = accuracyStart

            latitudeMax = latitudeStart
            longitudeMax = longitudeStart
            latitudeMin = latitudeStart
            longitudeMin = longitudeStart

            if (name == "") {
                val df2 = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
                name = df2.format(timeStart)
            }

            timeLastFix = timeStart
            timeEnd = timeStart

            durationMoving = 0
            duration = 0
            distance = 0f
        }

        timeLastFix = timeEnd

        latitudeEnd = location.location.latitude
        longitudeEnd = location.location.longitude
        altitudeEnd = if (location.location.hasAltitude()) location.location.altitude else GPSApplication.NOT_AVAILABLE.toDouble()
        egmAltitudeCorrectionEnd = location.getAltitudeEGM96Correction()
        speedEnd = if (location.location.hasSpeed()) location.location.speed else GPSApplication.NOT_AVAILABLE.toFloat()
        accuracyEnd = if (location.location.hasAccuracy()) location.location.accuracy else STANDARD_ACCURACY
        timeEnd = location.location.time

        if (egmAltitudeCorrectionEnd == GPSApplication.NOT_AVAILABLE.toDouble()) getEGMAltitudeCorrectionEnd()
        if (egmAltitudeCorrectionStart == GPSApplication.NOT_AVAILABLE.toDouble()) getEGMAltitudeCorrectionStart()

        if (altitudeEnd != GPSApplication.NOT_AVAILABLE.toDouble()) altitudeFilter.load(timeEnd, altitudeEnd)

        if (validMap != 0) {
            if (latitudeEnd > latitudeMax) latitudeMax = latitudeEnd
            if (longitudeEnd > longitudeMax) longitudeMax = longitudeEnd
            if (latitudeEnd < latitudeMin) latitudeMin = latitudeEnd
            if (longitudeEnd < longitudeMin) longitudeMin = longitudeEnd

            if (abs(longitudeLastStepDistance - longitudeEnd) > 90) validMap = 0
        }

        duration = timeEnd - timeStart
        if (speedEnd >= MOVEMENT_SPEED_THRESHOLD) durationMoving += timeEnd - timeLastFix

        val lastStepDistanceLoc = Location("TEMP").apply {
            latitude = latitudeLastStepDistance
            longitude = longitudeLastStepDistance
        }
        val endLoc = Location("TEMP").apply {
            latitude = latitudeEnd
            longitude = longitudeEnd
        }
        distanceInProgress = lastStepDistanceLoc.distanceTo(endLoc)

        val deltaDistancePlusAccuracy = distanceInProgress + accuracyEnd
        if (deltaDistancePlusAccuracy < distanceInProgress + accuracyEnd) {
            accuracyLastStepDistance = deltaDistancePlusAccuracy
        }

        if (distanceInProgress > accuracyEnd + accuracyLastStepDistance) {
            distance += distanceInProgress
            if (distanceLastAltitude != GPSApplication.NOT_AVAILABLE.toLong()) distanceLastAltitude += distanceInProgress.toLong()
            distanceInProgress = 0f

            latitudeLastStepDistance = latitudeEnd
            longitudeLastStepDistance = longitudeEnd
            accuracyLastStepDistance = accuracyEnd
        }

        if (altitudeEnd != GPSApplication.NOT_AVAILABLE.toDouble() && distanceLastAltitude == GPSApplication.NOT_AVAILABLE.toLong()) {
            distanceLastAltitude = 0
            altitudeUp = 0.0
            altitudeDown = 0.0
            if (altitudeStart == GPSApplication.NOT_AVAILABLE.toDouble()) altitudeStart = altitudeEnd
            altitudeLastStepAltitude = altitudeEnd
            accuracyLastStepAltitude = accuracyEnd
        }

        if (altitudeLastStepAltitude != GPSApplication.NOT_AVAILABLE.toDouble() && altitudeEnd != GPSApplication.NOT_AVAILABLE.toDouble()) {
            altitudeInProgress = altitudeEnd - altitudeLastStepAltitude
            val deltaAltitudePlusAccuracy = abs(altitudeInProgress).toFloat() + accuracyEnd
            if (deltaAltitudePlusAccuracy <= accuracyLastStepAltitude) {
                accuracyLastStepAltitude = deltaAltitudePlusAccuracy
                distanceLastAltitude = 0
            }
            if (abs(altitudeInProgress) > MIN_ALTITUDE_STEP && altitudeFilter.isValid() && abs(altitudeInProgress).toFloat() > (SECURITY_COEFFICIENT * (accuracyLastStepAltitude + accuracyEnd))) {
                if (distanceLastAltitude < 5000) {
                    val hypotenuse = sqrt((distanceLastAltitude * distanceLastAltitude + altitudeInProgress * altitudeInProgress).toDouble()).toFloat()
                    distance = distance + hypotenuse - distanceLastAltitude
                }
                altitudeLastStepAltitude = altitudeEnd
                accuracyLastStepAltitude = accuracyEnd
                distanceLastAltitude = 0

                if (altitudeInProgress > 0) altitudeUp += altitudeInProgress else altitudeDown -= altitudeInProgress
                altitudeInProgress = 0.0
            }
        }

        if (speedEnd != GPSApplication.NOT_AVAILABLE.toFloat() && speedEnd > speedMax) speedMax = speedEnd
        if (duration > 0) speedAverage = (distance + distanceInProgress) / (duration.toFloat() / 1000f)
        if (durationMoving > 0) speedAverageMoving = (distance + distanceInProgress) / (durationMoving.toFloat() / 1000f)
        numberOfLocations++
    }

    constructor()
    constructor(name: String) { this.name = name }

    fun fromDB(
        id: Long,
        name: String,
        from: String?,
        to: String?,
        latitudeStart: Double,
        longitudeStart: Double,
        altitudeStart: Double,
        accuracyStart: Float,
        speedStart: Float,
        timeStart: Long,
        timeLastFix: Long,
        latitudeEnd: Double,
        longitudeEnd: Double,
        altitudeEnd: Double,
        accuracyEnd: Float,
        speedEnd: Float,
        timeEnd: Long,
        latitudeLastStepDistance: Double,
        longitudeLastStepDistance: Double,
        accuracyLastStepDistance: Float,
        altitudeLastStepAltitude: Double,
        accuracyLastStepAltitude: Float,
        latitudeMin: Double,
        longitudeMin: Double,
        latitudeMax: Double,
        longitudeMax: Double,
        duration: Long,
        durationMoving: Long,
        distance: Float,
        distanceInProgress: Float,
        distanceLastAltitude: Long,
        altitudeUp: Double,
        altitudeDown: Double,
        altitudeInProgress: Double,
        speedMax: Float,
        speedAverage: Float,
        speedAverageMoving: Float,
        numberOfLocations: Long,
        numberOfPlacemarks: Long,
        validMap: Int,
        type: Int,
        description: String
    ) {
        this.id = id
        this.name = name
        this.description = description

        this.latitudeStart = latitudeStart
        this.longitudeStart = longitudeStart
        this.altitudeStart = altitudeStart
        this.accuracyStart = accuracyStart
        this.speedStart = speedStart
        this.timeStart = timeStart

        this.timeLastFix = timeLastFix

        this.latitudeEnd = latitudeEnd
        this.longitudeEnd = longitudeEnd
        this.altitudeEnd = altitudeEnd
        this.accuracyEnd = accuracyEnd
        this.speedEnd = speedEnd
        this.timeEnd = timeEnd

        this.latitudeLastStepDistance = latitudeLastStepDistance
        this.longitudeLastStepDistance = longitudeLastStepDistance
        this.accuracyLastStepDistance = accuracyLastStepDistance

        this.altitudeLastStepAltitude = altitudeLastStepAltitude
        this.accuracyLastStepAltitude = accuracyLastStepAltitude

        this.latitudeMin = latitudeMin
        this.longitudeMin = longitudeMin

        this.latitudeMax = latitudeMax
        this.longitudeMax = longitudeMax

        this.duration = duration
        this.durationMoving = durationMoving

        this.distance = distance
        this.distanceInProgress = distanceInProgress
        this.distanceLastAltitude = distanceLastAltitude

        this.altitudeUp = altitudeUp
        this.altitudeDown = altitudeDown
        this.altitudeInProgress = altitudeInProgress

        this.speedMax = speedMax
        this.speedAverage = speedAverage
        this.speedAverageMoving = speedAverageMoving

        this.numberOfLocations = numberOfLocations
        this.numberOfPlacemarks = numberOfPlacemarks

        this.validMap = validMap
        this.type = type

        val egm96 = EGM96.getInstance()
        if (egm96 != null && egm96.isLoaded) {
            if (this.latitudeStart != GPSApplication.NOT_AVAILABLE.toDouble()) egmAltitudeCorrectionStart = egm96.getEGMCorrection(this.latitudeStart, this.longitudeStart)
            if (this.latitudeEnd != GPSApplication.NOT_AVAILABLE.toDouble()) egmAltitudeCorrectionEnd = egm96.getEGMCorrection(this.latitudeEnd, this.longitudeEnd)
        }
    }

    // ------------------------------ Java 호환 특수 Getter (로직 포함)
    fun getEGMAltitudeCorrectionStart(): Double {
        if (egmAltitudeCorrectionStart == GPSApplication.NOT_AVAILABLE.toDouble()) {
            val egm96 = EGM96.getInstance()
            if (egm96 != null && egm96.isLoaded) {
                if (latitudeStart != GPSApplication.NOT_AVAILABLE.toDouble())
                    egmAltitudeCorrectionStart = egm96.getEGMCorrection(latitudeStart, longitudeStart)
            }
        }
        return egmAltitudeCorrectionStart
    }
    fun getEGMAltitudeCorrectionEnd(): Double {
        if (egmAltitudeCorrectionEnd == GPSApplication.NOT_AVAILABLE.toDouble()) {
            val egm96 = EGM96.getInstance()
            if (egm96 != null && egm96.isLoaded) {
                if (latitudeEnd != GPSApplication.NOT_AVAILABLE.toDouble())
                    egmAltitudeCorrectionEnd = egm96.getEGMCorrection(latitudeEnd, longitudeEnd)
            }
        }
        return egmAltitudeCorrectionEnd
    }
    // 나머지는 var 프로퍼티로 Java Getter/Setter 자동 생성됨

    fun isValidAltitude(): Boolean = altitudeFilter.isValid()

    fun addPlacemark(location: LocationExtended): Long {
        numberOfPlacemarks++
        if (name == "") {
            val df2 = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
            name = df2.format(location.location.time)
        }
        return numberOfPlacemarks
    }

    // NOTE: Java에서 getEstimatedDistance()로 접근해야 하므로 별도 함수 제거하고 아래 JvmName 프로퍼티만 유지

    fun getEstimatedAltitudeUp(egmCorrection: Boolean): Double {
        if (egmAltitudeCorrectionStart == GPSApplication.NOT_AVAILABLE.toDouble() || egmAltitudeCorrectionEnd == GPSApplication.NOT_AVAILABLE.toDouble()) {
            val egm96 = EGM96.getInstance()
            if (egm96 != null && egm96.isLoaded) {
                if (latitudeStart != GPSApplication.NOT_AVAILABLE.toDouble()) egmAltitudeCorrectionStart = egm96.getEGMCorrection(latitudeStart, longitudeStart)
                if (latitudeEnd != GPSApplication.NOT_AVAILABLE.toDouble()) egmAltitudeCorrectionEnd = egm96.getEGMCorrection(latitudeEnd, longitudeEnd)
            }
        }
        var egmcorr = 0.0
        if (egmCorrection && egmAltitudeCorrectionStart != GPSApplication.NOT_AVAILABLE.toDouble() && egmAltitudeCorrectionEnd != GPSApplication.NOT_AVAILABLE.toDouble()) {
            egmcorr = egmAltitudeCorrectionStart - egmAltitudeCorrectionEnd
        }
        var dresultUp = if (altitudeInProgress > 0) altitudeUp + altitudeInProgress else altitudeUp
        dresultUp -= if (egmcorr < 0) egmcorr else 0.0
        var dresultDown = if (altitudeInProgress < 0) altitudeDown - altitudeInProgress else altitudeDown
        dresultDown -= if (egmcorr > 0) egmcorr else 0.0

        if (dresultUp < 0) {
            dresultDown -= dresultUp
            dresultUp = 0.0
        }
        if (dresultDown < 0) {
            dresultUp -= dresultDown
        }
        return dresultUp
    }

    fun getEstimatedAltitudeDown(egmCorrection: Boolean): Double {
        if (egmAltitudeCorrectionStart == GPSApplication.NOT_AVAILABLE.toDouble() || egmAltitudeCorrectionEnd == GPSApplication.NOT_AVAILABLE.toDouble()) {
            val egm96 = EGM96.getInstance()
            if (egm96 != null && egm96.isLoaded) {
                if (latitudeStart != GPSApplication.NOT_AVAILABLE.toDouble()) egmAltitudeCorrectionStart = egm96.getEGMCorrection(latitudeStart, longitudeStart)
                if (latitudeEnd != GPSApplication.NOT_AVAILABLE.toDouble()) egmAltitudeCorrectionEnd = egm96.getEGMCorrection(latitudeEnd, longitudeEnd)
            }
        }
        var egmcorr = 0.0
        if (egmCorrection && egmAltitudeCorrectionStart != GPSApplication.NOT_AVAILABLE.toDouble() && egmAltitudeCorrectionEnd != GPSApplication.NOT_AVAILABLE.toDouble()) {
            egmcorr = egmAltitudeCorrectionStart - egmAltitudeCorrectionEnd
        }
        var dresultUp = if (altitudeInProgress > 0) altitudeUp + altitudeInProgress else altitudeUp
        dresultUp -= if (egmcorr < 0) egmcorr else 0.0
        var dresultDown = if (altitudeInProgress < 0) altitudeDown - altitudeInProgress else altitudeDown
        dresultDown -= if (egmcorr > 0) egmcorr else 0.0
        if (dresultUp < 0) {
            dresultDown -= dresultUp
            dresultUp = 0.0
        }
        if (dresultDown < 0) {
            dresultDown = 0.0
        }
        return dresultDown
    }

    fun getEstimatedAltitudeGap(egmCorrection: Boolean): Double {
        return getEstimatedAltitudeUp(egmCorrection) - getEstimatedAltitudeDown(egmCorrection)
    }

    @get:JvmName("getBearing")
    val bearing: Float
        get() {
            if (latitudeEnd != GPSApplication.NOT_AVAILABLE.toDouble()) {
                if ((latitudeStart == latitudeEnd && longitudeStart == longitudeEnd) || (distance == 0f)) return GPSApplication.NOT_AVAILABLE.toFloat()
                val endLoc = Location("TEMP").apply { latitude = latitudeEnd; longitude = longitudeEnd }
                val startLoc = Location("TEMP").apply { latitude = latitudeStart; longitude = longitudeStart }
                var bTo = startLoc.bearingTo(endLoc)
                if (bTo < 0) bTo += 360f
                return bTo
            }
            return GPSApplication.NOT_AVAILABLE.toFloat()
        }

    @get:JvmName("getPrefTime")
    val prefTime: Long
        get() {
            val gpsApp = GPSApplication.getInstance()
            return when (gpsApp.prefShowTrackStatsType) {
                0 -> duration
                1 -> durationMoving
                else -> duration
            }
        }

    @get:JvmName("getPrefSpeedAverage")
    val prefSpeedAverage: Float
        get() {
            if (numberOfLocations == 0L) return GPSApplication.NOT_AVAILABLE.toFloat()
            val gpsApp = GPSApplication.getInstance()
            return when (gpsApp.prefShowTrackStatsType) {
                0 -> speedAverage
                1 -> speedAverageMoving
                else -> speedAverage
            }
        }
    
    @get:JvmName("getEstimatedDistance")
    val estimatedDistance: Float
        get() {
            if (numberOfLocations == 0L) return GPSApplication.NOT_AVAILABLE.toFloat()
            if (numberOfLocations == 1L) return 0f
            return distance + distanceInProgress
        }

    @get:JvmName("getEstimatedTrackType")
    val estimatedTrackType: Int
        get() {
            if (type != TRACK_TYPE_ND) return type
            if (distance == GPSApplication.NOT_AVAILABLE.toFloat() || speedMax == GPSApplication.NOT_AVAILABLE.toFloat()) {
                return if (numberOfPlacemarks == 0L) TRACK_TYPE_ND else TRACK_TYPE_STEADY
            }
            if (distance < 15.0f || speedMax == 0.0f || speedAverageMoving == GPSApplication.NOT_AVAILABLE.toFloat()) return TRACK_TYPE_STEADY
            if (speedMax < (7.0f / 3.6f)) {
                if (altitudeUp != GPSApplication.NOT_AVAILABLE.toDouble() && altitudeDown != GPSApplication.NOT_AVAILABLE.toDouble())
                    return if (altitudeDown + altitudeUp > (0.1f * distance) && distance > 500.0f) TRACK_TYPE_MOUNTAIN else TRACK_TYPE_WALK
                else return TRACK_TYPE_WALK
            }
            if (speedMax < (15.0f / 3.6f)) {
                if (speedAverageMoving > 8.0f / 3.6f) return TRACK_TYPE_RUN else {
                    if (altitudeUp != GPSApplication.NOT_AVAILABLE.toDouble() && altitudeDown != GPSApplication.NOT_AVAILABLE.toDouble())
                        return if (altitudeDown + altitudeUp > (0.1f * distance) && distance > 500.0f) TRACK_TYPE_MOUNTAIN else TRACK_TYPE_WALK
                    else return TRACK_TYPE_WALK
                }
            }
            if (speedMax < (50.0f / 3.6f)) {
                val avg = (speedAverageMoving + speedMax) / 2
                return when {
                    avg > 35.0f / 3.6f -> TRACK_TYPE_CAR
                    avg > 20.0f / 3.6f -> TRACK_TYPE_BICYCLE
                    avg > 12.0f / 3.6f -> TRACK_TYPE_RUN
                    else -> {
                        if (altitudeUp != GPSApplication.NOT_AVAILABLE.toDouble() && altitudeDown != GPSApplication.NOT_AVAILABLE.toDouble())
                            if (altitudeDown + altitudeUp > (0.1f * distance) && distance > 500.0f) TRACK_TYPE_MOUNTAIN else TRACK_TYPE_WALK
                        else TRACK_TYPE_WALK
                    }
                }
            }
            if (altitudeUp != GPSApplication.NOT_AVAILABLE.toDouble() && altitudeDown != GPSApplication.NOT_AVAILABLE.toDouble())
                if ((altitudeDown + altitudeUp > 5000.0) && (speedMax > 300.0f / 3.6f)) return TRACK_TYPE_FLIGHT
            return TRACK_TYPE_CAR
        }
}


