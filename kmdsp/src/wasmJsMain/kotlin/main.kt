//import dev.atsushieno.ktmidi.JsSynthesizerMidiAccess
//import dev.atsushieno.ktmidi.MergedMidiAccess
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import dev.atsushieno.kmdsp.App
import dev.atsushieno.kmdsp.AppModel
import dev.atsushieno.ktmidi.WebMidiAccess
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class, DelicateCoroutinesApi::class)
fun main() {
    MainScope().launch { // we need this for delay
        //val accesses = listOf(WebMidiAccess(), JsSynthesizerMidiAccess.create(ArrayBuffer(0), "dummy"))
        AppModel.midiAccess.value = WebMidiAccess()//MergedMidiAccess("WasmMidiAccesses", accesses)
        while (!WebMidiAccess.isReady)
            delay(1)
        CanvasBasedWindow(canvasElementId = "ComposeTarget") { App() }
    }
}