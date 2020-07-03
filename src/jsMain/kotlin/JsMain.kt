import io.ktor.http.Url
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.w3c.dom.*
import org.w3c.dom.events.Event
import org.w3c.dom.url.URLSearchParams
import kotlin.browser.document
import kotlin.browser.window

fun main() {
/*    val slekt = document.getElementById("lang_select") as HTMLSelectElement

        slekt.add(Option(
            text= "example",
            value = "example",
            defaultSelected = false,
            selected = false
        ))*/

    val inputTextArea = document.getElementById("input_text") as HTMLTextAreaElement
    val outputTextArea = document.getElementById("output_text") as HTMLTextAreaElement
    val errorsTextArea = document.getElementById("errors_text") as HTMLTextAreaElement
    //val params:URLSearchParams = js("URLSearchParams.getAll()") as URLSearchParams
    val params = Url(document.URL).parameters
    val players:List<String>? = params.getAll("players")

    val apiKey:String? = params["key"]//?.get(0)
    if(apiKey != null && players != null) {
        GlobalScope.launch {
            outputTextArea.textContent = steamGamesInCommon(apiKey, *(players.toTypedArray())).entries.
                map { it.value ?: it.key }.
                fold("") { acc, elem ->
                    "$acc\n$elem"
                }
        }
    }else {
        outputTextArea.textContent = "at least one was null:\n key: $apiKey ; players: $players"
    }

    //println("params:"+params.toString())
    //outputTextArea.textContent = params.toString()

    inputTextArea.setAttribute("placeholder", UiStrings.inputHint)
    outputTextArea.setAttribute("placeholder", UiStrings.outputHint)
    errorsTextArea.setAttribute("placeholder", UiStrings.errorsHint)


    val button = document.getElementById("action_button") as HTMLButtonElement
/*    button.addEventListener("click", { event:Event ->
        val transcribr = Language.values().firstOrNull { it.ordinal == slekt.selectedIndex }!!.transcriber
        outputTextArea.textContent = transcribr.transcribe(inputTextArea.value)
        //errorsTextArea.textContent = "transcriber: $transcribr"
    })*/

    //remove js warning
    val jsWarning = document.getElementById("js_warning") as HTMLDivElement
    jsWarning.remove()
}