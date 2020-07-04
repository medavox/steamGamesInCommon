import kotlinx.serialization.json.*
import org.jsoup.parser.Parser
import redis.clients.jedis.JedisPool
import redis.clients.jedis.params.SetParams
import java.io.File
import java.lang.NullPointerException

class RedisApi : AutoCloseable {
    companion object {
        val APP_ID_KEY_PREFIX = "appid:"
    }

    override fun close() = pool.close() // when closing your application

    val pool:JedisPool = JedisPool("localhost", 1989)

    fun getGameNameForAppId(appid:Int):String? = pool.resource.use { jedis ->
        if(jedis.exists("$APP_ID_KEY_PREFIX$appid") ) jedis.get("$APP_ID_KEY_PREFIX$appid") else null
    }

    fun bulkReadKotlinMap(map:Map<Int, String>) {
        pool.resource.use { jedis ->
            //initialise cache from local file
            for (entry in map.entries) {
                val result = jedis.set(APP_ID_KEY_PREFIX+entry.key.toString(), entry.value, SetParams().nx())
                if (result != "OK") println("$result for ${entry.key} to ${entry.value}")
            }
        }
    }

    fun bulkReadJsonAppList(input:String) {
        val jsonParser = Json(JsonConfiguration.Stable)
        val jsonFile = File(input)
        if (!jsonFile.exists() || jsonFile.length() == 0L) {
            System.err.println("ERROR: input file \"${jsonFile.name}\" is empty or doesn't exist!")
            return
        }
        //it's bad practice to read a 5MB file into memory all at once,
        //but fuck it, we only need to do this once
        //{"applist":{"apps":[{"appid":216938,"name":"Pieterw test app76 ( 216938 )"},{"appid":660010,"name":"test2"},
        val gamesArray: JsonArray = jsonParser.parseJson(jsonFile.readText()).jsonObject["applist"]?.
        jsonObject?.get("apps")?.jsonArray ?: throw NullPointerException("json array was null!")


        println("json array size: ${gamesArray.size}")
        //println("read \"${jsonFile.name}\". new in-memory properties size: ${cache.size}")
        //println("value for 113020: ${cache["113020"]}")

        pool.resource.use { jedis ->
            //now it's all loaded in, write it out to Redis
            for (entry: JsonElement in gamesArray) {
                val obj = entry.jsonObject
                val key = APP_ID_KEY_PREFIX+obj["appid"]
                val value = Parser.unescapeEntities(obj["name"]?.content, false)
                val result = jedis.set(key.toString(), value, SetParams().nx())
                if (result != "OK") println("$result for $key to $value")
            }
        }
    }
}