import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request

class SteamApi(
    private val key: String,
    private val client: OkHttpClient = OkHttpClient(),
    private val json: Json = Json(JsonConfiguration.Stable)
) {
    /**Gets 17-digit from a steam vanity ID, or a string that is already a 17-digit steam id.*/
    fun getSteamId(vanityOrHash: String): String? =
        if (vanityOrHash.matches(Regex("\\d{17}"))) {//is already a SteamId
            vanityOrHash
        } else {
            val url =
                "http://api.steampowered.com/ISteamUser/ResolveVanityURL/v0001/?key=$key&vanityurl=$vanityOrHash&format=json"
            //if (debug) println(url)
            val request: Request = Request.Builder()
                .url(url)
                .build()

            client.newCall(request).execute().use { response ->
                val responseString = response.body?.string()

                if (responseString == null) println("ERROR: got null response for ID $vanityOrHash")
                responseString?.let {
                    json.parseJson(responseString).jsonObject["response"]?.jsonObject?.get("steamid")?.toString()
                        ?.trim { c -> c == '"' }
                }
            }
        }

    fun getGamesOwnedByPlayer(playerId: String):List<String> {
        val url = "http://api.steampowered.com/IPlayerService/GetOwnedGames/v0001/?key=$key&steamid=$playerId&format=json"
        //if (debug) println(url)
        val request:Request = Request.Builder().url(url).build()
        return client.newCall(request).execute().use { response ->
            val responseString = response.body?.string()

            if (responseString == null) {
                println("ERROR: got null response for game library request for ID $playerId")
                //Pair(id, listOf<Pair<String, String>>())
                listOf()
            } else {
                val gamesJson: JsonObject? = json.parseJson(responseString).jsonObject["response"]?.jsonObject
                val gameIdList = gamesJson?.get("games")?.jsonArray
                val gameIds: List<String> = gameIdList?.mapNotNull {
                        it.jsonObject["appid"]?.primitive?.contentOrNull
                } ?: listOf()
                if (gameIds.isEmpty()) {
                    println("got zero games for steam ID $playerId; is the profile public?")
                }
                gameIds
            }
        }
    }

    fun getFriendsOfPlayer(playerId: String):List<String> {
        val url = "http://api.steampowered.com/ISteamUser/GetFriendList/v0001/?key=$key&steamid=$playerId&format=json&relationship=friend"
        val request:Request = Request.Builder().url(url).build()
        return client.newCall(request).execute().use { response ->
            println("response: $response; body:")
            println("${response.body?.string()}")
            listOf()
        }
    }

    /**Gets the current nickname for this player, or null if the query failed somehow.*/
    fun getNickForPlayerId(playerId:String):String? {

    }
}