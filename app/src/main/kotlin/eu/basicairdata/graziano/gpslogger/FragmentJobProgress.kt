package eu.basicairdata.graziano.gpslogger

import android.os.Bundle
import android.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class FragmentJobProgress : Fragment() {
    private var progressBar: ProgressBar? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_job_progress, container, false)
        progressBar = view.findViewById(R.id.id_jobProgressBar)
        progressBar?.progress = GPSApplication.getInstance().jobProgress
        return view
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

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(msg: Short) {
        if (msg == EventBusMSG.UPDATE_JOB_PROGRESS) update()
    }

    fun update() {
        if (isAdded) {
            val jp = GPSApplication.getInstance().jobProgress
            val pending = GPSApplication.getInstance().jobsPending
            progressBar?.progress = if (jp == 1000 || pending == 0) 0 else jp
        }
    }
}


