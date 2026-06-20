package com.yunhao.studio.spatial.chart.content

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.WorkbookFactory

object SpreadsheetParser {
    private val supportedExtensions = setOf("csv", "tsv", "xlsx", "xls")

    fun supportedMimeTypes(): Array<String> = arrayOf(
        "text/csv",
        "text/comma-separated-values",
        "text/tab-separated-values",
        "application/csv",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    )

    fun parse(contentResolver: ContentResolver, uri: Uri): SpreadsheetDocument {
        val displayName = queryDisplayName(contentResolver, uri) ?: uri.lastPathSegment ?: "Spreadsheet"
        val extension = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        require(extension in supportedExtensions) {
            "Unsupported file type: .$extension"
        }

        val sheets = when (extension) {
            "csv" -> listOf(SpreadsheetSheet(name = "CSV", rows = parseDelimited(contentResolver, uri, ',')))
            "tsv" -> listOf(SpreadsheetSheet(name = "TSV", rows = parseDelimited(contentResolver, uri, '\t')))
            "xlsx", "xls" -> parseWorkbook(contentResolver, uri)
            else -> error("Unsupported file type: .$extension")
        }

        return SpreadsheetDocument(
            displayName = displayName,
            sourceLabel = displayName,
            sheets = sheets.ifEmpty { listOf(SpreadsheetSheet(name = "Empty", rows = emptyList())) },
        )
    }

    private fun parseWorkbook(contentResolver: ContentResolver, uri: Uri): List<SpreadsheetSheet> {
        val formatter = DataFormatter()
        contentResolver.openInputStream(uri).use { inputStream ->
            requireNotNull(inputStream) { "Unable to read spreadsheet content." }
            WorkbookFactory.create(inputStream).use { workbook ->
                val formulaEvaluator = workbook.creationHelper.createFormulaEvaluator()
                return List(workbook.numberOfSheets) { index ->
                    val sheet = workbook.getSheetAt(index)
                    val lastRow = sheet.lastRowNum
                    val rows = buildList(lastRow + 1) {
                        for (rowIndex in 0..lastRow) {
                            val row = sheet.getRow(rowIndex) ?: continue
                            val lastCell = row.lastCellNum.toInt().coerceAtLeast(0)
                            val values = buildList(lastCell) {
                                for (cellIndex in 0 until lastCell) {
                                    val cell = row.getCell(cellIndex)
                                    add(
                                        when {
                                            cell == null -> ""
                                            cell.cellType == CellType.FORMULA -> formatter.formatCellValue(cell, formulaEvaluator)
                                            else -> formatter.formatCellValue(cell)
                                        }
                                    )
                                }
                            }
                            add(values)
                        }
                    }
                    SpreadsheetSheet(name = sheet.sheetName, rows = rows)
                }
            }
        }
    }

    private fun parseDelimited(contentResolver: ContentResolver, uri: Uri, delimiter: Char): List<List<String>> {
        contentResolver.openInputStream(uri).use { inputStream ->
            requireNotNull(inputStream) { "Unable to read spreadsheet content." }
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                return reader.lineSequence().map { parseDelimitedLine(it, delimiter) }.toList()
            }
        }
    }

    private fun parseDelimitedLine(line: String, delimiter: Char): List<String> {
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && inQuotes && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index += 1
                }
                char == '"' -> inQuotes = !inQuotes
                char == delimiter && !inQuotes -> {
                    cells.add(current.toString().trim())
                    current.clear()
                }
                else -> current.append(char)
            }
            index += 1
        }
        cells.add(current.toString().trim())
        return cells
    }

    private fun queryDisplayName(contentResolver: ContentResolver, uri: Uri): String? {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                return cursor.getString(index)
            }
        }
        return null
    }
}
