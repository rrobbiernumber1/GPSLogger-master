package eu.basicairdata.graziano.gpslogger

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.Locale

class FragmentGPSFix : Fragment() {

    private val phdformatter = PhysicalDataFormatter()
    private val gpsApp = GPSApplication.getInstance()
    private var isAWarningClicked = false

    private var flGPSFix: FrameLayout? = null
    private var tvLatitude: TextView? = null
    private var tvLongitude: TextView? = null
    private var tvLatitudeUM: TextView? = null
    private var tvLongitudeUM: TextView? = null
    private var tvAltitude: TextView? = null
    private var tvAltitudeUM: TextView? = null
    private var tvSpeed: TextView? = null
    private var tvSpeedUM: TextView? = null
    private var tvBearing: TextView? = null
    private var tvAccuracy: TextView? = null
    private var tvAccuracyUM: TextView? = null
    private var tvGPSFixStatus: TextView? = null
    private var tvDirectionUM: TextView? = null
    private var tvTime: TextView? = null
    private var tvTimeLabel: TextView? = null
    private var tvSatellites: TextView? = null

    private var tlCoordinates: TableLayout? = null
    private var tlAltitude: TableLayout? = null
    private var tlSpeed: TableLayout? = null
    private var tlBearing: TableLayout? = null
    private var tlAccuracy: TableLayout? = null
    private var tlTime: TableLayout? = null
    private var tlSatellites: TableLayout? = null

    private var cvWarningLocationDenied: CardView? = null
    private var cvWarningGPSDisabled: CardView? = null
    private var cvWarningBackgroundRestricted: CardView? = null
    private var cvWarningBatteryOptimised: CardView? = null

    private var llTimeSatellites: LinearLayout? = null
    private var iwWarningBatteryOptimisedClose: ImageView? = null
    private var iwCopyCoordinatesToClipboard: ImageView? = null

    private var phdLatitude: PhysicalData? = null
    private var phdLongitude: PhysicalData? = null
    private var phdAltitude: PhysicalData? = null
    private var phdSpeed: PhysicalData? = null
    private var phdBearing: PhysicalData? = null
    private var phdAccuracy: PhysicalData? = null
    private var phdTime: PhysicalData? = null

    private var location: LocationExtended? = null
    private var AltitudeManualCorrection = 0.0
    private var prefDirections = 0
    private var GPSStatus = GPSApplication.GPS_DISABLED
    private var EGMAltitudeCorrection = false
    private var isValidAltitude = false
    private var isBackgroundActivityRestricted = false
    private var powerManager: PowerManager? = null

