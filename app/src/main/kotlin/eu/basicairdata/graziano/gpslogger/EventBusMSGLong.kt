package eu.basicairdata.graziano.gpslogger

/**
 * A class that is made to be used as parameter for EventBus messages.
 * This type of messages contain a track ID and a Value as additional data.
 */
data class EventBusMSGLong(
    var eventBusMSG: Short,
    var trackID: Long,
    var value: Long
) {
    companion object
}


