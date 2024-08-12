package dev.atsushieno.kmdsp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.atsushieno.ktmidi.*
import io.github.vinceglb.filekit.core.FileKit
import io.github.vinceglb.filekit.core.PickerMode
import io.github.vinceglb.filekit.core.baseName
import kotlinx.coroutines.launch
import org.androidaudioplugin.composeaudiocontrols.DiatonicKeyboard
import org.androidaudioplugin.composeaudiocontrols.midi.KtMidiDeviceSelector

fun Byte.toUnsigned() = if (this >= 0) this.toInt() else this + 256

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
        midiPlayer.value.pause()
        midiPlayer.value.stop()
        resetUiStates()

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
        midiPlayer.value.play()
    }

    fun resetUiStates() {
        noteOnStates.forEach { (0 until it.size).forEach { idx -> it[idx] = 0 } }
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

@Composable
fun App() {
    MaterialTheme {
        val midiPlayer by remember { AppModel.midiPlayer }
        Scaffold {
            with(KmdspScopeImpl(KmdspDrawScopeImpl(), MidiPlayerStatusImpl(midiPlayer))) {
                Row(Modifier.background(draw.backgroundColor)) {
                    TrackComboList()
                    Column {
                        TitleBar()
                        Row {
                            PlayerControlPanel()
                            PlayerStatusPanel()
                        }
                    }
                }
            }
        }
    }
}

interface MidiPlayerStatus {
    val musicPositionInMilliseconds: Long
    val musicTotalMilliseconds: Int
    val tickCount: Int
    val tempo: Int
    val timeSignature: String
}

private fun pow(x: Int, y: Int): Int = if (y == 0) 1 else x * pow(x, y - 1)

private fun timeSignatureToString(timeSignature: List<Byte>) =
    "${timeSignature[0]}/${pow(2, timeSignature[1].toInt())}"

class MidiPlayerStatusImpl(private val player: MidiPlayer) : MidiPlayerStatus {
    override val musicPositionInMilliseconds: Long
        get() = player.positionInMilliseconds
    override val musicTotalMilliseconds: Int
        get() = player.totalPlayTimeMilliseconds
    override val tickCount: Int
        get() = player.playDeltaTime
    override val tempo: Int
        get() = player.tempo
    override val timeSignature: String
        get() = timeSignatureToString(player.timeSignature)
}

interface KmdspDrawScope {
    @get:Composable
    val backgroundColor: Color
    @get:Composable
    val brightLabelColor: Color
    @get:Composable
    val labelColor: Color
    @get:Composable
    val valueColor: Color
    @get:Composable
    val statusLabelColor: Color
    @get:Composable
    val statusInactiveColor: Color
    @get:Composable
    val statusValueColor: Color
}

class KmdspDrawScopeImpl : KmdspDrawScope {
    //val colorScheme = darkColorScheme() // darkColors()
    override val backgroundColor: Color
        @Composable
        //get() = colorScheme.background
        get() = Color.Black
    override val brightLabelColor: Color
        @Composable
        //get() = colorScheme.primary
        get() = Color.White
    override val labelColor: Color
        @Composable
        //get() = colorScheme.secondary
        get() = Color.Gray
    override val valueColor: Color
        @Composable
        //get() = colorScheme.primary
        get() = Color.LightGray
    override val statusLabelColor: Color
        @Composable
        //get() = colorScheme.primary
        get() = Color.LightGray
    override val statusInactiveColor: Color
        @Composable
        //get() =  colorScheme.secondary
        get() = Color.DarkGray
    override val statusValueColor: Color
        @Composable
        //get() =  colorScheme.primary
        get() = Color.LightGray
}

interface KmdspScope {
    val draw: KmdspDrawScope
    val player: MidiPlayerStatus
}

class KmdspScopeImpl(override val draw: KmdspDrawScope, override val player: MidiPlayerStatus): KmdspScope

@Composable
fun KmdspScope.TrackComboList() {
    LazyColumn {
        items(16) {
            TrackCombo(it)
        }
    }
}

@Composable
fun KmdspScope.TrackCombo(trackNumber: Int) {
    Column {
        TrackComboStatus(trackNumber)
        KeyboardView(trackNumber)
    }
}

@Composable
fun KmdspScope.TrackStatusLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 10.sp, color = draw.labelColor, modifier = modifier)
}

@Composable
fun KmdspScope.TrackStatusValue(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 10.sp, color = draw.valueColor, modifier = modifier)
}

@Composable
fun KmdspScope.TrackComboStatus(trackNumber: Int) {
    Row {
        Column {
            TrackStatusLabel("MIDI")
            Text(text = "TRACK.", fontSize = 10.sp, color = draw.brightLabelColor)
        }
        Text((trackNumber + 1).toString(), fontSize = 20.sp, color = draw.valueColor, modifier = Modifier.width(60.dp))

        Column(modifier = Modifier.width(30.dp)) {
            TrackStatusLabel("VOL")
            TrackStatusLabel("RSD")
        }
        Column(modifier = Modifier.width(40.dp)) {
            TrackStatusValue("999")
            TrackStatusValue("999")
        }
        Column(modifier = Modifier.width(30.dp)) {
            TrackStatusLabel("EXP")
            TrackStatusLabel("CSD")
        }
        Column(modifier = Modifier.width(40.dp)) {
            TrackStatusValue("999")
            TrackStatusValue("999")
        }
        Column(modifier = Modifier.width(30.dp)) {
            TrackStatusLabel("MOD")
            TrackStatusLabel("DSD")
        }
        Column(modifier = Modifier.width(40.dp)) {
            TrackStatusValue("999")
            TrackStatusValue("999")
        }
        Column(modifier = Modifier.width(20.dp)) {
            TrackStatusValue("H")
            TrackStatusValue("P")
        }
        Column(modifier = Modifier.width(30.dp)) {
            TrackStatusLabel("So")
            TrackStatusLabel("SP")
        }
        Column(modifier = Modifier.width(40.dp)) {
            TrackStatusValue("999")
            TrackStatusValue("999")
        }
    }
}

