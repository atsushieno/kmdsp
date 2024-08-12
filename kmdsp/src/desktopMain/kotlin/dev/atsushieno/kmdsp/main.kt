package dev.atsushieno.kmdsp

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.atsushieno.ktmidi.AlsaMidiAccess
import dev.atsushieno.ktmidi.JvmMidiAccess
import dev.atsushieno.ktmidi.RtMidiAccess
import java.io.File

fun main(args: Array<String>) {
    AppModel.midiAccess.value =
        if (File("/dev/snd/seq").exists()) AlsaMidiAccess()
        else if (args.contains("jvm")) JvmMidiAccess()
        else if (System.getProperty("os.name").contains("Windows")) JvmMidiAccess()
        else RtMidiAccess() // rtmidi-javacpp does not support Windows build nowadays.

    println("Using MidiAccess API: ${AppModel.midiAccess.value.name}")

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "kmdsp",
        ) {
            App()
        }
    }
}