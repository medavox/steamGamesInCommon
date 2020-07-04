import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request

class SteamApi (
    private val steamWebApiKey: String,
    private val client: OkHttpClient,
    private val json: Json
) {
    fun getSteamId(vanityOrHash: String): String? =
        if (vanityOrHash.matches(Regex("\\d{17}"))) {//is already a SteamId
            vanityOrHash
        } else {
            val url =
                "http://api.steampowered.com/ISteamUser/ResolveVanityURL/v0001/?key=$steamWebApiKey&vanityurl=$vanityOrHash"
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

    fun getGamesOwnedByPlayer(playerId:String):List<String> {
        val url = "http://api.steampowered.com/IPlayerService/GetOwnedGames/v0001/?key=$steamWebApiKey&steamid=$playerId&format=json"
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
}