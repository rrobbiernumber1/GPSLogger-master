package eu.basicairdata.graziano.gpslogger

/**
 * The data structure that describes a 2D point on the Earth surface.
 * It is used to create the thumbnails of the Tracks.
 */
class LatLng @JvmOverloads constructor(
    @JvmField var latitude: Double = 0.0,
    @JvmField var longitude: Double = 0.0
) {}


