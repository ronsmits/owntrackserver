package exposed

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import todatetime
import java.lang.Math.toRadians
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

fun ResultRow.toLocation() = LocationTable.rowToLocation(this)

fun main() {
    Database.connect("jdbc:h2:tcp://domoticz.home/~/test", "org.h2.Driver")
    transaction {
        LocationTable.select { LocationTable.deviceId eq 23 }
            .orderBy(LocationTable.timestamp)
            .map { it.toLocation() }
            .windowed(2)
            .forEach { println("${it[0].datetime} ${it[0].distance(it[1])}") }
    }
}