    private val viewTreeObserverOnGLL: ViewTreeObserver.OnGlobalLayoutListener = object : ViewTreeObserver.OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            flGPSFix?.viewTreeObserver?.let {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                    @Suppress("DEPRECATION")
                    it.removeGlobalOnLayoutListener(this)
                } else {
                    it.removeOnGlobalLayoutListener(this)
                }
            }
            val viewHeight = (tlSpeed?.measuredHeight ?: 0) + (6 * resources.displayMetrics.density).toInt()
            val layoutHeight = (flGPSFix?.height ?: 0) - (6 * resources.displayMetrics.density).toInt()
            val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
            val isTimeAndSatellitesVisible = if (isPortrait) layoutHeight >= 6 * viewHeight else layoutHeight >= (3.9 * viewHeight).toInt()
            GPSApplication.getInstance().setSpaceForExtraTilesAvailable(isTimeAndSatellitesVisible)
            update()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_gpsfix, container, false)

        flGPSFix = view.findViewById(R.id.id_fragmentgpsfixFrameLayout)

        tvLatitude = view.findViewById(R.id.id_textView_Latitude)
        tvLongitude = view.findViewById(R.id.id_textView_Longitude)
        tvLatitudeUM = view.findViewById(R.id.id_textView_LatitudeUM)
        tvLongitudeUM = view.findViewById(R.id.id_textView_LongitudeUM)
        tvAltitude = view.findViewById(R.id.id_textView_Altitude)
        tvAltitudeUM = view.findViewById(R.id.id_textView_AltitudeUM)
        tvSpeed = view.findViewById(R.id.id_textView_Speed)
        tvSpeedUM = view.findViewById(R.id.id_textView_SpeedUM)
        tvBearing = view.findViewById(R.id.id_textView_Bearing)
        tvAccuracy = view.findViewById(R.id.id_textView_Accuracy)
        tvAccuracyUM = view.findViewById(R.id.id_textView_AccuracyUM)
        tvGPSFixStatus = view.findViewById(R.id.id_textView_GPSFixStatus)
        tvDirectionUM = view.findViewById(R.id.id_textView_BearingUM)
        tvTime = view.findViewById(R.id.id_textView_Time)
        tvTimeLabel = view.findViewById(R.id.id_textView_TimeLabel)
        tvSatellites = view.findViewById(R.id.id_textView_Satellites)

        cvWarningLocationDenied = view.findViewById(R.id.card_view_warning_location_denied)
        cvWarningGPSDisabled = view.findViewById(R.id.card_view_warning_enable_location_service)
        cvWarningBackgroundRestricted = view.findViewById(R.id.card_view_warning_background_restricted)
        cvWarningBatteryOptimised = view.findViewById(R.id.card_view_warning_battery_optimised)

        tlCoordinates = view.findViewById(R.id.id_TableLayout_Coordinates)
        tlAltitude = view.findViewById(R.id.id_TableLayout_Altitude)
        tlSpeed = view.findViewById(R.id.id_TableLayout_Speed)
        tlBearing = view.findViewById(R.id.id_TableLayout_Bearing)
        tlAccuracy = view.findViewById(R.id.id_TableLayout_Accuracy)
        tlTime = view.findViewById(R.id.id_TableLayout_Time)
        tlSatellites = view.findViewById(R.id.id_TableLayout_Satellites)

        iwWarningBatteryOptimisedClose = view.findViewById(R.id.id_warning_battery_optimised_close)
        iwCopyCoordinatesToClipboard = view.findViewById(R.id.id_coordinates_copy)
        llTimeSatellites = view.findViewById(R.id.id_linearLayout_Time_Satellites)

        powerManager = gpsApp.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager

        cvWarningGPSDisabled?.setOnClickListener {
            if (!isAWarningClicked && (GPSStatus == GPSApplication.GPS_DISABLED)) {
                isAWarningClicked = true
                val callGPSSettingIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                try {
                    startActivityForResult(callGPSSettingIntent, 0)
                } catch (_: Exception) {
                    isAWarningClicked = false
                }
            }
        }

        iwWarningBatteryOptimisedClose?.setOnClickListener {
            gpsApp.setBatteryOptimisedWarningVisible(false)
            update()
        }

        iwCopyCoordinatesToClipboard?.setOnClickListener {
            val clipboard = requireActivity().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(
                "Coordinates",
                String.format(Locale.US, "%.9f", location?.latitude) + ", " + String.format(Locale.US, "%.9f", location?.longitude)
            )
            clipboard.setPrimaryClip(clip)
            val toast = Toast.makeText(gpsApp.applicationContext, gpsApp.getString(R.string.toast_coordinates_copied_to_clipboard), Toast.LENGTH_SHORT)
            toast.setGravity(Gravity.BOTTOM, 0, GPSApplication.TOAST_VERTICAL_OFFSET)
            toast.show()
        }

        cvWarningBackgroundRestricted?.setOnClickListener {
            if (!isAWarningClicked && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)) {
                isAWarningClicked = true
                val callAppSettingIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", requireContext().packageName, null)
                callAppSettingIntent.data = uri
                try {
                    startActivityForResult(callAppSettingIntent, 0)
                } catch (_: Exception) {
                    isAWarningClicked = false
                }
            }
        }

        cvWarningBatteryOptimised?.setOnClickListener {
            if (!isAWarningClicked && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)) {
                isAWarningClicked = true
                val intent = Intent()
                intent.action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                try {
                    requireContext().startActivity(intent)
                } catch (_: Exception) {
                    isAWarningClicked = false
                }
            }
        }

        cvWarningLocationDenied?.setOnClickListener {
            if (!isAWarningClicked) {
                (activity as GPSActivity).checkLocationAndNotificationPermission()
            }
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        isAWarningClicked = false
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this)
        }
        EventBus.getDefault().register(this)

        flGPSFix?.viewTreeObserver?.addOnGlobalLayoutListener(viewTreeObserverOnGLL)
        update()
    }

    override fun onPause() {
        flGPSFix?.viewTreeObserver?.let {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                @Suppress("DEPRECATION")
                it.removeGlobalOnLayoutListener(viewTreeObserverOnGLL)
            } else {
                it.removeOnGlobalLayoutListener(viewTreeObserverOnGLL)
            }
        }
        EventBus.getDefault().unregister(this)
        super.onPause()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(msg: Short) {
        if (msg == EventBusMSG.UPDATE_FIX) {
            update()
        }
    }

    fun update() {
        location = gpsApp.currentLocationExtended
        AltitudeManualCorrection = gpsApp.prefAltitudeCorrection
        prefDirections = gpsApp.prefShowDirections
        GPSStatus = gpsApp.getGPSStatus()
        EGMAltitudeCorrection = gpsApp.prefEGM96AltitudeCorrection
        isBackgroundActivityRestricted = gpsApp.isBackgroundActivityRestricted

        if (isAdded) {
            if ((location != null) && (GPSStatus == GPSApplication.GPS_OK)) {
                phdLatitude = phdformatter.format(location!!.latitude, PhysicalDataFormatter.FORMAT_LATITUDE)
                phdLongitude = phdformatter.format(location!!.longitude, PhysicalDataFormatter.FORMAT_LONGITUDE)
                phdAltitude = phdformatter.format(location!!.getAltitudeCorrected(AltitudeManualCorrection, EGMAltitudeCorrection), PhysicalDataFormatter.FORMAT_ALTITUDE)
                phdSpeed = phdformatter.format(location!!.speed, PhysicalDataFormatter.FORMAT_SPEED)
                phdBearing = phdformatter.format(location!!.bearing, PhysicalDataFormatter.FORMAT_BEARING)
                phdAccuracy = phdformatter.format(location!!.accuracy, PhysicalDataFormatter.FORMAT_ACCURACY)
                phdTime = phdformatter.format(location!!.time, PhysicalDataFormatter.FORMAT_TIME)

                tvLatitude?.text = phdLatitude?.value
                tvLongitude?.text = phdLongitude?.value
                tvLatitudeUM?.text = phdLatitude?.um
                tvLongitudeUM?.text = phdLongitude?.um
                tvAltitude?.text = phdAltitude?.value
                tvAltitudeUM?.text = phdAltitude?.um
                tvSpeed?.text = phdSpeed?.value
                tvSpeedUM?.text = phdSpeed?.um
                tvBearing?.text = phdBearing?.value
                tvAccuracy?.text = phdAccuracy?.value
                tvAccuracyUM?.text = phdAccuracy?.um
                tvTime?.text = phdTime?.value
                tvTimeLabel?.text = if (phdTime?.um?.isEmpty() == true) getString(R.string.time) else String.format(
                    Locale.getDefault(), "%s (%s)", getString(R.string.time), phdTime?.um
                )
                tvSatellites?.text = if (location!!.getNumberOfSatellitesUsedInFix() != GPSApplication.NOT_AVAILABLE)
                    location!!.getNumberOfSatellitesUsedInFix().toString() + "/" + location!!.getNumberOfSatellites() else ""

                isValidAltitude = EGMAltitudeCorrection && (location!!.getAltitudeEGM96Correction() != GPSApplication.NOT_AVAILABLE.toDouble())
                val cPrimary = resources.getColor(R.color.textColorPrimary)
                val cSecondary = resources.getColor(R.color.textColorSecondary)
                tvAltitude?.setTextColor(if (isValidAltitude) cPrimary else cSecondary)
                tvAltitudeUM?.setTextColor(if (isValidAltitude) cPrimary else cSecondary)

                tvGPSFixStatus?.visibility = View.GONE
                tvDirectionUM?.visibility = if (prefDirections == 0) View.GONE else View.VISIBLE

                tlCoordinates?.visibility = if (phdLatitude?.value == "") View.INVISIBLE else View.VISIBLE
                tlAltitude?.visibility = if (phdAltitude?.value == "") View.INVISIBLE else View.VISIBLE
                tlSpeed?.visibility = if (phdSpeed?.value == "") View.INVISIBLE else View.VISIBLE
                tlBearing?.visibility = if (phdBearing?.value == "") View.INVISIBLE else View.VISIBLE
                tlAccuracy?.visibility = if (phdAccuracy?.value == "") View.INVISIBLE else View.VISIBLE
                tlTime?.visibility = View.VISIBLE
                tlSatellites?.visibility = if (location!!.getNumberOfSatellitesUsedInFix() == GPSApplication.NOT_AVAILABLE) View.INVISIBLE else View.VISIBLE

                llTimeSatellites?.visibility = if (gpsApp.isSpaceForExtraTilesAvailable) View.VISIBLE else View.GONE

                tvGPSFixStatus?.visibility = View.INVISIBLE
                cvWarningBackgroundRestricted?.visibility = View.GONE
                cvWarningBatteryOptimised?.visibility = View.GONE
                cvWarningGPSDisabled?.visibility = View.GONE
                cvWarningLocationDenied?.visibility = View.GONE
            } else {
                tlCoordinates?.visibility = View.INVISIBLE
                tlAltitude?.visibility = View.INVISIBLE
                tlSpeed?.visibility = View.INVISIBLE
                tlBearing?.visibility = View.INVISIBLE
                tlAccuracy?.visibility = View.INVISIBLE
                tlTime?.visibility = View.INVISIBLE
                tlSatellites?.visibility = View.INVISIBLE

                var ssat = ""
                if (((GPSStatus == GPSApplication.GPS_SEARCHING) || (GPSStatus == GPSApplication.GPS_STABILIZING) || (GPSStatus == GPSApplication.GPS_TEMPORARYUNAVAILABLE)) && (gpsApp.numberOfSatellitesUsedInFix != GPSApplication.NOT_AVAILABLE)) {
                    ssat = "\n\n" + gpsApp.numberOfSatellitesUsedInFix + "/" + gpsApp.numberOfSatellitesTotal + " " + getString(R.string.satellites)
                }

                tvGPSFixStatus?.visibility = View.VISIBLE
                when (GPSStatus) {
                    GPSApplication.GPS_DISABLED -> {
                        tvGPSFixStatus?.setText(R.string.gps_disabled)
                        cvWarningGPSDisabled?.visibility = View.VISIBLE
                    }
                    GPSApplication.GPS_OUTOFSERVICE -> {
                        tvGPSFixStatus?.setText(R.string.gps_out_of_service)
                        cvWarningGPSDisabled?.visibility = View.GONE
                    }
                    GPSApplication.GPS_TEMPORARYUNAVAILABLE, GPSApplication.GPS_SEARCHING -> {
                        tvGPSFixStatus?.text = getString(R.string.gps_searching) + ssat
                        cvWarningGPSDisabled?.visibility = View.GONE
                    }
                    GPSApplication.GPS_STABILIZING -> {
                        tvGPSFixStatus?.text = getString(R.string.gps_stabilizing) + ssat
                        cvWarningGPSDisabled?.visibility = View.GONE
                    }
                }

                cvWarningBackgroundRestricted?.visibility = if (isBackgroundActivityRestricted) View.VISIBLE else View.GONE

                if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    && !(powerManager?.isIgnoringBatteryOptimizations(gpsApp.packageName) ?: true)
                    && gpsApp.isBatteryOptimisedWarningVisible
                ) {
                    cvWarningBatteryOptimised?.visibility = View.VISIBLE
                } else {
                    cvWarningBatteryOptimised?.visibility = View.GONE
                }

                if (ContextCompat.checkSelfPermission(requireActivity(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    tvGPSFixStatus?.setText(R.string.gps_not_accessible)
                    cvWarningLocationDenied?.visibility = View.VISIBLE
                } else {
                    cvWarningLocationDenied?.visibility = View.GONE
                }
            }
        }
    }
}


