import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.events.message.priv.PrivateMessageReceivedEvent
import net.dv8tion.jda.api.events.message.priv.PrivateMessageUpdateEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.requests.GatewayIntent
import java.lang.StringBuilder

class DiscordBot(private val selfUser:SelfUser) : ListenerAdapter() {
    private val DISCORD_MAX_MESSAGE_LENGTH = 2000
    private var output = StringBuilder()
    private val backend = Functionality(steamWebApiKey) { output.appendln(it) }

    /**Discord doesn't allow a single message to be longer than 2000 characters.
     * This function splits all messages into chunks smaller than that
     * */
    private fun splitLongMessage(longMessage:String, maxChunkSize:Int=DISCORD_MAX_MESSAGE_LENGTH):List<String> {
        println("max message length: $maxChunkSize")
        if(longMessage.length < maxChunkSize) {
            return listOf(longMessage)
        }
        val numberOfSplitPoints = longMessage.length / maxChunkSize
        println("number of splits: $numberOfSplitPoints")
        val splitPoints = mutableListOf<Int>(0)
        var lastDifferenceBetweenMaxAndActualSplitPoint = 0
        for( i in 1..numberOfSplitPoints) {
            val splitIndex = i * maxChunkSize
            //only cut the end off, so we don't affect the indices
            val searchString = longMessage.substring(0, splitIndex - lastDifferenceBetweenMaxAndActualSplitPoint)
            println("search string length: "+searchString.length)
            val splitPoint = searchString.indexOfLast { it == '\n' }
            lastDifferenceBetweenMaxAndActualSplitPoint += (splitIndex - splitPoint)
            splitPoints.add(splitPoint)
        }
        println("split points for message of length ${longMessage.length}: "+splitPoints)
        //find the \n whose index is highest, but < 2000
        val messageChunks = mutableListOf<String>()
        for (i in 0 until splitPoints.size-1) {
            messageChunks.add(longMessage.substring(splitPoints[i], splitPoints[i+1]))
        }
        messageChunks.add(longMessage.substring(splitPoints[splitPoints.size-1], longMessage.length))
        println("message chunks: ${messageChunks.size} of sizes ${messageChunks.map { it.length }}")
        return messageChunks
    }

    private fun argumentsFromCommand(command:String):Array<String> {
        return command.split(" ").
            let { it.subList(1, it.size) }.
            map { arg -> arg.trim()}.
            filter { it.isNotBlank() && it.isNotEmpty() }.
            toTypedArray()
    }

    override fun onPrivateMessageUpdate(event: PrivateMessageUpdateEvent) {
        if(event.message.author != selfUser) {//ignore our own messages
            handleCommand(event.message, event.channel)
        }
    }

    override fun onMessageReceived(event: MessageReceivedEvent) {
        if(event.message.author != selfUser) {//ignore our own messages
            handleCommand(event.message, event.channel)
        }
    }

    private fun sgicCommand(arguments:Array<String>):String {
        if(arguments.size < 2) {
            return "please specify at least 2 steam IDs."
        }
        return try {
            val playerIDs = backend.sanitiseInputIds(*arguments)
            if(playerIDs.isNotEmpty()) backend.steamGamesInCommon(playerIDs)
            "```\n${output.toString()}\n```"
        } catch(e:MultiException) {
            output.clear()
            e.exceptions.mapNotNull {it.message}.forEach { output.appendln("ERROR: $it") }
            output.toString()
        }
    }

    private fun friendsOfCommand(arguments:Array<String>):String {
        if(arguments.isEmpty()) {
            return "please specify at least 1 steam ID."
        }
        return try {
            val playerIDs = backend.sanitiseInputIds(*arguments)
            if(playerIDs.isNotEmpty()) backend.friendsOf(playerIDs)
            output.toString()
        } catch(e:MultiException) {
            output.clear()
            e.exceptions.mapNotNull {it.message}.forEach { output.appendln("ERROR: $it") }
            output.toString()
        }
    }

    private fun handleCommand(msg:Message, channel:MessageChannel) {
        output.clear()

        try {
            val funToRun:(Array<String>) -> String = with(msg.contentRaw) { when {
                startsWith("!sgic ") -> ::sgicCommand
                startsWith("!friendsof ") -> ::friendsOfCommand
                contains("!help") -> { _ -> helpText }
                else -> { _ -> "command not recognised. Try `!sgic`, `!friendsof` or `!help`"}
            }}
            msg.addReaction("\uD83E\uDD16").queue()
            val results = funToRun(argumentsFromCommand(msg.contentRaw))

            for (submessage in splitLongMessage(results)) {
                channel.sendMessage(submessage).queue()
            }
        } catch(owt:Throwable) {
            channel.sendMessage("Woops! something went wrong at my end (tech jargon:||${owt.javaClass.name}||). Try again?").queue()
            throw owt
        }
    }
}
private val helpText = """Find games everyone can play!

NOTE: the steam profile of every user must be public!

Commands:

!sgic <ID> <ID>...
    (Steam Games In Common)
    ID can be a steam ID (eg 76561197962146232) or a 'vanity name' (eg AbacusAvenger).
    Lists games which all the specified users own. 
    When more than 2 users are specified, also lists games that only one player doesn't own.

    example: !sgic 

!friendsof <ID> <ID>...
    lists the steam friends of all the specified users.
    Aan easier method to get steam IDs for the main !sgic command.

!help (also works in public channels)
displays this text

If you can program (or are curious), the source code for this bot is at https://github.com/medavox/steamGamesInCommon
"""

fun main() {
    val jda = JDABuilder.createLight(discordToken, GatewayIntent.GUILD_MESSAGES, GatewayIntent.DIRECT_MESSAGES)
        .setActivity(Activity.listening("!help"))
        .build()
    jda.awaitReady()
    jda.addEventListener(DiscordBot(jda.selfUser))
}
