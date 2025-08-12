package eu.basicairdata.graziano.gpslogger

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.Collections

class ExternalViewerChecker(private val context: Context) {

    private var externalViewerList: ArrayList<ExternalViewer> = ArrayList()
    val externalViewersListProp: ArrayList<ExternalViewer> get() = externalViewerList

    private class CustomComparator : Comparator<ExternalViewer> {
        override fun compare(o1: ExternalViewer, o2: ExternalViewer): Int = o1.label.compareTo(o2.label)
    }

    private class FileType(
        var packages: ArrayList<String>?,
        var mimeType: String,
        var fileType: String,
    )

    fun getExternalViewersList(): ArrayList<ExternalViewer> = externalViewerList

    fun size(): Int = externalViewerList.size

    fun isEmpty(): Boolean = externalViewerList.isEmpty()

    fun makeExternalViewersList() {
        val pm = context.packageManager
        externalViewerList = ArrayList()
        val fileTypeList = ArrayList<FileType>()
        fileTypeList.add(FileType(null, "application/gpx+xml", GPSApplication.FILETYPE_GPX))
        fileTypeList.add(FileType(null, "application/vnd.google-earth.kml+xml", GPSApplication.FILETYPE_KML))

        val intent = Intent(Intent.ACTION_VIEW)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        for (ft in fileTypeList) {
            val file = File(if (ft.mimeType == GPSApplication.FILETYPE_GPX) GPSApplication.FILE_EMPTY_GPX else GPSApplication.FILE_EMPTY_KML)
            val uri: Uri = FileProvider.getUriForFile(GPSApplication.getInstance(), "eu.basicairdata.graziano.gpslogger.fileprovider", file)
            intent.setDataAndType(uri, ft.mimeType)

            val kmlLRI: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)
            for (tmpRI in kmlLRI) {
                var isPackageInList = false
                if (ft.packages != null) {
                    for (s in ft.packages!!) {
                        if (s == tmpRI.activityInfo.applicationInfo.packageName) {
                            isPackageInList = true
                            break
                        }
                    }
                } else isPackageInList = true

                if (isPackageInList) {
                    val aInfo = ExternalViewer()
                    aInfo.packageName = tmpRI.activityInfo.applicationInfo.packageName
                    aInfo.label = tmpRI.activityInfo.applicationInfo.loadLabel(pm).toString()

                    var found = false
                    for (a in externalViewerList) {
                        if (a.label == aInfo.label && a.packageName == aInfo.packageName) {
                            found = true
                            break
                        }
                    }
                    if (!found) {
                        aInfo.mimeType = ft.mimeType
                        aInfo.fileType = ft.fileType
                        aInfo.icon = tmpRI.activityInfo.applicationInfo.loadIcon(pm)
                        externalViewerList.add(aInfo)
                    }
                }
            }
        }
        Collections.sort(externalViewerList, CustomComparator())
        for (a in externalViewerList) {
            if (a.packageName == "at.xylem.mapin") {
                a.fileType = GPSApplication.FILETYPE_KML
                a.mimeType = "application/vnd.google-earth.kml+xml"
            }
            if (a.packageName == "com.mapswithme.maps.pro") {
                a.fileType = GPSApplication.FILETYPE_KML
                a.mimeType = "application/vnd.google-earth.kml+xml"
            }
            if (a.packageName == "app.organicmaps") {
                a.fileType = GPSApplication.FILETYPE_KML
                a.mimeType = "application/vnd.google-earth.kml+xml"
            }
        }
    }
}


