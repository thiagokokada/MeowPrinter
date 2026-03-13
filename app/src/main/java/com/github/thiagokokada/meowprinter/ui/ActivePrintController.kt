package com.github.thiagokokada.meowprinter.ui

object ActivePrintController {
    private var cancelHandler: (() -> Unit)? = null

    val isPrintActive: Boolean
        get() = cancelHandler != null

    fun start(cancelHandler: () -> Unit) {
        this.cancelHandler = cancelHandler
    }

    fun finish() {
        cancelHandler = null
    }

    fun cancel() {
        cancelHandler?.invoke()
    }
}
