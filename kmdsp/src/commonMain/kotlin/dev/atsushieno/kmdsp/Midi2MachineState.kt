package dev.atsushieno.kmdsp

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import dev.atsushieno.ktmidi.*
internal infix fun Byte.shl(n: Int): Int = this.toInt() shl n
internal fun Int.toUnsigned() = if (this < 0) 0x100000000 + this else this.toLong()

class Midi2MachineState {
    fun interface Listener {
        fun onEvent(e: Ump)
    }

    var diagnosticsHandler: (String, Ump?) -> Unit =
        { message, ump -> throw UnsupportedOperationException(message + (if (ump != null) " : $ump" else null)) }

    val eventListeners by lazy { mutableListOf<Listener>() }

    var systemCommon = Midi1SystemCommonState() // They are compatible with MIDI2 protocol

    private val channels = mutableMapOf<Int,Midi2MachineChannelState>()
    val usedChannels : Iterable<Midi2MachineChannelState>
        get() = channels.values
    fun channel(index: Int): Midi2MachineChannelState {
        var ch = channels[index]
        if (ch == null) {
            ch = Midi2MachineChannelState()
            channels[index] = ch
        }
        return ch
    }

    private fun withNoteRangeCheckV1(u: Ump, action: () -> Unit) = if (u.midi1Msb in 0..127) action() else diagnosticsHandler("Note is out of range", u)
    private fun withNoteRangeCheckV2(u: Ump, action: () -> Unit) = if (u.midi2Note in 0..127) action() else diagnosticsHandler("Note is out of range", u)

