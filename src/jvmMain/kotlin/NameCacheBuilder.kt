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
suspend fun doit(key: String, vararg players: String) = coroutineScope {
    val client = HttpClient()
    // request all player IDs asynchronously in parallel.
    //get 64-bit steam ID from 'vanityName' (mine is addham):
    val playerSchemas = players.map {
        async {
            println("http://api.steampowered.com/ISteamUser/ResolveVanityURL/v0001/?key=$key&vanityurl=$it")
            client.get<String>(
                    "http://api.steampowered.com/ISteamUser/ResolveVanityURL/v0001/?key=$key&vanityurl=$it"
            )
        }
    }

    // Get the request contents without blocking threads, but wait until all requests are done.
    // Suspension point.
    val json = Json(JsonConfiguration.Stable)
    //.await() BOTH waits for the result to complete AND gets its result
    val playerIDs: List<String> = playerSchemas.map {
        //println(it.await())
        json.parseJson(it.await()).jsonObject["response"]?.jsonObject
    }.map { it?.get("steamid")?.toString()?.trim{ c -> c == '"'} }.filterNotNull()

    players.forEachIndexed { i, s -> println("$s: ${playerIDs[i]}") }

    //get list of owned games for provided list of 64-bit steam IDs (comma-seperated) (profiles must be public):
    //http://api.steampowered.com/IPlayerService/GetOwnedGames/v0001/?key=$key&steamid=76561197979296883&format=json
    //the Pair<String, String> is app id: play time forever
    val ownedGames:Map<String, List<Pair<String, String>>?> = playerIDs.associate { id: String ->
        val games = async {
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
        //println(gameIdList)
        Pair(id, idPlayTimePairs)
    }

    //create a Set<String> of unique app IDs, so we only have to lookup the name of each game once
    val gameIds:MutableSet<String> = mutableSetOf()
    ownedGames.values.filterNotNull().forEach{ playerGameList ->
        playerGameList.forEach { (appid, playtime) ->
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
        gameIds.forEach { appid ->
            val highTimeoutClient = HttpClient() {
                install(HttpTimeout) {
                    // timeout config
                    requestTimeoutMillis = 30_000
                }
            }
            if (cachedNames.getProperty(appid) != null) {
                //println("found $appid in cache: ${cachedNames.getProperty(appid)}")
                gameIdsToNames.put(appid, cachedNames.getProperty(appid))
            } else {
                println("$appid not found in cache; trying steam web API...")
                //println("http://api.steampowered.com/ISteamUserStats/GetSchemaForGame/v2/?key=$key&appid=$appid")
                val schemaDeferred = async {
                    highTimeoutClient.get<String>(
                            "http://api.steampowered.com/ISteamUserStats/GetSchemaForGame/v2/?key=$key&appid=$appid"
                    )
                }.await()
                //despite the name 'appids', the store API no longer supports multiple appids, for some unknown reason:
                //https://www.reddit.com/r/Steam/comments/2kz2ay/steam_store_api_multiple_app_id_lists_no_longer/
                //println("rawdata length: ${schemaDeferred.length}")
                val gameSchema: JsonObject? = json.parseJson(schemaDeferred).jsonObject["game"]?.jsonObject
                if (gameSchema == null || !gameSchema.containsKey("gameName")) {
                    //problem: sometimes, the game data retrieved by this route is empty
                    //so we have to perform some workarounds, as described in the above reddit post


                    //1. check the local cachefile (CSV) for game name
                    //if that fails, add it to the backupApi query list
                    //http://store.steampowered.com/api/appdetails/?appids=<only one appid>
                    //the above query blocks you with status 429 if you hit it roughly more than 200 times in 5 minutes, apparently
                    //option C:
                    //https://store.steampowered.com/app/<appid>
                    //find the human-readable name in the raw HTML of the store page:
                    // <div class="apphub_AppName">{human-readable name}</div>
                    //Regex("<title>(?:Save \\d{1,3}% on )?(.+) on Steam</title>")
                    missingDataIds.add(appid)
                } else {//the game data exists, so get the name from it
                    //ANOTHER PROBLEM: sometimes, the name is replaced with "ValveTestApp<AppId>"
                    //so for AppId 72850, it's ValveTestApp72850
                    val possibleName = gameSchema["gameName"].toString().trim { it == '"' }
                    if (!possibleName.startsWith("ValveTestApp") && !possibleName.contains("UntitledApp") && !possibleName.isBlank() && !possibleName.isEmpty()) {//it actually is fine
                        println("found name for $appid: $schemaDeferred")
                        cachedNames.setProperty(appid, possibleName)
                        gameIdsToNames.put(appid, possibleName)
                        println("$appid: $possibleName")
                    } else {
                        //bollocks
                        println("$appid was missing from web API :(")
                        missingDataIds.add(appid)
                    }
                }
            }
        }

        if(missingDataIds.isNotEmpty()) println("trying to scrape missing entries from HTML of store pages...")
        for(appid in missingDataIds) {
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
            }
        }
    }finally {
        //write out any newly retrieved game names to the .properties cachefile
        val fos = FileOutputStream(cacheFile)
        cachedNames.store(fos, "")
        fos.close()
    }

    println("${missingDataIds.size} or ${(missingDataIds.size*1000) / gameIds.size }‰ of name lookups failed")
    client.close()
}