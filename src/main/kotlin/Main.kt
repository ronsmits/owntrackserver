import com.muquit.libsodiumjna.SodiumLibrary
import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.post
import io.javalin.core.security.Role
import io.javalin.core.security.SecurityUtil.roles
import io.javalin.core.util.Header
import io.javalin.http.Context
import io.javalin.http.Handler
import joptsimple.OptionParser
import org.apache.commons.codec.digest.Crypt.crypt
import org.slf4j.LoggerFactory
import java.io.FileInputStream
import java.io.StringReader
import java.util.*
import javax.json.Json
import javax.json.JsonObject
import kotlin.system.exitProcess
import io.javalin.http.staticfiles.Location as JavalinLocation


private val logger = LoggerFactory.getLogger("main")

enum class ApiRoles : Role { NO, REPORT, STORE, ADMIN }
object Auth {

    fun accessManager(handler: Handler, ctx: Context, permittedRoles: Set<Role>) {
        when {
            ctx.userRoles.any { it in permittedRoles } -> handler.handle(ctx)
            else -> ctx.header(Header.WWW_AUTHENTICATE, "Basic").status(401).result("not permitted")
        }
    }

    private val Context.userRoles: List<ApiRoles>
        get() = this.basicAuthCredentials()?.let { (username, password) ->
            findUser(username, crypt(password, username))
        } ?: listOf()

    fun findUser(username: String, password: String): List<ApiRoles> {
        return usersList.firstOrNull { it.username == username && it.password == password }?.roles ?: listOf()
    }
}

fun getUserName(tst: String) = usersList.firstOrNull { it.deviceId == tst.toInt() }?.username

fun main(args: Array<String>) {
    val parser = OptionParser()
    with(parser) {
        accepts("b").withOptionalArg().ofType(String::class.java).describedAs("address of the mqtt broker")
            .defaultsTo("tcp://127.0.0.1:1883")
        accepts("f").withOptionalArg().ofType(String::class.java).describedAs("users.json file")
            .defaultsTo("users.json")
        accepts("c").withOptionalArg().ofType(String::class.java).describedAs("Context root defaults to '/'")
            .defaultsTo("")
        accepts("p").withOptionalArg().ofType(Int::class.java).describedAs("port to listen to").defaultsTo(7000)
        accepts("l").withRequiredArg().ofType(String::class.java).describedAs("path to sodium library")
            .defaultsTo("/usr/lib/x86_64-linux-gnu/libsodium.so.23")
        accepts("h").isForHelp
    }
    val optionset = parser.parse(*args)
    if (optionset.has("h")) {
        parser.printHelpOn(System.err)
        exitProcess(0)
    }
    logger.info(
        "args: -f ${optionset.valueOf("f") as String} " +
                "-b  ${optionset.valueOf("b") as String} " +
                "-c ${optionset.valueOf("c") as String} " +
                "-p ${optionset.valueOf("p") as Int} " +
                "-l ${optionset.valueOf("l") as String}"
    )

    setupSodium(optionset.valueOf("l") as String)
    Json.createReader(FileInputStream(optionset.valueOf("f") as String)).readArray()
        .forEach { usersList.add(User.fromJson(it.asJsonObject())) }

    Mqtt.connect(optionset.valueOf("b") as String)

    val app = Javalin.create {
        it.contextPath = optionset.valueOf("c") as String
        it.accessManager(Auth::accessManager)
        it.addStaticFiles("root", JavalinLocation.CLASSPATH)
    }.apply {
        exception(Exception::class.java) { e, _ -> e.printStackTrace() }
        error(404) { ctx -> ctx.json("not found") }
    }.start(optionset.valueOf("p") as Int)

    app.routes {
        post("/loc", { ctx -> handleLoc(ctx) }, roles(ApiRoles.REPORT))
    }
}

fun setupSodium(path: String) {
    SodiumLibrary.setLibraryPath(path)
    logger.info("library version ${SodiumLibrary.libsodiumVersionString()}")
}

const val topicBase="owntrack"

fun handleLoc(ctx: Context) {
    //logger.info(ctx.body())
    val stringBody = ctx.body()
    val receivedLocation = Json.createReader(StringReader(stringBody)).readObject()
    logger.info("[${Date()}] - received $receivedLocation at ${receivedLocation}")
    logger.info(receivedLocation["_type"].toString())
    when(receivedLocation.getString("_type")) {
        "waypoint"-> Mqtt.publish("$topicBase/waypoint", receivedLocation)
        "encrypted"-> decrypt(receivedLocation.getString("data"))?.run {
            val username = if(this.containsKey("tid")) getUserName(this.getString("tid"))+"/" else ""
            val topic = "$topicBase/$username${this.getString("_type")}"
            Mqtt.publish(topic, this)
        }
        else-> logger.info("dont know what to do with it")
    }
}

const val nonceLength = 24

fun decrypt(crypted: String): JsonObject? {
    logger.info("decrypting")
    val decode = Base64.getDecoder().decode(crypted)
    val nonce = decode.copyOfRange(0, nonceLength)
    val messageArray = decode.copyOfRange(nonceLength, decode.size)

    val key = ByteArray(32)
    for ((index, value) in "dit is geheim".iterator().withIndex()) {
        key.set(index, value.toByte())
    }

    val decryptedString = SodiumLibrary.cryptoSecretBoxOpenEasy(messageArray, nonce, key)
    logger.info("decrypted ${String(decryptedString)}")
    return Json.createReader(StringReader(String(decryptedString))).readObject()
}