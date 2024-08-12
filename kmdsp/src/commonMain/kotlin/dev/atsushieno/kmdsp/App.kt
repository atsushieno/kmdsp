package dev.atsushieno.kmdsp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.*
import androidx.compose.runtime.*
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

@Composable
fun App() {
    MaterialTheme {
        val midiPlayer by remember { AppModel.midiPlayer }
        Scaffold {
            Row(Modifier.background(LocalKmdspThemeBackgroundColor.current)) {
                TrackComboList()
                Column {
                    TitleBar()
                    Row {
                        PlayerControlPanel()
                        PlayerStatusPanel(midiPlayer)
                    }
                }
            }
        }
    }
}

private fun pow(x: Int, y: Int): Int = if (y == 0) 1 else x * pow(x, y - 1)

private fun timeSignatureToString(timeSignature: List<Byte>) =
    "${timeSignature[0]}/${pow(2, timeSignature[1].toInt())}"

val LocalKmdspThemeBackgroundColor = compositionLocalOf { Color.Black }
val LocalKmdspThemeBrightLabelColor = compositionLocalOf { Color.White }
val LocalKmdspThemeLabelColor = compositionLocalOf { Color.Gray }
val LocalKmdspThemeValueColor = compositionLocalOf { Color.LightGray }
val LocalKmdspThemeStatusLabelColor = compositionLocalOf { Color.LightGray }
val LocalKmdspThemeStatusInactiveColor = compositionLocalOf { Color.DarkGray }
val LocalKmdspThemeStatusValueColor = compositionLocalOf { Color.LightGray }

@Composable
fun TrackComboList() {
    LazyColumn {
        items(16) {
            TrackCombo(it)
        }
    }
}

@Composable
fun TrackCombo(trackNumber: Int) {
    Column {
        TrackComboStatus(trackNumber)
        KeyboardView(trackNumber)
    }
}

@Composable
fun TrackStatusLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 10.sp, color = LocalKmdspThemeLabelColor.current, modifier = modifier)
}

@Composable
fun TrackStatusValue(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 10.sp, color = LocalKmdspThemeValueColor.current, modifier = modifier)
}

@Composable
fun TrackComboStatus(trackNumber: Int) {
    Row {
        Column {
            TrackStatusLabel("MIDI")
            Text(text = "TRACK.", fontSize = 10.sp, color = LocalKmdspThemeBrightLabelColor.current)
        }
        Text((trackNumber + 1).toString(), fontSize = 20.sp, color = LocalKmdspThemeValueColor.current, modifier = Modifier.width(60.dp))

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
fun KeyboardView(trackNumber: Int) {
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
fun TitleBarLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 10.sp, color = LocalKmdspThemeStatusInactiveColor.current, modifier = modifier, textDecoration = TextDecoration.Underline)
}

@Composable
fun TitleBar() {
    Row(Modifier.padding(0.dp, 8.dp, 0.dp, 16.dp)) {
        Text("MIDI2DSP", color = LocalKmdspThemeBrightLabelColor.current, fontSize = 20.sp, modifier = Modifier.padding(0.dp, 0.dp, 12.dp, 0.dp))
        Column {
            TitleBarLabel("MIDI 2.0 music file player")
            TitleBarLabel("(C)2024 atsushieno and ktmidi developers")
        }
    }
}

@Composable
fun PlayerControlPanel() {
    Column {
        Row {
            CircularProgressIndicator(0.8f, color = LocalKmdspThemeStatusValueColor.current, modifier = Modifier.size(32.dp).padding(4.dp))
            LazyVerticalGrid(GridCells.Fixed(2), modifier = Modifier.width(100.dp)) {
                items(4) {
                    when(it) {
                        0 -> PlayerControlButton("▶️", "Play", onClick = { AppModel.play() })
                        1 -> PlayerControlButton("⏩", "FF", onClick = { AppModel.switchFFMode() })
                        2 -> PlayerControlButton("⏸️", "Pause", onClick = { AppModel.pause() })
                        3 -> PlayerControlButton("⏹️", "Stop", onClick = { AppModel.stop() })
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
fun ControlButtonLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 12.sp, color = LocalKmdspThemeStatusLabelColor.current, modifier = modifier)
}

@Composable
fun PlayerControlButton(emoji: String, text: String,
                        onClick: ()->Unit = {}) {
    Row(Modifier.border(1.dp, LocalKmdspThemeStatusInactiveColor.current)
        .clickable { onClick() }) {
        ControlButtonLabel(emoji)
        ControlButtonLabel(text)
    }
}

@Composable
fun FilePickerLauncher(currentFileName: String?, onChange: (baseFileName: String, filename: String, stream: List<Byte>) -> Unit, onDismiss: () -> Unit) {
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
fun PlayerStatusPanel(player: MidiPlayer) {
    Column {
        PlayerStatusPanelEntry("Passed", "Time", millisecondsToString(player.positionInMilliseconds))
        PlayerStatusPanelEntry("Total", "Time", millisecondsToString(player.totalPlayTimeMilliseconds.toLong()))
        PlayerStatusPanelEntry("Tick", "Count", player.playDeltaTime.toString())
        PlayerStatusPanelEntry("Tempo", "", player.tempo.toString())
        PlayerStatusPanelEntry("Time", "Signature", timeSignatureToString(player.timeSignature))
    }
}

@Composable
fun StatusPanelLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 10.sp, color = LocalKmdspThemeStatusLabelColor.current, modifier = modifier)
}

@Composable
fun StatusPanelValue(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 20.sp, color = LocalKmdspThemeStatusValueColor.current, modifier = modifier)
}

@Composable
fun PlayerStatusPanelEntry(text1: String, text2: String, value: String) {
    Row(Modifier.padding(4.dp)) {
        Box(Modifier.background(LocalKmdspThemeStatusValueColor.current).width(5.dp).wrapContentHeight()) {
            Text(" ", fontSize = 20.sp)
        }
        Column(Modifier.width(80.dp).padding(8.dp, 0.dp)) {
            StatusPanelLabel(text1)
            StatusPanelLabel(text2)
        }
        StatusPanelValue(value)
    }
    Box(Modifier.background(LocalKmdspThemeStatusInactiveColor.current).height(1.dp).width(200.dp)) {
        Text(" ", fontSize = 20.sp)
    }
}