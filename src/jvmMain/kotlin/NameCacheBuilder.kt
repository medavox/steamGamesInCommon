import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.*

/**Downloads names for app IDs using several different HTTP routes,
 * to build a local game_name_cache.properties file that can be packaged with all versions of this app,
 * saving having to download again on every instance
 * (speeds up the app, and reduces strain on our API Key's usage limits)*/
fun buildNameCache(key: String, vararg players: String) {
    val NUM_THREADS = 8
    val client = OkHttpClient()
    val json = Json(JsonConfiguration.Stable)
    //STEP 1
    //=====================================
    // request all player IDs asynchronously in parallel.
    //get 64-bit steam ID from 'vanityName' (mine is addham):
    val pp1 = ParallelProcess<String, String?>()
    pp1.processMutableQueueWithWorkerPool(LinkedList(players.toList()), { vanityOrHash ->
        if(vanityOrHash.matches(Regex("\\d{17}"))) {//is already a SteamId
            vanityOrHash
        }else {
            val url = "http://api.steampowered.com/ISteamUser/ResolveVanityURL/v0001/?key=$key&vanityurl=$vanityOrHash"
            println(url)
            val request:Request = Request.Builder()
                    .url(url)
                    .build()
            val response:String? = client.newCall(request).execute().body?.string()
            if(response == null) println("ERROR: got null response for ID $vanityOrHash")
            response?.let{
                json.parseJson(response).jsonObject["response"]?.
                    jsonObject?.get("steamid")?.toString()?.trim{ c -> c == '"'}
            }
        }
    }, NUM_THREADS)
    val playerIDs:List<String> = pp1.collectOutputWhenFinished().filterNotNull()

    //STEP 2
    //=====================================
    //get list of owned games for each 64-bit steam ID (comma-separated) (profiles must be public):
    //http://api.steampowered.com/IPlayerService/GetOwnedGames/v0001/?key=$key&steamid=76561197979296883&format=json
    //Pair<appid:String, playtime:String>
    val pp2 = ParallelProcess<String, Pair<String, List<Pair<String, String>>?>>()
    pp2.processMutableQueueWithWorkerPool(LinkedList(playerIDs), { id: String ->
        val url = "http://api.steampowered.com/IPlayerService/GetOwnedGames/v0001/?key=$key&steamid=$id&format=json"
        println(url)
        val request:Request = Request.Builder().url(url).build()
        val response:String? = client.newCall(request).execute().body?.string()
        if(response == null) {
            println("ERROR: got null response for game library request for ID $id")
            //Pair(id, listOf<Pair<String, String>>())
            null
        }else {
            val gamesJson: JsonObject? = json.parseJson(response).jsonObject["response"]?.jsonObject
            val gameIdList = gamesJson?.get("games")?.jsonArray
            val idPlayTimePairs: List<Pair<String, String>>? = gameIdList?.mapNotNull {
                val asObj = it.jsonObject
                val appid = asObj["appid"]?.primitive?.contentOrNull
                val playtime = asObj["playtime_forever"]?.primitive?.contentOrNull
                if (appid != null && playtime != null) Pair(appid, playtime) else null
            }
            if (idPlayTimePairs?.isEmpty() != false) {
                println("got zero games for steam ID $id; is the profile public?")
            }
            Pair(id, idPlayTimePairs)
        }
    }, NUM_THREADS)

    val ownedGames:Map<String, List<Pair<String, String>>?> = pp2.collectOutputWhenFinished().toMap()

    //create a Set<String> of unique app IDs, so we only have to lookup the name of each game once
    val gameIds:MutableSet<String> = mutableSetOf()
    ownedGames.values.filterNotNull().forEach{ playerGameList ->
        playerGameList.forEach { (appid, _) ->
            gameIds.add(appid)
        }
    }

    //STEP 3
    //=====================================
    //convert each app ID to its game name

    //http://api.steampowered.com/ISteamUserStats/GetSchemaForGame/v2/?key=$key&appid=218620
    val gameIdsToNames:MutableMap<String, String> = mutableMapOf()

    //http://store.steampowered.com/api/appdetails/?appids=
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
        //filter out any game IDs that we already have in the cache
        val gameIdsToLookup = gameIds.filter { appid ->
            cachedNames.getProperty(appid) == null
        }
        pp1.processMutableQueueWithWorkerPool(LinkedList(gameIdsToLookup), { appid ->
            val url = "https://store.steampowered.com/app/$appid"
            val request:Request = Request.Builder().url(url)
                    //thanks to https://stackoverflow.com/q/17643851
                    .header("Range", "bytes=0-511")//only download the beginning of the storepage HTML
                    //we just need the contents of the <title> tag, and we're not parsing it as valid html anyway
                    .build()
            val response:String? = client.newCall(request).execute().body?.string()
            println("response:")
            println("$response")
            if(response.isNullOrEmpty()) {
                null//exit early with a null result
            }else {
                val nameInTitle = Regex("<title>(?:Save \\d{1,3}% on )?(.+) on Steam</title>")
                //find the human-readable name in the raw HTML of the store page:
                // <div class="apphub_AppName">{human-readable name}</div>
                val nameFromTitleResult: MatchResult? = nameInTitle.find(response)
                val possibleName: String? = nameFromTitleResult?.groupValues?.get(1)
                possibleName?.let {
                    println("name found on store page: $possibleName")
                    cachedNames.setProperty(appid, possibleName)
                    gameIdsToNames.put(appid, possibleName)
                }
                possibleName
            }
        }, NUM_THREADS)

        val results = pp1.collectOutputWhenFinished()//this output collection isn't the most important data storage;
        // the real work is in the Properties file, which now needs writing to disk
        val failures = results.count { it == null }
        println("$failures or ${(failures*1000) / gameIds.size }‰ of name lookups failed")
    }finally {
        //write out any newly retrieved game names to the .properties cachefile
        val fos = FileOutputStream(cacheFile)
        cachedNames.store(fos, "")
        fos.close()
    }


}