package eu.basicairdata.graziano.gpslogger

import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import org.greenrobot.eventbus.EventBus
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * The Class that manage the EGM96 Altitude Correction.
 * It loads the geoid heights from the WW15MGH.DAC binary file into a 1440x721 array
 * and uses it to return the altitude correction basing on coordinates.
 */
class EGM96 private constructor() {

    companion object {
        private val instance: EGM96 = EGM96()
        @JvmStatic fun getInstance(): EGM96 = instance
        val EGM96_VALUE_INVALID: Double = GPSApplication.NOT_AVAILABLE.toDouble()
        private const val BOUNDARY = 3
    }

    private val egmGrid: Array<ShortArray> = Array(BOUNDARY + 1440 + BOUNDARY) { ShortArray(BOUNDARY + 721 + BOUNDARY) }
    @Volatile private var isEGMGridLoaded: Boolean = false
    @Volatile private var isEGMGridLoading: Boolean = false
    private var sharedFolder: DocumentFile? = null
    private var privateFolder: DocumentFile? = null

    fun loadGrid(sharedPath: String, privatePath: String) {
        if (!isEGMGridLoaded && !isEGMGridLoading) {
            isEGMGridLoading = true
            sharedFolder = if (sharedPath.startsWith("content"))
                DocumentFile.fromTreeUri(GPSApplication.getInstance(), Uri.parse(sharedPath))
            else DocumentFile.fromFile(File(sharedPath))
            privateFolder = DocumentFile.fromFile(File(privatePath))

            Thread(LoadEGM96Grid()).start()
        } else {
            if (isEGMGridLoading) Log.w("myApp", "[#] EGM96.kt - Grid is already loading, please wait")
            if (isEGMGridLoaded) Log.w("myApp", "[#] EGM96.kt - Grid already loaded")
        }
    }

    fun isGridAvailable(path: String): Boolean {
        return try {
            val gridFolder = if (path.startsWith("content"))
                DocumentFile.fromTreeUri(GPSApplication.getInstance(), Uri.parse(path))
            else DocumentFile.fromFile(File(path))
            val gridDocument = gridFolder?.findFile("WW15MGH.DAC")
            Log.w(
                "myApp",
                "[#] EGM96.kt - Check existence of EGM Grid into $path: " +
                    if ((gridDocument != null) && gridDocument.exists() && (gridDocument.length() == 2_076_480L)) "TRUE" else "FALSE"
            )
            (gridDocument != null) && gridDocument.exists() && (gridDocument.length() == 2_076_480L)
        } catch (_: NullPointerException) {
            false
        }
    }

    val isLoaded: Boolean get() = isEGMGridLoaded
    val isLoading: Boolean get() = isEGMGridLoading

    fun getEGMCorrection(latitude: Double, longitude: Double): Double {
        if (!isEGMGridLoaded) return EGM96_VALUE_INVALID

        var lat = 90.0 - latitude
        var lon = longitude
        if (lon < 0) lon += 360.0

        val ilon: Int = (lon / 0.25).toInt() + BOUNDARY
        val ilat: Int = (lat / 0.25).toInt() + BOUNDARY
        return try {
            val hc11 = egmGrid[ilon][ilat]
            val hc12 = egmGrid[ilon][ilat + 1]
            val hc21 = egmGrid[ilon + 1][ilat]
            val hc22 = egmGrid[ilon + 1][ilat + 1]

            val hc1 = hc11 + (hc12 - hc11) * (lat % 0.25) / 0.25
            val hc2 = hc21 + (hc22 - hc21) * (lat % 0.25) / 0.25
            ((hc1 + (hc2 - hc1) * (lon % 0.25) / 0.25) / 100)
        } catch (_: ArrayIndexOutOfBoundsException) {
            EGM96_VALUE_INVALID
        }
    }

    @Throws(IOException::class)
    private fun copyFile(`in`: InputStream, out: OutputStream) {
        val buffer = ByteArray(1024)
        while (true) {
            val read = `in`.read(buffer)
            if (read == -1) break
            out.write(buffer, 0, read)
        }
    }

