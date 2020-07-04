fun main() {
    val r = RedisApi()
    r.bulkReadKotlinMap(gameNameCache)
    r.bulkReadJsonAppList("appList.json")
}