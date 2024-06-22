package org.ronsmits.owntrackserver

import com.muquit.libsodiumjna.SodiumLibrary
import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.*
import io.javalin.http.Context
import io.javalin.http.Header
import io.javalin.http.UnauthorizedResponse
import io.javalin.http.staticfiles.Location
import io.javalin.security.RouteRole
import joptsimple.OptionParser
import org.slf4j.LoggerFactory
import java.io.FileInputStream
import java.io.StringReader
import java.util.*
import javax.json.Json
import javax.json.JsonObject
import kotlin.system.exitProcess
import io.javalin.http.staticfiles.Location as JavalinLocation


private val logger = LoggerFactory.getLogger("main")

enum class Role : RouteRole { ANYONE, REPORT, STORE, ADMIN }

object Auth {
    fun handleAccess(ctx: Context) {
        println(ctx.path())
        if(ctx.path() != "/loc") return
        val permittedRoles = ctx.routeRoles()
        when {
            permittedRoles.contains(Role.ANYONE) -> return
            ctx.routeRoles().any { it in permittedRoles } -> return
            else -> {
                ctx.header(Header.WWW_AUTHENTICATE, "Basic")
                throw UnauthorizedResponse();
            }
        }
    }

    fun findUser(username: String, password: String): List<Role> {
        return usersList.firstOrNull { it.username == username && it.password == password }?.roles ?: listOf()
    }
}
fun getUserName(tst: String) = usersList.firstOrNull { it.deviceId == tst.toInt() }?.username

fun main(args: Array<String>) {
    val parser = OptionParser()
    with(parser) {
        accepts("b").withOptionalArg().ofType(String::class.java).describedAs("address of the mqtt broker")
            .defaultsTo("tcp://conbee.lan:1883")
        accepts("f").withOptionalArg().ofType(String::class.java).describedAs("users.json file")
            .defaultsTo("users.json")
        accepts("c").withOptionalArg().ofType(String::class.java).describedAs("Context root defaults to '/'")
            .defaultsTo("")
        accepts("p").withOptionalArg().ofType(Int::class.java).describedAs("port to listen to").defaultsTo(7000)
        accepts("l").withRequiredArg().ofType(String::class.java).describedAs("path to sodium library")
            .defaultsTo("/usr/lib/x86_64-linux-gnu/libsodium.so.23")
        accepts("h").isForHelp
    }
    val optionSet = parser.parse(*args)
    if (optionSet.has("h")) {
        parser.printHelpOn(System.err)
        exitProcess(0)
    }
    logger.info(
        "args: -f ${optionSet.valueOf("f") as String} " +
                "-b ${optionSet.valueOf("b") as String} " +
                "-c ${optionSet.valueOf("c") as String} " +
                "-p ${optionSet.valueOf("p") as Int} " +
                "-l ${optionSet.valueOf("l") as String}"
    )

    setupSodium(optionSet.valueOf("l") as String)
    Json.createReader(FileInputStream(optionSet.valueOf("f") as String)).readArray()
        .forEach { usersList.add(User.fromJson(it.asJsonObject())) }

    Mqtt.connect(optionSet.valueOf("b") as String)

    Javalin.create{
        it.staticFiles.add("src/main/resources/root", location = Location.EXTERNAL)
        it.router.mount{
            it.beforeMatched(Auth::handleAccess)
        }.apiBuilder{
            path("/loc") {
                post(::handleLoc, Role.ANYONE)
            }
        }
    }.start(7070)
//    val app = Javalin.create {
////        it.contextPath = optionSet.valueOf("c") as String
//        it.accessManager(Auth::handleAccess)
////        it.addStaticFiles("root", JavalinLocation.CLASSPATH)
//    }.apply {
//        exception(Exception::class.java) { e, _ -> e.printStackTrace() }
//        error(404) { ctx -> ctx.json("not found") }
//    }.start(optionSet.valueOf("p") as Int)
//
//    app.routes {
//        post("/loc", { ctx -> handleLoc(ctx) }, roles(ApiRoles.REPORT))
//    }
}

fun setupSodium(path: String) {
    SodiumLibrary.setLibraryPath(path)
    logger.info("library version ${SodiumLibrary.libsodiumVersionString()}")
}

const val topicBase = "owntrack"

fun handleLoc(ctx: Context) {
    //logger.info(ctx.body())
    val stringBody = ctx.body()
    val receivedLocation = Json.createReader(StringReader(stringBody)).readObject()
    logger.info("[${Date()}] - received $receivedLocation at $receivedLocation")
    val topic = receivedLocation.getString("topic")
    Mqtt.publish(topic, receivedLocation)
    println(topic)
    when (receivedLocation.getString("_type")) {
        "waypoint" -> Mqtt.publish("$topicBase/waypoint", receivedLocation)
        "encrypted" -> decrypt(receivedLocation.getString("data"))?.run {
            val username = if (this.containsKey("tid"))
                getUserName(this.getString("tid")) + "/" else ""
            val topic = "$topicBase/$username${this.getString("_type")}"
            Mqtt.publish(topic, this)
        }

//        else -> logger.info("dont know what to do with it")
    }
}

const val nonceLength = 24

fun decrypt(crypted: String): JsonObject? {
    val decode = Base64.getDecoder().decode(crypted)
    val nonce = decode.copyOfRange(0, nonceLength)
    val messageArray = decode.copyOfRange(nonceLength, decode.size)

    val key = ByteArray(32)
    for ((index, value) in "dit is geheim".iterator().withIndex()) {
        key[index] = value.code.toByte()
    }

    val decryptedString = SodiumLibrary.cryptoSecretBoxOpenEasy(messageArray, nonce, key)
    logger.info("decrypted ${String(decryptedString)}")
    return Json.createReader(StringReader(String(decryptedString))).readObject()
}