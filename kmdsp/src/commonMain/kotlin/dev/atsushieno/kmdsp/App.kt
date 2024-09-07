package dev.atsushieno.kmdsp

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.atsushieno.ktmidi.MidiCC
import dev.atsushieno.ktmidi.PlayerState
import io.github.vinceglb.filekit.core.FileKit
import io.github.vinceglb.filekit.core.PickerMode
import io.github.vinceglb.filekit.core.baseName
import kotlinx.coroutines.launch
import org.androidaudioplugin.composeaudiocontrols.DiatonicKeyboard
import org.androidaudioplugin.composeaudiocontrols.midi.KtMidiDeviceSelector
import org.jetbrains.skia.Font

fun Byte.toUnsigned() = if (this >= 0) this.toInt() else this + 256

@Composable
fun App() {
    MaterialTheme {
        Scaffold {
            Row(Modifier.background(LocalKmdspThemeBackgroundColor.current)) {
                TrackComboList()
                Column {
                    TitleBar()
                    Row {
                        PlayerControlPanel()
                        PlayerStatusPanel()
                    }
                    KeyOnMeterComboList()
                }
            }
        }
    }
}

private fun pow(x: Int, y: Int): Int = if (y == 0) 1 else x * pow(x, y - 1)

private fun timeSignatureToString(timeSignatureNominator: Byte, timeSignatureDenoimnatorBase: Byte) =
    "${timeSignatureNominator}/${pow(2, timeSignatureDenoimnatorBase.toInt())}"

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
        items(AppModel.numTracks) {
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
    Text(text, fontSize = 10.sp, lineHeight = 14.sp, color = LocalKmdspThemeLabelColor.current, modifier = modifier)
}

@Composable
fun TrackStatusValue(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 10.sp, lineHeight = 14.sp, color = LocalKmdspThemeValueColor.current, modifier = modifier)
}

