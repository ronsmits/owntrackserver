package exposed

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import todatetime
import usersList
import java.lang.Math.toRadians
import javax.json.Json
import javax.json.JsonArray
import javax.json.JsonArrayBuilder
import javax.json.JsonObject
import kotlin.math.*

object LocationTable : Table("locations") {
    val id = integer("id").autoIncrement().primaryKey()
    val deviceId = integer("deviceid")
    val timestamp = long("timestamp").index()
    val latitude = double("lat")
    val longitude = double("lon")
    val velocity = integer("velocity")
}

data class Location(
    val id: Int,
    val deviceId: Int,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val velocity: Int
) {
    val datetime by lazy {
        timestamp.todatetime()
    }

    companion object {
        const val earthRadiusKm: Double = 6372.8
    }

    fun geoJsonMarker(): JsonObject {
        val factory = Json.createObjectBuilder().add(
            "geometry",
            Json.createObjectBuilder().add("type", "Point").add(
                "coordinates",
                Json.createArrayBuilder().add(longitude).add(latitude)
            )
        ).add("type", "Feature").add("properties", Json.createObjectBuilder().build()).build()

        return factory!!
    }

    fun geoPoint(): JsonArray = Json.createArrayBuilder().add(longitude).add(latitude).build()!!

    override fun toString(): String = "$id $deviceId $latitude $longitude $velocity ${timestamp.todatetime()}"

    fun distance(other: Location): Double {
        val dLat = toRadians(other.latitude - this.latitude);
        val dLon = toRadians(other.longitude - this.longitude);
        val originLat = toRadians(this.latitude);
        val destinationLat = toRadians(other.latitude);

        val a = sin(dLat / 2).pow(2.toDouble()) + sin(dLon / 2).pow(2.toDouble()) * cos(originLat) * cos(destinationLat);
        val c = 2 * asin(sqrt(a));
        return earthRadiusKm * c;
    }
}

fun LocationTable.rowToLocation(row: ResultRow): Location = Location(
    id = row[id],
    deviceId = row[deviceId],
    timestamp = row[timestamp],
    latitude = row[latitude],
    longitude = row[longitude],
    velocity = row[velocity]
)

fun Location.user() = usersList.filter { this.deviceId == deviceId }.first().username
fun ResultRow.toLocation() = LocationTable.rowToLocation(this)

fun main() {
    Database.connect("jdbc:h2:tcp://domoticz.home/~/test", "org.h2.Driver")
//    transaction {
//        LocationTable.select { LocationTable.deviceId eq 23 }
//            .orderBy(LocationTable.timestamp)
//            .map { it.toLocation() }
//            .windowed(2)
//            .filter{it[0].distance(it[1])>0}.flatten().sortedBy { it.timestamp }.distinctBy { it.id }.forEach { println(it) }
//    }

    val list = transaction {
        LocationTable.select { LocationTable.deviceId eq 23 }
            .orderBy(LocationTable.timestamp)
            .asSequence()
            .map { it.toLocation() }
            .filter { it.datetime.monthValue == 10 && it.datetime.dayOfMonth == 2 }
            .windowed(2)
            .filter { it[0].distance(it[1]) > 0 }
            .flatten()
            .sortedBy { it.timestamp }
            .distinctBy { it.id }
            .toList()

    }
    println(list.size)
    toGeoList(list)
}

fun toGeoList(list: List<Location>) {
    val factory = Json.createObjectBuilder()
        .add("type", "Feature")
        .add("properties", Json.createObjectBuilder())
        .add(
            "geometry", Json.createObjectBuilder()
                .add("type", "LineString")
                .add("coordinates", coordinateListBuilder(list))
        )

    println(factory.build().toString())
}

private fun coordinateListBuilder(list: List<Location>): JsonArrayBuilder {
    val array = Json.createArrayBuilder()
    list.forEach { array.add(it.geoPoint()) }
    return array
}