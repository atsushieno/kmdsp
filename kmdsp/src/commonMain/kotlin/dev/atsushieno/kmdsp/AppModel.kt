package dev.atsushieno.kmdsp

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.toMutableStateList
import dev.atsushieno.ktmidi.*

object AppModel {
    suspend fun setSelectedDevice(id: Int) {
        midiPlayer.value.pause()
        midiPlayer.value.stop()
        if (id < 0)
            midiOutput.value.close()
        else
            midiOutput.value = midiAccess.value.openOutput(midiAccess.value.outputs.toList()[id].id)
        selectedDeviceIndex.value = id
    }

    fun playMusicFile(selectedFile: String, fileFullName: String, stream: List<Byte>) {
        currentMusicFile.value = selectedFile

        pause()
        stop()

        if (selectedFile.endsWith(".umpx")) {
            val music = Midi2Music().apply { read(stream, true) }
            midiPlayer.value = Midi2Player(music, midiOutput.value).apply {
                addOnMessageListener { evt -> umpHandlers.forEach { it(evt) } }
            }
        } else {
            val music = Midi1Music().apply { read(stream) }
            midiPlayer.value = Midi1Player(music, midiOutput.value).apply {
                addOnEventListener { evt -> midi1Handlers.forEach { it(evt) } }
            }
        }

        play()
    }

    fun resetUiStates() {
        noteOnStates.forEach { (0 until it.size).forEach { idx -> it[idx] = 0 } }
    }

    fun play() {
        midiPlayer.value.play()
    }

    fun pause() {
        midiPlayer.value.pause()
    }

    fun stop() {
        midiPlayer.value.stop()
        resetUiStates()
    }

    fun switchFFMode() {
        if (midiPlayer.value.tempoChangeRatio == 8.0)
            midiPlayer.value.tempoChangeRatio = 1.0
        else
            midiPlayer.value.tempoChangeRatio *= 2.0
    }

    val umpHandlers = mutableListOf<(Ump)->Unit>()
    val midi1Handlers = mutableListOf<(Midi1Event)->Unit>()

    val selectedDeviceIndex = mutableStateOf(-1)
    val currentMusicFile = mutableStateOf<String?>(null)
    var midiAccess: MutableState<MidiAccess> = mutableStateOf(EmptyMidiAccess())
    var midiOutput = mutableStateOf(EmptyMidiAccess.output)
    var midiPlayer: MutableState<MidiPlayer> = mutableStateOf(Midi1Player(Midi1Music(), midiOutput.value))

    val noteOnStates = List(256) { List(128) { 0L }.toMutableStateList() }

    init {
        midi1Handlers.add {
            when (it.message.statusCode.toUnsigned()) {
                MidiChannelStatus.NOTE_ON -> noteOnStates[it.message.channel.toInt()][it.message.msb.toInt()] = 1
                MidiChannelStatus.NOTE_OFF -> noteOnStates[it.message.channel.toInt()][it.message.msb.toInt()] = 0
            }
        }
        umpHandlers.add {
            val channel = it.group * 16 + it.channelInGroup
            when (it.statusCode) {
                MidiChannelStatus.NOTE_ON -> noteOnStates[channel][it.midi2Note] = 1
                MidiChannelStatus.NOTE_OFF -> noteOnStates[channel][it.midi2Note] = 0
            }
        }
    }
}