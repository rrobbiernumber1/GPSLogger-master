package eu.basicairdata.graziano.gpslogger

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * The Foreground Service that keeps alive the app in background when recording.
 * It shows a notification that shows the status of the recording, the traveled distance
 * and the related duration.
 */
class GPSService : Service() {

    private val id: Int = 1
    private var oldNotificationText: String = ""
    private var builder: NotificationCompat.Builder? = null
    private var mNotificationManager: NotificationManager? = null
    private var recordingState: Boolean = false
    private var wakeLock: PowerManager.WakeLock? = null

    inner class LocalBinder : Binder() {
        val serviceInstance: GPSService
            get() = this@GPSService
    }

    private val mBinder: IBinder = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GPSLogger:wakelock")
        Log.w("myApp", "[#] GPSService.kt - CREATE = onCreate")
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this)
        }
        EventBus.getDefault().register(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        mNotificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        startForeground(id, notification)
        Log.w("myApp", "[#] GPSService.kt - START = onStartCommand")
        return START_NOT_STICKY
    }

    @SuppressLint("WakelockTimeout")
    override fun onBind(intent: Intent?): IBinder {
        if (wakeLock != null && !wakeLock!!.isHeld) {
            wakeLock!!.acquire()
            Log.w("myApp", "[#] GPSService.kt - WAKELOCK acquired")
        }
        Log.w("myApp", "[#] GPSService.kt - BIND = onBind")
        return mBinder
    }

    override fun onDestroy() {
        if (wakeLock != null && wakeLock!!.isHeld) {
            wakeLock!!.release()
            Log.w("myApp", "[#] GPSService.kt - WAKELOCK released")
        }
        EventBus.getDefault().unregister(this)
        Log.w("myApp", "[#] GPSService.kt - DESTROY = onDestroy")
        super.onDestroy()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(msg: Short) {
        if (msg == EventBusMSG.UPDATE_FIX && builder != null
            && (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || mNotificationManager?.areNotificationsEnabled() == true)
        ) {
            val notificationText = composeContentText()
            if (oldNotificationText != notificationText) {
                builder!!.setContentText(notificationText)
                builder!!.setOngoing(true)
                if (isIconRecording() != recordingState) {
                    recordingState = isIconRecording()
                    builder!!.setSmallIcon(if (recordingState) R.mipmap.ic_notify_recording_24dp else R.mipmap.ic_notify_24dp)
                }
                mNotificationManager?.notify(id, builder!!.build())
                oldNotificationText = notificationText
            }
        }
    }

    private val notification: Notification
        get() {
            val channelId = "GPSLoggerServiceChannel"
            recordingState = isIconRecording()
            builder = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(if (recordingState) R.mipmap.ic_notify_recording_24dp else R.mipmap.ic_notify_24dp)
                .setColor(resources.getColor(R.color.colorPrimaryLight))
                .setContentTitle(getString(R.string.app_name))
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOngoing(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentText(composeContentText())

            val startIntent = Intent(applicationContext, GPSActivity::class.java)
            startIntent.action = Intent.ACTION_MAIN
            startIntent.addCategory(Intent.CATEGORY_LAUNCHER)
            startIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            val contentIntent = PendingIntent.getActivity(applicationContext, 1, startIntent, PendingIntent.FLAG_IMMUTABLE)
            builder!!.setContentIntent(contentIntent)
            return builder!!.build()
        }

    private fun isIconRecording(): Boolean =
        GPSApplication.getInstance().getGPSStatus() == GPSApplication.GPS_OK && GPSApplication.getInstance().isRecording

    private fun composeContentText(): String {
        var notificationText = ""
        val gpsStatus = GPSApplication.getInstance().getGPSStatus()
        when (gpsStatus) {
            GPSApplication.GPS_DISABLED -> notificationText = getString(R.string.gps_disabled)
            GPSApplication.GPS_OUTOFSERVICE -> notificationText = getString(R.string.gps_out_of_service)
            GPSApplication.GPS_TEMPORARYUNAVAILABLE, GPSApplication.GPS_SEARCHING -> notificationText = getString(R.string.gps_searching)
            GPSApplication.GPS_STABILIZING -> notificationText = getString(R.string.gps_stabilizing)
            GPSApplication.GPS_OK -> {
                if (GPSApplication.getInstance().isRecording && GPSApplication.getInstance().currentTrack != null) {
                    val phdformatter = PhysicalDataFormatter()
                    val phdDuration = phdformatter.format(GPSApplication.getInstance().currentTrack.prefTime, PhysicalDataFormatter.FORMAT_DURATION)
                    if (phdDuration.value.isEmpty()) phdDuration.value = "00:00"
                    notificationText = getString(R.string.duration) + ": " + phdDuration.value

                    val phdDistance = phdformatter.format(GPSApplication.getInstance().currentTrack.estimatedDistance, PhysicalDataFormatter.FORMAT_DISTANCE)
                    if (!phdDistance.value.isEmpty()) {
                        notificationText += " - " + getString(R.string.distance) + ": " + phdDistance.value + " " + phdDistance.um
                    }
                } else {
                    notificationText = getString(R.string.notification_contenttext)
                }
            }
        }
        return notificationText
    }
}


