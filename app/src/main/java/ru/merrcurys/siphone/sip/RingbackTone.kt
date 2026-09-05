package ru.merrcurys.siphone.sip

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

// Синтезирует WAV с сигналом «гудки» (короткий тон + длинная пауза, как при дозвоне)
// и кладет его в cacheDir. PCM 16 бит, моно, 8000 Гц.
object RingbackTone {
    private const val FILE_NAME = "ringback.wav"
    private const val SAMPLE_RATE = 8000
    private const val TONE_HZ = 425
    private const val BEEP_MS = 1_000
    private const val PAUSE_MS = 4_000

    fun file(context: Context): File {
        val file = File(context.cacheDir, FILE_NAME)
        if (!file.exists() || file.length() == 0L) {
            writeWav(file)
        }
        return file
    }

    private fun writeWav(file: File) {
        val beepSamples = SAMPLE_RATE * BEEP_MS / 1_000
        val totalSamples = SAMPLE_RATE * (BEEP_MS + PAUSE_MS) / 1_000
        val dataSize = totalSamples * 2

        val out = ByteArrayOutputStream(44 + dataSize)
        writeAscii(out, "RIFF")
        writeIntLe(out, 36 + dataSize)
        writeAscii(out, "WAVE")
        writeAscii(out, "fmt ")
        writeIntLe(out, 16)
        writeShortLe(out, 1)
        writeShortLe(out, 1)
        writeIntLe(out, SAMPLE_RATE)
        writeIntLe(out, SAMPLE_RATE * 2)
        writeShortLe(out, 2)
        writeShortLe(out, 16)
        writeAscii(out, "data")
        writeIntLe(out, dataSize)

        val period = SAMPLE_RATE.toDouble() / TONE_HZ
        for (i in 0 until totalSamples) {
            val sample = if (i < beepSamples) {
                // Плавное нарастание/затухание, чтобы не было щелчков
                val phase = (i % period) / period
                val sin = Math.sin(2.0 * Math.PI * phase)
                val envelope = Math.min(1.0, i / (SAMPLE_RATE * 0.01)) *
                    Math.min(1.0, (beepSamples - i) / (SAMPLE_RATE * 0.01))
                (sin * 0.35 * Short.MAX_VALUE.toDouble() * envelope).toInt()
            } else {
                0
            }
            writeShortLe(out, sample)
        }

        FileOutputStream(file).use { it.write(out.toByteArray()) }
    }

    private fun writeAscii(out: ByteArrayOutputStream, s: String) {
        out.write(s.toByteArray(Charsets.US_ASCII))
    }

    private fun writeIntLe(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 24) and 0xFF)
    }

    private fun writeShortLe(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
    }
}
