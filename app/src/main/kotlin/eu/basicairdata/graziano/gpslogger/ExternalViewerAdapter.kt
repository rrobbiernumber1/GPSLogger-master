package eu.basicairdata.graziano.gpslogger

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView

class ExternalViewerAdapter(context: Context, private val listData: ArrayList<ExternalViewer>) : BaseAdapter() {
    private val layoutInflater: LayoutInflater = LayoutInflater.from(context)

    override fun getCount(): Int = listData.size

    override fun getItem(position: Int): Any = listData[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        var holder: ViewHolder
        val view = if (convertView == null) {
            val v = layoutInflater.inflate(R.layout.appdialog_list_row, null)
            holder = ViewHolder(
                icon = v.findViewById(R.id.id_appdialog_row_imageView_icon),
                description = v.findViewById(R.id.id_appdialog_row_textView_description),
                format = v.findViewById(R.id.id_appdialog_row_textView_format)
            )
            v.tag = holder
            v
        } else {
            holder = convertView.tag as ViewHolder
            convertView
        }
        val item = listData[position]
        holder.icon.setImageDrawable(item.icon)
        holder.description.text = item.label
        holder.format.text = if (item.fileType == GPSApplication.FILETYPE_GPX) {
            "GPX"
        } else if (item.fileType == GPSApplication.FILETYPE_KML) {
            "KML"
        } else ""
        return view
    }

    private data class ViewHolder(
        val icon: ImageView,
        val description: TextView,
        val format: TextView
    )
}


