package eu.basicairdata.graziano.gpslogger

import android.Manifest
import android.app.ActivityManager
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.location.GnssStatus
import android.location.GpsStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationProvider
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Vibrator
import android.util.Log
import androidx.annotation.NonNull
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import java.io.File
import java.io.FileFilter
import java.io.FileOutputStream
import java.io.IOException
import java.util.*
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt

class GPSApplication : Application(), LocationListener {

    companion object {
        const val NOT_AVAILABLE: Int = -100000

        private const val STABILIZER_TIME = 3000
        private const val DEFAULT_SWITCHOFF_HANDLER_TIME = 5000
        private const val GPS_UNAVAILABLE_HANDLER_TIME = 7000

        private const val MAX_ACTIVE_EXPORTER_THREADS = 3
        private const val EXPORTING_STATUS_CHECK_INTERVAL = 16L

        const val GPS_DISABLED = 0
        const val GPS_OUTOFSERVICE = 1
        const val GPS_TEMPORARYUNAVAILABLE = 2
        const val GPS_SEARCHING = 3
        const val GPS_STABILIZING = 4
        const val GPS_OK = 5

        const val JOB_TYPE_NONE = 0
        const val JOB_TYPE_EXPORT = 1
        const val JOB_TYPE_VIEW = 2
        const val JOB_TYPE_SHARE = 3
        const val JOB_TYPE_DELETE = 4

        private const val TASK_SHUTDOWN = "TASK_SHUTDOWN"
        private const val TASK_NEWTRACK = "TASK_NEWTRACK"
        private const val TASK_ADDLOCATION = "TASK_ADDLOCATION"
        private const val TASK_ADDPLACEMARK = "TASK_ADDPLACEMARK"
        private const val TASK_UPDATEFIX = "TASK_UPDATEFIX"
        private const val TASK_DELETETRACKS = "TASK_DELETETRACKS"

        @JvmField val FLAG_RECORDING: String = "flagRecording"
        @JvmField val FILETYPE_KML: String = ".kml"
        @JvmField val FILETYPE_GPX: String = ".gpx"

        private val NEGATIVE = floatArrayOf(
            -1.0f, 0f, 0f, 0f, 248f,
            0f, -1.0f, 0f, 0f, 248f,
            0f, 0f, -1.0f, 0f, 248f,
            0f, 0f, 0f, 1.00f, 0f
        )

        @JvmField val colorMatrixColorFilter: ColorMatrixColorFilter = ColorMatrixColorFilter(NEGATIVE)

        @JvmField var TOAST_VERTICAL_OFFSET: Int = 0

        @JvmField var DIRECTORY_TEMP: String = ""
        @JvmField var DIRECTORY_FILESDIR_TRACKS: String = ""
        @JvmField var FILE_EMPTY_GPX: String = ""
        @JvmField var FILE_EMPTY_KML: String = ""

        private lateinit var singleton: GPSApplication
        @JvmStatic fun getInstance(): GPSApplication = singleton
    }

