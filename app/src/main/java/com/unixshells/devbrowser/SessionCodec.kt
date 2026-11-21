package com.unixshells.devbrowser

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/** Estado de uma aba salvo em disco. */
data class SavedTab(
    val id: Int,
    val url: String,
    val title: String,
    /** Estado opaco do WebView (WebView.saveState serializado). Pode ser null. */
    val webViewState: ByteArray?
)

/** Estado completo da sessao de abas. */
data class SavedSession(
    val selectedTabId: Int,
    val nextId: Int,
    val desktopMode: Boolean,
    val tabs: List<SavedTab>
)

/**
 * Serializacao binaria simples da sessao. Pura Kotlin/JVM, sem dependencias
 * do Android, para ser testavel em testes unitarios.
 *
 * Formato (big-endian, java.io.DataOutputStream):
 *   int MAGIC, int VERSION, int selectedTabId, int nextId, boolean desktopMode,
 *   int tabCount, por aba: int id, UTF url, UTF title, int stateLength (-1 = sem estado), bytes state
 */
object SessionCodec {
    const val MAGIC = 0x44425353 // "DBSS"
    const val VERSION = 1
    const val MAX_TABS = 200
    const val MAX_STATE_BYTES = 16 * 1024 * 1024
    /** writeUTF aceita no maximo 65535 bytes; 16000 caracteres cabem com folga. */
    private const val MAX_TEXT_CHARS = 16000

    fun encode(session: SavedSession): ByteArray {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(VERSION)
            out.writeInt(session.selectedTabId)
            out.writeInt(session.nextId)
            out.writeBoolean(session.desktopMode)
            out.writeInt(session.tabs.size)
            for (tab in session.tabs) {
                out.writeInt(tab.id)
                out.writeUTF(clip(tab.url))
                out.writeUTF(clip(tab.title))
                val state = tab.webViewState
                if (state == null) {
                    out.writeInt(-1)
                } else {
                    out.writeInt(state.size)
                    out.write(state)
                }
            }
        }
        return buffer.toByteArray()
    }

    /** Retorna null se os dados forem invalidos, corrompidos ou de outra versao. */
    fun decode(data: ByteArray): SavedSession? {
        return try {
            DataInputStream(ByteArrayInputStream(data)).use { input ->
                if (input.readInt() != MAGIC) return null
                if (input.readInt() != VERSION) return null
                val selected = input.readInt()
                val nextId = input.readInt()
                val desktop = input.readBoolean()
                val count = input.readInt()
                if (count < 0 || count > MAX_TABS) return null

                val tabs = ArrayList<SavedTab>(count)
                repeat(count) {
                    val id = input.readInt()
                    val url = input.readUTF()
                    val title = input.readUTF()
                    val length = input.readInt()
                    if (length > MAX_STATE_BYTES) return null
                    val state = if (length < 0) {
                        null
                    } else {
                        ByteArray(length).also { input.readFully(it) }
                    }
                    tabs.add(SavedTab(id, url, title, state))
                }
                SavedSession(selected, nextId, desktop, tabs)
            }
        } catch (e: IOException) {
            null
        }
    }

    private fun clip(text: String): String =
        if (text.length > MAX_TEXT_CHARS) text.substring(0, MAX_TEXT_CHARS) else text
}
