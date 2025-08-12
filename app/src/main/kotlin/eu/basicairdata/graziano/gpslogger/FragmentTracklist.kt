package eu.basicairdata.graziano.gpslogger

import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.AdapterView
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe

import java.io.File
import java.util.ArrayList
import java.util.Collections
import java.util.List

class FragmentTracklist : Fragment() {
    private var recyclerView: RecyclerView? = null
    private var layoutManager: RecyclerView.LayoutManager? = null
    private var adapter: TrackAdapter? = null
    private val data: MutableList<Track> = Collections.synchronizedList(ArrayList())
    private var viewRoot: View? = null
    private var tvTracklistEmpty: TextView? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        viewRoot = inflater.inflate(R.layout.fragment_tracklist, container, false)
        tvTracklistEmpty = viewRoot!!.findViewById(R.id.id_textView_TracklistEmpty)
        recyclerView = viewRoot!!.findViewById(R.id.my_recycler_view)
        recyclerView!!.setHasFixedSize(true)
        layoutManager = LinearLayoutManager(activity)
        recyclerView!!.layoutManager = layoutManager
        recyclerView!!.itemAnimator = DefaultItemAnimator().apply { setChangeDuration(0) }
        adapter = TrackAdapter(data)
        when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_NO -> adapter!!.isLightTheme = true
            Configuration.UI_MODE_NIGHT_YES, Configuration.UI_MODE_NIGHT_UNDEFINED -> adapter!!.isLightTheme = false
        }
        recyclerView!!.adapter = adapter
        return viewRoot as View
    }

    override fun onResume() {
        super.onResume()
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this)
        }
        EventBus.getDefault().register(this)
        update()
    }

    override fun onPause() {
        EventBus.getDefault().unregister(this)
        super.onPause()
    }

    @Subscribe
    fun onEvent(msg: EventBusMSGNormal) {
        var i = 0
        var found = false
        when (msg.eventBusMSG) {
            EventBusMSG.TRACKLIST_SELECT, EventBusMSG.TRACKLIST_DESELECT -> {
                synchronized(data) {
                    do {
                        if (data[i].id == msg.trackID) {
                            found = true
                            data[i].isSelected = msg.eventBusMSG == EventBusMSG.TRACKLIST_SELECT
                        }
                        i++
                    } while (i < data.size && !found)
                }
            }
            EventBusMSG.TRACKLIST_RANGE_SELECTION -> {
                if (GPSApplication.getInstance().lastClickId != GPSApplication.NOT_AVAILABLE.toLong()) {
                    synchronized(data) {
                        do {
                            if (data[i].id == GPSApplication.getInstance().lastClickId) {
                                data[i].isSelected = GPSApplication.getInstance().lastClickState
                                found = !found
                            }
                            if (data[i].id == msg.trackID) {
                                data[i].isSelected = GPSApplication.getInstance().lastClickState
                                found = !found
                            }
                            if (found) data[i].isSelected = GPSApplication.getInstance().lastClickState
                            i++
                        } while (i < data.size)
                    }
                    EventBus.getDefault().post(EventBusMSG.UPDATE_TRACKLIST)
                }
            }
        }
    }

    @Subscribe
    fun onEvent(msg: Short) {
        if (msg == EventBusMSG.UPDATE_TRACK) {
            if (!data.isEmpty() && GPSApplication.getInstance().isCurrentTrackVisible) {
                val trk = GPSApplication.getInstance().currentTrack
                synchronized(data) {
                    if (data[0].id == trk.id) {
                        activity?.runOnUiThread {
                            val holder = recyclerView!!.findViewHolderForAdapterPosition(0)
                            if (holder != null) {
                                (holder as TrackAdapter.TrackHolder).UpdateTrackStats(data[0])
                            }
                        }
                    }
                }
            }
            return
        }
        if (msg == EventBusMSG.REFRESH_TRACKLIST) {
            try {
                activity?.runOnUiThread { adapter?.notifyDataSetChanged() }
            } catch (_: NullPointerException) {
            }
            return
        }
        if (msg == EventBusMSG.NOTIFY_TRACKS_DELETED) {
            deleteSomeTracks()
            return
        }
        if (msg == EventBusMSG.UPDATE_TRACKLIST) {
            update()
            return
        }
        if (msg == EventBusMSG.ACTION_BULK_SHARE_TRACKS) {
            GPSApplication.getInstance().loadJob(GPSApplication.JOB_TYPE_SHARE)
            GPSApplication.getInstance().executeJob()
            GPSApplication.getInstance().deselectAllTracks()
            return
        }
        if (msg == EventBusMSG.ACTION_EDIT_TRACK) {
            for (T in GPSApplication.getInstance().trackList) {
                if (T.isSelected) {
                    GPSApplication.getInstance().setTrackToEdit(T)
                    val fm: FragmentManager = activity!!.supportFragmentManager
                    val tpDialog = FragmentTrackPropertiesDialog()
                    tpDialog.setTitleResource(R.string.card_menu_edit)
                    tpDialog.setFinalizeTrackWithOk(false)
                    tpDialog.show(fm, "")
                    break
                }
            }
        }
        if (msg == EventBusMSG.ACTION_BULK_VIEW_TRACKS) {
            val evList = ArrayList(GPSApplication.getInstance().externalViewerChecker.getExternalViewersList())
            if (!evList.isEmpty()) {
                if (evList.size == 1) {
                    GPSApplication.getInstance().setTrackViewer(evList[0])
                    openTrack()
                } else {
                    val pn = PreferenceManager.getDefaultSharedPreferences(requireContext()).getString("prefTracksViewer", "")
                    var foundDefault = false
                    for (ev in evList) {
                        if (ev.packageName == pn) {
                            GPSApplication.getInstance().setTrackViewer(ev)
                            foundDefault = true
                        }
                    }
                    if (!foundDefault) {
                        val dialog = Dialog(requireContext())
                        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
                        val view = layoutInflater.inflate(R.layout.appdialog_list, null)
                        val lv = view.findViewById<ListView>(R.id.id_appdialog_list)
                        val clad = ExternalViewerAdapter(requireActivity().applicationContext, evList)
                        lv.adapter = clad
                        lv.onItemClickListener = AdapterView.OnItemClickListener { _: AdapterView<*>, _: View, position: Int, _: Long ->
                            GPSApplication.getInstance().setTrackViewer(evList[position])
                            openTrack()
                            dialog.dismiss()
                        }
                        dialog.setContentView(view)
                        dialog.show()
                    } else {
                        openTrack()
                    }
                }
            }
            return
        }
        if (msg == EventBusMSG.ACTION_BULK_DELETE_TRACKS) {
            val builder = AlertDialog.Builder(requireContext())
            builder.setMessage(resources.getString(R.string.card_message_delete_confirmation))
            builder.setIcon(android.R.drawable.ic_menu_info_details)
            builder.setPositiveButton(R.string.yes) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
                GPSApplication.getInstance().loadJob(GPSApplication.JOB_TYPE_DELETE)
                GPSApplication.getInstance().executeJob()
            }
            builder.setNegativeButton(R.string.no) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
            val dialog = builder.create()
            dialog.show()
            return
        }
        if (msg == EventBusMSG.INTENT_SEND) {
            val selectedTracks = GPSApplication.getInstance().exportingTaskList
            val files = ArrayList<Uri>()
            var file: File
            val extraSubject = StringBuilder(getString(R.string.app_name) + " - ")
            val extraText = StringBuilder()
            var i = 0
            val intent = Intent()
            intent.action = Intent.ACTION_SEND_MULTIPLE
            intent.type = "text/xml"
            for (ET in selectedTracks) {
                val track = GPSApplication.getInstance().gpsDataBase.getTrack(ET.id)
                if (track == null) return
                if (i > 0) {
                    extraSubject.append(" + ")
                    extraText.append("\n\n----------------------------\n")
                }
                extraSubject.append(track.name)
                val phdformatter = PhysicalDataFormatter()
                val phdDuration = phdformatter.format(track.duration, PhysicalDataFormatter.FORMAT_DURATION)
                val phdDurationMoving = phdformatter.format(track.durationMoving, PhysicalDataFormatter.FORMAT_DURATION)
                val phdSpeedMax = phdformatter.format(track.speedMax, PhysicalDataFormatter.FORMAT_SPEED)
                val phdSpeedAvg = phdformatter.format(track.speedAverage, PhysicalDataFormatter.FORMAT_SPEED_AVG)
                val phdSpeedAvgMoving = phdformatter.format(track.speedAverageMoving, PhysicalDataFormatter.FORMAT_SPEED_AVG)
                val phdDistance = phdformatter.format(track.estimatedDistance, PhysicalDataFormatter.FORMAT_DISTANCE)
                val phdAltitudeGap = phdformatter.format(track.getEstimatedAltitudeGap(GPSApplication.getInstance().prefEGM96AltitudeCorrection), PhysicalDataFormatter.FORMAT_ALTITUDE)
                val phdOverallDirection = phdformatter.format(track.bearing, PhysicalDataFormatter.FORMAT_BEARING)
                if (track.numberOfLocations <= 1) {
                    extraText.append(getString(R.string.app_name) + " - " + getString(R.string.tab_track) + " " + track.name
                            + if (track.description.isEmpty()) "\n" + track.description + "\n" else ""
                            + "\n" + track.numberOfLocations + " " + getString(R.string.trackpoints)
                            + "\n" + track.numberOfPlacemarks + " " + getString(R.string.annotations))
                } else {
                    extraText.append(getString(R.string.app_name) + " - " + getString(R.string.tab_track) + " " + track.name
                            + if (!track.description.isEmpty()) "\n" + track.description else ""
                            + "\n" + track.numberOfLocations + " " + getString(R.string.trackpoints)
                            + "\n" + track.numberOfPlacemarks + " " + getString(R.string.annotations)
                            + "\n"
                            + "\n" + getString(R.string.distance) + " = " + phdDistance.value + " " + phdDistance.um
                            + "\n" + getString(R.string.duration) + " = " + phdDuration.value + " | " + phdDurationMoving.value
                            + "\n" + getString(R.string.altitude_gap) + " = " + phdAltitudeGap.value + " " + phdAltitudeGap.um
                            + "\n" + getString(R.string.max_speed) + " = " + phdSpeedMax.value + " " + phdSpeedMax.um
                            + "\n" + getString(R.string.average_speed) + " = " + phdSpeedAvg.value + " | " + phdSpeedAvgMoving.value + " " + phdSpeedAvg.um
                            + "\n" + getString(R.string.overall_direction) + " = " + phdOverallDirection.value + " " + phdOverallDirection.um
                            + "\n"
                            + "\n" + getString(R.string.pref_track_stats) + ": " + getString(R.string.pref_track_stats_totaltime) + " | " + getString(R.string.pref_track_stats_movingtime))
                }
                var fname = GPSApplication.getInstance().getFileName(track) + ".kml"
                file = File(GPSApplication.DIRECTORY_TEMP + "/", fname)
                if (file.exists() && GPSApplication.getInstance().prefExportKML) {
                    val uri = FileProvider.getUriForFile(GPSApplication.getInstance(), "eu.basicairdata.graziano.gpslogger.fileprovider", file)
                    files.add(uri)
                }
                fname = GPSApplication.getInstance().getFileName(track) + ".gpx"
                file = File(GPSApplication.DIRECTORY_TEMP + "/", fname)
                if (file.exists() && GPSApplication.getInstance().prefExportGPX) {
                    val uri = FileProvider.getUriForFile(GPSApplication.getInstance(), "eu.basicairdata.graziano.gpslogger.fileprovider", file)
                    files.add(uri)
                }
                fname = GPSApplication.getInstance().getFileName(track) + ".txt"
                file = File(GPSApplication.DIRECTORY_TEMP + "/", fname)
                if (file.exists() && GPSApplication.getInstance().prefExportTXT) {
                    val uri = FileProvider.getUriForFile(GPSApplication.getInstance(), "eu.basicairdata.graziano.gpslogger.fileprovider", file)
                    files.add(uri)
                }
                i++
            }
            intent.putExtra(Intent.EXTRA_SUBJECT, extraSubject.toString())
            intent.putExtra(Intent.EXTRA_TEXT, extraText.toString())
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, files)
            intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            val resInfoList = requireContext().packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            for (resolveInfo in resInfoList) {
                val packageName = resolveInfo.activityInfo.packageName
                for (U in files) {
                    GPSApplication.getInstance().applicationContext.grantUriPermission(packageName, U, Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            val chooser = Intent.createChooser(intent, getString(R.string.card_menu_share))
            try {
                if (intent.resolveActivity(requireContext().packageManager) != null && !files.isEmpty()) {
                    startActivity(chooser)
                }
            } catch (_: NullPointerException) {
            }
        }
    }

    fun openTrack() {
        GPSApplication.getInstance().loadJob(GPSApplication.JOB_TYPE_VIEW)
        GPSApplication.getInstance().executeJob()
        GPSApplication.getInstance().deselectAllTracks()
    }

    fun update() {
        if (isAdded) {
            val TI = GPSApplication.getInstance().trackList
            synchronized(data) {
                data.clear()
                if (!TI.isEmpty()) {
                    data.addAll(TI)
                    if (data[0].id == GPSApplication.getInstance().currentTrack.id) {
                        GPSApplication.getInstance().isCurrentTrackVisible = true
                    } else {
                        GPSApplication.getInstance().isCurrentTrackVisible = false
                    }
                } else {
                    GPSApplication.getInstance().isCurrentTrackVisible = false
                }
                try {
                    activity?.runOnUiThread {
                        tvTracklistEmpty?.visibility = if (data.isEmpty()) View.VISIBLE else View.GONE
                        adapter?.notifyDataSetChanged()
                    }
                } catch (_: NullPointerException) {
                }
            }
        }
    }

    fun deleteSomeTracks() {
        try {
            activity?.runOnUiThread {
                val TI = GPSApplication.getInstance().trackList
                synchronized(data) {
                    var i = data.size - 1
                    while (i >= 0) {
                        if (!TI.contains(data[i])) {
                            data.removeAt(i)
                            adapter?.notifyItemRemoved(i)
                        }
                        i--
                    }
                    tvTracklistEmpty?.visibility = if (data.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        } catch (_: NullPointerException) {
            update()
        }
    }
}


