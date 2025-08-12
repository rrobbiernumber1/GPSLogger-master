package eu.basicairdata.graziano.gpslogger

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class FragmentTrack : Fragment() {
    private val phdformatter = PhysicalDataFormatter()
    private val gpsApp = GPSApplication.getInstance()

    private var flTrack: FrameLayout? = null

    private var tvDuration: TextView? = null
    private var tvTrackName: TextView? = null
    private var tvTrackID: TextView? = null
    private var tvDistance: TextView? = null
    private var tvDistanceUM: TextView? = null
    private var tvAnnotations: TextView? = null
    private var tvTrackpoints: TextView? = null
    private var tvMaxSpeed: TextView? = null
    private var tvMaxSpeedUM: TextView? = null
    private var tvAverageSpeed: TextView? = null
    private var tvAverageSpeedUM: TextView? = null
    private var tvAltitudeGap: TextView? = null
    private var tvAltitudeGapUM: TextView? = null
    private var tvOverallDirection: TextView? = null
    private var tvTrackStatus: TextView? = null
    private var tvDirectionUM: TextView? = null

    private var tlTrack: TableLayout? = null
    private var tlTrackpoints: TableLayout? = null
    private var tlAnnotations: TableLayout? = null
    private var tlDuration: TableLayout? = null
    private var tlSpeedMax: TableLayout? = null
    private var tlSpeedAvg: TableLayout? = null
    private var tlDistance: TableLayout? = null
    private var tlAltitudeGap: TableLayout? = null
    private var tlOverallDirection: TableLayout? = null

    private var llTrackpointsAnnotations: LinearLayout? = null

    private var phdDuration: PhysicalData? = null
    private var phdSpeedMax: PhysicalData? = null
    private var phdSpeedAvg: PhysicalData? = null
    private var phdDistance: PhysicalData? = null
    private var phdAltitudeGap: PhysicalData? = null
    private var phdOverallDirection: PhysicalData? = null

    private var fTrackID = ""
    private var fTrackName = ""
    private var track: Track? = null
    private var prefDirections = 0
    private var EGMAltitudeCorrection = false
    private var isValidAltitude = false

    private val viewTreeObserverOnGLL: ViewTreeObserver.OnGlobalLayoutListener = object : ViewTreeObserver.OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            flTrack?.viewTreeObserver?.let {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                    @Suppress("DEPRECATION")
                    it.removeGlobalOnLayoutListener(this)
                } else {
                    it.removeOnGlobalLayoutListener(this)
                }
            }
            val viewHeight = (tlDistance?.measuredHeight ?: 0) + (6 * resources.displayMetrics.density).toInt()
            val layoutHeight = (flTrack?.height ?: 0) - (6 * resources.displayMetrics.density).toInt()
            val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
            val isTimeAndSatellitesVisible = if (isPortrait) layoutHeight >= 6 * viewHeight else layoutHeight >= (3.9 * viewHeight).toInt()
            GPSApplication.getInstance().setSpaceForExtraTilesAvailable(isTimeAndSatellitesVisible)
            update()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_track, container, false)

        flTrack = view.findViewById(R.id.id_fragmenttrackFrameLayout)

        tvDuration = view.findViewById(R.id.id_textView_Duration)
        tvTrackID = view.findViewById(R.id.id_textView_TrackIDLabel)
        tvTrackName = view.findViewById(R.id.id_textView_TrackName)
        tvTrackpoints = view.findViewById(R.id.id_textView_Trackpoints)
        tvAnnotations = view.findViewById(R.id.id_textView_Annotations)
        tvDistance = view.findViewById(R.id.id_textView_Distance)
        tvMaxSpeed = view.findViewById(R.id.id_textView_SpeedMax)
        tvAverageSpeed = view.findViewById(R.id.id_textView_SpeedAvg)
        tvAltitudeGap = view.findViewById(R.id.id_textView_AltitudeGap)
        tvOverallDirection = view.findViewById(R.id.id_textView_OverallDirection)
        tvTrackStatus = view.findViewById(R.id.id_textView_TrackStatus)
        tvDirectionUM = view.findViewById(R.id.id_textView_OverallDirectionUM)
        tvDistanceUM = view.findViewById(R.id.id_textView_DistanceUM)
        tvMaxSpeedUM = view.findViewById(R.id.id_textView_SpeedMaxUM)
        tvAverageSpeedUM = view.findViewById(R.id.id_textView_SpeedAvgUM)
        tvAltitudeGapUM = view.findViewById(R.id.id_textView_AltitudeGapUM)

        tlTrack = view.findViewById(R.id.id_tableLayout_TrackName)
        tlTrackpoints = view.findViewById(R.id.id_TableLayout_Trackpoints)
        tlAnnotations = view.findViewById(R.id.id_TableLayout_Annotations)
        tlDuration = view.findViewById(R.id.id_tableLayout_Duration)
        tlSpeedMax = view.findViewById(R.id.id_tableLayout_SpeedMax)
        tlDistance = view.findViewById(R.id.id_tableLayout_Distance)
        tlSpeedAvg = view.findViewById(R.id.id_tableLayout_SpeedAvg)
        tlAltitudeGap = view.findViewById(R.id.id_tableLayout_AltitudeGap)
        tlOverallDirection = view.findViewById(R.id.id_tableLayout_OverallDirection)

        llTrackpointsAnnotations = view.findViewById(R.id.id_linearLayout_Annotation_Trackpoints)

        tvTrackStatus?.text = getString(R.string.track_empty) + "\n\n" + getString(R.string.track_start_with_button_below)
        return view
    }

    override fun onResume() {
        super.onResume()
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this)
        }
        EventBus.getDefault().register(this)
        flTrack?.viewTreeObserver?.addOnGlobalLayoutListener(viewTreeObserverOnGLL)
        update()
    }

    override fun onPause() {
        flTrack?.viewTreeObserver?.let {
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
        if (msg == EventBusMSG.UPDATE_TRACK) update()
    }

    fun update() {
        track = gpsApp.currentTrack
        prefDirections = gpsApp.prefShowDirections
        EGMAltitudeCorrection = gpsApp.prefEGM96AltitudeCorrection

        if (isAdded) {
            if (track != null && (track!!.numberOfLocations + track!!.numberOfPlacemarks > 0)) {
                fTrackID = if (track!!.description.isEmpty()) getString(R.string.track_id) + " " + track!!.id else track!!.description
                fTrackName = track!!.name
                phdDuration = phdformatter.format(track!!.getPrefTime(), PhysicalDataFormatter.FORMAT_DURATION)
                phdSpeedMax = phdformatter.format(track!!.speedMax, PhysicalDataFormatter.FORMAT_SPEED)
                phdSpeedAvg = phdformatter.format(track!!.getPrefSpeedAverage(), PhysicalDataFormatter.FORMAT_SPEED_AVG)
                phdDistance = phdformatter.format(track!!.estimatedDistance, PhysicalDataFormatter.FORMAT_DISTANCE)
                phdAltitudeGap = phdformatter.format(track!!.getEstimatedAltitudeGap(EGMAltitudeCorrection), PhysicalDataFormatter.FORMAT_ALTITUDE)
                phdOverallDirection = phdformatter.format(track!!.bearing, PhysicalDataFormatter.FORMAT_BEARING)

                tvTrackID?.text = fTrackID
                tvTrackName?.text = fTrackName
                tvDuration?.text = phdDuration?.value
                tvMaxSpeed?.text = phdSpeedMax?.value
                tvAverageSpeed?.text = phdSpeedAvg?.value
                tvDistance?.text = phdDistance?.value
                tvAltitudeGap?.text = phdAltitudeGap?.value
                tvOverallDirection?.text = phdOverallDirection?.value

                tvMaxSpeedUM?.text = phdSpeedMax?.um
                tvAverageSpeedUM?.text = phdSpeedAvg?.um
                tvDistanceUM?.text = phdDistance?.um
                tvAltitudeGapUM?.text = phdAltitudeGap?.um

                llTrackpointsAnnotations?.visibility = if (gpsApp.isSpaceForExtraTilesAvailable) View.VISIBLE else View.GONE

                tvAnnotations?.text = track!!.numberOfPlacemarks.toString()
                tvTrackpoints?.text = track!!.numberOfLocations.toString()

                isValidAltitude = track!!.isValidAltitude
                val cPrimary = resources.getColor(R.color.textColorPrimary)
                val cSecondary = resources.getColor(R.color.textColorSecondary)
                tvAltitudeGap?.setTextColor(if (isValidAltitude) cPrimary else cSecondary)
                tvAltitudeGapUM?.setTextColor(if (isValidAltitude) cPrimary else cSecondary)

                tvTrackStatus?.visibility = View.INVISIBLE
                tvDirectionUM?.visibility = if (prefDirections == 0) View.GONE else View.VISIBLE

                tlTrack?.visibility = if (fTrackName == "") View.INVISIBLE else View.VISIBLE
                tlDuration?.visibility = if (phdDuration?.value == "") View.INVISIBLE else View.VISIBLE
                tlSpeedMax?.visibility = if (phdSpeedMax?.value == "") View.INVISIBLE else View.VISIBLE
                tlSpeedAvg?.visibility = if (phdSpeedAvg?.value == "") View.INVISIBLE else View.VISIBLE
                tlDistance?.visibility = if (phdDistance?.value == "") View.INVISIBLE else View.VISIBLE
                tlOverallDirection?.visibility = if (phdOverallDirection?.value == "") View.INVISIBLE else View.VISIBLE
                tlAltitudeGap?.visibility = if (phdAltitudeGap?.value == "") View.INVISIBLE else View.VISIBLE
                tlTrackpoints?.visibility = if (track!!.numberOfLocations > 0) View.VISIBLE else View.INVISIBLE
                tlAnnotations?.visibility = if (track!!.numberOfPlacemarks + track!!.numberOfLocations > 0) View.VISIBLE else View.INVISIBLE
            } else {
                tvTrackStatus?.visibility = View.VISIBLE
                tlTrack?.visibility = View.INVISIBLE
                tlDuration?.visibility = View.INVISIBLE
                tlSpeedMax?.visibility = View.INVISIBLE
                tlSpeedAvg?.visibility = View.INVISIBLE
                tlDistance?.visibility = View.INVISIBLE
                tlOverallDirection?.visibility = View.INVISIBLE
                tlAltitudeGap?.visibility = View.INVISIBLE
                tlTrackpoints?.visibility = View.INVISIBLE
                tlAnnotations?.visibility = View.INVISIBLE
            }
        }
    }
}


