package eu.basicairdata.graziano.gpslogger

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import org.greenrobot.eventbus.EventBus

/**
 * The dialog that appears when the user adds a new Annotation (Placemark).
 */
class FragmentPlacemarkDialog : DialogFragment() {

    private lateinit var etDescription: EditText

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val createPlacemarkAlert = AlertDialog.Builder(requireActivity())
        createPlacemarkAlert.setTitle(R.string.dlg_add_annotation)

        val inflater: LayoutInflater = requireActivity().layoutInflater
        val view: View = inflater.inflate(R.layout.fragment_placemark_dialog, null)

        etDescription = view.findViewById(R.id.placemark_description)
        etDescription.postDelayed({
            if (isAdded) {
                etDescription.requestFocus()
                val imm = requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(etDescription, InputMethodManager.SHOW_IMPLICIT)
            }
        }, 200)

        createPlacemarkAlert.setView(view)
            .setPositiveButton(R.string.dlg_button_add) { _: DialogInterface?, _: Int ->
                if (isAdded) {
                    val placemarkDescription = etDescription.text.toString()
                    val gpsApp = GPSApplication.getInstance()
                    gpsApp.setPlacemarkDescription(placemarkDescription.trim { it <= ' ' })
                    EventBus.getDefault().post(EventBusMSG.ADD_PLACEMARK)
                }
            }
            .setNeutralButton(R.string.cancel) { _: DialogInterface?, _: Int -> }

        return createPlacemarkAlert.create()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    }
}


