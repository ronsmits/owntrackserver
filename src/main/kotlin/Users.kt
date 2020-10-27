import javax.json.Json
import javax.json.JsonObject

val usersList = mutableListOf<User>()

data class User(val username: String, val password: String, val deviceId: Int, val roles: List<ApiRoles>) {
    companion object {
        fun fromJson(jsonObject: JsonObject): User {
            return User(username = jsonObject.getString("user"),
                password = jsonObject.getString("password"),
                deviceId = jsonObject.getInt("deviceId"),
                roles = jsonObject.getJsonArray("roles").map { ApiRoles.valueOf(it.toString().replace("\"", "")) })
        }
    }
    fun toJson(): JsonObject {
        val arrayBuilder = Json.createArrayBuilder()
        roles.forEach { arrayBuilder.add(it.name) }
        return Json.createObjectBuilder()
            .add("user", username)
            .add("password", password)
            .add("deviceId", deviceId)
            .add("roles", arrayBuilder).build()
    }

}