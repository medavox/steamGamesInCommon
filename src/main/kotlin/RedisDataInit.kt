
import kotlinx.serialization.json.*
import org.jsoup.parser.Parser
import redis.clients.jedis.Jedis

import redis.clients.jedis.params.SetParams
import java.io.File
import java.lang.NullPointerException

object RedisDataInit {
    val e = System.err

}

fun main() {
    RedisDataInit.convertKotlinMap(gameNameCache)
    RedisDataInit.convertJsonAppList("appList.json")
}