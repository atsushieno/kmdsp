package dev.atsushieno.kmdsp

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import dev.atsushieno.ktmidi.DteTarget
import dev.atsushieno.ktmidi.Midi1Message
import dev.atsushieno.ktmidi.MidiCC
import dev.atsushieno.ktmidi.MidiChannelStatus
import dev.atsushieno.ktmidi.MidiRpn

class Midi1MachineState {
    fun interface OnMidi1MessageListener {
        fun onMessage(e: Midi1Message)
    }

    val messageListeners by lazy { mutableListOf<OnMidi1MessageListener>() }

    val controllerCatalog = Midi1ControllerCatalog()

    var systemCommon = Midi1SystemCommonState()

    var channels = Array(16) { Midi1MachineChannelState() }

    fun processMessage(evt: Midi1Message) {
        val ch = evt.channel.toUnsigned()
        when (evt.statusCode.toUnsigned()) {
            MidiChannelStatus.NOTE_ON -> {
                with (channels[ch]) {
                    noteVelocity[evt.msb.toUnsigned()].value = evt.lsb
                    noteOnStatus[evt.msb.toUnsigned()].value = true
                }
            }
            MidiChannelStatus.NOTE_OFF -> {
                with (channels[ch]) {
                    noteVelocity[evt.msb.toUnsigned()].value = evt.lsb
                    noteOnStatus[evt.msb.toUnsigned()].value = false
                }
            }
            MidiChannelStatus.PAF ->
                channels[ch].pafVelocity[evt.msb.toUnsigned()].value = evt.lsb
            MidiChannelStatus.CC -> {
                // FIXME: handle RPNs and NRPNs by DTE
                when (evt.msb.toInt()) {
                    MidiCC.NRPN_MSB,
                    MidiCC.NRPN_LSB ->
                        channels[ch].dteTarget.value = DteTarget.NRPN
                    MidiCC.RPN_MSB,
                    MidiCC.RPN_LSB ->
                        channels[ch].dteTarget.value = DteTarget.RPN

                    MidiCC.DTE_MSB ->
                        channels[ch].processDte(evt.lsb, true)
                    MidiCC.DTE_LSB ->
                        channels[ch].processDte(evt.lsb, false)
                    MidiCC.DTE_INCREMENT ->
                        channels[ch].processDteIncrement()
                    MidiCC.DTE_DECREMENT ->
                        channels[ch].processDteDecrement()
                }
                channels[ch].controls[evt.msb.toUnsigned()].value = evt.lsb
                when (evt.msb.toUnsigned()) {
                    MidiCC.OMNI_MODE_OFF -> channels[ch].omniMode.value = false
                    MidiCC.OMNI_MODE_ON -> channels[ch].omniMode.value = true
                    MidiCC.MONO_MODE_ON -> channels[ch].monoPolyMode.value = false
                    MidiCC.POLY_MODE_ON -> channels[ch].monoPolyMode.value = true
                }
            }
            MidiChannelStatus.PROGRAM ->
                channels[ch].program.value = evt.msb
            MidiChannelStatus.CAF ->
                channels[ch].caf.value = evt.msb
            MidiChannelStatus.PITCH_BEND ->
                channels[ch].pitchbend.value = ((evt.msb.toUnsigned() shl 7) + evt.lsb).toShort()
        }

        messageListeners.forEach { it.onMessage(evt) }
    }
}

private val midi1StandardRpnEnabled = BooleanArray(0x80 * 0x80) { false }.apply {
    this[MidiRpn.PITCH_BEND_SENSITIVITY] = true
    this[MidiRpn.FINE_TUNING] = true
    this[MidiRpn.COARSE_TUNING] = true
    this[MidiRpn.TUNING_PROGRAM] = true
    this[MidiRpn.TUNING_BANK_SELECT] = true
    this[MidiRpn.MODULATION_DEPTH] = true
}

class Midi1ControllerCatalog(
    val enabledRpns: BooleanArray = midi1StandardRpnEnabled.copyOf(),
    val enabledNrpns: BooleanArray = BooleanArray(0x80 * 0x80) { false }
) {
    fun enableAllNrpnMsbs() {
        (0 until 0x80).forEach { enabledNrpns[it * 0x80] = true }
    }
}


class Midi1SystemCommonState {
    var mtcQuarterFrame = mutableStateOf<Byte>(0)
    var songPositionPointer = mutableStateOf<Short>(0)
    var songSelect = mutableStateOf<Byte>(0)
}

class Midi1MachineChannelState {
    val noteOnStatus = Array(128) { mutableStateOf(false) }
    val noteVelocity = Array(128) { mutableStateOf<Byte>(0) }
    val pafVelocity = Array(128) { mutableStateOf<Byte>(0) }
    val controls = Array(128) { mutableStateOf<Byte>(0) }
    // They need independent flag to indicate which was set currently.
    var omniMode = mutableStateOf<Boolean?>(null)
    var monoPolyMode = mutableStateOf<Boolean?>(null)
    // They store values sent by DTE (MSB+LSB), per index (MSB+LSB)
    val rpns = Array(128 * 128) { mutableStateOf<Short>(0) } // only 5 should be used though
    val nrpns = Array(128 * 128) { mutableStateOf<Short>(0) }
    var program = mutableStateOf<Byte>(0)
    var caf = mutableStateOf<Byte>(0)
    var pitchbend = mutableStateOf<Short>(8192)
    var dteTarget = mutableStateOf(DteTarget.RPN)
    private var dte_target_value: Byte = 0

    val currentRPN: Int
        get() = ((controls[MidiCC.RPN_MSB].value.toUnsigned() shl 7) + controls[MidiCC.RPN_LSB].value)
    val currentNRPN: Int
        get() = ((controls[MidiCC.NRPN_MSB].value.toUnsigned() shl 7) + controls[MidiCC.NRPN_LSB].value)

    fun processDte(value: Byte, isMsb: Boolean) {
        lateinit var arr: Array<MutableState<Short>>
        var target = 0
        when (dteTarget.value) {
            DteTarget.RPN -> {
                target = currentRPN
                arr = rpns
            }
            DteTarget.NRPN -> {
                target = currentNRPN
                arr = nrpns
            }
        }
        val cur = arr[target].value.toInt()
        if (isMsb)
            arr[target].value = ((cur and 0x007F) + ((value.toUnsigned() and 0x7F) shl 7)).toShort()
        else
            arr[target].value = ((cur and 0x3F80) + (value.toUnsigned() and 0x7F)).toShort()
    }

    fun processDteIncrement() {
        when (dteTarget.value) {
            DteTarget.RPN -> rpns[controls[MidiCC.RPN_MSB].value * 0x80 + controls[MidiCC.RPN_LSB].value].value++
            DteTarget.NRPN -> nrpns[controls[MidiCC.NRPN_MSB].value * 0x80 + controls[MidiCC.NRPN_LSB].value].value++
        }
    }

    fun processDteDecrement() {
        when (dteTarget.value) {
            DteTarget.RPN -> rpns[controls[MidiCC.RPN_MSB].value * 0x80 + controls[MidiCC.RPN_LSB].value].value--
            DteTarget.NRPN -> nrpns[controls[MidiCC.NRPN_MSB].value * 0x80 + controls[MidiCC.NRPN_LSB].value].value--
        }
    }
}
