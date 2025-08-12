package eu.basicairdata.graziano.gpslogger

/**
 * A class that is made to be used as parameter for EventBus messages.
 * This type of messages contain a track ID as additional data.
 */
class EventBusMSGNormal(var eventBusMSG: Short, var trackID: Long)


