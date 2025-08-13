package eu.basicairdata.graziano.gpslogger

import android.app.Dialog
import android.content.res.Resources
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.NonNull
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import org.greenrobot.eventbus.EventBus

class FragmentTrackTypeDialog : DialogFragment() {

    private class ActivityCategory(val layout: LinearLayout) {
        val activityTypeList: ArrayList<ActivityType> = ArrayList()
    }

    class ActivityType(val value: Int) {
        val drawableId: Int = Track.ACTIVITY_DRAWABLE_RESOURCE[value]
    }

    @NonNull
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext())

        val scale = requireContext().resources.displayMetrics.density
        val iconMargin = (4 * scale + 0.5f).toInt()

        val inflater: LayoutInflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.fragment_track_type_dialog, null)
        builder.setView(view)

        val activityCategories: ArrayList<ActivityCategory> = ArrayList()

        val acFitness = ActivityCategory(view.findViewById(R.id.tracktype_main_linearlayout_cat_fitness))
        acFitness.activityTypeList.add(ActivityType(Track.TRACK_TYPE_WALK))
        acFitness.activityTypeList.add(ActivityType(Track.TRACK_TYPE_HIKING))
        acFitness.activityTypeList.add(ActivityType(Track.TRACK_TYPE_NORDICWALKING))
        acFitness.activityTypeList.add(ActivityType(Track.TRACK_TYPE_RUN))
        activityCategories.add(acFitness)

        val acWatersports = ActivityCategory(view.findViewById(R.id.tracktype_main_linearlayout_cat_water))
        acWatersports.activityTypeList.add(ActivityType(Track.TRACK_TYPE_SWIMMING))
        acWatersports.activityTypeList.add(ActivityType(Track.TRACK_TYPE_SCUBADIVING))
        acWatersports.activityTypeList.add(ActivityType(Track.TRACK_TYPE_ROWING))
        acWatersports.activityTypeList.add(ActivityType(Track.TRACK_TYPE_KAYAKING))
        acWatersports.activityTypeList.add(ActivityType(Track.TRACK_TYPE_SURFING))
        acWatersports.activityTypeList.add(ActivityType(Track.TRACK_TYPE_KITESURFING))
        acWatersports.activityTypeList.add(ActivityType(Track.TRACK_TYPE_SAILING))
        acWatersports.activityTypeList.add(ActivityType(Track.TRACK_TYPE_BOAT))
        activityCategories.add(acWatersports)

        val acSnowIce = ActivityCategory(view.findViewById(R.id.tracktype_main_linearlayout_cat_snow))
        acSnowIce.activityTypeList.add(ActivityType(Track.TRACK_TYPE_DOWNHILLSKIING))
        acSnowIce.activityTypeList.add(ActivityType(Track.TRACK_TYPE_SNOWBOARDING))
        acSnowIce.activityTypeList.add(ActivityType(Track.TRACK_TYPE_SLEDDING))
        acSnowIce.activityTypeList.add(ActivityType(Track.TRACK_TYPE_SNOWMOBILE))
        acSnowIce.activityTypeList.add(ActivityType(Track.TRACK_TYPE_SNOWSHOEING))
        acSnowIce.activityTypeList.add(ActivityType(Track.TRACK_TYPE_ICESKATING))
        activityCategories.add(acSnowIce)

        val acAir = ActivityCategory(view.findViewById(R.id.tracktype_main_linearlayout_cat_air))
        acAir.activityTypeList.add(ActivityType(Track.TRACK_TYPE_FLIGHT))
        acAir.activityTypeList.add(ActivityType(Track.TRACK_TYPE_HELICOPTER))
        acAir.activityTypeList.add(ActivityType(Track.TRACK_TYPE_ROCKET))
        acAir.activityTypeList.add(ActivityType(Track.TRACK_TYPE_PARAGLIDING))
        acAir.activityTypeList.add(ActivityType(Track.TRACK_TYPE_AIRBALLOON))
        activityCategories.add(acAir)

        val acWheel = ActivityCategory(view.findViewById(R.id.tracktype_main_linearlayout_cat_wheel))
        acWheel.activityTypeList.add(ActivityType(Track.TRACK_TYPE_BICYCLE))
        acWheel.activityTypeList.add(ActivityType(Track.TRACK_TYPE_SKATEBOARDING))
        acWheel.activityTypeList.add(ActivityType(Track.TRACK_TYPE_ROLLERSKATING))
        acWheel.activityTypeList.add(ActivityType(Track.TRACK_TYPE_WHEELCHAIR))
        activityCategories.add(acWheel)

        val acMobility = ActivityCategory(view.findViewById(R.id.tracktype_main_linearlayout_cat_mobility))
        acMobility.activityTypeList.add(ActivityType(Track.TRACK_TYPE_ELECTRICSCOOTER))
        acMobility.activityTypeList.add(ActivityType(Track.TRACK_TYPE_MOPED))
        acMobility.activityTypeList.add(ActivityType(Track.TRACK_TYPE_MOTORCYCLE))
        acMobility.activityTypeList.add(ActivityType(Track.TRACK_TYPE_CAR))
        acMobility.activityTypeList.add(ActivityType(Track.TRACK_TYPE_TRUCK))
        acMobility.activityTypeList.add(ActivityType(Track.TRACK_TYPE_BUS))
        acMobility.activityTypeList.add(ActivityType(Track.TRACK_TYPE_TRAIN))
        acMobility.activityTypeList.add(ActivityType(Track.TRACK_TYPE_AGRICULTURE))
        activityCategories.add(acMobility)

        val acOther = ActivityCategory(view.findViewById(R.id.tracktype_main_linearlayout_cat_other))
        acOther.activityTypeList.add(ActivityType(Track.TRACK_TYPE_STEADY))
        acOther.activityTypeList.add(ActivityType(Track.TRACK_TYPE_MOUNTAIN))
        acOther.activityTypeList.add(ActivityType(Track.TRACK_TYPE_CITY))
        acOther.activityTypeList.add(ActivityType(Track.TRACK_TYPE_FOREST))
        acOther.activityTypeList.add(ActivityType(Track.TRACK_TYPE_WORK))
        acOther.activityTypeList.add(ActivityType(Track.TRACK_TYPE_PHOTOGRAPHY))
        acOther.activityTypeList.add(ActivityType(Track.TRACK_TYPE_RESEARCH))
        activityCategories.add(acOther)

        val acOtherSports = ActivityCategory(view.findViewById(R.id.tracktype_main_linearlayout_cat_other_sports))
        acOtherSports.activityTypeList.add(ActivityType(Track.TRACK_TYPE_SOCCER))
        acOtherSports.activityTypeList.add(ActivityType(Track.TRACK_TYPE_GOLF))
        acOtherSports.activityTypeList.add(ActivityType(Track.TRACK_TYPE_PETS))
        acOtherSports.activityTypeList.add(ActivityType(Track.TRACK_TYPE_MAP))
        activityCategories.add(acOtherSports)

        val resources: Resources = requireContext().resources
        val theme: Resources.Theme = requireContext().theme

        for (ac in activityCategories) {
            for (aType in ac.activityTypeList) {
                val iv = ImageView(requireActivity().applicationContext)

                val drawable: Drawable? = VectorDrawableCompat.create(resources, aType.drawableId, theme)
                iv.setImageDrawable(drawable)
                iv.setColorFilter(
                    resources.getColor(
                        if (aType.value == GPSApplication.getInstance().selectedTrackTypeOnDialog) R.color.textColorRecControlPrimary else R.color.colorIconDisabledOnDialog
                    ),
                    PorterDuff.Mode.SRC_IN
                )

                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                iv.layoutParams = lp
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    iv.tooltipText = Track.ACTIVITY_DESCRIPTION[aType.value]
                }
                iv.tag = aType.value

                val marginParams = iv.layoutParams as LinearLayout.LayoutParams
                marginParams.setMargins(iconMargin, iconMargin, iconMargin, iconMargin)
                iv.layoutParams = marginParams

                ac.layout.addView(iv)

                iv.setOnClickListener { view: View ->
                    GPSApplication.getInstance().selectedTrackTypeOnDialog = view.tag as Int
                    EventBus.getDefault().post(EventBusMSG.REFRESH_TRACKTYPE)
                    dismiss()
                }
            }
        }

        return builder.create()
    }
}


