fun main() {
    val r = LocalRedisApi()
    r.convertKotlinMap(gameNameCache)
    r.convertJsonAppList("appList.json")
}