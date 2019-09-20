import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.post
import io.javalin.core.security.Role
import io.javalin.core.security.SecurityUtil.roles
import io.javalin.http.Context
import io.javalin.http.Handler
import joptsimple.OptionParser
import org.apache.commons.codec.digest.Crypt
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.slf4j.LoggerFactory
import java.io.FileInputStream
import java.time.Instant
import java.time.ZoneId
import javax.json.Json
import kotlin.system.exitProcess

private val logger = LoggerFactory.getLogger("main")

enum class ApiRoles : Role { NO, REPORT }
object Auth {

    fun accessManager(handler: Handler, ctx: Context, permittedRoles: Set<Role>) {
        when {
            ctx.userRoles.any { it in permittedRoles } -> handler.handle(ctx)
            else -> ctx.status(401).json("not permitted")
        }
    }

    private val Context.userRoles: List<ApiRoles>
        get() = this.basicAuthCredentials()?.let { (username, password) ->
            findUser(username, Crypt.crypt(password, username))
        } ?: listOf()

    fun findUser(username: String, password: String): List<ApiRoles> {
        return usersList.filter { it.username == username && it.password == password }.firstOrNull()?.roles ?: listOf()
    }
}


fun main(args: Array<String>) {
    val parser = OptionParser()
    with(parser) {
        accepts("m").withOptionalArg().ofType(String::class.java).describedAs("address of the mqtt broker")
            .defaultsTo("tcp://127.0.0.1:1883")
        accepts("f").withOptionalArg().ofType(String::class.java).describedAs("users.json file")
            .defaultsTo("users.json")
        accepts("c").withOptionalArg().ofType(String::class.java).describedAs("Context root defaults to '/'")
            .defaultsTo("")
    }
    val optionset = parser.parse(*args)
    if (!optionset.has("m") || !optionset.has("f")) {
        parser.printHelpOn(System.err)
        exitProcess(0)
    }
    logger.info(
        "args: -f ${optionset.valueOf("f") as String} -m  ${optionset.valueOf("m") as String} -c ${optionset.valueOf(
            "c") as String}"
    )
    Json.createReader(FileInputStream(optionset.valueOf("f") as String)).readArray()
        .forEach { usersList.add(User().fromJson(it.asJsonObject())) }
    Mqtt.connect(optionset.valueOf("m") as String)

    val app = Javalin.create {
        it.contextPath = optionset.valueOf("c") as String
        it.accessManager(Auth::accessManager)
    }.apply {
        exception(Exception::class.java) { e, ctx -> e.printStackTrace() }
        error(404) { ctx -> ctx.json("not found") }
    }.start(7000)

    app.routes {
        post("/loc", { ctx -> handleLoc(ctx) }, roles(ApiRoles.REPORT))
    }
}

fun handleLoc(ctx: Context) {
    val receivedLocaton = ctx.body<OwnTrackResponse>()
    logger.info("body is $receivedLocaton at ${receivedLocaton.datetime}")
    sendRegionUpdate(receivedLocaton.tid, receivedLocaton.inregions.contains("home"))
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
