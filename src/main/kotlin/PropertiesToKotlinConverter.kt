import org.jsoup.parser.Parser
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.*

object PropertiesToKotlinConverter {
    val e = System.err
    fun convert(outputString: String, vararg inputs:String) {
        //initialise cache from local file
        val cache = Properties()
        for(cacheFileString in inputs) {
            val cacheFile = File(cacheFileString)
            if (!cacheFile.exists() || cacheFile.length() == 0L) {
                e.println("ERROR: input file \"${cacheFile.name}\" is empty or doesn't exist!")
                return
            } else {
                val inputStream: InputStream = FileInputStream(cacheFile)
                cache.load(inputStream)
                println("read \"${cacheFile.name}\". new in-memory properties size: ${cache.size}")
                inputStream.close()
                println("value for 113020: ${cache["113020"]}")
            }
        }
        val output = File(outputString)
        //now it's all loaded in, write it out in a generated Kotlin format
        if(output.exists()) {
            e.println("ERROR: output file \"${output.name}\" already exists. not overwriting.")
            return
        }
        output.createNewFile()
        output.writeText("val gameNameCache:Map<Int, String> = mapOf(\n")//use write on the first call, to overwrite the existing file
        var lastLine = ""
        for(entry in cache.entries.sortedBy { it.key as String }) {
            output.appendText(lastLine+if(lastLine.isEmpty()) "" else ",\n")
            lastLine = "\t${entry.key} to \"\"\"${Parser.unescapeEntities(entry.value.toString(), false)}\"\"\""
        }
        output.appendText(lastLine)
        output.appendText("\n)")
    }
}

fun main() {
    PropertiesToKotlinConverter.convert("cache.kt", "old_cache_sorted.properties", "game_name_cache.properties")
}