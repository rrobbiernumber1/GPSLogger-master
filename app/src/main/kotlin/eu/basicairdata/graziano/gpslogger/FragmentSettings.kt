package eu.basicairdata.graziano.gpslogger

import android.app.Activity
import android.app.Dialog
import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.AsyncTask
import android.text.InputType
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.res.ResourcesCompat
import androidx.preference.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.DecimalFormat
import androidx.core.content.edit

class FragmentSettings : PreferenceFragmentCompat() {

    private val REQUEST_ACTION_OPEN_DOCUMENT_TREE = 3

    private lateinit var prefListener: SharedPreferences.OnSharedPreferenceChangeListener
    private lateinit var prefs: SharedPreferences

    var intervalfilter = 0.0
    var distfilter = 0.0
    var distfilterm = 0.0
    var altcor = 0.0
    var altcorm = 0.0
    private lateinit var progressDialog: ProgressDialog
    var isDownloaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        addPreferencesFromResource(R.xml.app_preferences)

        // Ensure export and temp folders exist
        var tsd = File(GPSApplication.getInstance().prefExportFolder)
        if (!tsd.exists()) tsd.mkdir()
        tsd = File(GPSApplication.DIRECTORY_TEMP)
        if (!tsd.exists()) tsd.mkdir()

        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())

        // Check if EGM96 file is downloaded and the size of the file is correct
        isDownloaded = EGM96.getInstance().isGridAvailable(GPSApplication.getInstance().applicationContext.filesDir.toString()) ||
                EGM96.getInstance().isGridAvailable(GPSApplication.getInstance().prefExportFolder)
        if (!isDownloaded) {
            val settings = PreferenceManager.getDefaultSharedPreferences(requireContext())
            settings.edit(commit = true) {
                putBoolean("prefEGM96AltitudeCorrection", false)
            }
            findPreference<SwitchPreferenceCompat>("prefEGM96AltitudeCorrection")?.isChecked = false
        }

        // Progress dialog
        progressDialog = ProgressDialog(requireActivity())
        progressDialog.isIndeterminate = true
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
        progressDialog.setCancelable(true)
        progressDialog.setMessage(getString(R.string.pref_EGM96AltitudeCorrection_download_progress))

        prefListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            Log.w("myApp", "[#] FragmentSettings.kt - SharedPreferences.OnSharedPreferenceChangeListener, key = $key")
            when (key) {
                "prefUM" -> {
                    altcorm = prefs.getString("prefAltitudeCorrection", "0")?.toDoubleOrNull() ?: 0.0
                    altcor = if (isUMMetric()) altcorm else altcorm * PhysicalDataFormatter.M_TO_FT
                    distfilterm = prefs.getString("prefGPSdistance", "0")?.toDoubleOrNull() ?: 0.0
                    distfilter = if (isUMMetric()) distfilterm else distfilterm * PhysicalDataFormatter.M_TO_FT
                    val editor = prefs.edit()
                    editor.putString("prefAltitudeCorrectionRaw", altcor.toString())
                    editor.putString("prefGPSdistanceRaw", distfilter.toString())
                    editor.commit()
                    findPreference<EditTextPreference>("prefAltitudeCorrectionRaw")?.text = prefs.getString("prefAltitudeCorrectionRaw", "0")
                    findPreference<EditTextPreference>("prefGPSdistanceRaw")?.text = prefs.getString("prefGPSdistanceRaw", "0")
                }
                "prefAltitudeCorrectionRaw" -> {
                    altcor = sharedPreferences.getString("prefAltitudeCorrectionRaw", "0")?.toDoubleOrNull() ?: 0.0
                    altcorm = if (isUMMetric()) altcor else altcor / PhysicalDataFormatter.M_TO_FT
                    val editor = prefs.edit()
                    editor.putString("prefAltitudeCorrection", altcorm.toString())
                    editor.commit()
                }
                "prefGPSdistanceRaw" -> {
                    distfilter = sharedPreferences.getString("prefGPSdistanceRaw", "0")?.toDoubleOrNull()?.let { kotlin.math.abs(it) } ?: 0.0
                    distfilterm = if (isUMMetric()) distfilter else distfilter / PhysicalDataFormatter.M_TO_FT
                    val editor = prefs.edit()
                    editor.putString("prefGPSdistance", distfilterm.toString())
                    editor.commit()
                }
                "prefEGM96AltitudeCorrection" -> {
                    if (sharedPreferences.getBoolean(key, false)) {
                        isDownloaded = EGM96.getInstance().isGridAvailable(GPSApplication.getInstance().applicationContext.filesDir.toString()) ||
                                EGM96.getInstance().isGridAvailable(GPSApplication.getInstance().prefExportFolder)
                        if (!isDownloaded) {
                            val downloadTask = DownloadTask(requireActivity())
                            downloadTask.execute("http://download.osgeo.org/proj/vdatum/egm96_15/outdated/WW15MGH.DAC")

                            progressDialog.setOnCancelListener { downloadTask.cancel(true) }

                            PrefEGM96SetToFalse()
                        } else {
                            EGM96.getInstance().loadGrid(GPSApplication.getInstance().prefExportFolder, GPSApplication.getInstance().applicationContext.filesDir.toString())
                        }
                    }
                }
                "prefColorTheme" -> {
                    val settings = PreferenceManager.getDefaultSharedPreferences(requireContext())
                    val editor1 = settings.edit()
                    editor1.putString(key, sharedPreferences.getString(key, "2"))
                    editor1.commit()

                    requireActivity().window.setWindowAnimations(R.style.MyCrossfadeAnimation_Window)
                    AppCompatDelegate.setDefaultNightMode(PreferenceManager.getDefaultSharedPreferences(requireContext()).getString("prefColorTheme", "2")!!.toInt())
                }
            }
            SetupPreferences()
        }

        findPreference<EditTextPreference>("prefGPSdistanceRaw")?.setOnBindEditTextListener {
            it.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            it.selectAll()
        }

        findPreference<EditTextPreference>("prefAltitudeCorrectionRaw")?.setOnBindEditTextListener {
            it.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED or InputType.TYPE_NUMBER_FLAG_DECIMAL
            it.selectAll()
        }

        findPreference<EditTextPreference>("prefGPSinterval")?.setOnBindEditTextListener {
            it.inputType = InputType.TYPE_CLASS_NUMBER
            it.selectAll()
        }
    }

    override fun onResume() {
        super.onResume()
        setDivider(ColorDrawable(Color.TRANSPARENT))
        setDividerHeight(0)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        GPSApplication.getInstance().externalViewerChecker.makeExternalViewersList()
        SetupPreferences()
    }

    override fun onPause() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        Log.w("myApp", "[#] FragmentSettings.kt - onPause")
        org.greenrobot.eventbus.EventBus.getDefault().post(EventBusMSG.UPDATE_SETTINGS)
        super.onPause()
    }

    override fun onCreatePreferences(bundle: Bundle?, s: String?) {
        Log.w("myApp", "[#] FragmentSettings.kt - onCreatePreferences")
    }

    /** Returns true when the Unit of Measurement is set to Metric, false otherwise */
    private fun isUMMetric(): Boolean {
        return prefs.getString("prefUM", "0") == "0"
    }

    /** Sets up Preference screen */
    fun SetupPreferences() {
        val pUM = findPreference<ListPreference>("prefUM")
        val pUMSpeed = findPreference<ListPreference>("prefUMOfSpeed")
        val pGPSDistance = findPreference<EditTextPreference>("prefGPSdistanceRaw")
        val pGPSInterval = findPreference<EditTextPreference>("prefGPSinterval")
        val pGPSUpdateFrequency = findPreference<ListPreference>("prefGPSupdatefrequency")
        val pKMLAltitudeMode = findPreference<ListPreference>("prefKMLAltitudeMode")
        val pGPXVersion = findPreference<ListPreference>("prefGPXVersion")
        val pShowTrackStatsType = findPreference<ListPreference>("prefShowTrackStatsType")
        val pShowDirections = findPreference<ListPreference>("prefShowDirections")
        val pColorTheme = findPreference<ListPreference>("prefColorTheme")
        val pExportFolder = findPreference<Preference>("prefExportFolder")
        val pAltitudeCorrection = findPreference<EditTextPreference>("prefAltitudeCorrectionRaw")
        val pTracksViewer = findPreference<Preference>("prefTracksViewer")

        // Adds UM to titles
        pGPSDistance?.dialogTitle = getString(R.string.pref_GPS_distance_filter) + " (" +
                if (isUMMetric()) getString(R.string.UM_m) else getString(R.string.UM_ft) + ")"
        pAltitudeCorrection?.dialogTitle = getString(R.string.pref_AltitudeCorrection) + " (" +
                if (isUMMetric()) getString(R.string.UM_m) else getString(R.string.UM_ft) + ")"
        pGPSInterval?.dialogTitle = getString(R.string.pref_GPS_interval_filter) + " (" + getString(R.string.UM_s) + ")"

        // Keep Screen On Flag
        if (prefs.getBoolean("prefKeepScreenOn", true)) requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Track Viewer
        val evList = ArrayList(GPSApplication.getInstance().externalViewerChecker.getExternalViewersList())
        when (GPSApplication.getInstance().externalViewerChecker.size()) {
            0 -> {
                pTracksViewer?.isEnabled = false
                pTracksViewer?.onPreferenceClickListener = null
            }
            1 -> {
                pTracksViewer?.isEnabled = true
                pTracksViewer?.onPreferenceClickListener = null
            }
            else -> {
                pTracksViewer?.isEnabled = true
                pTracksViewer?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                    val externalViewerChecker = GPSApplication.getInstance().externalViewerChecker
                    if (externalViewerChecker.size() >= 1) {
                        val dialog = Dialog(requireActivity())
                        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
                        val view = layoutInflater.inflate(R.layout.appdialog_list, null)
                        val lv = view.findViewById<ListView>(R.id.id_appdialog_list)

                        val aild = ArrayList<ExternalViewer>()

                        val askai = ExternalViewer()
                        askai.label = getString(R.string.pref_track_viewer_select_every_time)
                        askai.icon = ResourcesCompat.getDrawable(resources, R.drawable.ic_visibility_24dp, requireActivity().theme)

                        aild.add(askai)
                        aild.addAll(evList)

                        val clad = ExternalViewerAdapter(requireActivity(), aild)

                        lv.adapter = clad
                        lv.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
                            val settings = PreferenceManager.getDefaultSharedPreferences(requireContext())
                            val editor1 = settings.edit()
                            editor1.putString("prefTracksViewer", aild[position].packageName)
                            editor1.commit()
                            SetupPreferences()
                            dialog.dismiss()
                        }
                        dialog.setContentView(view)
                        dialog.show()
                    }
                    true
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            pExportFolder?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                Log.w("myApp", "[#] FragmentSettings.kt - pExportFolder preference clicked")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    intent.putExtra("android.content.extra.SHOW_ADVANCED", true)
                    intent.putExtra("android.content.extra.FANCY", true)
                    intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                    startActivityForResult(intent, REQUEST_ACTION_OPEN_DOCUMENT_TREE)
                }
                false
            }
        } else pExportFolder?.isVisible = false

        // Tracks viewer summary
        if (evList.isEmpty()) {
            pTracksViewer?.setSummary(R.string.pref_track_viewer_not_installed)
        } else if (evList.size == 1) {
            pTracksViewer?.summary = evList[0].label + if (evList[0].fileType == GPSApplication.FILETYPE_GPX) " (GPX)" else " (KML)"
        } else {
            pTracksViewer?.setSummary(R.string.pref_track_viewer_select_every_time)
            val pn = prefs.getString("prefTracksViewer", "") ?: ""
            Log.w("myApp", "[#] FragmentSettings.kt - prefTracksViewer = $pn")
            for (ev in evList) {
                if (ev.packageName == pn) {
                    pTracksViewer?.summary = ev.label + if (ev.fileType == GPSApplication.FILETYPE_GPX) " (GPX)" else " (KML)"
                }
            }
        }

        // Parse and set summaries
        altcorm = prefs.getString("prefAltitudeCorrection", "0")?.toDoubleOrNull() ?: 0.0
        altcor = if (isUMMetric()) altcorm else altcorm * PhysicalDataFormatter.M_TO_FT

        distfilterm = prefs.getString("prefGPSdistance", "0")?.toDoubleOrNull()?.let { kotlin.math.abs(it) } ?: 0.0
        distfilter = if (isUMMetric()) distfilterm else distfilterm * PhysicalDataFormatter.M_TO_FT

        intervalfilter = prefs.getString("prefGPSinterval", "0")?.toDoubleOrNull() ?: 0.0

        val editor = prefs.edit()
        editor.putString("prefAltitudeCorrectionRaw", altcor.toString())
        editor.putString("prefGPSdistanceRaw", distfilter.toString())
        editor.commit()

        val df = DecimalFormat().apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 3
        }

        if (isUMMetric()) {
            pGPSDistance?.summary = if (distfilter != 0.0) df.format(distfilter) + " " + getString(R.string.UM_m) else getString(R.string.pref_GPS_filter_disabled)
            pAltitudeCorrection?.summary = if (altcor != 0.0) df.format(altcor) + " " + getString(R.string.UM_m) else getString(R.string.pref_AltitudeCorrection_summary_not_defined)
        }
        if (prefs.getString("prefUM", "0") == "8") {
            pGPSDistance?.summary = if (distfilter != 0.0) df.format(distfilter) + " " + getString(R.string.UM_ft) else getString(R.string.pref_GPS_filter_disabled)
            pAltitudeCorrection?.summary = if (altcor != 0.0) df.format(altcor) + " " + getString(R.string.UM_ft) else getString(R.string.pref_AltitudeCorrection_summary_not_defined)
        }
        if (prefs.getString("prefUM", "0") == "16") {
            pGPSDistance?.summary = if (distfilter != 0.0) df.format(distfilter) + " " + getString(R.string.UM_ft) else getString(R.string.pref_GPS_filter_disabled)
            pAltitudeCorrection?.summary = if (altcor != 0.0) df.format(altcor) + " " + getString(R.string.UM_ft) else getString(R.string.pref_AltitudeCorrection_summary_not_defined)
        }

        pGPSInterval?.summary = if (intervalfilter != 0.0) df.format(intervalfilter) + " " + getString(R.string.UM_s) else getString(R.string.pref_GPS_filter_disabled)

        pColorTheme?.summary = pColorTheme?.entry
        pUMSpeed?.summary = pUMSpeed?.entry
        pUM?.summary = pUM?.entry
        pGPSUpdateFrequency?.summary = pGPSUpdateFrequency?.entry
        pKMLAltitudeMode?.summary = pKMLAltitudeMode?.entry
        pGPXVersion?.summary = pGPXVersion?.entry
        pShowTrackStatsType?.summary = pShowTrackStatsType?.entry
        pShowDirections?.summary = pShowDirections?.entry

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (GPSApplication.getInstance().isExportFolderWritable) {
                pExportFolder?.summary = GPSApplication.getInstance().extractFolderNameFromEncodedUri(prefs.getString("prefExportFolder", "") ?: "")
            } else {
                pExportFolder?.summary = getString(R.string.pref_not_set)
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_ACTION_OPEN_DOCUMENT_TREE && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                val treeUri: Uri? = data.data
                if (treeUri != null) {
                    requireActivity().grantUriPermission(requireActivity().packageName, treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    GPSApplication.getInstance().contentResolver.takePersistableUriPermission(
                        treeUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                    Log.w("myApp", "[#] GPSActivity.kt - onActivityResult URI: ${treeUri}")
                    Log.w("myApp", "[#] GPSActivity.kt - onActivityResult URI: ${treeUri.path}")
                    Log.w("myApp", "[#] GPSActivity.kt - onActivityResult URI: ${treeUri.encodedPath}")

                    GPSApplication.getInstance().prefExportFolder = treeUri.toString()
                    SetupPreferences()
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    fun PrefEGM96SetToFalse() {
        val settings = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val editor1 = settings.edit()
        editor1.putBoolean("prefEGM96AltitudeCorrection", false)
        editor1.commit()
        findPreference<SwitchPreferenceCompat>("prefEGM96AltitudeCorrection")?.isChecked = false
    }

    fun PrefEGM96SetToTrue() {
        val settings = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val editor1 = settings.edit()
        editor1.putBoolean("prefEGM96AltitudeCorrection", true)
        editor1.commit()
        findPreference<SwitchPreferenceCompat>("prefEGM96AltitudeCorrection")?.isChecked = true
        EGM96.getInstance().loadGrid(GPSApplication.getInstance().prefExportFolder, GPSApplication.getInstance().applicationContext.filesDir.toString())
    }

    private inner class DownloadTask(private val ctx: Context) : AsyncTask<String, Int, String?>() {
        override fun doInBackground(vararg sUrl: String): String? {
            var input: InputStream? = null
            var output: OutputStream? = null
            var connection: HttpURLConnection? = null
            try {
                val url = URL(sUrl[0])
                connection = url.openConnection() as HttpURLConnection
                connection.connect()

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    return "Server returned HTTP ${connection.responseCode} ${connection.responseMessage}"
                }

                val fileLength = connection.contentLength

                input = connection.inputStream
                output = FileOutputStream(ctx.applicationContext.filesDir.toString() + "/WW15MGH.DAC")

                val data = ByteArray(4096)
                var total: Long = 0
                while (true) {
                    val count = input.read(data)
                    if (count == -1) break
                    if (isCancelled) {
                        input.close()
                        return null
                    }
                    total += count
                    if (fileLength > 0) publishProgress((total * 2028 / fileLength).toInt())
                    output.write(data, 0, count)
                }
            } catch (e: Exception) {
                return e.toString()
            } finally {
                try {
                    output?.close()
                    input?.close()
                } catch (_: IOException) {
                }
                connection?.disconnect()
            }
            return null
        }

        override fun onPreExecute() {
            super.onPreExecute()
            progressDialog.show()
        }

        override fun onProgressUpdate(vararg values: Int?) {
            super.onProgressUpdate(*values)
            progressDialog.isIndeterminate = false
            progressDialog.max = 2028
            progressDialog.progress = values[0] ?: 0
        }

        override fun onPostExecute(result: String?) {
            if (activity != null) {
                progressDialog.dismiss()
                if (result != null) {
                    Toast.makeText(ctx, getString(R.string.toast_download_error) + ": " + result, Toast.LENGTH_LONG).show()
                } else {
                    isDownloaded = EGM96.getInstance().isGridAvailable(GPSApplication.getInstance().applicationContext.filesDir.toString()) ||
                            EGM96.getInstance().isGridAvailable(GPSApplication.getInstance().prefExportFolder)
                    if (isDownloaded) {
                        Toast.makeText(ctx, getString(R.string.toast_download_completed), Toast.LENGTH_SHORT).show()
                        PrefEGM96SetToTrue()
                    } else {
                        Toast.makeText(ctx, getString(R.string.toast_download_failed), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}


