package eu.basicairdata.graziano.gpslogger

import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast

class FragmentAboutDialog : DialogFragment() {
    private val COPYRIGHT_RANGE_END = "2024"

    @NonNull
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val createAboutAlert = AlertDialog.Builder(requireActivity(), R.style.MyMaterialThemeAbout)
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.fragment_about_dialog, null)

        val gpsApp = GPSApplication.getInstance()
        val tvVersion = view.findViewById<TextView>(R.id.id_about_textView_Version)
        val versionName = BuildConfig.VERSION_NAME
        tvVersion.text = getString(R.string.about_version) + " " + versionName

        val tvDescription = view.findViewById<TextView>(R.id.id_about_textView_description)
        tvDescription.text = getString(R.string.about_description, COPYRIGHT_RANGE_END)

        var appOrigin = 0
        try {
            val installer = gpsApp.applicationContext.packageManager.getInstallerPackageName(gpsApp.applicationContext.packageName)
            if (installer == "com.android.vending" || installer == "com.google.android.feedback") appOrigin = 1
        } catch (e: Exception) {
            Log.w("myApp", "[#] GPSApplication.java - Exception trying to determine the package installer")
            appOrigin = 0
        }

        when (appOrigin) {
            1 -> {
                tvDescription.text = tvDescription.text.toString() + "\n\n" + getString(R.string.about_description_googleplaystore)
                createAboutAlert.setView(view).setNegativeButton(R.string.about_rate_this_app) { _: DialogInterface, _: Int ->
                    var marketfailed = false
                    try {
                        context!!.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + BuildConfig.APPLICATION_ID)))
                    } catch (e: Exception) {
                        marketfailed = true
                    }
                    if (marketfailed) {
                        try {
                            context!!.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + BuildConfig.APPLICATION_ID)))
                        } catch (e: Exception) {
                            Toast.makeText(context, getString(R.string.about_unable_to_rate), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        createAboutAlert.setView(view).setPositiveButton(R.string.about_ok) { _: DialogInterface, _: Int -> }
        return createAboutAlert.create()
    }

    override fun onViewCreated(view: View, @Nullable savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }
}


