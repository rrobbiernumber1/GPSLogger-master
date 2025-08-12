package eu.basicairdata.graziano.gpslogger

object EventBusMSG {
    const val APP_RESUME: Short = 1
    const val APP_PAUSE: Short = 2
    const val NEW_TRACK: Short = 3
    const val UPDATE_FIX: Short = 4
    const val UPDATE_TRACK: Short = 5
    const val UPDATE_TRACKLIST: Short = 6
    const val UPDATE_SETTINGS: Short = 7
    const val REQUEST_ADD_PLACEMARK: Short = 8
    const val ADD_PLACEMARK: Short = 9
    const val APPLY_SETTINGS: Short = 10
    const val TOAST_TRACK_EXPORTED: Short = 11
    const val UPDATE_JOB_PROGRESS: Short = 13
    const val NOTIFY_TRACKS_DELETED: Short = 14
    const val UPDATE_ACTIONBAR: Short = 15
    const val REFRESH_TRACKLIST: Short = 16
    const val REFRESH_TRACKTYPE: Short = 17

    const val TRACKLIST_DESELECT: Short = 24
    const val TRACKLIST_SELECT: Short = 25
    const val INTENT_SEND: Short = 26
    const val TOAST_UNABLE_TO_WRITE_THE_FILE: Short = 27

    const val ACTION_BULK_DELETE_TRACKS: Short = 40
    const val ACTION_BULK_EXPORT_TRACKS: Short = 41
    const val ACTION_BULK_VIEW_TRACKS: Short = 42
    const val ACTION_BULK_SHARE_TRACKS: Short = 43
    const val TRACKLIST_RANGE_SELECTION: Short = 44
    const val ACTION_EDIT_TRACK: Short = 45
}


