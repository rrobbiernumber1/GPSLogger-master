package eu.basicairdata.graziano.gpslogger

import android.app.Dialog
import android.content.DialogInterface
import android.content.SharedPreferences
import android.graphics.PorterDuff
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.preference.PreferenceManager
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe

class FragmentTrackPropertiesDialog : DialogFragment() {

    private var etDescription: EditText? = null
    private val tracktypeImageView: Array<ImageView?> = arrayOfNulls(6)
    private var tracktypeMore: ImageView? = null

    private var trackToEdit: Track? = null
    private var title: Int = 0
    private var finalizeTrackWithOk: Boolean = false
    private var isTrackTypeIconClicked: Boolean = false

    private data class LastUsedTrackType(var type: Int, var date: Long)

    private val lastUsedTrackTypeList: ArrayList<LastUsedTrackType> = ArrayList()

    companion object {
        private const val KEY_TITLE = "_title"
        private const val KEY_ISFINALIZATION = "_isFinalization"
    }

    override fun onSaveInstanceState(@NonNull outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_TITLE, title)
        outState.putBoolean(KEY_ISFINALIZATION, finalizeTrackWithOk)
    }

    @NonNull
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext())
        trackToEdit = GPSApplication.getInstance().trackToEdit

        if (trackToEdit == null) dismiss()

        if (savedInstanceState != null) {
            title = savedInstanceState.getInt(KEY_TITLE, 0)
            finalizeTrackWithOk = savedInstanceState.getBoolean(KEY_ISFINALIZATION, false)
        } else {
            GPSApplication.getInstance().setSelectedTrackTypeOnDialog(trackToEdit!!.estimatedTrackType)
        }

        if (title != 0) builder.setTitle(title)

        val inflater: LayoutInflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.fragment_track_properties_dialog, null)

        trackToEdit?.let { t ->
            etDescription = view.findViewById(R.id.track_description)
            if (t.description.isNotEmpty()) {
                etDescription?.setText(t.description)
            }
            etDescription?.hint = GPSApplication.getInstance().getString(R.string.track_id) + " " + t.id
        }

        tracktypeImageView[0] = view.findViewById(R.id.tracktype_0)
        tracktypeImageView[1] = view.findViewById(R.id.tracktype_1)
        tracktypeImageView[2] = view.findViewById(R.id.tracktype_2)
        tracktypeImageView[3] = view.findViewById(R.id.tracktype_3)
        tracktypeImageView[4] = view.findViewById(R.id.tracktype_4)
        tracktypeImageView[5] = view.findViewById(R.id.tracktype_5)

        tracktypeMore = view.findViewById(R.id.tracktype_more)
        tracktypeMore?.setOnClickListener {
            val fm: FragmentManager = requireActivity().supportFragmentManager
            val fragmentTrackTypeDialog = FragmentTrackTypeDialog()
            fragmentTrackTypeDialog.show(fm, "")
        }

        updateTrackTypeIcons()

        for (i in tracktypeImageView.indices) {
            tracktypeImageView[i]?.setOnClickListener { v: View ->
                for (j in tracktypeImageView.indices) {
                    if (v == tracktypeImageView[j]) {
                        tracktypeImageView[j]?.setColorFilter(resources.getColor(R.color.textColorRecControlPrimary), PorterDuff.Mode.SRC_IN)
                        GPSApplication.getInstance().setSelectedTrackTypeOnDialog(tracktypeImageView[j]?.tag as Int)
                        isTrackTypeIconClicked = true
                    } else {
                        tracktypeImageView[j]?.setColorFilter(resources.getColor(R.color.colorIconDisabledOnDialog), PorterDuff.Mode.SRC_IN)
                    }
                }
            }
        }

        builder.setView(view)
            .setPositiveButton(R.string.ok) { dialog: DialogInterface, _: Int ->
                if (isAdded) {
                    val trackDescription = etDescription?.text?.toString() ?: ""
                    trackToEdit?.description = trackDescription.trim { it <= ' ' }
                    if (GPSApplication.getInstance().selectedTrackTypeOnDialog != GPSApplication.NOT_AVAILABLE) {
                        trackToEdit?.type = GPSApplication.getInstance().selectedTrackTypeOnDialog
                    }
                    GPSApplication.getInstance().gpsDataBase.updateTrack(trackToEdit)
                    if (finalizeTrackWithOk) {
                        EventBus.getDefault().post(EventBusMSG.NEW_TRACK)
                        val toast = Toast.makeText(GPSApplication.getInstance().applicationContext, R.string.toast_track_saved_into_tracklist, Toast.LENGTH_SHORT)
                        toast.setGravity(Gravity.BOTTOM, 0, GPSApplication.TOAST_VERTICAL_OFFSET)
                        toast.show()
                    } else {
                        GPSApplication.getInstance().UpdateTrackList()
                        EventBus.getDefault().post(EventBusMSG.UPDATE_TRACK)
                    }

                    if (isTrackTypeIconClicked || finalizeTrackWithOk) {
                        for (lut in lastUsedTrackTypeList) {
                            if (lut.type == GPSApplication.getInstance().selectedTrackTypeOnDialog) {
                                lut.date = System.currentTimeMillis()
                            }
                        }
                        savePreferences()
                    }
                }
            }
            .setNeutralButton(R.string.cancel) { _: DialogInterface, _: Int -> }

        return builder.create()
    }

    override fun onViewCreated(view: View, @Nullable savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    }

    override fun onResume() {
        super.onResume()
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this)
        }
        EventBus.getDefault().register(this)
    }

    override fun onPause() {
        EventBus.getDefault().unregister(this)
        super.onPause()
    }

    @Subscribe
    fun onEvent(msg: Short) {
        if (msg == EventBusMSG.REFRESH_TRACKTYPE) {
            isTrackTypeIconClicked = true
            updateTrackTypeIcons()
        }
    }

    fun updateTrackTypeIcons() {
        Log.w("myApp", "[#] FragentTrackPropertiesDialog - updateTrackTypeIcons()")
        loadPreferences()
        for (i in tracktypeImageView.indices) {
            val resType = lastUsedTrackTypeList[i].type
            tracktypeImageView[i]?.setImageResource(Track.ACTIVITY_DRAWABLE_RESOURCE[resType])
            tracktypeImageView[i]?.setColorFilter(resources.getColor(R.color.colorIconDisabledOnDialog), PorterDuff.Mode.SRC_IN)
            tracktypeImageView[i]?.tag = resType
        }
        for (lut in lastUsedTrackTypeList) {
            if (lut.type == GPSApplication.getInstance().selectedTrackTypeOnDialog) {
                try {
                    tracktypeImageView[lastUsedTrackTypeList.indexOf(lut)]?.setColorFilter(resources.getColor(R.color.textColorRecControlPrimary), PorterDuff.Mode.SRC_IN)
                } catch (_: IndexOutOfBoundsException) {
                }
            }
        }
    }

    fun setTitleResource(titleResource: Int) {
        title = titleResource
    }

    fun setFinalizeTrackWithOk(finalize: Boolean) {
        finalizeTrackWithOk = finalize
    }

    private fun savePreferences() {
        val preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(GPSApplication.getInstance().applicationContext)
        val editor = preferences.edit()
        for (i in 0 until 6) {
            editor.putInt("prefLastUsedTrackType$i", lastUsedTrackTypeList[i].type)
            editor.putLong("prefLastDateTrackType$i", lastUsedTrackTypeList[i].date)
        }
        editor.commit()
    }

    private fun loadPreferences() {
        val preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(GPSApplication.getInstance().applicationContext)
        lastUsedTrackTypeList.clear()
        for (i in 0 until 6) {
            val type = preferences.getInt("prefLastUsedTrackType$i", i)
            val date = preferences.getLong("prefLastDateTrackType$i", (i * 10).toLong())
            lastUsedTrackTypeList.add(LastUsedTrackType(type, date))
        }
        lastUsedTrackTypeList.sortWith(compareBy { it.date })

        var isTrackTypePresent = false
        for (lut in lastUsedTrackTypeList) {
            if (lut.type == GPSApplication.getInstance().selectedTrackTypeOnDialog) isTrackTypePresent = true
        }
        if (!isTrackTypePresent && (GPSApplication.getInstance().selectedTrackTypeOnDialog != GPSApplication.NOT_AVAILABLE)) {
            lastUsedTrackTypeList[0].type = GPSApplication.getInstance().selectedTrackTypeOnDialog
            lastUsedTrackTypeList[0].date = System.currentTimeMillis()
        }
        lastUsedTrackTypeList.sortWith(compareBy { it.type })
    }
}


