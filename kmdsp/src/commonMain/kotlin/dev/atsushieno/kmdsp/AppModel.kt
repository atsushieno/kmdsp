package dev.atsushieno.kmdsp

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.toMutableStateList
import dev.atsushieno.ktmidi.*
import kotlin.random.Random

object AppModel {
    const val numTracks = 16

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

        try {
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
        } catch (ex: Exception) {
            println(ex)
            lastError = ex
        }

        play()
    }

    fun resetUiStates() {
        noteOnStates.forEach { (0 until it.size).forEach { idx -> it[idx] = 0 } }
        keyOnMeterStates.forEach { it.value = 0 }
        midiPlayerState.value = midiPlayer.value.state
    }

    fun play() {
        midiPlayer.value.play()
        midiPlayerState.value = midiPlayer.value.state
    }

    fun pause() {
        midiPlayer.value.pause()
        midiPlayerState.value = midiPlayer.value.state
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
    var midiPlayerState = mutableStateOf(PlayerState.STOPPED)

    val noteOnStates = List(256) { List(128) { 0L }.toMutableStateList() }
    val keyOnMeterStates = List(256) { mutableStateOf(0) }

    var musicCurrentMilliseconds = mutableStateOf(0L)
    var musicCurrentTicks = mutableStateOf(0L)
    var musicStatus = mutableStateOf(MusicStatus(midiPlayer.value))

    val midi1Machine = Midi1MachineState()
    val midi2Machine = Midi2MachineState()

    // the decay/rotation speed depends on tempo.
    val animatedTweenBaseMilliseconds
        get() = 1500 * 500000 / musicStatus.value.tempo


    var lastError: Exception? = null

    init {
        midi1Handlers.add{ evt ->
            val it = evt.message
            midi1Machine.processMessage(it)

            if (it.statusByte.toUnsigned() == Midi1Status.META) {
                when (it.metaType.toInt()) {
                    MidiMetaType.TEMPO, MidiMetaType.TIME_SIGNATURE ->
                        this.musicStatus.value = MusicStatus(midiPlayer.value)
                }
            }
            when (it.statusCode.toUnsigned()) {
                MidiChannelStatus.NOTE_ON -> {
                    noteOnStates[it.channel.toInt()][it.msb.toInt()] = 1
                    if (it.lsb.toInt() != 0)
                        this.keyOnMeterStates[it.channel.toInt()].value = Random.nextInt()
                }
                MidiChannelStatus.NOTE_OFF -> noteOnStates[it.channel.toInt()][it.msb.toInt()] = 0
            }

            musicCurrentTicks.value = midiPlayer.value.playDeltaTime.toLong()
            musicCurrentMilliseconds.value = midiPlayer.value.positionInMilliseconds
        }

        umpHandlers.add {
            midi2Machine.processEvent(it)

            val channel = it.group * 16 + it.channelInGroup
            if (it.messageType == MidiMessageType.FLEX_DATA) {
                when (it.statusCode.toByte()) {
                    FlexDataStatus.TEMPO, FlexDataStatus.TIME_SIGNATURE ->
                        this.musicStatus.value = MusicStatus(midiPlayer.value)
                }
            }
            when (it.statusCode) {
                MidiChannelStatus.NOTE_ON -> noteOnStates[channel][it.midi2Note] = 1
                MidiChannelStatus.NOTE_OFF -> noteOnStates[channel][it.midi2Note] = 0
            }
            musicCurrentTicks.value = midiPlayer.value.playDeltaTime.toLong()
            musicCurrentMilliseconds.value = midiPlayer.value.positionInMilliseconds
        }
    }
}

data class MusicStatus(
    val totalPlayTimeMilliseconds: Long,
    val playDeltaTime: Int,
    val tempo: Int,
    val timeSignatureNominator: Byte,
    val timeSignatureDenominatorBase: Byte
) {
    constructor(player: MidiPlayer) : this(
        player.totalPlayTimeMilliseconds.toLong(),
        player.playDeltaTime,
        player.tempo,
        player.timeSignature[0],
        player.timeSignature[1])
}