@Composable
fun TrackComboStatus(trackNumber: Int) {
    // FIXME: support midi2Machine as well.
    val channelState = AppModel.midi1Machine.channels[trackNumber]
    Row {
        Column {
            TrackStatusLabel("MIDI")
            Text(text = "CH.", fontSize = 10.sp, lineHeight = 14.sp, color = LocalKmdspThemeBrightLabelColor.current)
        }
        Text(" ${trackNumber + 1}", fontSize = 24.sp, color = LocalKmdspThemeValueColor.current, modifier = Modifier.width(60.dp))

        Column(modifier = Modifier.width(30.dp)) {
            TrackStatusLabel("VOL")
            TrackStatusLabel("RSD")
        }
        Column(modifier = Modifier.width(40.dp)) {
            val vol by remember { channelState.controls[MidiCC.VOLUME] }
            val rsd by remember { channelState.controls[MidiCC.RSD] }
            TrackStatusValue(vol.toString())
            TrackStatusValue(rsd.toString())
        }
        Column(modifier = Modifier.width(30.dp)) {
            TrackStatusLabel("EXP")
            TrackStatusLabel("CSD")
        }
        Column(modifier = Modifier.width(40.dp)) {
            val exp by remember { channelState.controls[MidiCC.EXPRESSION] }
            val csd by remember { channelState.controls[MidiCC.CSD] }
            TrackStatusValue(exp.toString())
            TrackStatusValue(csd.toString())
        }
        Column(modifier = Modifier.width(30.dp)) {
            TrackStatusLabel("MOD")
            TrackStatusLabel("DSD")
        }
        Column(modifier = Modifier.width(40.dp)) {
            val mod by remember { channelState.controls[MidiCC.MODULATION] }
            val dsd by remember { channelState.controls[MidiCC.EFFECT_3] }
            TrackStatusValue(mod.toString())
            TrackStatusValue(dsd.toString())
        }
        Column(modifier = Modifier.width(20.dp)) {
            val h by remember { channelState.controls[MidiCC.HOLD] }
            val p by remember { channelState.controls[MidiCC.PORTAMENTO_SWITCH] }
            TrackStatusValue(if (h > 63) "H" else "-")
            TrackStatusValue(if (p > 63) "P" else "-")
        }
        Column(modifier = Modifier.width(30.dp)) {
            TrackStatusLabel("So")
            TrackStatusLabel("SP")
        }
        Column(modifier = Modifier.width(40.dp)) {
            val so by remember { channelState.controls[MidiCC.SOSTENUTO] }
            val sp by remember { channelState.controls[MidiCC.SOFT_PEDAL] }
            TrackStatusValue(so.toString())
            TrackStatusValue(sp.toString())
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
    val infiniteTransition = rememberInfiniteTransition(label = "infinite")
    val playerState by remember { AppModel.midiPlayerState }
    val progress by infiniteTransition.animateFloat(0f, 1f,
        InfiniteRepeatableSpec(TweenSpec(AppModel.animatedTweenBaseMilliseconds, 0, LinearEasing)))
    Column {
        Row {
            CircularProgressIndicator(if (playerState == PlayerState.PLAYING) progress else 0f,
                color = LocalKmdspThemeStatusValueColor.current,
                modifier = Modifier.size(32.dp).padding(4.dp)
            )
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
        val outputs by remember { derivedStateOf { midiAccess.outputs.toList() } }
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
fun PlayerStatusPanel() {
    val status by remember { AppModel.musicStatus }
    val currentTicks by remember { AppModel.musicCurrentTicks }
    val currentPosition by remember { AppModel.musicCurrentMilliseconds }
    Column {
        PlayerStatusPanelEntry("Passed", "Time", millisecondsToString(currentPosition))
        PlayerStatusPanelEntry("Total", "Time", millisecondsToString(status.totalPlayTimeMilliseconds))
        PlayerStatusPanelEntry("Tick", "Count", currentTicks.toString())
        PlayerStatusPanelEntry("BPM", "", (60000000 / status.tempo).toString())
        PlayerStatusPanelEntry("Time", "Signature", timeSignatureToString(status.timeSignatureNominator, status.timeSignatureDenominatorBase))
    }
}

@Composable
fun StatusPanelLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 10.sp, lineHeight = 12.sp, color = LocalKmdspThemeStatusLabelColor.current, modifier = modifier)
}

@Composable
fun StatusPanelValue(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 20.sp, textAlign = TextAlign.End, color = LocalKmdspThemeStatusValueColor.current, modifier = modifier)
}

@Composable
fun PlayerStatusPanelEntry(text1: String, text2: String, value: String) {
    Row {
        Box(Modifier.background(LocalKmdspThemeStatusValueColor.current).width(5.dp).wrapContentHeight()) {
            Text(" ", fontSize = 20.sp)
        }
        Column(Modifier.width(80.dp).padding(8.dp, 0.dp)) {
            StatusPanelLabel(text1)
            StatusPanelLabel(" $text2")
        }
        StatusPanelValue(value)
    }
    Box(Modifier.background(LocalKmdspThemeStatusInactiveColor.current).height(1.dp).width(200.dp)) {
        Text(" ", fontSize = 20.sp)
    }
}

@Composable
fun KeyOnMeterComboList() {
    Row {
        Column {
            TrackStatusLabel("Ch. ")
            Box(Modifier.width(24.dp).height(60.dp))
            TrackStatusLabel("PRG ")
            TrackStatusLabel("BNK ")
            TrackStatusLabel("PAN ")
        }
        LazyRow {
            items(AppModel.numTracks) {
                KeyOnMeterCombo(it)
            }
        }
    }
}

@Composable
fun KeyOnMeterCombo(channel: Int) {
    Column {
        Text(text = (channel + 1).toString(), fontSize = 10.sp, color = LocalKmdspThemeBrightLabelColor.current)

        // FIXME: support midi2Machine state
        val channelState = AppModel.midi1Machine.channels[channel]
        KeyOnMeter(channel)
        val program by remember { channelState.program }
        // FIXME: LSB?
        val bank by remember { channelState.controls[MidiCC.BANK_SELECT] }
        // FIXME: LSB?
        val pan by remember { channelState.controls[MidiCC.PAN] }
        TrackStatusValue(program.toString())
        TrackStatusValue(bank.toString())
        TrackStatusValue(pan.toString())
        PanDial(channel)
    }
}

@Composable
fun KeyOnMeter(channel: Int) {
    val meterHeight = remember { Animatable(0f) }
    val keyOnMeterState by remember { AppModel.keyOnMeterStates[channel] }
    LaunchedEffect(channel, keyOnMeterState) {
        // it should not trigger the effect if it is not at playing state (e.g. at initial state)
        if (AppModel.midiPlayer.value.state == PlayerState.PLAYING)
            meterHeight.snapTo(1f)
        meterHeight.animateTo(0f, initialVelocity = 1f, animationSpec = TweenSpec(AppModel.animatedTweenBaseMilliseconds))
    }
    val bgColor = LocalKmdspThemeStatusInactiveColor.current
    val fgColor = LocalKmdspThemeStatusValueColor.current
    Box {
        Box(Modifier.width(24.dp).height(60.dp).background(bgColor))
        val h = 60 * meterHeight.value
        Box(Modifier.offset(0.dp, (60 - h).dp).width(24.dp).height(h.dp).background(fgColor))
    }
}

@Composable
fun PanDial(channel: Int) {
    val outColor = LocalKmdspThemeStatusLabelColor.current
    val inColor = LocalKmdspThemeStatusValueColor.current
    // FIXME: support midi2Machine state
    // FIXME: LSB?
    val pan by remember { AppModel.midi1Machine.channels[channel].controls[MidiCC.PAN] }

    Canvas(Modifier.width(28.dp).height(28.dp).padding(2.dp)) {
        drawCircle(outColor, style = Stroke(1f))
        val panAngle = -(64 - pan) / 128.0 * 120 + 240
        drawArc(inColor, panAngle.toFloat(), 60f, true, topLeft = Offset(2.dp.toPx(), 2.dp.toPx()), size = Size(20.dp.toPx(), 20.dp.toPx()))
    }
}