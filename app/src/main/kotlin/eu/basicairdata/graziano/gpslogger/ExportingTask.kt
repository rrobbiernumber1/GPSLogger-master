package eu.basicairdata.graziano.gpslogger

/**
 * The data structure that stores all the information needed to export a Track.
 * It stores the properties, the amount of work, and the status of the exportation.
 */
data class ExportingTask(
    var id: Long = 0,
    var numberOfPoints_Total: Long = 0,
    var numberOfPoints_Processed: Long = 0,
    var status: Short = STATUS_PENDING,
    var name: String = ""
) {
    companion object {
        const val STATUS_PENDING: Short = 0
        const val STATUS_RUNNING: Short = 1
        const val STATUS_ENDED_SUCCESS: Short = 2
        const val STATUS_ENDED_FAILED: Short = 3
    }
}


