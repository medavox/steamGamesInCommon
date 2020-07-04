
import kotlinx.serialization.json.*
import org.jsoup.parser.Parser
import redis.clients.jedis.Jedis

import redis.clients.jedis.params.SetParams
import java.io.File
import java.lang.NullPointerException

object RedisDataInit {
    val e = System.err
    val jedis = Jedis("127.0.0.1", 1989)
    fun convertKotlinMap(map:Map<Int, String>) {
        //initialise cache from local file
        for(entry in map.entries) {
            val result = jedis.set(entry.key.toString(), entry.value, SetParams().nx())
            if(result != "OK") println("$result for ${entry.key} to ${entry.value}")
        }
        jedis.close()
    }

    fun convertJsonAppList(input:String) {
        val jsonParser = Json(JsonConfiguration.Stable)
        val jsonFile = File(input)
        if (!jsonFile.exists() || jsonFile.length() == 0L) {
            e.println("ERROR: input file \"${jsonFile.name}\" is empty or doesn't exist!")
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

        //now it's all loaded in, write it out in a generated Kotlin format
        for(entry: JsonElement in gamesArray) {
            val obj = entry.jsonObject
            val key = obj["appid"]
            val value = Parser.unescapeEntities(obj["name"]?.content, false)
            val result = jedis.set(key.toString(), value, SetParams().nx())
            if(result != "OK") println("$result for $key to $value")
        }
    }
}

fun main() {
    RedisDataInit.convertJsonAppList("appList.json")
}