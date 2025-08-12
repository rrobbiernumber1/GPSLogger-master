package eu.basicairdata.graziano.gpslogger

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.*
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import androidx.viewpager.widget.ViewPager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.tabs.TabLayout
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import java.util.*

class GPSActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_ID_MULTIPLE_PERMISSIONS = 1
        private const val REQUEST_ACTION_OPEN_DOCUMENT_TREE = 2
    }

    private val gpsApp: GPSApplication = GPSApplication.getInstance()
    private lateinit var toolbar: Toolbar
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager
    private var actionMode: ActionMode? = null
    private lateinit var bottomSheet: View
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>

    private var toast: Toast? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
        android.util.Log.w("myApp", "[#] $this - onCreate()")
        setTheme(R.style.MyMaterialTheme)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gps)

        toolbar = findViewById(R.id.id_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)

        viewPager = findViewById(R.id.id_viewpager)
        viewPager.offscreenPageLimit = 3
        viewPager.isFocusable = false
        setupViewPager(viewPager)

        tabLayout = findViewById(R.id.id_tablayout)
        tabLayout.tabMode = TabLayout.MODE_FIXED
        tabLayout.setupWithViewPager(viewPager)
        tabLayout.isFocusable = false
        tabLayout.addOnTabSelectedListener(object : TabLayout.ViewPagerOnTabSelectedListener(viewPager) {
            override fun onTabSelected(tab: TabLayout.Tab) {
                super.onTabSelected(tab)
                gpsApp.setGPSActivityActiveTab(tab.position)
                updateBottomSheetPosition()
                activateActionModeIfNeeded()
            }
        })

        bottomSheet = findViewById(R.id.id_bottomsheet)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        bottomSheetBehavior.isHideable = false
        bottomSheet.isFocusable = false
        updateNavigationBarColor(window, applicationContext)
    }

    override fun onStart() {
        android.util.Log.w("myApp", "[#] $this - onStart()")
        super.onStart()
        gpsApp.setGPSActivityActiveTab(tabLayout.selectedTabPosition)
    }

    override fun onStop() {
        android.util.Log.w("myApp", "[#] $this - onStop()")
        super.onStop()
    }

    override fun onResume() {
        android.util.Log.w("myApp", "[#] $this - onResume()")
        super.onResume()

        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this)
        }
        EventBus.getDefault().register(this)
        loadPreferences()
        EventBus.getDefault().post(EventBusMSG.APP_RESUME)

        if (!gpsApp.isLocationPermissionChecked) {
            checkLocationAndNotificationPermission()
            gpsApp.isLocationPermissionChecked = true
        }

        activateActionModeIfNeeded()

        if (gpsApp.preferenceFlagExists(GPSApplication.FLAG_RECORDING) && !gpsApp.isRecording) {
            android.util.Log.w("myApp", "[#] GPSActivity.kt - THE APP HAS BEEN KILLED IN BACKGROUND DURING A RECORDING !!!")
            gpsApp.clearPreferenceFlag_NoBackup(GPSApplication.FLAG_RECORDING)

            val builder = AlertDialog.Builder(this)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                builder.setMessage(resources.getString(R.string.dlg_app_killed) + "\n\n" + resources.getString(R.string.dlg_app_killed_description))
                builder.setNeutralButton(R.string.open_android_app_settings) { dialog: DialogInterface, _: Int ->
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    try { startActivity(intent) } catch (_: Exception) {}
                    dialog.dismiss()
                }
            } else builder.setMessage(resources.getString(R.string.dlg_app_killed))
            builder.setIcon(android.R.drawable.ic_menu_info_details)
            builder.setPositiveButton(R.string.about_ok) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
            val dialog = builder.create()
            dialog.show()
        }
        if (gpsApp.isJustStarted && (gpsApp.currentTrack.numberOfLocations + gpsApp.currentTrack.numberOfPlacemarks > 0)) {
            val toast = Toast.makeText(gpsApp.applicationContext, R.string.toast_active_track_not_empty, Toast.LENGTH_LONG)
            toast.setGravity(Gravity.BOTTOM, 0, GPSApplication.TOAST_VERTICAL_OFFSET)
            toast.show()
        }
        if (gpsApp.isJustStarted) gpsApp.deleteOldFilesFromCache(2)
        gpsApp.isJustStarted = false
    }

    override fun onPause() {
        android.util.Log.w("myApp", "[#] $this - onPause()")
        EventBus.getDefault().post(EventBusMSG.APP_PAUSE)
        EventBus.getDefault().unregister(this)
        super.onPause()
    }

    override fun onBackPressed() {
        ShutdownApp()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        android.util.Log.w("myApp", "[#] onKeyShortcut")
        when (keyCode) {
            KeyEvent.KEYCODE_R -> { if (!gpsApp.isStopButtonFlag && !gpsApp.isRecording) gpsApp.isRecording = true; return true }
            KeyEvent.KEYCODE_P -> { if (!gpsApp.isStopButtonFlag && gpsApp.isRecording) gpsApp.isRecording = false; return true }
            KeyEvent.KEYCODE_T -> { if (!gpsApp.isStopButtonFlag) gpsApp.isRecording = !gpsApp.isRecording; return true }
            KeyEvent.KEYCODE_A -> { if (!gpsApp.isStopButtonFlag) { gpsApp.setQuickPlacemarkRequest(false); gpsApp.isPlacemarkRequested = true }; return true }
            KeyEvent.KEYCODE_Q -> { if (!gpsApp.isStopButtonFlag) { gpsApp.setQuickPlacemarkRequest(true); gpsApp.isPlacemarkRequested = true }; return true }
            KeyEvent.KEYCODE_S -> {
                if (gpsApp.isRecording || gpsApp.isPlacemarkRequested || (gpsApp.currentTrack.numberOfLocations + gpsApp.currentTrack.numberOfPlacemarks > 0))
                    onRequestStop(true, true)
                return true
            }
            KeyEvent.KEYCODE_X -> {
                if (gpsApp.isRecording || gpsApp.isPlacemarkRequested || (gpsApp.currentTrack.numberOfLocations + gpsApp.currentTrack.numberOfPlacemarks > 0))
                    onRequestStop(false, true)
                return true
            }
            KeyEvent.KEYCODE_E -> { gpsApp.handlerTime = 60000; startActivity(Intent(this, SettingsActivity::class.java)); return true }
            KeyEvent.KEYCODE_L -> { gpsApp.isBottomBarLocked = !gpsApp.isBottomBarLocked; return true }
            KeyEvent.KEYCODE_1 -> { tabLayout.getTabAt(0)?.select(); gpsApp.setGPSActivityActiveTab(tabLayout.selectedTabPosition); return true }
            KeyEvent.KEYCODE_2 -> { tabLayout.getTabAt(1)?.select(); gpsApp.setGPSActivityActiveTab(tabLayout.selectedTabPosition); return true }
            KeyEvent.KEYCODE_3 -> { tabLayout.getTabAt(2)?.select(); gpsApp.setGPSActivityActiveTab(tabLayout.selectedTabPosition); return true }
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        updateBottomSheetPosition()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_settings -> { gpsApp.handlerTime = 60000; startActivity(Intent(this, SettingsActivity::class.java)); return true }
            R.id.action_about -> { supportFragmentManager.let { FragmentAboutDialog().show(it, "") }; return true }
            R.id.action_online_help -> {
                try {
                    val url = "https://www.basicairdata.eu/projects/android/android-gps-logger/getting-started-guide-for-gps-logger/"
                    val i = Intent(Intent.ACTION_VIEW)
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    i.data = Uri.parse(url)
                    startActivity(i)
                } catch (_: Exception) {
                    val toast = Toast.makeText(gpsApp.applicationContext, R.string.toast_no_browser_installed, Toast.LENGTH_LONG)
                    toast.setGravity(Gravity.BOTTOM, 0, GPSApplication.TOAST_VERTICAL_OFFSET)
                    toast.show()
                }
                return true
            }
            R.id.action_shutdown -> { ShutdownApp(); return true }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onRequestPermissionsResult(requestCode: Int, @NonNull permissions: Array<String>, @NonNull grantResults: IntArray) {
        when (requestCode) {
            REQUEST_ID_MULTIPLE_PERMISSIONS -> {
                val perms: MutableMap<String, Int> = HashMap()
                if (grantResults.isNotEmpty()) {
                    for (i in permissions.indices) perms[permissions[i]] = grantResults[i]
                    if (perms.containsKey(Manifest.permission.ACCESS_FINE_LOCATION)) {
                        if (perms[Manifest.permission.ACCESS_FINE_LOCATION] == PackageManager.PERMISSION_GRANTED) {
                            android.util.Log.w("myApp", "[#] GPSActivity.kt - ACCESS_FINE_LOCATION = PERMISSION_GRANTED; setGPSLocationUpdates!")
                            gpsApp.setGPSLocationUpdates(false)
                            gpsApp.setGPSLocationUpdates(true)
                            gpsApp.updateGPSLocationFrequency()
                        } else {
                            android.util.Log.w("myApp", "[#] GPSActivity.kt - ACCESS_FINE_LOCATION = PERMISSION_DENIED")
                        }
                    }
                    if (perms.containsKey(Manifest.permission.INTERNET)) {
                        if (perms[Manifest.permission.INTERNET] == PackageManager.PERMISSION_GRANTED) {
                            android.util.Log.w("myApp", "[#] GPSActivity.kt - INTERNET = PERMISSION_GRANTED")
                        } else {
                            android.util.Log.w("myApp", "[#] GPSActivity.kt - INTERNET = PERMISSION_DENIED")
                        }
                    }
                }
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    @Subscribe
    fun onEvent(msg: EventBusMSGNormal) {
        when (msg.eventBusMSG) {
            EventBusMSG.TRACKLIST_SELECT, EventBusMSG.TRACKLIST_DESELECT -> activateActionModeIfNeeded()
        }
    }

    @Subscribe
    fun onEvent(msg: Short) {
        when (msg) {
            EventBusMSG.REQUEST_ADD_PLACEMARK -> {
                val fm = supportFragmentManager
                FragmentPlacemarkDialog().show(fm, "")
            }
            EventBusMSG.UPDATE_TRACKLIST, EventBusMSG.NOTIFY_TRACKS_DELETED -> activateActionModeIfNeeded()
            EventBusMSG.APPLY_SETTINGS -> loadPreferences()
            EventBusMSG.TOAST_TRACK_EXPORTED -> runOnUiThread {
                val toast = Toast.makeText(gpsApp.applicationContext,
                    gpsApp.getString(R.string.toast_track_exported, gpsApp.extractFolderNameFromEncodedUri(gpsApp.prefExportFolder)), Toast.LENGTH_LONG)
                toast.setGravity(Gravity.BOTTOM, 0, GPSApplication.TOAST_VERTICAL_OFFSET)
                toast.show()
            }
            EventBusMSG.TOAST_UNABLE_TO_WRITE_THE_FILE -> runOnUiThread {
                val toast = Toast.makeText(gpsApp.applicationContext, R.string.export_unable_to_write_file, Toast.LENGTH_LONG)
                toast.setGravity(Gravity.BOTTOM, 0, GPSApplication.TOAST_VERTICAL_OFFSET)
                toast.show()
            }
            EventBusMSG.ACTION_BULK_EXPORT_TRACKS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    if (!gpsApp.isExportFolderWritable) {
                        openDirectory()
                    } else {
                        gpsApp.loadJob(GPSApplication.JOB_TYPE_EXPORT)
                        gpsApp.executeJob()
                        gpsApp.deselectAllTracks()
                    }
                } else {
                    if (gpsApp.isExportFolderWritable) {
                        gpsApp.loadJob(GPSApplication.JOB_TYPE_EXPORT)
                        gpsApp.executeJob()
                        gpsApp.deselectAllTracks()
                    } else {
                        EventBus.getDefault().post(EventBusMSG.TOAST_UNABLE_TO_WRITE_THE_FILE)
                    }
                }
            }
        }
    }

    private fun updateBottomSheetPosition() {
        gpsApp.setGPSActivityActiveTab(tabLayout.selectedTabPosition)
        if (gpsApp.getGPSActivityActiveTab() != 2) {
            bottomSheetBehavior.peekHeight = 1
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            bottomSheetBehavior.peekHeight = bottomSheet.height
        } else {
            bottomSheetBehavior.peekHeight = 1
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }
    }

    private fun setupViewPager(viewPager: ViewPager) {
        val adapter = ViewPagerAdapter(supportFragmentManager)
        adapter.addFragment(FragmentGPSFix(), getString(R.string.tab_gpsfix))
        adapter.addFragment(FragmentTrack(), getString(R.string.tab_track))
        adapter.addFragment(FragmentTracklist(), getString(R.string.tab_tracklist))
        viewPager.adapter = adapter
    }

    class ViewPagerAdapter(manager: androidx.fragment.app.FragmentManager) : androidx.fragment.app.FragmentPagerAdapter(manager) {
        private val fragmentsList: MutableList<androidx.fragment.app.Fragment> = ArrayList()
        private val fragmentsTitleList: MutableList<String> = ArrayList()
        override fun getItem(position: Int): androidx.fragment.app.Fragment = fragmentsList[position]
        override fun getCount(): Int = fragmentsList.size
        fun addFragment(fragment: androidx.fragment.app.Fragment, title: String) {
            fragmentsList.add(fragment)
            fragmentsTitleList.add(title)
        }
        override fun getPageTitle(position: Int): CharSequence = fragmentsTitleList[position]
    }

    private fun loadPreferences() {
        val preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        if (preferences.getBoolean("prefKeepScreenOn", true)) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun ShutdownApp() {
        if ((gpsApp.currentTrack.numberOfLocations > 0) || (gpsApp.currentTrack.numberOfPlacemarks > 0) || (gpsApp.isRecording) || (gpsApp.isPlacemarkRequested)) {
            val builder = AlertDialog.Builder(this)
            builder.setMessage(resources.getString(R.string.message_exit_finalizing))
            builder.setIcon(android.R.drawable.ic_menu_info_details)
            builder.setPositiveButton(R.string.yes) { dialog, _ ->
                gpsApp.isRecording = false
                gpsApp.isPlacemarkRequested = false
                EventBus.getDefault().post(EventBusMSG.NEW_TRACK)
                gpsApp.stopAndUnbindGPSService()
                gpsApp.isLocationPermissionChecked = false
                dialog.dismiss()
                gpsApp.isJustStarted = true
                finish()
            }
            builder.setNeutralButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
            builder.setNegativeButton(R.string.no) { dialog, _ ->
                gpsApp.isRecording = false
                gpsApp.isPlacemarkRequested = false
                gpsApp.stopAndUnbindGPSService()
                gpsApp.isLocationPermissionChecked = false
                dialog.dismiss()
                gpsApp.isJustStarted = true
                finish()
            }
            val dialog = builder.create()
            dialog.show()
        } else {
            gpsApp.isRecording = false
            gpsApp.isPlacemarkRequested = false
            gpsApp.stopAndUnbindGPSService()
            gpsApp.isLocationPermissionChecked = false
            finish()
        }
    }

    private fun activateActionModeIfNeeded() {
        runOnUiThread {
            if ((gpsApp.numberOfSelectedTracks > 0) && (gpsApp.getGPSActivityActiveTab() == 2)) {
                if (actionMode == null) actionMode = startSupportActionMode(ToolbarActionMode())
                actionMode?.title = if (gpsApp.numberOfSelectedTracks > 1) gpsApp.numberOfSelectedTracks.toString() else ""
            } else if (actionMode != null) {
                actionMode?.finish()
                actionMode = null
            }
        }
    }

    fun checkLocationAndNotificationPermission() {
        var requestPermission = false
        val listPermissionsNeeded: MutableList<String> = ArrayList()

        android.util.Log.w("myApp", "[#] GPSActivity.kt - Check Location Permission...")
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            android.util.Log.w("myApp", "[#] GPSActivity.kt - Precise Location Permission granted")
        } else {
            android.util.Log.w("myApp", "[#] GPSActivity.kt - Precise Location Permission denied")
            val showRationale = ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)
            if (showRationale || !gpsApp.isLocationPermissionChecked || (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)) {
                android.util.Log.w("myApp", "[#] GPSActivity.kt - Precise Location Permission denied, need new check")
                listPermissionsNeeded.add(Manifest.permission.ACCESS_COARSE_LOCATION)
                listPermissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)
                requestPermission = true
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.util.Log.w("myApp", "[#] GPSActivity.kt - Check Post Notifications Permission...")
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                android.util.Log.w("myApp", "[#] GPSActivity.kt - Post Notifications Permission granted")
            } else {
                android.util.Log.w("myApp", "[#] GPSActivity.kt - Post Notifications Permission denied")
                val showRationale = ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS)
                if (showRationale || !gpsApp.isLocationPermissionChecked || (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)) {
                    android.util.Log.w("myApp", "[#] GPSActivity.kt - Post Notifications Permission denied, need new check")
                    listPermissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
                    requestPermission = true
                }
            }
        }

        if (requestPermission) ActivityCompat.requestPermissions(this, listPermissionsNeeded.toTypedArray(), REQUEST_ID_MULTIPLE_PERMISSIONS)
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_ACTION_OPEN_DOCUMENT_TREE && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                val treeUri = data.data
                if (treeUri != null) {
                    grantUriPermission(packageName, treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    gpsApp.contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    android.util.Log.w("myApp", "[#] GPSActivity.kt - onActivityResult URI: $treeUri")
                    android.util.Log.w("myApp", "[#] GPSActivity.kt - onActivityResult URI: ${treeUri.path}")
                    android.util.Log.w("myApp", "[#] GPSActivity.kt - onActivityResult URI: ${treeUri.encodedPath}")

                    gpsApp.prefExportFolder = treeUri.toString()
                    gpsApp.loadJob(GPSApplication.JOB_TYPE_EXPORT)
                    gpsApp.executeJob()
                    gpsApp.deselectAllTracks()
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    fun openDirectory() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            intent.putExtra("android.content.extra.SHOW_ADVANCED", true)
            intent.putExtra("android.content.extra.FANCY", true)
            intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            startActivityForResult(intent, REQUEST_ACTION_OPEN_DOCUMENT_TREE)
        }
    }

    fun onToggleRecord() {
        if (!gpsApp.isBottomBarLocked) {
            if (!gpsApp.isStopButtonFlag) {
                gpsApp.isRecording = !gpsApp.isRecording
                if (!gpsApp.isFirstFixFound && gpsApp.isRecording) {
                    toast?.cancel()
                    toast = Toast.makeText(gpsApp.applicationContext, R.string.toast_recording_when_gps_found, Toast.LENGTH_LONG)
                    toast?.setGravity(Gravity.BOTTOM, 0, GPSApplication.TOAST_VERTICAL_OFFSET)
                    toast?.show()
                }
            }
        } else {
            toast?.cancel()
            toast = Toast.makeText(gpsApp.applicationContext, R.string.toast_bottom_bar_locked, Toast.LENGTH_SHORT)
            toast?.setGravity(Gravity.BOTTOM, 0, GPSApplication.TOAST_VERTICAL_OFFSET)
            toast?.show()
        }
    }

    fun onRequestStop(showDialog: Boolean, forceWhenBottomBarIsLocked: Boolean) {
        if (!gpsApp.isBottomBarLocked || forceWhenBottomBarIsLocked) {
            if (!gpsApp.isStopButtonFlag) {
                gpsApp.setStopButtonFlag(true, if (gpsApp.currentTrack.numberOfLocations + gpsApp.currentTrack.numberOfPlacemarks > 0) 1000 else 300)
                gpsApp.isRecording = false
                gpsApp.isPlacemarkRequested = false
                if (gpsApp.currentTrack.numberOfLocations + gpsApp.currentTrack.numberOfPlacemarks > 0) {
                    if (showDialog) {
                        val fm = supportFragmentManager
                        val tpDialog = FragmentTrackPropertiesDialog()
                        gpsApp.trackToEdit = gpsApp.currentTrack
                        tpDialog.setTitleResource(R.string.finalize_track)
                        tpDialog.setFinalizeTrackWithOk(true)
                        tpDialog.show(fm, "")
                    } else {
                        EventBus.getDefault().post(EventBusMSG.NEW_TRACK)
                        toast = Toast.makeText(gpsApp.applicationContext, R.string.toast_track_saved_into_tracklist, Toast.LENGTH_SHORT)
                        toast?.setGravity(Gravity.BOTTOM, 0, GPSApplication.TOAST_VERTICAL_OFFSET)
                        toast?.show()
                    }
                } else {
                    toast?.cancel()
                    toast = Toast.makeText(gpsApp.applicationContext, R.string.toast_nothing_to_save, Toast.LENGTH_SHORT)
                    toast?.setGravity(Gravity.BOTTOM, 0, GPSApplication.TOAST_VERTICAL_OFFSET)
                    toast?.show()
                }
            }
        } else {
            toast?.cancel()
            toast = Toast.makeText(gpsApp.applicationContext, R.string.toast_bottom_bar_locked, Toast.LENGTH_SHORT)
            toast?.setGravity(Gravity.BOTTOM, 0, GPSApplication.TOAST_VERTICAL_OFFSET)
            toast?.show()
        }
    }

    fun onRequestAnnotation() {
        if (!gpsApp.isBottomBarLocked) {
            if (!gpsApp.isStopButtonFlag) {
                gpsApp.isPlacemarkRequested = !gpsApp.isPlacemarkRequested
                if (!gpsApp.isFirstFixFound && gpsApp.isPlacemarkRequested) {
                    toast?.cancel()
                    toast = Toast.makeText(gpsApp.applicationContext, R.string.toast_annotate_when_gps_found, Toast.LENGTH_LONG)
                    toast?.setGravity(Gravity.BOTTOM, 0, GPSApplication.TOAST_VERTICAL_OFFSET)
                    toast?.show()
                }
            }
        } else {
            toast?.cancel()
            toast = Toast.makeText(gpsApp.applicationContext, R.string.toast_bottom_bar_locked, Toast.LENGTH_SHORT)
            toast?.setGravity(Gravity.BOTTOM, 0, GPSApplication.TOAST_VERTICAL_OFFSET)
            toast?.show()
        }
    }

    fun onRequestForceRecord() {
        if (!gpsApp.isBottomBarLocked) {
            gpsApp.isForcedTrackpointsRecording = true
        } else {
            toast?.cancel()
            toast = Toast.makeText(gpsApp.applicationContext, R.string.toast_bottom_bar_locked, Toast.LENGTH_SHORT)
            toast?.setGravity(Gravity.BOTTOM, 0, GPSApplication.TOAST_VERTICAL_OFFSET)
            toast?.show()
        }
    }

    fun onToggleLock() {
        gpsApp.isBottomBarLocked = !gpsApp.isBottomBarLocked
        if (gpsApp.isBottomBarLocked) {
            toast?.cancel()
            toast = Toast.makeText(gpsApp.applicationContext, R.string.toast_bottom_bar_locked, Toast.LENGTH_SHORT)
            toast?.setGravity(Gravity.BOTTOM, 0, GPSApplication.TOAST_VERTICAL_OFFSET)
            toast?.show()
        }
    }

    fun updateNavigationBarColor(window: Window?, applicationContext: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (window != null && applicationContext is Application) {
                if (TextUtils.equals("1", PreferenceManager.getDefaultSharedPreferences(applicationContext).getString("prefColorTheme", "2"))) {
                    window.navigationBarColor = resources.getColor(R.color.colorRecControlBackground, theme)
                    window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                } else {
                    window.navigationBarColor = resources.getColor(R.color.colorRecControlBackground, theme)
                }
            }
        }
    }
}


