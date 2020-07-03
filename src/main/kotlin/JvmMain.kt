import kotlinx.coroutines.runBlocking
import java.util.*

fun main(args:Array<String>) = runBlocking<Unit> {
    if(args.size < 2) {
        System.err.println("required arguments: <steam web API key> [player]...")
    }
    val names = Arrays.copyOfRange(args, 1, args.size)
    //buildNameCache(args[0], *names)
    steamGamesInCommon(args[0], *names)
}