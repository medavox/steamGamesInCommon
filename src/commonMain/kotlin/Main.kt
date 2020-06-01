import io.ktor.client.HttpClient
import io.ktor.client.request.get
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration

/**Input: a list of steam vanityNames
 * (https://steamcommunity.com/id/THIS_IS_YOUR_VANITY_NAME)
 *
 * Output: a list of games that the provided list of users all own
 * TODO: include free games that:
 *   1) they have all played before
 *   2) at minimum number have played before
 *   3) a minimum percentage of the group has played before
 *   4) the average playtime is above a certain amount (total group playtime divided by number of players)
 *   5) at least one player has played before*/
fun entryPoint(key:String, vararg players:String):List<String> {

    val client = HttpClient()
    suspend fun parallelRequests() = coroutineScope<Unit> {

        // request all player IDs asynchronously in parallel.
        //get 64-bit steam ID from 'vanityName' (mine is addham):
        //only accepts one vanity name at a time, so it might be worth caching...
        //can also use this to create a list of recent players, to reduce player effort after first use
        val playerSchemas = players.map { async { client.get<String>(
            "http://api.steampowered.com/ISteamUser/ResolveVanityURL/v0001/?key=$key&vanityurl=$it"
        ) } }

        // Get the request contents without blocking threads, but wait until all requests are done.
        // Suspension point.
        val json = Json(JsonConfiguration.Stable)
        //.await() BOTH waits for the result to complete AND gets its result
        val playerIDs:List<String> = playerSchemas.map {
            json.parseJson(it.await())
        }.map { it.jsonObject.get("steamid").toString() }

        players.forEachIndexed { i, s -> println("$s: ${playerIDs[i]}") }

        //get list of owned games for provided list of 64-bit steam IDs (comma-seperated) (profiles must be public):
        //http://api.steampowered.com/IPlayerService/GetOwnedGames/v0001/?key=$key&steamid=76561197979296883&format=json

        //convert app ID to game name:
        //http://api.steampowered.com/ISteamUserStats/GetSchemaForGame/v2/?key=$key&appid=218620

    }
    client.close()



    return listOf()
}