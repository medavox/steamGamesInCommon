package api

/**Checks the local Redis cache before querying Steam via HTTP.
 * If new data is then successfully retrieved by the query, it's stored in the local cache.
 * Otherwise, null (for single data types) or an empty collection is returned*/
class CachedSteamApi(private val redisApi: RedisApi, private val steamApi: SteamApi) {
    //TODO: ideal discord UI: provide one full steam ID or vanity name, and a list of nicknames (to find games in common with)
    // the nicknames are matched to steam IDs by checking the nicknames of the provided ID(s)' friends-of-friends,
    // and using any that match.
    // probably error rate, so this is another place we'll need a user-facing error message

    fun getNicksForPlayerIds(vararg steamids:String):Map<String, String> {
        val split = steamids.partition { redisApi.hasNickForPlayer(it) }
        println("cached nicks: ${split.first.size}; uncached: ${split.second.size}")
        val needSteamApi = split.second.toMutableList()
        val redissed = mutableMapOf<String, String>()
        for(hasCachedNick in split.first) {
            val nick = redisApi.getNickForPlayer(hasCachedNick)
            if(nick != null) {
                redissed.put(hasCachedNick, nick)
            } else {
                needSteamApi.add(hasCachedNick)
            }
        }
        val steamed:Map<String, String> = steamApi.getNicksForPlayerIds(*(needSteamApi.toTypedArray()))
        steamed.forEach { (id: String, nick: String) ->
            redisApi.setNickForPlayer(id, nick)
        }
        return redissed + steamed
    }

    fun getGamesForPlayer(steamid:String):Set<String>? {
        return if(redisApi.hasGamesForPlayer(steamid)) {
            redisApi.getGamesForPlayer(steamid)
        } else {
            val games:List<String> = steamApi.getGamesOwnedByPlayer(steamid)
            if(games.isEmpty()) {
                setOf<String>()
            }  else {
                redisApi.setGamesForPlayer(steamid, *(games.toTypedArray()))
                games.toSet()
            }
        }
    }

    fun getGameNameForAppId(appid:Int):String? {
        return if(redisApi.hasGameNameForAppId(appid)) {
            redisApi.getGameNameForAppId(appid)
        } else {
            val gameName:String? = steamApi.getGameNameForAppId(appid.toString())
            gameName?.let {
                redisApi.setGameNameForAppId(appid, gameName)
            }
            gameName//might be null
        }
    }

    fun getFriendsOfPlayer(steamid: String):Set<String>? {
        return if(redisApi.hasFriendsForPlayer(steamid)) {
            redisApi.getFriendsForPlayer(steamid)
        } else {
            val friends:List<String> = steamApi.getFriendsOfPlayer(steamid)
            if(friends.isEmpty()) {
                null
            }  else {
                redisApi.setFriendsForPlayer(steamid, *(friends.toTypedArray()))
                friends.toSet()
            }
        }
    }

    private fun getSteamIdForVanityName(vanityName:String):String? {
        return if(redisApi.hasSteamIdForVanityName(vanityName)) {
            redisApi.getSteamIdForVanityName(vanityName)
        } else {
            val steamId = steamApi.getSteamIdForVanityName(vanityName)
            steamId?.let {
                redisApi.setSteamIdForVanityName(vanityName, steamId)
            }
            steamId
        }
    }

    /**Accepts a 17-digit steam ID OR a vanity ID, and outputs a 17-digit steam ID.
     * Returns null if
     * 1. a vanity ID was passed,
     * 2. whose steam ID is not already cached locally and
     * 3. something went wrong with the HTTP query to retrieve it from Steam's web API*/
    fun guaranteeSteamId(convertableToSteamId:String):String? {
        return if (convertableToSteamId.matches(Regex("\\d{17}"))) {//is already a SteamId
            convertableToSteamId
        } else if(convertableToSteamId.matches(Regex("\\d{10}"))) {//is missing the standard prefix; add it
            "7656119"+convertableToSteamId
        } else getSteamIdForVanityName(convertableToSteamId)
    }
}