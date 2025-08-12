package eu.basicairdata.graziano.gpslogger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ActionsBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val broadcastedAction = intent.action
        if (broadcastedAction != null) {
            when (broadcastedAction) {
                Intent.ACTION_SCREEN_OFF -> GPSApplication.getInstance().onScreenOff()
                Intent.ACTION_SCREEN_ON -> GPSApplication.getInstance().onScreenOn()
                Intent.ACTION_SHUTDOWN -> GPSApplication.getInstance()?.onShutdown()
            }
        }
    }
}


