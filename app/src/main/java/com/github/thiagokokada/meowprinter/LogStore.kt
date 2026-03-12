package com.github.thiagokokada.meowprinter

object LogStore {
    private val entries = mutableListOf<String>()

    fun append(message: String) {
        entries += message
    }

    fun clear() {
        entries.clear()
    }

    fun asText(): String = entries.joinToString(separator = "\n")
}
