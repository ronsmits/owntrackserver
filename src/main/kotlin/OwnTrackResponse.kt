import java.time.Instant
import java.time.ZoneId
import javax.json.Json

data class OwnTrackResponse(
    val _type: String = "",
    val acc: Int = 0,
    val alt: Int = 0,
    val batt: Int = 0,
    val conn: String="",
    val event: String="",
    val desc: String="",
    val inregions: List<String> = emptyList(),
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val t: String = "",
    val tid: String = "",
    val tst: Long = Instant.now().epochSecond,
    val wtst: Long = Instant.now().epochSecond,
    val vac: Int = 0,
    val vel: Int = 0
) {
    val datetime
        get() = Instant.ofEpochSecond(tst).atZone(ZoneId.systemDefault()).toLocalDateTime()

    fun toGeoJsonString(): String {
        val geoJson = Json.createObjectBuilder().add(
            "geometry",
            Json.createObjectBuilder().add("type", "Point").add(
                "coordinates",
                Json.createArrayBuilder().add(lon).add(lat)
            )
        ).add("type", "Feature").add("properties", Json.createObjectBuilder().build()).build()

        return geoJson.toString()
    }
}