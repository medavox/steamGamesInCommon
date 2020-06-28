import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.arguments.unique
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.*
class ArgParser: CliktCommand(name="pytokot") {
    val key by argument()
    val names by argument().multiple().unique()
    //val forceOverwrite:Boolean? by option("-f", "--force-overwrite").flag()
    //val sources by argument().file(mustExist = true, mustBeReadable = true).multiple().unique()
    //val tabSize by option("--tab-size", "-t").int().default(4)
    //val dest by argument().file(canBeFile = false)//todo: require a single file path/name if input is a single file,
    //                                                 or a directory if input is multiple files/directory

    override fun run() {
        Thread.setDefaultUncaughtExceptionHandler { thread: Thread, exception: Throwable ->
            System.err.println(exception.localizedMessage)
        }
        runBlocking<Unit> {
            launch {
                parallelRequests(key, *(names.toTypedArray()))
            }
        }
    }
}
fun main(args:Array<String>) = runBlocking<Unit> {
    //ArgParser().main(args)
    val names = Arrays.copyOfRange(args, 1, args.size)
    buildNameCache(args[0], *names)
    //parallelRequests(args[0], *names)
/*    if(args.size >= 2) {
        entryPoint(args[0], *(args.slice(1 until args.size).toTypedArray()))
    }else {
        println("use it properly, dummy!")
    }*/
}