    // Preferences
    var prefShowDecimalCoordinates: Boolean = false
    var prefUM: Int = PhysicalDataFormatter.UM_METRIC
    var prefUMOfSpeed: Int = PhysicalDataFormatter.UM_SPEED_KMH
    private var prefGPSdistance: Float = 0f
    private var prefGPSinterval: Float = 0f
    private var prefGPSupdatefrequency: Long = 1000L
    var prefEGM96AltitudeCorrection: Boolean = false
    var prefAltitudeCorrection: Double = 0.0
    var prefExportKML: Boolean = true
    var prefExportGPX: Boolean = true
    var prefGPXVersion: Int = 100
    var prefExportTXT: Boolean = false
    var prefKMLAltitudeMode: Int = 0
    var prefShowTrackStatsType: Int = 0
    var prefShowDirections: Int = 0
    private var prefGPSWeekRolloverCorrected: Boolean = false
    var prefShowLocalTime: Boolean = true
    private var _prefExportFolder: String = ""
      var prefExportFolder: String
        get() = _prefExportFolder
        set(value) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            val editor = prefs.edit()
            editor.putString("prefExportFolder", value)
            editor.commit()
            _prefExportFolder = value
            Log.w("myApp", "[#] GPSApplication.kt - prefExportFolder = $value")
        }

    private var mustUpdatePrefs: Boolean = true

    var isLocationPermissionChecked: Boolean = false
    var isFirstRun: Boolean = false
    var isJustStarted: Boolean = true
    private var isMockProvider: Boolean = false
    private var isScreenOn: Boolean = true
    var isBackgroundActivityRestricted: Boolean = false
    var isBatteryOptimisedWarningVisible: Boolean = true

    private var prevFix: LocationExtended? = null
    private var prevRecordedFix: LocationExtended? = null
    private var isPrevFixRecorded: Boolean = false
    var isFirstFixFound: Boolean = false
    private var isAccuracyDecimalCounter: Int = 0

    private lateinit var gpsStatusListener: MyGPSStatus

    var isCurrentTrackVisible: Boolean = false
    var isContextMenuShareVisible: Boolean = false
    var isContextMenuViewVisible: Boolean = false
    var viewInAppIcon: Drawable? = null
    var viewInApp: String = ""

    var isSpaceForExtraTilesAvailable: Boolean = true

    var lastClickId: Long = NOT_AVAILABLE.toLong()
    var lastClickState: Boolean = false

    private var trackViewer: ExternalViewer = ExternalViewer()
    val satellites: Satellites = Satellites()
    lateinit var gpsDataBase: DatabaseHandler

    private var placemarkDescription: String = ""
    var isPlacemarkRequested: Boolean = false
        set(value) {
            field = value
            EventBus.getDefault().post(EventBusMSG.UPDATE_TRACK)
        }
    private var isQuickPlacemarkRequest: Boolean = false
    var isRecording: Boolean = false
        set(recordingState) {
            prevRecordedFix = null
            field = recordingState
            EventBus.getDefault().post(EventBusMSG.UPDATE_TRACK)
            if (field) addPreferenceFlag_NoBackup(FLAG_RECORDING) else clearPreferenceFlag_NoBackup(FLAG_RECORDING)
        }
    var isBottomBarLocked: Boolean = false
        set(locked) {
            field = locked
            EventBus.getDefault().post(EventBusMSG.UPDATE_TRACK)
        }
    private var isGPSLocationUpdatesActive: Boolean = false
    var isForcedTrackpointsRecording: Boolean = false
    private var gpsStatus: Int = GPS_SEARCHING
    private var locationManager: LocationManager? = null
    var numberOfSatellitesTotal: Int = 0
        private set
    var numberOfSatellitesUsedInFix: Int = 0
        private set

    private var gpsActivityActiveTab: Int = 1
    private var _jobProgress: Int = 0
    private var _jobsPending: Int = 0
    var jobType: Int = JOB_TYPE_NONE

    private var numberOfStabilizationSamples: Int = 3
    private var stabilizer: Int = numberOfStabilizationSamples
    var handlerTime: Int = DEFAULT_SWITCHOFF_HANDLER_TIME

    var currentLocationExtended: LocationExtended? = null
    val currentPlacemark: LocationExtended?
        get() = _currentPlacemark
    private var _currentPlacemark: LocationExtended? = null
    lateinit var currentTrack: Track
    var trackToEdit: Track? = null
    var selectedTrackTypeOnDialog: Int = NOT_AVAILABLE

    private val arrayListTracks: MutableList<Track> = Collections.synchronizedList(ArrayList())
    val exportingTaskList: MutableList<ExportingTask> = ArrayList()
    val trackList: List<Track> get() = arrayListTracks
    val selectedTracks: ArrayList<Track> get() = getSelectedTracksInternal()

    private var asyncPrepareActionmodeToolbar: AsyncPrepareActionmodeToolbar? = null
    private lateinit var _externalViewerChecker: ExternalViewerChecker
    val externalViewerChecker: ExternalViewerChecker
        get() = _externalViewerChecker
    private var broadcastReceiver: BroadcastReceiver = ActionsBroadcastReceiver()

    private var thumbnailer: Thumbnailer? = null
    private var exporter: Exporter? = null
    private val asyncUpdateThread: AsyncUpdateThreadClass = AsyncUpdateThreadClass()

    // Singleton instance set in onCreate

    // Handlers and Runnables
    private var _isStopButtonFlag: Boolean = false
    val isStopButtonFlag: Boolean
        get() = _isStopButtonFlag
    private val stopButtonHandler = Handler()
    private val stopButtonRunnable = Runnable {
        _isStopButtonFlag = false
        EventBus.getDefault().post(EventBusMSG.UPDATE_TRACK)
    }

    private val disableLocationUpdatesHandler = Handler()
    private val disableLocationUpdatesRunnable = Runnable {
        setGPSLocationUpdates(false)
    }

    private val enableLocationUpdatesHandler = Handler()
    private val enableLocationUpdatesRunnable = Runnable {
        setGPSLocationUpdates(false)
        setGPSLocationUpdates(true)
    }

    private val gpsUnavailableHandler = Handler()
    private val gpsUnavailableRunnable = Runnable {
        if ((gpsStatus == GPS_OK) || (gpsStatus == GPS_STABILIZING)) {
            gpsStatus = GPS_TEMPORARYUNAVAILABLE
            stabilizer = numberOfStabilizationSamples
            EventBus.getDefault().post(EventBusMSG.UPDATE_FIX)
        }
    }

    private val exportingStatusCheckHandler = Handler()
    private val exportingStatusCheckRunnable = object : Runnable {
        override fun run() {
            var total = 0L
            var progress = 0L
            val exportersTotal = exportingTaskList.size
            var exportersPending = 0
            var exportersRunning = 0
            var exportersSuccess = 0
            var exportersFailed = 0

            for (et in exportingTaskList) {
                total += et.numberOfPoints_Total
                progress += et.numberOfPoints_Processed
                if (et.status == ExportingTask.STATUS_PENDING) exportersPending++
                if (et.status == ExportingTask.STATUS_RUNNING) exportersRunning++
                if (et.status == ExportingTask.STATUS_ENDED_SUCCESS) exportersSuccess++
                if (et.status == ExportingTask.STATUS_ENDED_FAILED) exportersFailed++
            }

            if (total != 0L) {
                val p = (1000.0 * progress / total).roundToInt()
                if (_jobProgress != p) {
                    _jobProgress = p
                    EventBus.getDefault().post(EventBusMSG.UPDATE_JOB_PROGRESS)
                }
            } else {
                if (_jobProgress != 0) {
                    _jobProgress = 0
                    EventBus.getDefault().post(EventBusMSG.UPDATE_JOB_PROGRESS)
                }
            }

            if (exportersFailed != 0) {
                EventBus.getDefault().post(EventBusMSG.TOAST_UNABLE_TO_WRITE_THE_FILE)
                if ((jobType == JOB_TYPE_EXPORT) && (prefExportFolder.startsWith("content://"))) {
                    Log.w("myApp", "[#] GPSApplication.kt - Unable to export into ${prefExportFolder}. Preference reset")
                    getInstance().prefExportFolder = ""
                }
                _jobProgress = 0
                _jobsPending = 0
                EventBus.getDefault().post(EventBusMSG.UPDATE_JOB_PROGRESS)
                return
            }

            if (exportersSuccess == exportersTotal) {
                when (jobType) {
                    JOB_TYPE_VIEW -> if (exportingTaskList.isNotEmpty()) viewTrack(exportingTaskList[0])
                    JOB_TYPE_SHARE -> EventBus.getDefault().post(EventBusMSG.INTENT_SEND)
                    else -> EventBus.getDefault().post(EventBusMSG.TOAST_TRACK_EXPORTED)
                }
                _jobProgress = 0
                _jobsPending = 0
                EventBus.getDefault().post(EventBusMSG.UPDATE_JOB_PROGRESS)
                return
            }

            if ((exportersRunning < MAX_ACTIVE_EXPORTER_THREADS) && (exportersPending > 0)) {
                for (et in exportingTaskList) {
                    if (et.status == ExportingTask.STATUS_PENDING) {
                        et.status = ExportingTask.STATUS_RUNNING
                        executeExportingTask(et)
                        break
                    }
                }
            }

            exportingStatusCheckHandler.postDelayed(this, EXPORTING_STATUS_CHECK_INTERVAL)
        }
    }

    private inner class MyGPSStatus {
        private var gpsStatusListener: GpsStatus.Listener? = null
        private var mGnssStatusListener: GnssStatus.Callback? = null

        init {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mGnssStatusListener = object : GnssStatus.Callback() {
                    override fun onSatelliteStatusChanged(@NonNull status: GnssStatus) {
                        super.onSatelliteStatusChanged(status)
                        updateGNSSStatus(status)
                    }
                }
            } else {
                gpsStatusListener = GpsStatus.Listener { event ->
                    when (event) {
                        GpsStatus.GPS_EVENT_SATELLITE_STATUS -> updateGPSStatus()
                    }
                }
            }
        }

        fun enable() {
            if (ContextCompat.checkSelfPermission(getInstance(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) locationManager?.registerGnssStatusCallback(mGnssStatusListener!!)
                else locationManager?.addGpsStatusListener(gpsStatusListener)
            }
        }

        fun disable() {
            if (ContextCompat.checkSelfPermission(getInstance(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) locationManager?.unregisterGnssStatusCallback(mGnssStatusListener!!)
                else locationManager?.removeGpsStatusListener(gpsStatusListener)
            }
        }
    }

    // Foreground Service
    private var gpsServiceIntent: Intent? = null
    private var gpsService: GPSService? = null
    private var isGPSServiceBound = false

    private val gpsServiceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as GPSService.LocalBinder
            gpsService = binder.serviceInstance
            Log.w("myApp", "[#] GPSApplication.kt - GPSSERVICE CONNECTED - onServiceConnected event")
            isGPSServiceBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            Log.w("myApp", "[#] GPSApplication.kt - GPSSERVICE DISCONNECTED - onServiceDisconnected event")
            isGPSServiceBound = false
        }
    }

    fun startAndBindGPSService() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            gpsServiceIntent = Intent(this@GPSApplication, GPSService::class.java)
            startService(gpsServiceIntent)
            bindService(gpsServiceIntent!!, gpsServiceConnection, Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT)
            Log.w("myApp", "[#] GPSApplication.kt - StartAndBindGPSService - SERVICE STARTED")
        } else {
            Log.w("myApp", "[#] GPSApplication.kt - StartAndBindGPSService - UNABLE TO START THE SERVICE")
        }
    }

    fun stopAndUnbindGPSService() {
        try {
            unbindService(gpsServiceConnection)
            Log.w("myApp", "[#] GPSApplication.kt - Service unbound")
        } catch (e: Exception) {
            Log.w("myApp", "[#] GPSApplication.kt - Unable to unbind the GPSService")
        }
        try {
            stopService(gpsServiceIntent)
            Log.w("myApp", "[#] GPSApplication.kt - Service stopped")
        } catch (e: Exception) {
            Log.w("myApp", "[#] GPSApplication.kt - Unable to stop GPSService")
        }
    }

    fun setStopButtonFlag(stopFlag: Boolean, millis: Long) {
        if (stopFlag) {
            _isStopButtonFlag = true
            stopButtonHandler.removeCallbacks(stopButtonRunnable)
            stopButtonHandler.postDelayed(stopButtonRunnable, millis)
        } else {
            _isStopButtonFlag = false
            stopButtonHandler.removeCallbacks(stopButtonRunnable)
        }
    }

    // Accessors kept only where Java interop requires distinct names (avoid duplicating Kotlin property accessors)
    fun getGPSStatus(): Int = gpsStatus
    fun isAccuracyDecimal(): Boolean = isAccuracyDecimalCounter != 0
    val jobProgress: Int get() = _jobProgress
    val jobsPending: Int get() = _jobsPending
    fun getGPSActivityActiveTab(): Int = gpsActivityActiveTab
    fun setGPSActivityActiveTab(gpsActivityActiveTab: Int) { this.gpsActivityActiveTab = gpsActivityActiveTab }
    fun setQuickPlacemarkRequest(quickPlacemarkRequest: Boolean) { isQuickPlacemarkRequest = quickPlacemarkRequest }
    fun setTrackViewer(trackViewer: ExternalViewer) { this.trackViewer = trackViewer }
    fun setPlacemarkDescription(Description: String) { this.placemarkDescription = Description }

    fun createPrivateFolders() {
        var sd = File(DIRECTORY_TEMP)
        if (!sd.exists()) {
            if (sd.mkdir()) Log.w("myApp", "[#] GPSApplication.kt - Folder created: ${sd.absolutePath}") else Log.w("myApp", "[#] GPSApplication.kt - Unable to create the folder: ${sd.absolutePath}")
        } else Log.w("myApp", "[#] GPSApplication.kt - Folder exists: ${sd.absolutePath}")

        sd = File(applicationContext.filesDir.toString() + "/Thumbnails")
        if (!sd.exists()) {
            if (sd.mkdir()) Log.w("myApp", "[#] GPSApplication.kt - Folder created: ${sd.absolutePath}") else Log.w("myApp", "[#] GPSApplication.kt - Unable to create the folder: ${sd.absolutePath}")
        } else Log.w("myApp", "[#] GPSApplication.kt - Folder exists: ${sd.absolutePath}")

        sd = File(DIRECTORY_FILESDIR_TRACKS)
        if (!sd.exists()) {
            if (sd.mkdir()) Log.w("myApp", "[#] GPSApplication.kt - Folder created: ${sd.absolutePath}") else Log.w("myApp", "[#] GPSApplication.kt - Unable to create the folder: ${sd.absolutePath}")
        } else Log.w("myApp", "[#] GPSApplication.kt - Folder exists: ${sd.absolutePath}")
    }

    private fun fileDelete(filename: String) {
        val file = File(filename)
        if (file.exists()) {
            val deleted = file.delete()
            if (deleted) Log.w("myApp", "[#] GPSApplication.kt - DeleteFile: $filename deleted") else Log.w("myApp", "[#] GPSApplication.kt - DeleteFile: $filename unable to delete the File")
        } else Log.w("myApp", "[#] GPSApplication.kt - DeleteFile: $filename doesn't exists")
    }

    fun fileFind(path: String, nameStart: String): Array<File>? {
        val _path = File(path)
        return try {
            _path.listFiles { file ->
                val name = file.name
                name.startsWith(nameStart)
            }
        } catch (e: Exception) { null }
    }

    private fun stringToDescFileName(str: String?): String {
        if (str == null || str.isEmpty()) return ""
        val sName = str.substring(0, minOf(128, str.length))
            .replace("\\", "_")
            .replace("/", "_")
            .replace(":", "_")
            .replace(".", "_")
            .replace("*", "_")
            .replace("?", "_")
            .replace("\"", "_")
            .replace("<", "_")
            .replace(">", "_")
            .replace("|", "_")
            .trim()
        return if (sName.isEmpty()) "" else sName
    }

    fun getFileName(track: Track): String {
        return if (track.description.isEmpty()) track.name else track.name + " - " + stringToDescFileName(track.description)
    }

    fun extractFolderNameFromEncodedUri(uriPath: String): String {
        val spath = Uri.decode(uriPath)
        val pathSeparator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) ":" else "/"
        return if (spath.contains(pathSeparator)) {
            val spathParts = spath.split(pathSeparator.toRegex()).toTypedArray()
            spathParts[spathParts.size - 1]
        } else spath
    }

    fun deleteOldFilesFromCache(days: Int) {
        class AsyncClearOldCache : Thread() {
            override fun run() {
                Thread.currentThread().priority = Thread.MIN_PRIORITY
                try { sleep(500) } catch (e: InterruptedException) { e.printStackTrace() }
                Log.w("myApp", "[#] GPSApplication.kt -  - CACHE CLEANER - Start DeleteOldFilesFromCache")
                val cacheDir = File(DIRECTORY_TEMP)
                if (cacheDir.isDirectory) {
                    val files = cacheDir.listFiles() ?: return
                    for (file in files) {
                        val lastModified = file.lastModified()
                        if (0 < lastModified) {
                            val lastMDate = Date(lastModified)
                            val today = Date(System.currentTimeMillis())
                            val diff = today.time - lastMDate.time
                            val diffDays = diff / (24 * 60 * 60 * 1000)
                            if (days <= diffDays) {
                                try {
                                    file.delete()
                                    Log.w("myApp", "[#] GPSApplication.kt - CACHE CLEANER - Cached file ${file.name} has $diffDays days: DELETED")
                                } catch (_: Exception) {}
                            }
                        }
                    }
                }
            }
        }
        AsyncClearOldCache().start()
    }

    fun addPreferenceFlag_NoBackup(flag: String) {
        val preferences_nobackup = getSharedPreferences("prefs_nobackup", Context.MODE_PRIVATE)
        val editor = preferences_nobackup.edit()
        editor.putBoolean(flag, true)
        editor.commit()
    }

    fun clearPreferenceFlag_NoBackup(flag: String) {
        val preferences_nobackup = getSharedPreferences("prefs_nobackup", Context.MODE_PRIVATE)
        val editor = preferences_nobackup.edit()
        editor.remove(flag)
        editor.commit()
    }

    fun preferenceFlagExists(flag: String): Boolean {
        val preferences_nobackup = getSharedPreferences("prefs_nobackup", Context.MODE_PRIVATE)
        return preferences_nobackup.getBoolean(flag, false)
    }

    override fun onCreate() {
        AppCompatDelegate.setDefaultNightMode(Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(applicationContext).getString("prefColorTheme", "2")!!))
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)

        super.onCreate()

        singleton = this

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "GPSLoggerServiceChannel",
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_LOW
            )
            channel.setSound(null, null)
            channel.enableLights(false)
            channel.enableVibration(false)
            channel.setSound(null, null)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        // Keep default EventBus without generated index (annotation processor still present for Java sources)
        EventBus.getDefault()
        EventBus.getDefault().register(this)

        TOAST_VERTICAL_OFFSET = (75 * resources.displayMetrics.density).toInt()

        DIRECTORY_TEMP = applicationContext.cacheDir.toString() + "/Tracks"
        DIRECTORY_FILESDIR_TRACKS = applicationContext.filesDir.toString() + "/URI"
        FILE_EMPTY_GPX = "$DIRECTORY_FILESDIR_TRACKS/empty.gpx"
        FILE_EMPTY_KML = "$DIRECTORY_FILESDIR_TRACKS/empty.kml"

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        gpsStatusListener = MyGPSStatus()

        createPrivateFolders()

        var sd = File(FILE_EMPTY_GPX)
        if (!sd.exists()) {
            try { sd.createNewFile() } catch (e: IOException) { e.printStackTrace(); Log.w("myApp", "[#] GPSApplication.kt - Unable to create ${sd.absolutePath}") }
        }
        sd = File(FILE_EMPTY_KML)
        if (!sd.exists()) {
            try { sd.createNewFile() } catch (e: IOException) { e.printStackTrace(); Log.w("myApp", "[#] GPSApplication.kt - Unable to create ${sd.absolutePath}") }
        }

        gpsDataBase = DatabaseHandler(this)

        if (gpsDataBase.getLastTrackID() == 0L) {
            gpsDataBase.addTrack(Track())
            isFirstRun = true
        }
        currentTrack = gpsDataBase.getLastTrack()

        asyncPrepareActionmodeToolbar = AsyncPrepareActionmodeToolbar()
        _externalViewerChecker = ExternalViewerChecker(applicationContext)

        LoadPreferences()

        asyncUpdateThread.start()

        val filter = IntentFilter(Intent.ACTION_SHUTDOWN)
        filter.addAction(Intent.ACTION_SCREEN_OFF)
        filter.addAction(Intent.ACTION_SCREEN_ON)
        registerReceiver(broadcastReceiver, filter)
    }

    override fun onTerminate() {
        Log.w("myApp", "[#] GPSApplication.kt - onTerminate")
        EventBus.getDefault().unregister(this)
        stopAndUnbindGPSService()
        unregisterReceiver(broadcastReceiver)
        super.onTerminate()
    }

    override fun onLocationChanged(loc: Location) {
        if (loc != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                if ((prevFix == null) || (loc.isFromMockProvider != isMockProvider)) {
                    if (loc.isFromMockProvider != isMockProvider) {
                        numberOfSatellitesTotal = NOT_AVAILABLE
                        numberOfSatellitesUsedInFix = NOT_AVAILABLE
                        isAccuracyDecimalCounter = 0
                    }
                    isMockProvider = loc.isFromMockProvider
                    if (isMockProvider) Log.w("myApp", "[#] GPSApplication.kt - Provider Type = MOCK PROVIDER") else Log.w("myApp", "[#] GPSApplication.kt - Provider Type = GPS PROVIDER")
                }
            }

            if (Math.round(loc.accuracy) != loc.accuracy.toInt()) isAccuracyDecimalCounter = 10 else isAccuracyDecimalCounter -= if (isAccuracyDecimalCounter > 0) 1 else 0

            if (loc.hasSpeed() && (loc.speed == 0f)) loc.removeBearing()
            if (loc.time <= 1388534400000L) loc.time = loc.time + 619315200000L
            val eloc = LocationExtended(loc)
            eloc.numberOfSatellites = numberOfSatellitesTotal
            eloc.numberOfSatellitesUsedInFix = numberOfSatellitesUsedInFix
            var forceRecord = false

            gpsUnavailableHandler.removeCallbacks(gpsUnavailableRunnable)
            gpsUnavailableHandler.postDelayed(gpsUnavailableRunnable, GPS_UNAVAILABLE_HANDLER_TIME.toLong())

            if (gpsStatus != GPS_OK) {
                if (gpsStatus != GPS_STABILIZING) {
                    gpsStatus = GPS_STABILIZING
                    stabilizer = numberOfStabilizationSamples
                    EventBus.getDefault().post(EventBusMSG.UPDATE_FIX)
                } else stabilizer--
                if (stabilizer <= 0) gpsStatus = GPS_OK
                prevFix = eloc
                prevRecordedFix = eloc
                isPrevFixRecorded = true
            }

            if ((prevFix != null) && (prevFix!!.location.hasSpeed()) && (eloc.location.hasSpeed()) && (gpsStatus == GPS_OK) && (isRecording)
                && (((eloc.location.speed == 0f) && (prevFix!!.location.speed != 0f)) || ((eloc.location.speed != 0f) && (prevFix!!.location.speed == 0f)))) {
                if (!isPrevFixRecorded) {                   
                    val ast = AsyncTODO()
                    ast.taskType = TASK_ADDLOCATION
                    ast.location = prevFix
                    asyncTODOQueue.add(ast)
                    prevRecordedFix = prevFix
                    isPrevFixRecorded = true
                }
                forceRecord = true
            }

            if ((isRecording) && (isPlacemarkRequested)) forceRecord = true

            if (gpsStatus == GPS_OK) {
                val ast = AsyncTODO()
                if (isRecording && ((prevRecordedFix == null)
                        || (forceRecord)
                        || ((prefGPSinterval == 0f) && (prefGPSdistance == 0f))
                        || ((prefGPSinterval > 0f) && (prefGPSdistance > 0f) && (((loc.time - prevRecordedFix!!.time) >= (prefGPSinterval * 1000.0f)) || (loc.distanceTo(prevRecordedFix!!.location) >= prefGPSdistance)))
                        || ((prefGPSinterval > 0f) && (prefGPSdistance == 0f) && ((loc.time - prevRecordedFix!!.time) >= (prefGPSinterval * 1000.0f)))
                        || ((prefGPSinterval == 0f) && (prefGPSdistance > 0f) && (loc.distanceTo(prevRecordedFix!!.location) >= prefGPSdistance))
                        || (currentTrack.numberOfLocations == 0L))
                        || (isForcedTrackpointsRecording)) {

                    if (isForcedTrackpointsRecording) {
                        val vibrator = applicationContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                        vibrator.vibrate(150)
                    }

                    prevRecordedFix = eloc
                    ast.taskType = TASK_ADDLOCATION
                    ast.location = eloc
                    asyncTODOQueue.add(ast)
                    isPrevFixRecorded = true
                } else {
                    ast.taskType = TASK_UPDATEFIX
                    ast.location = eloc
                    asyncTODOQueue.add(ast)
                    isPrevFixRecorded = false
                }
                if (isPlacemarkRequested) {
                    _currentPlacemark = LocationExtended(loc)
                    _currentPlacemark!!.numberOfSatellites = numberOfSatellitesTotal
                    _currentPlacemark!!.numberOfSatellitesUsedInFix = numberOfSatellitesUsedInFix
                    isPlacemarkRequested = false
                    EventBus.getDefault().post(EventBusMSG.UPDATE_TRACK)
                    if (!isQuickPlacemarkRequest) {
                        EventBus.getDefault().post(EventBusMSG.REQUEST_ADD_PLACEMARK)
                    } else {
                        setPlacemarkDescription("")
                        EventBus.getDefault().post(EventBusMSG.ADD_PLACEMARK)
                    }
                }
                prevFix = eloc
                isFirstFixFound = true
            }
        }
    }

    override fun onProviderDisabled(provider: String) {
        gpsStatus = GPS_DISABLED
        EventBus.getDefault().post(EventBusMSG.UPDATE_FIX)
    }

    override fun onProviderEnabled(provider: String) {
        gpsStatus = GPS_SEARCHING
        EventBus.getDefault().post(EventBusMSG.UPDATE_FIX)
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        when (status) {
            LocationProvider.OUT_OF_SERVICE -> {
                gpsUnavailableHandler.removeCallbacks(gpsUnavailableRunnable)
                gpsStatus = GPS_OUTOFSERVICE
                EventBus.getDefault().post(EventBusMSG.UPDATE_FIX)
            }
            LocationProvider.TEMPORARILY_UNAVAILABLE -> {
                gpsUnavailableHandler.removeCallbacks(gpsUnavailableRunnable)
                gpsStatus = GPS_TEMPORARYUNAVAILABLE
                EventBus.getDefault().post(EventBusMSG.UPDATE_FIX)
            }
            LocationProvider.AVAILABLE -> {
                gpsUnavailableHandler.removeCallbacks(gpsUnavailableRunnable)
            }
        }
    }

    @Subscribe
    fun onEvent(msg: Short) {
        when (msg) {
            EventBusMSG.NEW_TRACK -> {
                val ast = AsyncTODO()
                ast.taskType = TASK_NEWTRACK
                ast.location = null
                asyncTODOQueue.add(ast)
                return
            }
            EventBusMSG.ADD_PLACEMARK -> {
                val ast = AsyncTODO()
                ast.taskType = TASK_ADDPLACEMARK
                ast.location = currentPlacemark
                currentPlacemark?.description = placemarkDescription
                asyncTODOQueue.add(ast)
                return
            }
            EventBusMSG.APP_PAUSE -> {
                disableLocationUpdatesHandler.postDelayed(disableLocationUpdatesRunnable, handlerTime.toLong())
                if ((currentTrack.numberOfLocations == 0L) && (currentTrack.numberOfPlacemarks == 0L)
                    && (!isRecording) && (!isPlacemarkRequested)) stopAndUnbindGPSService()
                System.gc()
                return
            }
            EventBusMSG.APP_RESUME -> {
                isScreenOn = true
            if (asyncPrepareActionmodeToolbar?.isAlive != true) {
                    asyncPrepareActionmodeToolbar = AsyncPrepareActionmodeToolbar()
                    asyncPrepareActionmodeToolbar?.start()
                } else Log.w("myApp", "[#] GPSApplication.kt - asyncPrepareActionmodeToolbar already alive")

                disableLocationUpdatesHandler.removeCallbacks(disableLocationUpdatesRunnable)
                handlerTime = DEFAULT_SWITCHOFF_HANDLER_TIME
                setGPSLocationUpdates(true)
                if (mustUpdatePrefs) {
                    mustUpdatePrefs = false
                    LoadPreferences()
                }
                startAndBindGPSService()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val activityManager = applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                    isBackgroundActivityRestricted = activityManager.isBackgroundRestricted
                    if (isBackgroundActivityRestricted) Log.w("myApp", "[#] GPSApplication.kt - THE APP IS BACKGROUND RESTRICTED!")
                } else {
                    isBackgroundActivityRestricted = false
                }
                return
            }
            EventBusMSG.UPDATE_SETTINGS -> { mustUpdatePrefs = true; return }
        }
    }

    fun onShutdown() {
        gpsStatus = GPS_SEARCHING
        Log.w("myApp", "[#] GPSApplication.kt - onShutdown()")
        val ast = AsyncTODO()
        ast.taskType = TASK_SHUTDOWN
        ast.location = null
        asyncTODOQueue.add(ast)
        if (asyncUpdateThread.isAlive) {
            try {
                Log.w("myApp", "[#] GPSApplication.kt - onShutdown(): asyncUpdateThread isAlive. join...")
                asyncUpdateThread.join()
            } catch (e: InterruptedException) {
                e.printStackTrace()
                Log.w("myApp", "[#] GPSApplication.kt - onShutdown() InterruptedException: $e")
            }
        }
    }

    fun onScreenOff() { isScreenOn = false; Log.w("myApp", "[#] GPSApplication.kt - SCREEN_OFF") }
    fun onScreenOn() { Log.w("myApp", "[#] GPSApplication.kt - SCREEN_ON"); isScreenOn = true; EventBus.getDefault().post(EventBusMSG.UPDATE_FIX); EventBus.getDefault().post(EventBusMSG.UPDATE_TRACK) }

    fun setGPSLocationUpdates(state: Boolean) {
        enableLocationUpdatesHandler.removeCallbacks(enableLocationUpdatesRunnable)

        if (!state && !isRecording && isGPSLocationUpdatesActive && (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)) {
            gpsStatus = GPS_SEARCHING
            gpsStatusListener.disable()
            locationManager?.removeUpdates(this)
            isGPSLocationUpdatesActive = false
        }
        if (state && !isGPSLocationUpdatesActive && (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)) {
            var enabled = false
            try {
                locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, prefGPSupdatefrequency, 0f, this)
                enabled = true
            } catch (e: IllegalArgumentException) {
                gpsStatus = GPS_OUTOFSERVICE
                enableLocationUpdatesHandler.postDelayed(enableLocationUpdatesRunnable, 1000)
                Log.w("myApp", "[#] GPSApplication.kt - unable to set GPSLocationUpdates: GPS_PROVIDER not available")
            }
            if (enabled) {
                gpsStatusListener.enable()
                isGPSLocationUpdatesActive = true
                Log.w("myApp", "[#] GPSApplication.kt - setGPSLocationUpdates = true")
                numberOfStabilizationSamples = if (prefGPSupdatefrequency >= 1000) ceil(STABILIZER_TIME.toDouble() / prefGPSupdatefrequency.toDouble()).toInt() else ceil(STABILIZER_TIME.toDouble() / 1000.0).toInt()
            }
        }
    }

    fun updateGPSLocationFrequency() {
        if (isGPSLocationUpdatesActive && (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)) {
            gpsStatusListener.disable()
            locationManager?.removeUpdates(this)
            numberOfStabilizationSamples = if (prefGPSupdatefrequency >= 1000) ceil(STABILIZER_TIME.toDouble() / prefGPSupdatefrequency.toDouble()).toInt() else ceil(STABILIZER_TIME.toDouble() / 1000.0).toInt()
            gpsStatusListener.enable()
            locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, prefGPSupdatefrequency, 0f, this)
        }
    }

    fun updateGPSStatus() {
        try {
            if ((locationManager != null) && (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)) {
                satellites.updateStatus(locationManager!!.getGpsStatus(null))
                numberOfSatellitesTotal = satellites.numSatsTotal
                numberOfSatellitesUsedInFix = satellites.numSatsUsedInFix
            } else {
                numberOfSatellitesTotal = NOT_AVAILABLE
                numberOfSatellitesUsedInFix = NOT_AVAILABLE
            }
        } catch (_: NullPointerException) {
            numberOfSatellitesTotal = NOT_AVAILABLE
            numberOfSatellitesUsedInFix = NOT_AVAILABLE
        }
        if (gpsStatus != GPS_OK) {
            if (isScreenOn) EventBus.getDefault().post(EventBusMSG.UPDATE_FIX)
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    fun updateGNSSStatus(status: GnssStatus) {
        try {
            if ((locationManager != null) && (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)) {
                satellites.updateStatus(status)
                numberOfSatellitesTotal = satellites.numSatsTotal
                numberOfSatellitesUsedInFix = satellites.numSatsUsedInFix
            } else {
                numberOfSatellitesTotal = NOT_AVAILABLE
                numberOfSatellitesUsedInFix = NOT_AVAILABLE
            }
        } catch (_: NullPointerException) {
            numberOfSatellitesTotal = NOT_AVAILABLE
            numberOfSatellitesUsedInFix = NOT_AVAILABLE
        }
        if (gpsStatus != GPS_OK) {
            if (isScreenOn) EventBus.getDefault().post(EventBusMSG.UPDATE_FIX)
        }
    }

    private fun viewTrack(exportingTask: ExportingTask) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setPackage(trackViewer.packageName)
        Log.w("myApp", "[#] GPSApplication.kt - ViewTrack with ${trackViewer.packageName}")
        intent.addCategory(Intent.CATEGORY_DEFAULT)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (trackViewer.fileType.isNotEmpty()) {
            val file = File("$DIRECTORY_TEMP/", exportingTask.name + trackViewer.fileType)
            val uri = FileProvider.getUriForFile(getInstance(), "eu.basicairdata.graziano.gpslogger.fileprovider", file)
            applicationContext.grantUriPermission(trackViewer.packageName, uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.setDataAndType(uri, trackViewer.mimeType)
            try {
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Log.w("myApp", "[#] GPSApplication.kt - ViewTrack: Unable to view the track: $e")
                if (asyncPrepareActionmodeToolbar?.isAlive != true) {
                    asyncPrepareActionmodeToolbar = AsyncPrepareActionmodeToolbar()
                    asyncPrepareActionmodeToolbar?.start()
                } else Log.w("myApp", "[#] GPSApplication.kt - asyncPrepareActionmodeToolbar already alive")
            }
        }
    }

    fun UpdateTrackList() {
        val ID = gpsDataBase.getLastTrackID()
        if (ID > 0) {
            synchronized(arrayListTracks) {
                val SelectedT: ArrayList<Long> = ArrayList()
                for (T in arrayListTracks) {
                    if (T.isSelected) SelectedT.add(T.id)
                }
                arrayListTracks.clear()
                arrayListTracks.addAll(gpsDataBase.getTracksList(0, ID - 1))
                if ((ID > 1) && (gpsDataBase.getTrack(ID - 1) != null)) {
                    val fname = (ID - 1).toString() + ".png"
                    val file = File(applicationContext.filesDir.toString() + "/Thumbnails/", fname)
                    if (!file.exists()) thumbnailer = Thumbnailer(ID - 1)
                }
                if ((currentTrack.numberOfLocations) + (currentTrack.numberOfPlacemarks) > 0L) {
                    Log.w("myApp", "[#] GPSApplication.kt - Update Tracklist: current track (${currentTrack.id}) visible into the tracklist")
                    arrayListTracks.add(0, currentTrack)
                } else Log.w("myApp", "[#] GPSApplication.kt - Update Tracklist: current track not visible into the tracklist")

                for (T in arrayListTracks) {
                    for (SelT in SelectedT) {
                        if (SelT == T.id) {
                            T.isSelected = true
                            break
                        }
                    }
                }
            }
            EventBus.getDefault().post(EventBusMSG.UPDATE_TRACKLIST)
        }
    }

    private fun getSelectedTracksInternal(): ArrayList<Track> {
        val selTracks = ArrayList<Track>()
        synchronized(arrayListTracks) {
            for (T in arrayListTracks) if (T.isSelected) selTracks.add(T)
        }
        return selTracks
    }

    val numberOfSelectedTracks: Int
        get() {
            var nsel = 0
            synchronized(arrayListTracks) {
                for (T in arrayListTracks) if (T.isSelected) nsel++
            }
            return nsel
        }

    fun deselectAllTracks() {
        synchronized(arrayListTracks) {
            for (T in arrayListTracks) {
                if (T.isSelected) {
                    T.isSelected = false
                    EventBus.getDefault().post(EventBusMSGNormal(EventBusMSG.TRACKLIST_DESELECT, T.id))
                }
            }
        }
        EventBus.getDefault().post(EventBusMSG.REFRESH_TRACKLIST)
    }

    fun startExportingStatusChecker() { exportingStatusCheckRunnable.run() }

    fun executeExportingTask(exportingTask: ExportingTask) {
        when (jobType) {
            JOB_TYPE_EXPORT -> { exporter = Exporter(exportingTask, prefExportKML, prefExportGPX, prefExportTXT, prefExportFolder); exporter!!.start() }
            JOB_TYPE_VIEW -> {
                if (trackViewer.fileType == FILETYPE_GPX) exporter = Exporter(exportingTask, false, true, false, DIRECTORY_TEMP)
                if (trackViewer.fileType == FILETYPE_KML) exporter = Exporter(exportingTask, true, false, false, DIRECTORY_TEMP)
                exporter!!.start()
            }
            JOB_TYPE_SHARE -> { exporter = Exporter(exportingTask, prefExportKML, prefExportGPX, prefExportTXT, DIRECTORY_TEMP); exporter!!.start() }
            else -> {}
        }
    }

    fun loadJob(jobType: Int) {
        exportingTaskList.clear()
        synchronized(arrayListTracks) {
            for (t in arrayListTracks) {
                if (t.isSelected) {
                    val et = ExportingTask()
                    et.id = t.id
                    et.name = getFileName(t)
                    et.numberOfPoints_Total = t.numberOfLocations + t.numberOfPlacemarks
                    et.numberOfPoints_Processed = 0
                    exportingTaskList.add(et)
                }
            }
        }
        _jobsPending = exportingTaskList.size
        this.jobType = jobType
    }

    fun executeJob() {
        if (exportingTaskList.isNotEmpty()) {
            when (jobType) {
                JOB_TYPE_NONE -> {}
                JOB_TYPE_DELETE -> {
                    var s = TASK_DELETETRACKS
                    for (et in exportingTaskList) { s = "$s ${et.id}" }
                    val ast = AsyncTODO()
                    ast.taskType = s
                    ast.location = null
                    asyncTODOQueue.add(ast)
                }
                JOB_TYPE_EXPORT, JOB_TYPE_VIEW, JOB_TYPE_SHARE -> { createPrivateFolders(); startExportingStatusChecker() }
                else -> {}
            }
        } else {
            Log.w("myApp", "[#] GPSApplication.kt - Empty Job, nothing processed")
            _jobProgress = 0
            _jobsPending = 0
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    fun getBitmap(drawable: Drawable): Bitmap {
        return if (drawable is BitmapDrawable) {
            Log.w("myApp", "[#] GPSApplication.kt - getBitmap: instanceof BitmapDrawable")
            drawable.bitmap
        } else if ((Build.VERSION.SDK_INT >= 26) && (drawable is AdaptiveIconDrawable)) {
            Log.w("myApp", "[#] GPSApplication.kt - getBitmap: instanceof AdaptiveIconDrawable")
            val icon = drawable
            val w = icon.intrinsicWidth
            val h = icon.intrinsicHeight
            val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            icon.setBounds(0, 0, w, h)
            icon.draw(canvas)
            result
        } else {
            val density = applicationContext.resources.displayMetrics.density
            val defaultWidth = (24 * density).toInt()
            val defaultHeight = (24 * density).toInt()
            Log.w("myApp", "[#] GPSApplication.kt - getBitmap: !(Build.VERSION.SDK_INT >= 26) && (drawable instanceof AdaptiveIconDrawable)")
            Bitmap.createBitmap(defaultWidth, defaultHeight, Bitmap.Config.ARGB_8888)
        }
    }

    val isExportFolderWritable: Boolean
        get() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val uri = Uri.parse(prefExportFolder)
                Log.w("myApp", "[#] GPSApplication.kt - isExportFolderWritable: $prefExportFolder")
                val list = applicationContext.contentResolver.persistedUriPermissions
                for (item in list) {
                    Log.w("myApp", "[#] GPSApplication.kt - isExportFolderWritable check: ${item.uri}")
                    if (item.uri == uri) {
                        try {
                            val pickedDir = if (prefExportFolder.startsWith("content")) DocumentFile.fromTreeUri(getInstance(), uri) else DocumentFile.fromFile(File(prefExportFolder))
                            if ((pickedDir == null) || (!pickedDir.exists())) {
                                Log.w("myApp", "[#] GPSApplication.kt - THE EXPORT FOLDER DOESN'T EXIST")
                                return false
                            }
                            if ((!pickedDir.canRead()) || !pickedDir.canWrite()) {
                                Log.w("myApp", "[#] GPSApplication.kt - CANNOT READ/WRITE INTO THE EXPORT FOLDER")
                                return false
                            }
                            return true
                        } catch (e: IllegalArgumentException) {
                            Log.w("myApp", "[#] GPSApplication.kt - IllegalArgumentException - isExportFolderWritable = FALSE: ${item.uri}")
                        }
                    }
                    applicationContext.contentResolver.releasePersistableUriPermission(item.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
                Log.w("myApp", "[#] GPSApplication.kt - isExportFolderWritable = FALSE")
                return false
            } else {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    val sd = File(prefExportFolder)
                    return if (!sd.exists()) sd.mkdir() else true
                }
                return false
            }
        }

    private fun LoadPreferences() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val editor = preferences.edit()

        prefUM = Integer.parseInt(preferences.getString("prefUM", "0")!!)

        if (preferences.contains("prefUMSpeed")) {
            Log.w("myApp", "[#] GPSApplication.kt - Old setting prefUMSpeed present (${preferences.getString("prefUMSpeed", "0")} ). Converting to new preference prefUMOfSpeed.")
            val UMspd = preferences.getString("prefUMSpeed", "0")
            when (prefUM) {
                PhysicalDataFormatter.UM_METRIC -> editor.putString("prefUMOfSpeed", if (UMspd == "0") PhysicalDataFormatter.UM_SPEED_MS.toString() else PhysicalDataFormatter.UM_SPEED_KMH.toString())
                PhysicalDataFormatter.UM_IMPERIAL -> editor.putString("prefUMOfSpeed", if (UMspd == "0") PhysicalDataFormatter.UM_SPEED_FPS.toString() else PhysicalDataFormatter.UM_SPEED_MPH.toString())
                PhysicalDataFormatter.UM_NAUTICAL -> editor.putString("prefUMOfSpeed", if (UMspd == "0") PhysicalDataFormatter.UM_SPEED_KN.toString() else PhysicalDataFormatter.UM_SPEED_MPH.toString())
            }
            editor.remove("prefUMSpeed")
            editor.commit()
        } else prefUMOfSpeed = Integer.parseInt(preferences.getString("prefUMOfSpeed", "1")!!)

        if (preferences.contains("prefIsStoragePermissionChecked")) {
            editor.remove("prefIsStoragePermissionChecked")
            editor.commit()
        }

        prefGPSWeekRolloverCorrected = preferences.getBoolean("prefGPSWeekRolloverCorrected", false)
        prefShowDecimalCoordinates = preferences.getBoolean("prefShowDecimalCoordinates", false)
        prefShowLocalTime = preferences.getBoolean("prefShowLocalTime", true)

        prefGPSdistance = try { preferences.getString("prefGPSdistance", "0")!!.toFloat() } catch (_: NumberFormatException) { 0f }
        prefGPSinterval = try { preferences.getString("prefGPSinterval", "0")!!.toFloat() } catch (_: NumberFormatException) { 0f }

        Log.w("myApp", "[#] GPSApplication.kt - prefGPSdistance = $prefGPSdistance m")

        prefEGM96AltitudeCorrection = preferences.getBoolean("prefEGM96AltitudeCorrection", false)
        prefAltitudeCorrection = preferences.getString("prefAltitudeCorrection", "0")!!.toDouble()
        Log.w("myApp", "[#] GPSApplication.kt - Manual Correction set to $prefAltitudeCorrection m")
        prefExportKML = preferences.getBoolean("prefExportKML", true)
        prefExportGPX = preferences.getBoolean("prefExportGPX", true)
        prefExportTXT = preferences.getBoolean("prefExportTXT", false)
        prefKMLAltitudeMode = Integer.parseInt(preferences.getString("prefKMLAltitudeMode", "1")!!)
        prefGPXVersion = Integer.parseInt(preferences.getString("prefGPXVersion", "100")!!)
        prefShowTrackStatsType = Integer.parseInt(preferences.getString("prefShowTrackStatsType", "0")!!)
        prefShowDirections = Integer.parseInt(preferences.getString("prefShowDirections", "0")!!)

        val altcorm = preferences.getString("prefAltitudeCorrection", "0")!!.toDouble()
        val altcor = if (preferences.getString("prefUM", "0") == "0") altcorm else altcorm * PhysicalDataFormatter.M_TO_FT
        val distfilterm = preferences.getString("prefGPSdistance", "0")!!.toDouble()
        val distfilter = if (preferences.getString("prefUM", "0") == "0") distfilterm else distfilterm * PhysicalDataFormatter.M_TO_FT
        editor.putString("prefAltitudeCorrectionRaw", altcor.toString())
        editor.putString("prefGPSdistanceRaw", distfilter.toString())
        editor.commit()

        _prefExportFolder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) preferences.getString("prefExportFolder", "") ?: "" else Environment.getExternalStorageDirectory().toString() + "/GPSLogger"
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) prefExportFolder = _prefExportFolder

        val oldGPSupdatefrequency = prefGPSupdatefrequency
        prefGPSupdatefrequency = preferences.getString("prefGPSupdatefrequency", "1000")!!.toLong()
        if (oldGPSupdatefrequency != prefGPSupdatefrequency) updateGPSLocationFrequency()

        if (!prefExportKML && !prefExportGPX && !prefExportTXT) {
            editor.putBoolean("prefExportGPX", true)
            editor.commit()
            prefExportGPX = true
        }

        val egm96 = EGM96.getInstance()
        egm96?.loadGrid(prefExportFolder, applicationContext.filesDir.toString())

        EventBus.getDefault().post(EventBusMSG.APPLY_SETTINGS)
        EventBus.getDefault().post(EventBusMSG.UPDATE_FIX)
        EventBus.getDefault().post(EventBusMSG.UPDATE_TRACK)
        EventBus.getDefault().post(EventBusMSG.UPDATE_TRACKLIST)
    }

    private class AsyncTODO { var taskType: String = ""; var location: LocationExtended? = null }
    private val asyncTODOQueue: BlockingQueue<AsyncTODO> = LinkedBlockingQueue()

    private inner class AsyncUpdateThreadClass : Thread() {
        private lateinit var track: Track
        private var locationExtended: LocationExtended? = null
        override fun run() {
            track = currentTrack!!
            EventBus.getDefault().post(EventBusMSG.UPDATE_TRACK)
            UpdateTrackList()

            if (!prefGPSWeekRolloverCorrected) {
                if (!isFirstRun) {
                    Log.w("myApp", "[#] GPSApplication.kt - CORRECTING DATA FOR GPS WEEK ROLLOVER")
                    gpsDataBase.CorrectGPSWeekRollover()
                    Log.w("myApp", "[#] GPSApplication.kt - DATA FOR GPS WEEK ROLLOVER CORRECTED")
                    UpdateTrackList()
                    Log.w("myApp", "[#] GPSApplication.kt - TRACKLIST UPDATED WITH THE CORRECTED NAMES")
                }
                prefGPSWeekRolloverCorrected = true
                val editor = PreferenceManager.getDefaultSharedPreferences(applicationContext).edit()
                editor.putBoolean("prefGPSWeekRolloverCorrected", true)
                editor.commit()
            }

            var shutdown = false
            while (!shutdown) {
                val asyncTODO: AsyncTODO = try { asyncTODOQueue.take() } catch (e: InterruptedException) { Log.w("myApp", "[!] Buffer not available: ${e.message}"); break }

                if (asyncTODO.taskType == TASK_SHUTDOWN) { shutdown = true; Log.w("myApp", "[#] GPSApplication.kt - AsyncUpdateThreadClass: SHUTDOWN EVENT.") }

                if (asyncTODO.taskType == TASK_NEWTRACK) {
                if ((track.numberOfLocations != 0L) || (track.numberOfPlacemarks != 0L)) {
                        var fname = (track.id + 1).toString() + ".png"
                        var file = File(applicationContext.filesDir.toString() + "/Thumbnails/", fname)
                        if (file.exists()) file.delete()
                        fname = (track.id + 2).toString() + ".png"
                        file = File(applicationContext.filesDir.toString() + "/Thumbnails/", fname)
                        if (file.exists()) file.delete()
                        track = Track()
                        track.id = gpsDataBase.addTrack(track)
                        Log.w("myApp", "[#] GPSApplication.kt - TASK_NEWTRACK: ${track.id}")
                        currentTrack = track
                        UpdateTrackList()
                    } else Log.w("myApp", "[#] GPSApplication.kt - TASK_NEWTRACK: Track ${track.id} already empty (New track not created)")
                    currentTrack = track
                    EventBus.getDefault().post(EventBusMSG.UPDATE_TRACK)
                }

                if (asyncTODO.taskType == TASK_ADDLOCATION) {
                    locationExtended = LocationExtended(asyncTODO.location!!.location)
                    locationExtended!!.numberOfSatellites = asyncTODO.location!!.numberOfSatellites
                    locationExtended!!.numberOfSatellitesUsedInFix = asyncTODO.location!!.numberOfSatellitesUsedInFix
                    currentLocationExtended = locationExtended
                    if (isScreenOn) EventBus.getDefault().post(EventBusMSG.UPDATE_FIX)
                    track.add(locationExtended!!)
                    gpsDataBase.addLocationToTrack(locationExtended!!, track)
                    currentTrack = track
                    if (isScreenOn) EventBus.getDefault().post(EventBusMSG.UPDATE_TRACK)
                    if ((currentTrack.numberOfLocations + currentTrack.numberOfPlacemarks) == 1L) UpdateTrackList()
                }

                if (asyncTODO.taskType == TASK_ADDPLACEMARK) {
                    locationExtended = LocationExtended(asyncTODO.location!!.location)
                    locationExtended!!.description = asyncTODO.location!!.description
                    locationExtended!!.numberOfSatellites = asyncTODO.location!!.numberOfSatellites
                    locationExtended!!.numberOfSatellitesUsedInFix = asyncTODO.location!!.numberOfSatellitesUsedInFix
                    track.addPlacemark(locationExtended!!)
                    gpsDataBase.addPlacemarkToTrack(locationExtended!!, track)
                    currentTrack = track
                    EventBus.getDefault().post(EventBusMSG.UPDATE_TRACK)
                    if ((currentTrack.numberOfLocations + currentTrack.numberOfPlacemarks) == 1L) UpdateTrackList()
                }

                if (asyncTODO.taskType == TASK_UPDATEFIX) {
                    currentLocationExtended = LocationExtended(asyncTODO.location!!.location)
                    currentLocationExtended!!.numberOfSatellites = asyncTODO.location!!.numberOfSatellites
                    currentLocationExtended!!.numberOfSatellitesUsedInFix = asyncTODO.location!!.numberOfSatellitesUsedInFix
                    if (isScreenOn) EventBus.getDefault().post(EventBusMSG.UPDATE_FIX)
                }

                if (asyncTODO.taskType.startsWith(TASK_DELETETRACKS)) {
                    val sTokens = asyncTODO.taskType.substring(asyncTODO.taskType.indexOf(" ") + 1)
                    Log.w("myApp", "[#] GPSApplication.kt - DELETING ($sTokens)")
                    val tokens: MutableList<String> = ArrayList()
                    val tokenizer = StringTokenizer(sTokens, " ")
                    while (tokenizer.hasMoreElements()) tokens.add(tokenizer.nextToken())
                    if (tokens.isNotEmpty()) {
                        _jobProgress = 0
                        val tracksToBeDeleted = tokens.size
                        var tracksDeleted = 0
                        for (s in tokens) {
                            var trackToDelete: Track? = null
                            val i = Integer.valueOf(s)
                            if (i.toLong() != currentTrack.id) {
                                synchronized(arrayListTracks) {
                                    for (t in arrayListTracks) {
                                        if (t.id == i.toLong()) {
                                            trackToDelete = t
                                            gpsDataBase.DeleteTrack(i.toLong())
                                            Log.w("myApp", "[#] GPSApplication.kt - TASK_DELETE_TRACKS: Track $i deleted.")
                                            arrayListTracks.remove(t)
                                            break
                                        }
                                    }
                                }
                                if (trackToDelete != null) {
                                    val foundFiles = fileFind(DIRECTORY_TEMP, trackToDelete!!.name)
                                    if (foundFiles != null) {
                                        for (f in foundFiles) {
                                            Log.w("myApp", "[#] GPSApplication.kt - Deleting: ${f.absolutePath}")
                                            fileDelete(f.absolutePath)
                                        }
                                    }
                                    fileDelete(applicationContext.filesDir.toString() + "/Thumbnails/" + trackToDelete!!.id + ".png")
                                    tracksDeleted++
                                    _jobProgress = (1000.0 * tracksDeleted.toDouble() / tracksToBeDeleted.toDouble()).roundToInt()
                                    EventBus.getDefault().post(EventBusMSG.UPDATE_JOB_PROGRESS)
                                    if (_jobsPending > 0) _jobsPending--
                                }
                            } else {
                                Log.w("myApp", "[#] GPSApplication.kt - TASK_DELETE_TRACKS: Unable to delete the current track!")
                                tracksDeleted++
                                _jobProgress = (1000.0 * tracksDeleted.toDouble() / tracksToBeDeleted.toDouble()).roundToInt()
                                EventBus.getDefault().post(EventBusMSG.UPDATE_JOB_PROGRESS)
                                if (_jobsPending > 0) _jobsPending--
                            }
                        }
                    }
                    _jobProgress = 0
                    EventBus.getDefault().post(EventBusMSG.UPDATE_JOB_PROGRESS)
                    EventBus.getDefault().post(EventBusMSG.NOTIFY_TRACKS_DELETED)
                }
            }
        }
    }

    private inner class AsyncPrepareActionmodeToolbar : Thread() {
        override fun run() {
            isContextMenuShareVisible = false
            isContextMenuViewVisible = false
            viewInApp = ""
            viewInAppIcon = null

            val pm = packageManager

            val intentShare = Intent(Intent.ACTION_SEND_MULTIPLE)
            intentShare.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intentShare.type = "text/xml"
            if (intentShare.resolveActivity(pm) != null) isContextMenuShareVisible = true

            _externalViewerChecker.makeExternalViewersList()
            val pn = android.preference.PreferenceManager.getDefaultSharedPreferences(applicationContext).getString("prefTracksViewer", "")
            if (!_externalViewerChecker.isEmpty()) {
                isContextMenuViewVisible = true
                for (ev in _externalViewerChecker.getExternalViewersList()) {
                    if ((ev.packageName == pn) || (_externalViewerChecker.size() == 1)) {
                        viewInApp = ev.label + if (ev.fileType == FILETYPE_GPX) " (GPX)" else " (KML)"
                        val bitmap = if (Build.VERSION.SDK_INT >= 26) getBitmap(ev.icon!!) else (ev.icon as BitmapDrawable).bitmap
                        viewInAppIcon = BitmapDrawable(resources, Bitmap.createScaledBitmap(bitmap, (24 * resources.displayMetrics.density).toInt(), (24 * resources.displayMetrics.density).toInt(), true))
                    }
                }
            } else isContextMenuViewVisible = false

            Log.w("myApp", "[#] GPSApplication.kt - Tracklist ContextMenu prepared")
            EventBus.getDefault().post(EventBusMSG.UPDATE_ACTIONBAR)
        }
    }

    inner class Thumbnailer(id: Long) {
        private var id: Long
        private var numberOfLocations: Long
        private val drawPaint = Paint()
        private val bgPaint = Paint()
        private val endDotdrawPaint = Paint()
        private val endDotBGPaint = Paint()
        private val size = resources.getDimension(R.dimen.thumbSize).toInt()
        private val margin = ceil(resources.getDimension(R.dimen.thumbLineWidth) * 3.0).toInt()
        private val sizeMinusMargins = size - 2 * margin
        private var minLatitude = 0.0
        private var minLongitude = 0.0
        private var distanceProportion = 0.0
        private var drawScale = 0.0
        private var latOffset = 0.0
        private var lonOffset = 0.0

        init {
            val track = gpsDataBase.getTrack(id)
            if ((track.numberOfLocations > 2) && (track.distance >= 15) && (track.validMap != 0)) {
                this.id = track.id
                numberOfLocations = track.numberOfLocations
                drawPaint.color = resources.getColor(R.color.colorThumbnailLineColor)
                drawPaint.isAntiAlias = true
                drawPaint.strokeWidth = resources.getDimension(R.dimen.thumbLineWidth)
                drawPaint.style = Paint.Style.STROKE
                drawPaint.strokeJoin = Paint.Join.ROUND
                drawPaint.strokeCap = Paint.Cap.ROUND

                bgPaint.color = Color.BLACK
                bgPaint.isAntiAlias = true
                bgPaint.strokeWidth = resources.getDimension(R.dimen.thumbLineWidth) * 3
                bgPaint.style = Paint.Style.STROKE
                bgPaint.strokeJoin = Paint.Join.ROUND
                bgPaint.strokeCap = Paint.Cap.ROUND

                endDotdrawPaint.color = resources.getColor(R.color.colorThumbnailLineColor)
                endDotdrawPaint.isAntiAlias = true
                endDotdrawPaint.strokeWidth = resources.getDimension(R.dimen.thumbLineWidth) * 2.5f
                endDotdrawPaint.style = Paint.Style.STROKE
                endDotdrawPaint.strokeJoin = Paint.Join.ROUND
                endDotdrawPaint.strokeCap = Paint.Cap.ROUND

                endDotBGPaint.color = Color.BLACK
                endDotBGPaint.isAntiAlias = true
                endDotBGPaint.strokeWidth = resources.getDimension(R.dimen.thumbLineWidth) * 4.5f
                endDotBGPaint.style = Paint.Style.STROKE
                endDotBGPaint.strokeJoin = Paint.Join.ROUND
                endDotBGPaint.strokeCap = Paint.Cap.ROUND

                val midLatitude = (track.latitudeMax + track.latitudeMin) / 2
                val angleFromEquator = abs(midLatitude)
                distanceProportion = cos(Math.toRadians(angleFromEquator))
                drawScale = max(track.latitudeMax - track.latitudeMin, distanceProportion * (track.longitudeMax - track.longitudeMin))
                latOffset = sizeMinusMargins * (1 - (track.latitudeMax - track.latitudeMin) / drawScale) / 2
                lonOffset = sizeMinusMargins * (1 - (distanceProportion * (track.longitudeMax - track.longitudeMin) / drawScale)) / 2
                minLatitude = track.latitudeMin
                minLongitude = track.longitudeMin

                val asyncThumbnailThreadClass = AsyncThumbnailThreadClass()
                asyncThumbnailThreadClass.start()
            } else {
                this.id = id
                numberOfLocations = 0
            }
        }

        private inner class AsyncThumbnailThreadClass : Thread() {
            override fun run() {
                Thread.currentThread().priority = Thread.MIN_PRIORITY
                val fname = "$id.png"
                val file = File(applicationContext.filesDir.toString() + "/Thumbnails/", fname)
                if (file.exists()) file.delete()
                if (drawScale > 0) {
                    val groupOfLocations = 200
                    val path = Path()
                    val latlngList: MutableList<LatLng> = ArrayList()
                    var i = 0
                    while (i.toLong() < numberOfLocations) {
                        latlngList.addAll(gpsDataBase.getLatLngList(id, i.toLong(), (i + groupOfLocations - 1).toLong()))
                        i += groupOfLocations
                    }
                    if (latlngList.isNotEmpty()) {
                        val thumbBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                        val thumbCanvas = Canvas(thumbBitmap)
                        for (j in latlngList.indices) {
                            if (j == 0) path.moveTo((lonOffset + margin + sizeMinusMargins * ((latlngList[j].longitude - minLongitude) * distanceProportion / drawScale)).toFloat(), (-latOffset + size - (margin + sizeMinusMargins * ((latlngList[j].latitude - minLatitude) / drawScale))).toFloat())
                            else path.lineTo((lonOffset + margin + sizeMinusMargins * ((latlngList[j].longitude - minLongitude) * distanceProportion / drawScale)).toFloat(), (-latOffset + size - (margin + sizeMinusMargins * ((latlngList[j].latitude - minLatitude) / drawScale))).toFloat())
                        }
                        thumbCanvas.drawPath(path, bgPaint)
                        thumbCanvas.drawPoint((lonOffset + margin + sizeMinusMargins * ((latlngList[latlngList.size - 1].longitude - minLongitude) * distanceProportion / drawScale)).toFloat(), (-latOffset + size - (margin + sizeMinusMargins * ((latlngList[latlngList.size - 1].latitude - minLatitude) / drawScale))).toFloat(), endDotBGPaint)
                        thumbCanvas.drawPath(path, drawPaint)
                        thumbCanvas.drawPoint((lonOffset + margin + sizeMinusMargins * ((latlngList[latlngList.size - 1].longitude - minLongitude) * distanceProportion / drawScale)).toFloat(), (-latOffset + size - (margin + sizeMinusMargins * ((latlngList[latlngList.size - 1].latitude - minLatitude) / drawScale))).toFloat(), endDotdrawPaint)
                        try {
                            val out = FileOutputStream(file)
                            thumbBitmap.compress(Bitmap.CompressFormat.PNG, 60, out)
                            out.flush()
                            out.close()
                        } catch (e: Exception) { e.printStackTrace() }
                        EventBus.getDefault().post(EventBusMSG.REFRESH_TRACKLIST)
                    }
                }
            }
        }
    }
}


