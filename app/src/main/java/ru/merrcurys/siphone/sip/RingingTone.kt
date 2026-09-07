package ru.merrcurys.siphone.sip

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

// Синтезирует WAV с сигналом входящего звонка: две короткие посылки 425 Гц
// и длинная пауза, как у обычного телефона. PCM 16 бит, моно, 8000 Гц.
object RingingTone {
    private const val FILE_NAME = "ringing.wav"
    private const val SAMPLE_RATE = 8000
    private const val TONE_HZ = 425

    // Цикл рингтона: 0.4с звонок, 0.2с пауза, 0.4с звонок, 2.0с тишина
    private const val FIRST_RING_MS = 400
    private const val SHORT_PAUSE_MS = 200
    private const val SECOND_RING_MS = 400
    private const val SILENCE_MS = 2_000

    fun file(context: Context): File {
        val file = File(context.cacheDir, FILE_NAME)
        if (!file.exists() || file.length() == 0L) {
            writeWav(file)
        }
        return file
    }

    private fun writeWav(file: File) {
        val cycleSamples = SAMPLE_RATE * (FIRST_RING_MS + SHORT_PAUSE_MS + SECOND_RING_MS + SILENCE_MS) / 1_000
        val dataSize = cycleSamples * 2

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
        val firstRingSamples = SAMPLE_RATE * FIRST_RING_MS / 1_000
        val shortPauseSamples = SAMPLE_RATE * SHORT_PAUSE_MS / 1_000
        val secondRingSamples = SAMPLE_RATE * SECOND_RING_MS / 1_000

        for (i in 0 until cycleSamples) {
            val inRing = i < firstRingSamples ||
                (i in firstRingSamples + shortPauseSamples until firstRingSamples + shortPauseSamples + secondRingSamples)
            val sample = if (inRing) {
                // Плавное нарастание/затухание, чтобы не было щелчков
                val phase = (i % period) / period
                val sin = Math.sin(2.0 * Math.PI * phase)
                val envelope = Math.min(1.0, i / (SAMPLE_RATE * 0.01)) *
                    Math.min(1.0, (cycleSamples - i) / (SAMPLE_RATE * 0.01))
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
