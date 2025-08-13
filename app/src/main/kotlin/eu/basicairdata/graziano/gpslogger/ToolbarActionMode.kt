package eu.basicairdata.graziano.gpslogger

import android.os.Handler
import androidx.appcompat.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class ToolbarActionMode : ActionMode.Callback {
    private var actionMenu: Menu? = null
    private var menuItemDelete: MenuItem? = null
    private var menuItemExport: MenuItem? = null
    private var menuItemShare: MenuItem? = null
    private var menuItemView: MenuItem? = null
    private var menuItemEdit: MenuItem? = null
    private var isActionmodeButtonPressed = false

    private val gpsApp = GPSApplication.getInstance()
    private val actionmodeButtonPressedHandler = Handler()
    private val actionmodeButtonPressedRunnable = Runnable { setActionmodeButtonPressed(false) }

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        mode.menuInflater.inflate(R.menu.card_menu, menu)
        EventBus.getDefault().register(this)
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        actionMenu = menu
        menuItemEdit = actionMenu?.findItem(R.id.cardmenu_edit)?.apply { setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS) }
        menuItemShare = actionMenu?.findItem(R.id.cardmenu_share)?.apply { setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS) }
        menuItemView = actionMenu?.findItem(R.id.cardmenu_view)?.apply { setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS) }
        menuItemExport = actionMenu?.findItem(R.id.cardmenu_export)?.apply { setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS) }
        menuItemDelete = actionMenu?.findItem(R.id.cardmenu_delete)?.apply { setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS) }
        EvaluateVisibility()
        return true
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        if (!isActionmodeButtonPressed()) {
            when (item.itemId) {
                R.id.cardmenu_delete -> {
                    setActionmodeButtonPressed(true)
                    EventBus.getDefault().post(EventBusMSG.ACTION_BULK_DELETE_TRACKS)
                }
                R.id.cardmenu_export -> {
                    setActionmodeButtonPressed(true)
                    EventBus.getDefault().post(EventBusMSG.ACTION_BULK_EXPORT_TRACKS)
                }
                R.id.cardmenu_view -> {
                    setActionmodeButtonPressed(true)
                    EventBus.getDefault().post(EventBusMSG.ACTION_BULK_VIEW_TRACKS)
                }
                R.id.cardmenu_share -> {
                    setActionmodeButtonPressed(true)
                    EventBus.getDefault().post(EventBusMSG.ACTION_BULK_SHARE_TRACKS)
                }
                R.id.cardmenu_edit -> {
                    setActionmodeButtonPressed(true)
                    EventBus.getDefault().post(EventBusMSG.ACTION_EDIT_TRACK)
                }
                else -> return false
            }
            return true
        }
        return false
    }

    override fun onDestroyActionMode(mode: ActionMode) {
        EventBus.getDefault().unregister(this)
        if ((gpsApp.numberOfSelectedTracks > 0) && gpsApp.getGPSActivityActiveTab() == 2) {
            GPSApplication.getInstance().deselectAllTracks()
            GPSApplication.getInstance().lastClickId = GPSApplication.NOT_AVAILABLE.toLong()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(msg: EventBusMSGNormal) {
        when (msg.eventBusMSG) {
            EventBusMSG.TRACKLIST_SELECT, EventBusMSG.TRACKLIST_DESELECT -> EvaluateVisibility()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(msg: Short) {
        when (msg) {
            EventBusMSG.UPDATE_ACTIONBAR, EventBusMSG.UPDATE_TRACKLIST -> EvaluateVisibility()
        }
    }

    fun isActionmodeButtonPressed(): Boolean = isActionmodeButtonPressed

    fun setActionmodeButtonPressed(actionmodeButtonPressed: Boolean) {
        isActionmodeButtonPressed = actionmodeButtonPressed
        if (actionmodeButtonPressed) {
            actionmodeButtonPressedHandler.postDelayed(actionmodeButtonPressedRunnable, 500)
        } else actionmodeButtonPressedHandler.removeCallbacks(actionmodeButtonPressedRunnable)
    }

    fun EvaluateVisibility() {
        if (GPSApplication.getInstance().numberOfSelectedTracks > 0) {
            menuItemView?.isVisible = (gpsApp.numberOfSelectedTracks <= 1) && (gpsApp.isContextMenuViewVisible)
            menuItemEdit?.isVisible = gpsApp.numberOfSelectedTracks <= 1
            menuItemShare?.isVisible = gpsApp.isContextMenuShareVisible && (gpsApp.prefExportGPX || gpsApp.prefExportKML || gpsApp.prefExportTXT)
            menuItemExport?.isVisible = gpsApp.prefExportGPX || gpsApp.prefExportKML || gpsApp.prefExportTXT
            menuItemDelete?.isVisible = !gpsApp.selectedTracks.contains(gpsApp.currentTrack)

            if (menuItemView?.isVisible == true) {
                if (gpsApp.viewInApp != "") {
                    menuItemView?.title = gpsApp.getString(R.string.card_menu_view, gpsApp.viewInApp)
                    if (gpsApp.viewInAppIcon != null) menuItemView?.icon = gpsApp.viewInAppIcon
                    else menuItemView?.setIcon(R.drawable.ic_visibility_24dp)
                } else {
                    menuItemView?.setTitle(gpsApp.getString(R.string.card_menu_view_selector))?.setIcon(R.drawable.ic_visibility_24dp)
                }
            }
        }
    }
}


