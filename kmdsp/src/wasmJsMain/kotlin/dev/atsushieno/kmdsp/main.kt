package dev.atsushieno.kmdsp

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
//import dev.atsushieno.ktmidi.JsSynthesizerMidiAccess
//import dev.atsushieno.ktmidi.MergedMidiAccess
import dev.atsushieno.ktmidi.WebMidiAccess
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    MainScope().launch { // we need this for delay
        //val accesses = listOf(WebMidiAccess(), JsSynthesizerMidiAccess.create(ArrayBuffer(0), "dummy"))
        AppModel.midiAccess.value = WebMidiAccess()//MergedMidiAccess("WasmMidiAccesses", accesses)
        while (!WebMidiAccess.isReady)
            delay(1)
        ComposeViewport(document.body!!) {
            App()
        }
    }
}