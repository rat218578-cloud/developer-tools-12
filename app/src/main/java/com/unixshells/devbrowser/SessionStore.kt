package com.unixshells.devbrowser

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Persiste a sessao de abas em um arquivo privado do app (filesDir), com escrita
 * atomica (arquivo temporario + rename). Nenhum dado sai do dispositivo.
 *
 * Escritas passam por uma unica thread, na ordem em que foram pedidas, para que
 * um estado antigo nunca sobrescreva um mais recente.
 */
class SessionStore(context: Context) {

    companion object {
        private const val TAG = "SessionStore"
        private const val FILE_NAME = "tab_session.bin"
        private const val TMP_NAME = "tab_session.bin.tmp"
    }

    private val file = File(context.filesDir, FILE_NAME)
    private val tmpFile = File(context.filesDir, TMP_NAME)

    private val io: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "tab-session-io").apply { isDaemon = true }
    }

    /** Leitura sincrona na inicializacao. Retorna null se nao houver sessao valida. */
    fun load(): SavedSession? {
        if (!file.exists()) return null
        return try {
            SessionCodec.decode(file.readBytes())
        } catch (e: IOException) {
            Log.w(TAG, "Could not read saved session", e)
            null
        }
    }

    /** Gravacao em segundo plano, para mudancas frequentes (ex.: fim de pagina). */
    fun saveAsync(session: SavedSession) {
        val bytes = SessionCodec.encode(session)
        io.execute { writeAtomic(bytes) }
    }

    /** Gravacao que espera terminar, para o momento em que o app esta indo para segundo plano. */
    fun saveNow(session: SavedSession) {
        val bytes = SessionCodec.encode(session)
        try {
            io.submit(Runnable { writeAtomic(bytes) }).get()
        } catch (e: Exception) {
            Log.w(TAG, "Could not save session", e)
        }
    }

    private fun writeAtomic(bytes: ByteArray) {
        try {
            FileOutputStream(tmpFile).use { out ->
                out.write(bytes)
                out.fd.sync()
            }
            if (!tmpFile.renameTo(file)) {
                file.delete()
                tmpFile.renameTo(file)
            }
        } catch (e: IOException) {
            Log.w(TAG, "Could not write session file", e)
        }
    }
}
