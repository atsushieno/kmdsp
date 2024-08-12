package dev.atsushieno.kmdsp

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import dev.atsushieno.ktmidi.WebMidiAccess
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    AppModel.midiAccess.value = WebMidiAccess()
    MainScope().launch { // we need this for delay
        while (!WebMidiAccess.isReady)
            delay(1)
        ComposeViewport(document.body!!) {
            App()
        }
    }
}