import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.get
import io.javalin.apibuilder.ApiBuilder.post
import io.javalin.core.security.Role
import io.javalin.core.security.SecurityUtil.roles
import io.javalin.http.Context
import io.javalin.http.Handler
import io.javalin.http.staticfiles.Location
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneId
import javax.json.Json

private val logger = LoggerFactory.getLogger("main")

enum class ApiRoles : Role { NO, YES }
object Auth {

    fun accessManager(handler: Handler, ctx: Context, permittedRoles: Set<Role>) {
        when {
            ctx.userRoles.contains(ApiRoles.YES) -> handler.handle(ctx)
            ctx.path() == "/last" -> handler.handle(ctx)
            else -> ctx.status(401).json("not permitted")
        }
    }

    private val Context.userRoles: List<ApiRoles>
        get() = this.basicAuthCredentials()?.let { (username, password) ->
            userRoleMap[Pair(username, password)] ?: listOf()
        } ?: listOf()

    private val userRoleMap = hashMapOf(
        Pair("alice", "weak-password") to listOf(ApiRoles.NO),
        Pair("bob", "better-password") to listOf(ApiRoles.YES),
        Pair("ron", "waxhtwoord") to listOf(ApiRoles.YES)
    )
}

var lastseen = OwnTrackResponse()

fun main() {

    Mqtt.connect()

    val app = Javalin.create {
        it.accessManager(Auth::accessManager)
        it.addStaticFiles("/root", Location.CLASSPATH)
    }.apply {

        exception(Exception::class.java) { e, ctx -> e.printStackTrace() }
        error(404) { ctx -> ctx.json("not found") }
    }.start(7000)

    app.routes {
        get("/un-secured", { ctx -> ctx.result("Hello") }, roles(ApiRoles.NO))
        post("/loc", { ctx -> handleLoc(ctx) }, roles(ApiRoles.YES))
        get("/last", { ctx ->
            ctx.result(lastseen.toJsonString())
        }, roles(ApiRoles.NO))

    }
}

fun handleLoc(ctx: Context) {
    lastseen = ctx.body<OwnTrackResponse>()
    logger.info("body is $lastseen at ${lastseen.datetime}")
    if (lastseen.inregions.contains("home")) {
        sendRegionUpdate(lastseen.tid, lastseen.inregions.contains("home"))
    }
    ctx.result("[]")

}

fun sendRegionUpdate(tid: String, atHome: Boolean) {
    val jsonContent = Json.createObjectBuilder()
        .add("command", "switchlight")
        .add("idx", tid.toInt())
        .add("switchcmd", if (atHome) "On" else "Off").build()
    logger.info(jsonContent.toString())
    Mqtt.client.publish("domoticz/in", MqttMessage(jsonContent.toString().toByteArray()))
}

data class OwnTrackResponse(
    val _type: String = "",
    val acc: Int = 0,
    val alt: Int = 0,
    val batt: Int = 0,
    val inregions: List<String> = emptyList(),
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val t: String = "",
    val tid: String = "",
    val tst: Long = Instant.now().epochSecond,
    val vac: Int = 0,
    val vel: Int = 0
) {
    val datetime
        get() = Instant.ofEpochSecond(tst).atZone(ZoneId.systemDefault()).toLocalDateTime()

    fun toJsonString(): String {
        val factory = Json.createObjectBuilder().add(
            "geometry",
            Json.createObjectBuilder().add("type", "Point").add(
                "coordinates",
                Json.createArrayBuilder().add(lon).add(lat)
            )
        ).add("type", "Feature").add("properties", Json.createObjectBuilder().build()).build()

        return factory.toString()
    }
}
