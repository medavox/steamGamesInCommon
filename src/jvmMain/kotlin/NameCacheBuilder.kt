import io.ktor.client.HttpClient
import io.ktor.client.features.HttpTimeout
import io.ktor.client.features.RedirectResponseException
import io.ktor.client.request.get
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.*

/**Downloads names for app IDs using several different HTTP routes,
 * to build a local game_name_cache.properties file that can be packaged with all versions of this app,
 * saving having to download again on every instance
 * (speeds up the app, and reduces strain on our API Key's usage limits)*/
suspend fun buildNameCache(key: String, vararg players: String) = coroutineScope {
    val client = HttpClient()
    val json = Json(JsonConfiguration.Stable)
    // request all player IDs asynchronously in parallel.
    //get 64-bit steam ID from 'vanityName' (mine is addham):
    val playerIDs:List<String> = players.map {
        if(it.matches(Regex("\\d{17}"))) {//is already a SteamId
            it
        }else {
            val response = async {
                println("http://api.steampowered.com/ISteamUser/ResolveVanityURL/v0001/?key=$key&vanityurl=$it")
                client.get<String>(
                        "http://api.steampowered.com/ISteamUser/ResolveVanityURL/v0001/?key=$key&vanityurl=$it"
                )
            }.await()
            json.parseJson(response).jsonObject["response"]?.jsonObject?.get("steamid")?.toString()?.trim{ c -> c == '"'}
        }
    }.filterNotNull()

    // Get the request contents without blocking threads, but wait until all requests are done.
    // Suspension point.

/*    val playerIDs: List<String> = playerSchemas.map {
        json.parseJson(it).jsonObject["response"]?.jsonObject
    }.map { it?.get("steamid")?.toString()?.trim{ c -> c == '"'} }.filterNotNull()*/

    //players.forEachIndexed { i, s -> println("$s: ${playerIDs[i]}") }

    //get list of owned games for each 64-bit steam ID (comma-separated) (profiles must be public):
    //http://api.steampowered.com/IPlayerService/GetOwnedGames/v0001/?key=$key&steamid=76561197979296883&format=json
    //the Pair<String, String> is app id: play time forever
    val ownedGames:Map<String, List<Pair<String, String>>?> = playerIDs.associate { id: String ->
        val games = async {
            println("http://api.steampowered.com/IPlayerService/GetOwnedGames/v0001/?key=$key&steamid=$id&format=json")
            client.get<String>(
                    "http://api.steampowered.com/IPlayerService/GetOwnedGames/v0001/?key=$key&steamid=$id&format=json"
            )
        }
        val gamesJson:JsonObject? = json.parseJson(games.await()).jsonObject["response"]?.jsonObject
        val gameIdList = gamesJson?.get("games")?.jsonArray
        val idPlayTimePairs:List<Pair<String, String>>? = gameIdList?.map {
            val asObj = it.jsonObject
            val appid = asObj["appid"]?.primitive?.contentOrNull
            val playtime = asObj["playtime_forever"]?.primitive?.contentOrNull
            if(appid != null && playtime != null) Pair(appid, playtime) else null
        }?.filterNotNull()
        if(idPlayTimePairs?.isEmpty() != false) {
            println("got zero games for steam ID $id; is the profile public?")
        }
        Pair(id, idPlayTimePairs)
    }

    //create a Set<String> of unique app IDs, so we only have to lookup the name of each game once
    val gameIds:MutableSet<String> = mutableSetOf()
    ownedGames.values.filterNotNull().forEach{ playerGameList ->
        playerGameList.forEach { (appid, _) ->
            gameIds.add(appid)
        }
    }

    //todo: defer id-to-name lookups until the last step, to reduce the amount of querying of steam's shitty REST routes

    //convert each app ID to its game name
    //=====================================

    //http://api.steampowered.com/ISteamUserStats/GetSchemaForGame/v2/?key=$key&appid=218620
    val gameIdsToNames:MutableMap<String, String> = mutableMapOf()

    //http://store.steampowered.com/api/appdetails/?appids=
    val missingDataIds:MutableSet<String> = mutableSetOf()
    val cacheFile = File("game_name_cache.properties")
    if(!cacheFile.exists()) cacheFile.createNewFile()
    val cachedNames = Properties()
    val inputStream: InputStream = FileInputStream(cacheFile)
    cachedNames.load(inputStream)
    inputStream.close()

    println("found ${cachedNames.size} game name mappings in local .properties file")

    //initialise cache from local file
    //val cachedIdNameEntries:Map<String, String> =//todo: figure out how to load local resource files on common?
    println("${gameIds.size} game IDs to lookup")
    try {
        if(missingDataIds.isNotEmpty()) println("scraping ${missingDataIds.size} missing entries from HTML of store pages...")
        for(appid in gameIds) {
            if (cachedNames.getProperty(appid) != null) {
                continue
            }
            val storePageHtml = async {
                try {
                    client.get<String>("https://store.steampowered.com/app/$appid")
                }catch(rre: RedirectResponseException) {
                    rre.printStackTrace()
                    println("failed to get name of app $appid from store page scraper: ${rre::class.java}")
                    ""
                }
            }.await()
            if(storePageHtml.isEmpty()) continue

            val nameInTitle = Regex("<title>(?:Save \\d{1,3}% on )?(.+) on Steam</title>")
            //find the human-readable name in the raw HTML of the store page:
            // <div class="apphub_AppName">{human-readable name}</div>
            val nameFromTitleResult: MatchResult? = nameInTitle.find(storePageHtml)
            val possibleName: String? = nameFromTitleResult?.groupValues?.get(1)
            possibleName?.let {
                println("name found on store page: $possibleName")
                cachedNames.setProperty(appid, possibleName)
                gameIdsToNames.put(appid, possibleName)
                missingDataIds.remove(appid)
            }
        }
    }finally {
        //write out any newly retrieved game names to the .properties cachefile
        val fos = FileOutputStream(cacheFile)
        cachedNames.store(fos, "")
        fos.close()
    }

    println("${missingDataIds.size} or ${(missingDataIds.size*1000) / gameIds.size }‰ of name lookups failed:")
    println(missingDataIds)
    client.close()
}