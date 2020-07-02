import io.ktor.http.Url
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
    println("params:"+params.toString())
    outputTextArea.textContent = params.toString()
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