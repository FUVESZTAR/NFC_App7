package com.plantnfc.presentation.common

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.Ndef
import java.nio.charset.Charset

object NfcBridge {

    data class PendingWrite(
        val text: String,
        val link: String,
        val onSerial: (String) -> Unit,
        val onSuccess: () -> Unit,
        val onError: (String) -> Unit,
        val onDone: () -> Unit,
    )

    @Volatile var pendingWrite: PendingWrite? = null
    @Volatile private var readCb: ((String, List<String>, List<String>) -> Unit)? = null

    fun enqueueWrite(
        text: String, link: String,
        onSerial: (String) -> Unit,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onDone: () -> Unit,
    ) { pendingWrite = PendingWrite(text, link, onSerial, onSuccess, onError, onDone) }

    fun setReadCallback(cb: (String, List<String>, List<String>) -> Unit) { readCb = cb }

    fun handleTag(tag: Tag) {
        val serial = tag.id.joinToString(":") { "%02X".format(it) }
        val pw = pendingWrite
        if (pw != null) {
            pendingWrite = null
            pw.onSerial(serial)
            try {
                val ndef = Ndef.get(tag) ?: run { pw.onError("Tag not NDEF capable"); pw.onDone(); return }
                ndef.connect()
                val textRec = NdefRecord.createTextRecord("en", pw.text)
                val uriRec  = NdefRecord.createUri(android.net.Uri.parse(pw.link))
                ndef.writeNdefMessage(NdefMessage(arrayOf(textRec, uriRec)))
                ndef.close()
                pw.onSuccess()
            } catch (e: Exception) {
                pw.onError(e.message ?: "Write failed")
            } finally {
                pw.onDone()
            }
        } else {
            val ndef = Ndef.get(tag) ?: return
            try {
                ndef.connect()
                val msg = ndef.ndefMessage ?: run { ndef.close(); return }
                ndef.close()
                val texts = mutableListOf<String>()
                val urls  = mutableListOf<String>()
                msg.records.forEach { r ->
                    when {
                        r.tnf == NdefRecord.TNF_WELL_KNOWN &&
                        r.type.contentEquals(NdefRecord.RTD_TEXT) -> {
                            val p = r.payload
                            val langLen = p[0].toInt() and 0x3F
                            texts += String(p, 1 + langLen, p.size - 1 - langLen, Charset.forName("UTF-8"))
                        }
                        r.tnf == NdefRecord.TNF_WELL_KNOWN &&
                        r.type.contentEquals(NdefRecord.RTD_URI) -> {
                            val prefixes = mapOf(0x03 to "http://", 0x04 to "https://")
                            val prefix = prefixes[r.payload[0].toInt() and 0xFF] ?: ""
                            urls += prefix + String(r.payload, 1, r.payload.size - 1, Charsets.UTF_8)
                        }
                    }
                }
                readCb?.invoke(serial, texts, urls)
            } catch (_: Exception) { runCatching { ndef.close() } }
        }
    }
}
