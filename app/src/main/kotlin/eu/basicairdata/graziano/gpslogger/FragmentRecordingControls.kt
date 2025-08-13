package eu.basicairdata.graziano.gpslogger

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Vibrator
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.NonNull
import androidx.fragment.app.Fragment
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class FragmentRecordingControls : Fragment() {

    private var tvGeoPointsNumber: TextView? = null
    private var tvPlacemarksNumber: TextView? = null
    private var tvLockButton: TextView? = null
    private var tvStopButton: TextView? = null
    private var tvAnnotateButton: TextView? = null
    private var tvRecordButton: TextView? = null
    private val gpsApp: GPSApplication = GPSApplication.getInstance()

    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_recording_controls, container, false)

        vibrator = activity?.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        tvLockButton = view.findViewById(R.id.id_lock)
        tvLockButton?.setOnClickListener {
            if (isAdded) (activity as GPSActivity).onToggleLock()
        }

        tvStopButton = view.findViewById(R.id.id_stop)
        tvStopButton?.setOnClickListener {
            if (isAdded) (activity as GPSActivity).onRequestStop(true, false)
        }

        tvAnnotateButton = view.findViewById(R.id.id_annotate)
        tvAnnotateButton?.isHapticFeedbackEnabled = false
        tvAnnotateButton?.setOnClickListener {
            gpsApp.setQuickPlacemarkRequest(false)
            if (isAdded) (activity as GPSActivity).onRequestAnnotation()
        }
        tvAnnotateButton?.setOnLongClickListener {
            if (isAdded) {
                if (!gpsApp.isBottomBarLocked) vibrator?.vibrate(150)
                gpsApp.setQuickPlacemarkRequest(true)
                if (!gpsApp.isPlacemarkRequested) (activity as GPSActivity).onRequestAnnotation()
            }
            true
        }

        tvRecordButton = view.findViewById(R.id.id_record)
        tvRecordButton?.isHapticFeedbackEnabled = false
        tvRecordButton?.setOnClickListener {
            if (isAdded) (activity as GPSActivity).onToggleRecord()
        }
        tvRecordButton?.setOnLongClickListener {
            if (isAdded) {
                (activity as GPSActivity).onRequestForceRecord()
                Update()
            }
            true
        }
        tvRecordButton?.setOnTouchListener { _: View?, event: MotionEvent ->
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                Log.w("myApp", "[#] FragmentRecordingControls.kt - DEACTIVATE FORCE RECORDING OF TRACKPOINTS")
                gpsApp.isForcedTrackpointsRecording = false
                Update()
            }
            false
        }

        tvGeoPointsNumber = view.findViewById(R.id.id_textView_GeoPoints)
        tvPlacemarksNumber = view.findViewById(R.id.id_textView_Placemarks)
        return view
    }

    override fun onResume() {
        super.onResume()
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this)
        }
        EventBus.getDefault().register(this)
        Update()
    }

    override fun onPause() {
        EventBus.getDefault().unregister(this)
        super.onPause()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(msg: Short) {
        if (msg == EventBusMSG.UPDATE_TRACK) {
            Update()
        }
    }

    private fun setTextViewDrawableColor(drawable: Drawable?, color: Int) {
        if (drawable != null) {
            drawable.clearColorFilter()
            drawable.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        }
    }

    private fun setButtonToClickedState(@NonNull button: TextView, imageId: Int, stringId: Int) {
        button.setBackgroundColor(resources.getColor(R.color.colorPrimary))
        if (imageId != 0) button.setCompoundDrawablesWithIntrinsicBounds(0, imageId, 0, 0)
        button.setTextColor(resources.getColor(R.color.textColorRecControlSecondary_Active))
        if (stringId != 0) button.setText(getString(stringId))
        setTextViewDrawableColor(button.compoundDrawables[1], resources.getColor(R.color.textColorRecControlPrimary_Active))
    }

    private fun setButtonToNormalState(@NonNull button: TextView, imageId: Int, stringId: Int) {
        button.setBackgroundColor(Color.TRANSPARENT)
        if (imageId != 0) button.setCompoundDrawablesWithIntrinsicBounds(0, imageId, 0, 0)
        button.setTextColor(resources.getColor(R.color.textColorRecControlSecondary))
        if (stringId != 0) button.setText(getString(stringId))
        setTextViewDrawableColor(button.compoundDrawables[1], resources.getColor(R.color.textColorRecControlPrimary))
    }

    private fun setButtonToDisabledState(@NonNull button: TextView, imageId: Int, stringId: Int) {
        button.setBackgroundColor(Color.TRANSPARENT)
        if (imageId != 0) button.setCompoundDrawablesWithIntrinsicBounds(0, imageId, 0, 0)
        button.setTextColor(resources.getColor(R.color.textColorRecControlDisabled))
        if (stringId != 0) button.setText(getString(stringId))
        setTextViewDrawableColor(button.compoundDrawables[1], resources.getColor(R.color.textColorRecControlDisabled))
    }

    fun Update() {
        if (isAdded) {
            val track = gpsApp.currentTrack
            val isRec = gpsApp.isRecording || gpsApp.isForcedTrackpointsRecording
            val isAnnot = gpsApp.isPlacemarkRequested
            val isLck = gpsApp.isBottomBarLocked
            if (track != null) {
                tvGeoPointsNumber?.text = if (track.numberOfLocations == 0L) "" else track.numberOfLocations.toString()
                tvPlacemarksNumber?.text = if (track.numberOfPlacemarks == 0L) "" else track.numberOfPlacemarks.toString()
                tvRecordButton?.let { if (isRec) setButtonToClickedState(it, R.drawable.ic_pause_24, R.string.pause) else setButtonToNormalState(it, R.drawable.ic_record_24, R.string.record) }
                tvAnnotateButton?.let { if (isAnnot) setButtonToClickedState(it, 0, 0) else setButtonToNormalState(it, 0, 0) }
                tvLockButton?.let { if (isLck) setButtonToClickedState(it, R.drawable.ic_unlock_24, R.string.unlock) else setButtonToNormalState(it, R.drawable.ic_lock_24, R.string.lock) }
                tvStopButton?.let {
                    it.isClickable = isRec || isAnnot || (track.numberOfLocations + track.numberOfPlacemarks > 0)
                    if (isRec || isAnnot || (track.numberOfLocations + track.numberOfPlacemarks > 0) || gpsApp.isStopButtonFlag) {
                        if (gpsApp.isStopButtonFlag) setButtonToClickedState(it, 0, 0) else setButtonToNormalState(it, 0, 0)
                    } else {
                        setButtonToDisabledState(it, 0, 0)
                    }
                }
            }
        }
    }
}