    private inner class LoadEGM96Grid : Runnable {
        override fun run() {
            Log.w("myApp", "[#] EGM96.kt - LoadEGM96Grid")
            Thread.currentThread().priority = Thread.MIN_PRIORITY

            var sharedDocument: DocumentFile? = null
            var privateDocument: DocumentFile? = null
            var isPrivateDocumentPresent = false
            var isSharedDocumentPresent = false

            sharedDocument = sharedFolder?.findFile("WW15MGH.DAC")
            if ((sharedDocument != null) && sharedDocument.exists() && (sharedDocument.length() == 2_076_480L)) isSharedDocumentPresent = true
            Log.w(
                "myApp",
                "[#] EGM96.kt - Shared Copy of EGM file " + if (isSharedDocumentPresent) "EXISTS: ${sharedDocument?.uri}" else "NOT EXISTS"
            )

            privateDocument = privateFolder?.findFile("WW15MGH.DAC")
            if ((privateDocument != null) && privateDocument.exists() && (privateDocument.length() == 2_076_480L)) isPrivateDocumentPresent = true
            Log.w(
                "myApp",
                "[#] EGM96.kt - Private Copy of EGM file " + if (isPrivateDocumentPresent) "EXISTS: ${privateDocument?.uri}" else "NOT EXISTS"
            )

            if (!isPrivateDocumentPresent && isSharedDocumentPresent) {
                Log.w("myApp", "[#] EGM96.kt - Copy EGM96 Grid into FilesDir")
                if ((privateDocument != null) && privateDocument.exists()) privateDocument.delete()
                if (sharedDocument?.exists() == true) {
                    privateDocument = privateFolder?.createFile("", "WW15MGH.DAC")
                    try {
                        val `in` = GPSApplication.getInstance().contentResolver.openInputStream(sharedDocument.uri!!)
                        val out = GPSApplication.getInstance().contentResolver.openOutputStream(privateDocument!!.uri!!)
                        if (`in` != null && out != null) {
                            copyFile(`in`, out)
                            `in`.close()
                            out.flush()
                            out.close()
                            Log.w("myApp", "[#] EGM96.kt - EGM File copy completed")
                            isPrivateDocumentPresent = privateDocument.exists() && (privateDocument.length() == 2_076_480L)
                        }
                    } catch (e: Exception) {
                        Log.w("MyApp", "[#] EGM96.kt - Unable to make local copy of EGM file: ${e.message}")
                    }
                }
            }

            if (isPrivateDocumentPresent) {
                Log.w("myApp", "[#] EGM96.kt - Start loading grid from file: ${privateDocument?.uri}")
                val fin: InputStream = try {
                    GPSApplication.getInstance().contentResolver.openInputStream(privateDocument!!.uri!!)
                        ?: throw FileNotFoundException()
                } catch (_: FileNotFoundException) {
                    isEGMGridLoaded = false
                    isEGMGridLoading = false
                    Log.w("myApp", "[#] EGM96.kt - FileNotFoundException")
                    return
                }
                val bin = BufferedInputStream(fin)
                val din = DataInputStream(bin)
                val count = (privateDocument!!.length() / 2).toInt()
                var iLon = BOUNDARY
                var iLat = BOUNDARY
                for (i in 0 until count) {
                    try {
                        egmGrid[iLon][iLat] = din.readShort()
                        iLon++
                        if (iLon >= (1440 + BOUNDARY)) {
                            iLat++
                            iLon = BOUNDARY
                        }
                    } catch (_: IOException) {
                        isEGMGridLoaded = false
                        isEGMGridLoading = false
                        Log.w("myApp", "[#] EGM96.kt - IOException")
                        return
                    }
                }

                if (BOUNDARY > 0) {
                    for (ix in 0 until BOUNDARY) {
                        for (iy in BOUNDARY until BOUNDARY + 721) {
                            egmGrid[ix][iy] = egmGrid[ix + 1440][iy]
                            egmGrid[BOUNDARY + ix + 1440][iy] = egmGrid[BOUNDARY + ix][iy]
                        }
                    }
                    for (iy in 0 until BOUNDARY) {
                        for (ix in 0 until BOUNDARY + 1440 + BOUNDARY) {
                            if (ix > 720) {
                                egmGrid[ix][iy] = egmGrid[ix - 720][BOUNDARY + BOUNDARY - iy]
                                egmGrid[ix][BOUNDARY + iy + 721] = egmGrid[ix - 720][BOUNDARY + 721 - 2 - iy]
                            } else {
                                egmGrid[ix][iy] = egmGrid[ix + 720][BOUNDARY + BOUNDARY - iy]
                                egmGrid[ix][BOUNDARY + iy + 721] = egmGrid[ix + 720][BOUNDARY + 721 - 2 - iy]
                            }
                        }
                    }
                }

                isEGMGridLoading = false
                isEGMGridLoaded = true
                Log.w("myApp", "[#] EGM96.kt - Grid Successfully Loaded")
            } else {
                isEGMGridLoading = false
                isEGMGridLoaded = false
                if (privateDocument != null && privateDocument.length() != 2_076_480L) {
                    Log.w("myApp", "[#] EGM96.kt - File has invalid length: ${privateDocument.length()}")
                }
            }
            EventBus.getDefault().post(EventBusMSG.UPDATE_FIX)
            EventBus.getDefault().post(EventBusMSG.UPDATE_TRACK)
            EventBus.getDefault().post(EventBusMSG.UPDATE_TRACKLIST)
        }
    }
}