    fun processEvent(evt: Ump) {
        when (evt.messageType) {
            MidiMessageType.MIDI1 -> {
                when (evt.statusCode) {
                    MidiChannelStatus.NOTE_ON ->
                        withNoteRangeCheckV1(evt) {
                            with(channel(evt.groupAndChannel)) {
                                noteVelocity[evt.midi1Msb].value = (evt.midi1Lsb shl 9).toUShort()
                                noteOnStatus[evt.midi1Msb].value = true
                            }
                        }
                    MidiChannelStatus.NOTE_OFF ->
                        withNoteRangeCheckV1(evt) {
                            with(channel(evt.groupAndChannel)) {
                                noteVelocity[evt.midi1Msb].value = (evt.midi1Lsb shl 9).toUShort()
                                noteOnStatus[evt.midi1Msb].value = false
                            }
                        }
                    MidiChannelStatus.PAF ->
                        withNoteRangeCheckV1(evt) {
                            channel(evt.groupAndChannel).pafVelocity[evt.midi1Msb].value = (evt.midi1Lsb shl 25).toUInt()
                        }
                    MidiChannelStatus.CC -> {
                        // FIXME: handle RPNs and NRPNs by DTE
                        with(channel(evt.groupAndChannel)) {
                            when (evt.midi1Msb) {
                                MidiCC.NRPN_MSB,
                                MidiCC.NRPN_LSB ->
                                    dteTarget.value = DteTarget.NRPN
                                MidiCC.RPN_MSB,
                                MidiCC.RPN_LSB ->
                                    dteTarget.value = DteTarget.RPN

                                MidiCC.DTE_MSB ->
                                    processMidi1Dte(evt.midi1Lsb.toByte(), true)
                                MidiCC.DTE_LSB ->
                                    processMidi1Dte(evt.midi1Lsb.toByte(), false)
                                MidiCC.DTE_INCREMENT ->
                                    processMidi1DteIncrement()
                                MidiCC.DTE_DECREMENT ->
                                    processMidi1DteDecrement()
                            }
                            controls[evt.midi1Msb].value = (evt.midi1Lsb shl 25).toUInt()
                            when (evt.midi2CCIndex) {
                                MidiCC.OMNI_MODE_OFF -> omniMode.value = false
                                MidiCC.OMNI_MODE_ON -> omniMode.value = true
                                MidiCC.MONO_MODE_ON -> monoPolyMode.value = false
                                MidiCC.POLY_MODE_ON -> monoPolyMode.value = true
                            }
                        }
                    }
                    MidiChannelStatus.PROGRAM ->
                        channel(evt.groupAndChannel).program.value = evt.midi1Msb.toByte()
                    MidiChannelStatus.CAF ->
                        channel(evt.groupAndChannel).caf.value = (evt.midi1Msb shl 25).toUInt()
                    MidiChannelStatus.PITCH_BEND ->
                        channel(evt.groupAndChannel).pitchbend.value = ((evt.midi1Msb.toUnsigned() shl 25) + (evt.midi1Lsb shl 18)).toUInt()
                }
            }
            MidiMessageType.MIDI2 -> {
                when (evt.statusCode) {
                    MidiChannelStatus.NOTE_ON ->
                        withNoteRangeCheckV2(evt) {
                            with(channel(evt.groupAndChannel)) {
                                noteOnStatus[evt.midi2Note].value = true
                                noteVelocity[evt.midi2Note].value = evt.midi2Velocity16.toUShort()
                                noteAttribute[evt.midi2Note].value = evt.midi2NoteAttributeData.toUShort()
                                noteAttributeType[evt.midi2Note].value = evt.midi2NoteAttributeType.toUShort()
                            }
                        }
                    MidiChannelStatus.NOTE_OFF ->
                        withNoteRangeCheckV2(evt) {
                            with(channel(evt.groupAndChannel)) {
                                noteOnStatus[evt.midi2Note].value = false
                                noteVelocity[evt.midi2Note].value = 0u
                            }
                        }
                    MidiChannelStatus.PAF ->
                        withNoteRangeCheckV2(evt) {
                            channel(evt.groupAndChannel).pafVelocity[evt.midi2Note].value = evt.midi2PAfData
                        }
                    MidiChannelStatus.CC -> {
                        with(channel(evt.groupAndChannel)) {
                            controls[evt.midi2CCIndex].value = evt.midi2CCData
                            when (evt.midi2CCIndex) {
                                MidiCC.OMNI_MODE_OFF -> omniMode.value = false
                                MidiCC.OMNI_MODE_ON -> omniMode.value = true
                                MidiCC.MONO_MODE_ON -> monoPolyMode.value = false
                                MidiCC.POLY_MODE_ON -> monoPolyMode.value = true
                            }
                        }
                    }
                    MidiChannelStatus.PROGRAM -> {
                        if (evt.midi2ProgramOptions and 1 != 0) {
                            channel(evt.groupAndChannel).controls[MidiCC.BANK_SELECT].value =
                                evt.midi2ProgramBankMsb.toUInt()
                            channel(evt.groupAndChannel).controls[MidiCC.BANK_SELECT_LSB].value =
                                evt.midi2ProgramBankMsb.toUInt()
                        }
                        channel(evt.groupAndChannel).program.value = evt.midi2ProgramProgram.toByte()
                    }
                    MidiChannelStatus.CAF ->
                        channel(evt.groupAndChannel).caf.value = evt.midi2CAfData
                    MidiChannelStatus.PITCH_BEND ->
                        channel(evt.groupAndChannel).pitchbend.value = evt.midi2PitchBendData
                    MidiChannelStatus.PER_NOTE_PITCH_BEND ->
                        channel(evt.groupAndChannel).perNotePitchbend[evt.midi2Note].value = evt.midi2PitchBendData
                    MidiChannelStatus.PER_NOTE_RCC ->
                        withNoteRangeCheckV2(evt) {
                            channel(evt.groupAndChannel).perNoteRCC[evt.midi2PerNoteRCCIndex][evt.midi2Note].value =
                                evt.midi2PerNoteRCCData
                        }
                    MidiChannelStatus.PER_NOTE_ACC ->
                        withNoteRangeCheckV2(evt) {
                            channel(evt.groupAndChannel).perNoteACC[evt.midi2PerNoteACCIndex][evt.midi2Note].value =
                                evt.midi2PerNoteACCData
                        }
                    MidiChannelStatus.RPN ->
                        channel(evt.groupAndChannel).rpns[evt.midi2RpnMsb * 128 + evt.midi2RpnLsb].value = evt.midi2RpnData
                    MidiChannelStatus.NRPN ->
                        channel(evt.groupAndChannel).nrpns[evt.midi2NrpnMsb * 128 + evt.midi2NrpnLsb].value =
                            evt.midi2NrpnData
                    MidiChannelStatus.RELATIVE_RPN ->
                        channel(evt.groupAndChannel).rpns[evt.midi2RpnMsb * 128 + evt.midi2RpnLsb].value =
                            (channel(evt.groupAndChannel).rpns[evt.midi2RpnMsb * 128 + evt.midi2RpnLsb].value.toLong() + evt.midi2RpnData.toInt()).toUInt()
                    MidiChannelStatus.RELATIVE_NRPN ->
                        channel(evt.groupAndChannel).nrpns[evt.midi2RpnMsb * 128 + evt.midi2RpnLsb].value =
                            (channel(evt.groupAndChannel).nrpns[evt.midi2NrpnMsb * 128 + evt.midi2NrpnLsb].value.toLong() + evt.midi2NrpnData.toInt()).toUInt()
                }
            }
        }
        for (receiver in eventListeners)
            receiver.onEvent(evt)
    }
}

