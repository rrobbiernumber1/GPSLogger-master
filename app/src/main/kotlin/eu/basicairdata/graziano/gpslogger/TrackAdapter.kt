package eu.basicairdata.graziano.gpslogger

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import org.greenrobot.eventbus.EventBus

/**
 * The Adapter for the Card View of the Tracklist.
 */
class TrackAdapter(data: List<Track>) : RecyclerView.Adapter<TrackAdapter.TrackHolder>() {

    private val dataSet: List<Track> = synchronized(data) { data }
    var isLightTheme: Boolean = false
    private var startAnimationTime: Long = 0
    private var pointsCount: Long = GPSApplication.getInstance().currentTrack.numberOfLocations + GPSApplication.getInstance().currentTrack.numberOfPlacemarks

    companion object {
        private val BMP_CURRENT_TRACK_RECORDING: Bitmap = BitmapFactory.decodeResource(GPSApplication.getInstance().resources, R.mipmap.ic_recording_48dp)
        private val BMP_CURRENT_TRACK_PAUSED: Bitmap = BitmapFactory.decodeResource(GPSApplication.getInstance().resources, R.mipmap.ic_paused_white_48dp)
    }

    inner class TrackHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener, View.OnLongClickListener {

        private val phdformatter = PhysicalDataFormatter()
        private lateinit var track: Track
        private var tt: Int = 0

        private val card: CardView = itemView.findViewById(R.id.card_view)
        private val textViewTrackName: TextView = itemView.findViewById(R.id.id_textView_card_TrackName)
        private val textViewTrackDescription: TextView = itemView.findViewById(R.id.id_textView_card_TrackDesc)
        private val textViewTrackLength: TextView = itemView.findViewById(R.id.id_textView_card_length)
        private val textViewTrackDuration: TextView = itemView.findViewById(R.id.id_textView_card_duration)
        private val textViewTrackAltitudeGap: TextView = itemView.findViewById(R.id.id_textView_card_altitudegap)
        private val textViewTrackMaxSpeed: TextView = itemView.findViewById(R.id.id_textView_card_maxspeed)
        private val textViewTrackAverageSpeed: TextView = itemView.findViewById(R.id.id_textView_card_averagespeed)
        private val textViewTrackGeopoints: TextView = itemView.findViewById(R.id.id_textView_card_geopoints)
        private val textViewTrackPlacemarks: TextView = itemView.findViewById(R.id.id_textView_card_placemarks)
        private val imageViewThumbnail: ImageView = itemView.findViewById(R.id.id_imageView_card_minimap)
        private val imageViewPulse: ImageView = itemView.findViewById(R.id.id_imageView_card_pulse)
        private val imageViewIcon: ImageView = itemView.findViewById(R.id.id_imageView_card_tracktype)

        init {
            itemView.setOnClickListener(this)
            itemView.setOnLongClickListener(this)
            if (isLightTheme) {
                imageViewThumbnail.colorFilter = GPSApplication.colorMatrixColorFilter
                imageViewPulse.colorFilter = GPSApplication.colorMatrixColorFilter
            }
        }

        override fun onClick(v: View) {
            if (GPSApplication.getInstance().jobsPending == 0) {
                track.isSelected = !track.isSelected
                card.isSelected = track.isSelected
                GPSApplication.getInstance().lastClickId = track.id
                GPSApplication.getInstance().lastClickState = track.isSelected
                EventBus.getDefault().post(EventBusMSGNormal(if (track.isSelected) EventBusMSG.TRACKLIST_SELECT else EventBusMSG.TRACKLIST_DESELECT, track.id))
            }
        }

        override fun onLongClick(view: View): Boolean {
            if (GPSApplication.getInstance().jobsPending == 0
                && GPSApplication.getInstance().lastClickId != track.id
                && GPSApplication.getInstance().numberOfSelectedTracks > 0
            ) {
                EventBus.getDefault().post(EventBusMSGNormal(EventBusMSG.TRACKLIST_RANGE_SELECTION, track.id))
            }
            return false
        }

        fun updateTrackStats(trk: Track) {
            if (trk.numberOfLocations >= 1) {
                var phd = phdformatter.format(trk.estimatedDistance, PhysicalDataFormatter.FORMAT_DISTANCE)
                textViewTrackLength.text = phd.value + " " + phd.um
                phd = phdformatter.format(trk.prefTime, PhysicalDataFormatter.FORMAT_DURATION)
                textViewTrackDuration.text = phd.value
                phd = phdformatter.format(trk.getEstimatedAltitudeGap(GPSApplication.getInstance().prefEGM96AltitudeCorrection), PhysicalDataFormatter.FORMAT_ALTITUDE)
                textViewTrackAltitudeGap.text = phd.value + " " + phd.um
                phd = phdformatter.format(trk.speedMax, PhysicalDataFormatter.FORMAT_SPEED)
                textViewTrackMaxSpeed.text = phd.value + " " + phd.um
                phd = phdformatter.format(trk.prefSpeedAverage, PhysicalDataFormatter.FORMAT_SPEED_AVG)
                textViewTrackAverageSpeed.text = phd.value + " " + phd.um
            } else {
                textViewTrackLength.text = ""
                textViewTrackDuration.text = ""
                textViewTrackAltitudeGap.text = ""
                textViewTrackMaxSpeed.text = ""
                textViewTrackAverageSpeed.text = ""
            }
            textViewTrackGeopoints.text = trk.numberOfLocations.toString()
            textViewTrackPlacemarks.text = trk.numberOfPlacemarks.toString()

            tt = trk.estimatedTrackType
            if (tt != GPSApplication.NOT_AVAILABLE) imageViewIcon.setImageResource(Track.ACTIVITY_DRAWABLE_RESOURCE[tt])
            else imageViewIcon.setImageBitmap(null)

            if (GPSApplication.getInstance().isRecording) {
                imageViewThumbnail.setImageBitmap(BMP_CURRENT_TRACK_RECORDING)
                imageViewPulse.visibility = View.VISIBLE
                if ((pointsCount != trk.numberOfLocations + trk.numberOfPlacemarks) && (System.currentTimeMillis() - startAnimationTime >= 700L)) {
                    pointsCount = trk.numberOfLocations + trk.numberOfPlacemarks
                    val sunRise = AnimationUtils.loadAnimation(GPSApplication.getInstance().applicationContext, R.anim.record_pulse)
                    imageViewPulse.startAnimation(sunRise)
                    startAnimationTime = System.currentTimeMillis()
                }
            } else {
                imageViewPulse.visibility = View.INVISIBLE
                imageViewThumbnail.setImageBitmap(BMP_CURRENT_TRACK_PAUSED)
            }
        }

        fun bindTrack(trk: Track) {
            track = trk
            card.isSelected = track.isSelected
            imageViewPulse.visibility = View.INVISIBLE
            textViewTrackName.text = track.name
            if (track.description.isEmpty()) textViewTrackDescription.text = GPSApplication.getInstance().getString(R.string.track_id) + " " + track.id
            else textViewTrackDescription.text = track.description
            if (trk.numberOfLocations >= 1) {
                var phd = phdformatter.format(track.estimatedDistance, PhysicalDataFormatter.FORMAT_DISTANCE)
                textViewTrackLength.text = phd.value + " " + phd.um
                phd = phdformatter.format(track.prefTime, PhysicalDataFormatter.FORMAT_DURATION)
                textViewTrackDuration.text = phd.value
                phd = phdformatter.format(track.getEstimatedAltitudeGap(GPSApplication.getInstance().prefEGM96AltitudeCorrection), PhysicalDataFormatter.FORMAT_ALTITUDE)
                textViewTrackAltitudeGap.text = phd.value + " " + phd.um
                phd = phdformatter.format(track.speedMax, PhysicalDataFormatter.FORMAT_SPEED)
                textViewTrackMaxSpeed.text = phd.value + " " + phd.um
                phd = phdformatter.format(track.prefSpeedAverage, PhysicalDataFormatter.FORMAT_SPEED_AVG)
                textViewTrackAverageSpeed.text = phd.value + " " + phd.um
            } else {
                textViewTrackLength.text = ""
                textViewTrackDuration.text = ""
                textViewTrackAltitudeGap.text = ""
                textViewTrackMaxSpeed.text = ""
                textViewTrackAverageSpeed.text = ""
            }
            textViewTrackGeopoints.text = track.numberOfLocations.toString()
            textViewTrackPlacemarks.text = track.numberOfPlacemarks.toString()

            tt = trk.estimatedTrackType
            if (tt != GPSApplication.NOT_AVAILABLE) {
                try {
                    imageViewIcon.setImageResource(Track.ACTIVITY_DRAWABLE_RESOURCE[tt])
                } catch (_: IndexOutOfBoundsException) {
                    imageViewIcon.setImageBitmap(null)
                }
            } else imageViewIcon.setImageBitmap(null)

            if (GPSApplication.getInstance().currentTrack.id == track.id) {
                imageViewThumbnail.setImageBitmap(if (GPSApplication.getInstance().isRecording) BMP_CURRENT_TRACK_RECORDING else BMP_CURRENT_TRACK_PAUSED)
            } else {
                Glide.clear(imageViewThumbnail)
                Glide.with(GPSApplication.getInstance().applicationContext)
                    .load(GPSApplication.getInstance().applicationContext.filesDir.toString() + "/Thumbnails/" + track.id + ".png")
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .error(null)
                    .dontAnimate()
                    .into(imageViewThumbnail)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackHolder =
        TrackHolder(LayoutInflater.from(parent.context).inflate(R.layout.card_trackinfo, parent, false))

    override fun onBindViewHolder(holder: TrackHolder, position: Int) {
        holder.bindTrack(dataSet[position])
    }

    override fun getItemCount(): Int = dataSet.size
}


