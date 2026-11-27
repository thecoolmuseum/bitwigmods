package io.github.ada.listeditor;

import bitwig.obf.Hub; // a short MIDI message (status and two data bytes, packed)
import bitwig.obf.P2Z; // the thread that reads incoming MIDI from the audio engine
import bitwig.obf.ohx; // the receivers of one MIDI input port
import bitwig.obf.qPh; // a MIDI message
import bml.api.At;
import bml.api.Hook;

/**
 * Listens to the notes played on a MIDI keyboard, without changing them: the hook hands the message to the
 * list editor, then makes Bitwig's own call unchanged. It runs on Bitwig's MIDI reader thread.
 */
public final class MidiHooks {
    private MidiHooks() {
    }

    /** Every short message from a MIDI input port, as Bitwig's reader hands it to the port's receivers. */
    @Hook(target = P2Z.class, method = "Anf()V", at = At.REPLACE_CALL, callOwner = ohx.class, call = "beI(LqPh;)V",
          occurrence = 1, id = "midi-in")
    public static void midiIn(ohx port, qPh message) {
        try {
            if (message instanceof Hub shortMessage) {
                ListEditorWidget.midi(shortMessage.beI); // the packed bytes
            }
        } catch (Throwable ignored) {
            // listening never stands in the way of the MIDI
        }
        port.beI(message);
    }
}