class Midi2MachineChannelState {
    val noteOnStatus = Array(128) { mutableStateOf(false) }
    val noteVelocity = Array(128) { mutableStateOf<UShort>(0u) }
    val noteAttribute = Array(128) { mutableStateOf<UShort>(0u) }
    val noteAttributeType = Array(128) { mutableStateOf<UShort>(0u) }
    val pafVelocity = Array(128) { mutableStateOf(0u) }
    val controls = Array(128) { mutableStateOf(0u) }
    // They need independent flag to indicate which was set currently.
    var omniMode = mutableStateOf<Boolean?>(null)
    var monoPolyMode = mutableStateOf<Boolean?>(null)
    val perNoteRCC = Array(128) { Array(128) { mutableStateOf(0u) } }
    val perNoteACC = Array(128) { Array(128) { mutableStateOf(0u) } }
    val rpns = Array(128 * 128) { mutableStateOf(0u) } // only 5 should be used though
    val nrpns = Array(128 * 128) { mutableStateOf(0u) }
    var program = mutableStateOf<Byte>(0)
    var caf = mutableStateOf(0u)
    var pitchbend = mutableStateOf(0x80000000u)
    val perNotePitchbend = Array(128) { mutableStateOf(0u) }
    var dteTarget = mutableStateOf(DteTarget.RPN)
    private var dte_target_value: Byte = 0

    // This SHOULD NOT HAPPEN, but in case they were sent as legacy MIDI 1.0 messages...
    fun processMidi1Dte(value: Byte, isMsb: Boolean) {
        val arr: Array<MutableState<UInt>>
        when (dteTarget.value) {
            DteTarget.RPN -> {
                dte_target_value = (controls[(if (isMsb) MidiCC.RPN_MSB else MidiCC.RPN_LSB)].value shr 25).toByte()
                arr = rpns
            }
            DteTarget.NRPN -> {
                dte_target_value = (controls[(if (isMsb) MidiCC.NRPN_MSB else MidiCC.NRPN_LSB)].value shr 25).toByte()
                arr = nrpns
            }
        }
        val cur = arr[dte_target_value.toUnsigned()]
        if (isMsb)
            arr[dte_target_value.toUnsigned()].value = (value shl 25).toUInt() + (cur.value and 0x1FE0000.toUInt())
        else
            arr[dte_target_value.toUnsigned()].value = (cur.value and 0xFE000000.toUInt()) + (value shl 18).toUInt()
    }

    // This SHOULD NOT HAPPEN, but in case they were sent as legacy MIDI 1.0 messages...
    // increment as if it were sent in 7-bit precision -> translate it to 32-bit context
    fun processMidi1DteIncrement() {
        when (dteTarget.value) {
            DteTarget.RPN -> rpns[dte_target_value.toUnsigned()].value += (1u shl 25)
            DteTarget.NRPN -> nrpns[dte_target_value.toUnsigned()].value += (1u shl 25)
        }
    }

    // This SHOULD NOT HAPPEN, but in case they were sent as legacy MIDI 1.0 messages...
    // increment as if it were sent in 7-bit precision -> translate it to 32-bit context
    fun processMidi1DteDecrement() {
        when (dteTarget.value) {
            DteTarget.RPN -> rpns[dte_target_value.toUnsigned()].value -= (1u shl 25)
            DteTarget.NRPN -> nrpns[dte_target_value.toUnsigned()].value -= (1u shl 25)
        }
    }
}
