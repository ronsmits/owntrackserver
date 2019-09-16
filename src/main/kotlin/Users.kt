import javax.json.Json
import javax.json.JsonObject

val usersList = mutableListOf<User>()

class User() {
    constructor (
        username: String,
        password: String,
        deviceId: Int,
        roles: MutableList<ApiRoles> = mutableListOf(ApiRoles.NO)
    ) : this() {
        this.username = username
        this.password = password
        this.deviceId = deviceId
        this.roles = roles
    }

    lateinit var username: String
    lateinit var password: String
    var deviceId: Int = 0
    var roles = mutableListOf<ApiRoles>()

    fun toJson(): JsonObject {
        val arrayBuilder = Json.createArrayBuilder()
        roles.forEach { it -> arrayBuilder.add(it.name) }
        return Json.createObjectBuilder()
            .add("user", username)
            .add("password", password)
            .add("deviceId", deviceId)
            .add("roles", arrayBuilder).build()
    }

    fun fromJson(obj: JsonObject): User {
        username = obj.getString("user")
        password = obj.getString("password")
        deviceId = obj.getInt("deviceId")
        obj.getJsonArray("roles").forEach { roles.add(ApiRoles.valueOf(it.toString().replace("\"", ""))) }
        return this
    }
}

fun main() {
    val array = Json.createArrayBuilder().add(User("ron", "test", 1).toJson()).build()
    println(array)
    val x = User().fromJson(User("ron", "test", 1).toJson())
    println(x.toJson())

    array.forEach { usersList.add(User().fromJson(it.asJsonObject())) }
}

