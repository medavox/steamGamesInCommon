import kotlinx.coroutines.runBlocking
import java.util.*

fun main(args:Array<String>) = runBlocking<Unit> {
    //ArgParser().main(args)
    if(args.size < 2) {
        System.err.println("required arguments: <steam web API key> [player]...")
    }
    val names = Arrays.copyOfRange(args, 1, args.size)
    //buildNameCache(args[0], *names)
    steamGamesInCommon(args[0], *names)
/*    if(args.size >= 2) {
        entryPoint(args[0], *(args.slice(1 until args.size).toTypedArray()))
    }else {
        println("use it properly, dummy!")
    }*/
}