@Composable
fun KmdspScope.KeyboardView(trackNumber: Int) {
    // Note that on this player a "Track" maps to a channel, not an SMF track.
    val noteOnStates = remember { AppModel.noteOnStates[trackNumber] }
    DiatonicKeyboard(
        noteOnStates = noteOnStates,
        //totalWidth = 500.dp,
        numWhiteKeys = 7 * 7,
        whiteKeyWidth = 8.dp,
        totalHeight = 24.dp,
        blackKeyHeight = 16.dp)
}

@Composable
fun KmdspScope.TitleBarLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 10.sp, color = draw.statusInactiveColor, modifier = modifier, textDecoration = TextDecoration.Underline)
}

@Composable
fun KmdspScope.TitleBar() {
    Row(Modifier.padding(0.dp, 8.dp, 0.dp, 16.dp)) {
        Text("MIDI2DSP", color = draw.brightLabelColor, fontSize = 20.sp, modifier = Modifier.padding(0.dp, 0.dp, 12.dp, 0.dp))
        Column {
            TitleBarLabel("MIDI 2.0 music file player")
            TitleBarLabel("(C)2024 atsushieno and ktmidi developers")
        }
    }
}

@Composable
fun KmdspScope.PlayerControlPanel() {
    Column {
        Row {
            CircularProgressIndicator(0.8f, color = draw.statusValueColor, modifier = Modifier.size(32.dp).padding(4.dp))
            LazyVerticalGrid(GridCells.Fixed(2), modifier = Modifier.width(100.dp)) {
                items(4) {
                    when(it) {
                        0 -> PlayerControlButton('\u25B6', "Play")
                        1 -> PlayerControlButton('\u23E9', "FF")
                        2 -> PlayerControlButton('\u23F8', "Pause")
                        3 -> PlayerControlButton('\u23F9', "Stop")
                    }
                }
            }
        }

        val selectedDeviceIndex by remember { AppModel.selectedDeviceIndex }
        val midiAccess by remember { AppModel.midiAccess }
        val outputs = midiAccess.outputs.toList()
        val coroutine = rememberCoroutineScope()
        KtMidiDeviceSelector(Modifier, selectedDeviceIndex,
            outputs,
            onSelectionChange = {
                coroutine.launch {
                    AppModel.setSelectedDevice(it)
                }
            }
        )

        FilePickerLauncher(AppModel.currentMusicFile.value, { selectedFile, fileFullName, stream ->
            AppModel.playMusicFile(selectedFile, fileFullName, stream)
        }, {
        })
    }
}

@Composable
fun KmdspScope.ControlButtonLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 12.sp, color = draw.statusLabelColor, modifier = modifier)
}

@Composable
fun KmdspScope.PlayerControlButton(emoji: Char, text: String) {
    Row(Modifier.border(1.dp, draw.statusInactiveColor)) {
        ControlButtonLabel(emoji.toString())
        ControlButtonLabel(text)
    }
}

@Composable
fun KmdspScope.FilePickerLauncher(currentFileName: String?, onChange: (baseFileName: String, filename: String, stream: List<Byte>) -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    Button(onClick = {
        scope.launch {
            val file = FileKit.pickFile(mode = PickerMode.Single)
            if (file != null)
                onChange(file.baseName, file.path ?: file.name, file.readBytes().toList())
            else
                onDismiss()
        }
    }) {
        ControlButtonLabel(currentFileName ?: "(select a file)")
    }
}

private fun millisecondsToString(value: Long) : String = "${value / 1000 / 60}:${value / 1000 % 60}"

@Composable
fun KmdspScope.PlayerStatusPanel() {
    Column {
        PlayerStatusPanelEntry("Passed", "Time", millisecondsToString(player.musicPositionInMilliseconds))
        PlayerStatusPanelEntry("Total", "Time", millisecondsToString(player.musicTotalMilliseconds.toLong()))
        PlayerStatusPanelEntry("Tick", "Count", player.tickCount.toString())
        PlayerStatusPanelEntry("Tempo", "", player.tempo.toString())
        PlayerStatusPanelEntry("Time", "Signature", player.timeSignature)
    }
}

@Composable
fun KmdspScope.StatusPanelLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 10.sp, color = draw.statusLabelColor, modifier = modifier)
}

@Composable
fun KmdspScope.StatusPanelValue(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 20.sp, color = draw.statusValueColor, modifier = modifier)
}

@Composable
fun KmdspScope.PlayerStatusPanelEntry(text1: String, text2: String, value: String) {
    Row(Modifier.padding(4.dp)) {
        Box(Modifier.background(draw.statusLabelColor).width(5.dp).wrapContentHeight()) {
            Text(" ", fontSize = 20.sp)
        }
        Column(Modifier.width(80.dp).padding(8.dp, 0.dp)) {
            StatusPanelLabel(text1)
            StatusPanelLabel(text2)
        }
        StatusPanelValue(value)
    }
    Box(Modifier.background(draw.statusInactiveColor).height(1.dp).width(200.dp)) {
        Text(" ", fontSize = 20.sp)
    }
}