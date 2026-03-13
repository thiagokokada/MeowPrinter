package com.github.thiagokokada.meowprinter.document

object TableBlockTextCodec {
    fun encode(cells: List<List<String>>): String {
        return cells.joinToString(separator = "\n") { row ->
            row.joinToString(separator = " | ")
        }
    }

    fun decode(input: String, rows: Int, columns: Int): List<List<String>> {
        val normalizedRows = input
            .lines()
            .map { line -> line.split("|").map(String::trim) }

        return List(rows) { rowIndex ->
            List(columns) { columnIndex ->
                normalizedRows.getOrNull(rowIndex)?.getOrNull(columnIndex).orEmpty()
            }
        }
    }
}
