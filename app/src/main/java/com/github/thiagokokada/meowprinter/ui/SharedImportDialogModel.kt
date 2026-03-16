package com.github.thiagokokada.meowprinter.ui

data class SharedImportDialogAction(
    val label: CharSequence,
    val onSelected: () -> Unit,
)

data class SharedImportDialogModel(
    val titleRes: Int,
    val messageRes: Int,
    val positiveAction: SharedImportDialogAction,
    val negativeAction: SharedImportDialogAction,
)
