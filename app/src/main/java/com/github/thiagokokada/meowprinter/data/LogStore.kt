package com.github.thiagokokada.meowprinter.data

object LogStore {
    private var entries: List<String> = emptyList()

    fun append(message: String) {
        entries = entries + message
    }

    fun clear() {
        entries = emptyList()
    }

    fun asText(): String = entries.joinToString(separator = "\n")
}
