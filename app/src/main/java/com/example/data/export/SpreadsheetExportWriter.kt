package com.example.data.export

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Represents a single worksheet/table for Excel (.xlsx) or CSV (.csv) export.
 */
data class ExportSheetData(
    val name: String,
    val headers: List<String>,
    val rows: List<List<ExportCellValue>>
)

sealed interface ExportCellValue {
    data class Text(val value: String) : ExportCellValue
    data class WholeNumber(val value: Long) : ExportCellValue
    data class DecimalNumber(val value: Double) : ExportCellValue

    fun asPlainText(): String = when (this) {
        is Text -> value
        is WholeNumber -> value.toString()
        is DecimalNumber -> {
            if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
        }
    }
}

fun textCell(value: String?): ExportCellValue = ExportCellValue.Text(value?.trim().orEmpty())
fun intCell(value: Int): ExportCellValue = ExportCellValue.WholeNumber(value.toLong())
fun longCell(value: Long): ExportCellValue = ExportCellValue.WholeNumber(value)
fun doubleCell(value: Double): ExportCellValue = ExportCellValue.DecimalNumber(value)

/**
 * Pure Kotlin, zero-dependency, standards-compliant OOXML (.xlsx) & RFC-4180 UTF-8-BOM (.csv) writer.
 *
 * Designed for:
 * - Full compatibility with Microsoft Excel, Google Sheets, LibreOffice Calc, and Apple Numbers.
 * - Multi-sheet Excel workbooks with bold styled headers, auto-sized columns, AutoFilter,
 *   native numeric cells, and clean inline strings (streaming-friendly for large datasets).
 */
object SpreadsheetExportWriter {

    private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    fun writeXlsxBytes(sheets: List<ExportSheetData>): ByteArray {
        require(sheets.isNotEmpty()) { "Minimal satu sheet diperlukan untuk membuat file Excel." }
        val sanitizedSheets = deduplicateSheetNames(sheets)
        val out = ByteArrayOutputStream(32 * 1024)

        ZipOutputStream(out).use { zip ->
            writeZipEntry(zip, "[Content_Types].xml", buildContentTypesXml(sanitizedSheets.size))
            writeZipEntry(zip, "_rels/.rels", buildRootRelsXml())
            writeZipEntry(zip, "xl/workbook.xml", buildWorkbookXml(sanitizedSheets))
            writeZipEntry(zip, "xl/_rels/workbook.xml.rels", buildWorkbookRelsXml(sanitizedSheets.size))
            writeZipEntry(zip, "xl/styles.xml", buildStylesXml())

            sanitizedSheets.forEachIndexed { index, sheet ->
                writeZipEntry(zip, "xl/worksheets/sheet${index + 1}.xml", buildWorksheetXml(sheet))
            }
        }
        return out.toByteArray()
    }

    fun writeCsvBytes(sheets: List<ExportSheetData>): ByteArray {
        require(sheets.isNotEmpty()) { "Minimal satu tabel diperlukan untuk membuat file CSV." }
        val out = ByteArrayOutputStream(16 * 1024)
        out.write(UTF8_BOM)
        val sb = StringBuilder(8192)
        val multiSheet = sheets.size > 1

        sheets.forEachIndexed { idx, sheet ->
            if (idx > 0) sb.append("\r\n")
            if (multiSheet) {
                sb.append(escapeCsvCell("=== ${sheet.name.uppercase()} ===")).append("\r\n")
            }
            sb.append(sheet.headers.joinToString(",") { escapeCsvCell(it) }).append("\r\n")
            sheet.rows.forEach { row ->
                sb.append(row.joinToString(",") { cell -> escapeCsvCell(cell.asPlainText()) }).append("\r\n")
            }
        }
        out.write(sb.toString().toByteArray(StandardCharsets.UTF_8))
        return out.toByteArray()
    }

    private fun escapeCsvCell(raw: String): String {
        val clean = raw.replace("\r\n", "\n").replace("\r", "\n")
        val needsQuotes = clean.contains(',') || clean.contains('"') || clean.contains('\n')
        return if (needsQuotes) {
            "\"${clean.replace("\"", "\"\"")}\""
        } else {
            clean
        }
    }

    private fun writeZipEntry(zip: ZipOutputStream, entryName: String, content: String) {
        zip.putNextEntry(ZipEntry(entryName))
        zip.write(content.toByteArray(StandardCharsets.UTF_8))
        zip.closeEntry()
    }

    private fun deduplicateSheetNames(sheets: List<ExportSheetData>): List<ExportSheetData> {
        val used = mutableSetOf<String>()
        return sheets.mapIndexed { idx, sheet ->
            val base = sheet.name
                .replace(Regex("""[\[\]:*?/\\]"""), " ")
                .trim()
                .ifBlank { "Sheet${idx + 1}" }
                .take(31)
            var candidate = base
            var counter = 2
            while (!used.add(candidate.lowercase())) {
                val suffix = " $counter"
                candidate = base.take(31 - suffix.length) + suffix
                counter++
            }
            sheet.copy(name = candidate)
        }
    }

    private fun buildContentTypesXml(sheetCount: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        for (i in 1..sheetCount) {
            append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        append("""</Types>""")
    }

    private fun buildRootRelsXml(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""" +
            """</Relationships>"""

    private fun buildWorkbookXml(sheets: List<ExportSheetData>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        append("""<sheets>""")
        sheets.forEachIndexed { idx, sheet ->
            val id = idx + 1
            append("""<sheet name="${escapeXml(sheet.name)}" sheetId="$id" r:id="rId$id"/>""")
        }
        append("""</sheets>""")
        append("""</workbook>""")
    }

    private fun buildWorkbookRelsXml(sheetCount: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..sheetCount) {
            append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>""")
        }
        append("""<Relationship Id="rId${sheetCount + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        append("""</Relationships>""")
    }

    /**
     * Stylesheet:
     * - Style 0: default cell
     * - Style 1: Header row (Bold, Lime #B5D45D text on Dark #171A17 fill, thin border, centered vertically)
     * - Style 2: Body text cell (wrap text, clean thin border)
     * - Style 3: Body zebra row cell (subtle Soft Mint #F4FAEA fill, thin border)
     * - Style 4: Body numeric cell (right-aligned, thin border)
     * - Style 5: Body zebra numeric cell (Soft Mint fill, right-aligned, thin border)
     */
    private fun buildStylesXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="2">
    <font><sz val="11"/><color rgb="FF20251D"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FFB5D45D"/><name val="Calibri"/></font>
  </fonts>
  <fills count="4">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF171A17"/><bgColor indexed="64"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFF4FAEA"/><bgColor indexed="64"/></patternFill></fill>
  </fills>
  <borders count="2">
    <border><left/><right/><top/><bottom/><diagonal/></border>
    <border>
      <left style="thin"><color rgb="FFD2DEC0"/></left>
      <right style="thin"><color rgb="FFD2DEC0"/></right>
      <top style="thin"><color rgb="FFD2DEC0"/></top>
      <bottom style="thin"><color rgb="FFD2DEC0"/></bottom>
      <diagonal/>
    </border>
  </borders>
  <cellStyleXfs count="1">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
  </cellStyleXfs>
  <cellXfs count="6">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="left" vertical="center" wrapText="1"/></xf>
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment horizontal="left" vertical="top" wrapText="1"/></xf>
    <xf numFmtId="0" fontId="0" fillId="3" borderId="1" xfId="0" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="left" vertical="top" wrapText="1"/></xf>
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment horizontal="right" vertical="top"/></xf>
    <xf numFmtId="0" fontId="0" fillId="3" borderId="1" xfId="0" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="right" vertical="top"/></xf>
  </cellXfs>
</styleSheet>"""

    private fun buildWorksheetXml(sheet: ExportSheetData): String {
        val colCount = sheet.headers.size.coerceAtLeast(1)
        val colWidths = IntArray(colCount) { idx ->
            sheet.headers.getOrNull(idx)?.length?.coerceIn(10, 42) ?: 14
        }
        // Sample up to first 500 rows for responsive auto-fit widths even on huge datasets
        val sampleLimit = sheet.rows.size.coerceAtMost(500)
        for (r in 0 until sampleLimit) {
            val row = sheet.rows[r]
            for (c in 0 until minOf(colCount, row.size)) {
                val len = row[c].asPlainText().length
                if (len > colWidths[c]) {
                    colWidths[c] = len.coerceAtMost(48)
                }
            }
        }

        val lastColLetter = columnIndexToLetter(colCount - 1)
        val totalRows = sheet.rows.size + 1

        return buildString(16384) {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
            append("""<dimension ref="A1:$lastColLetter$totalRows"/>""")
            append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
            append("""<cols>""")
            for (c in 0 until colCount) {
                val width = (colWidths[c] + 4).coerceIn(10, 52)
                append("""<col min="${c + 1}" max="${c + 1}" width="$width" customWidth="1"/>""")
            }
            append("""</cols>""")
            append("""<sheetData>""")

            // Row 1: Header
            append("""<row r="1" ht="24" customHeight="1">""")
            sheet.headers.forEachIndexed { colIdx, header ->
                val ref = "${columnIndexToLetter(colIdx)}1"
                append("""<c r="$ref" s="1" t="inlineStr"><is><t xml:space="preserve">${escapeXml(header)}</t></is></c>""")
            }
            append("""</row>""")

            // Data rows
            sheet.rows.forEachIndexed { rowIdx, row ->
                val excelRow = rowIdx + 2
                val isZebra = rowIdx % 2 == 1
                append("""<row r="$excelRow">""")
                row.forEachIndexed { colIdx, cell ->
                    val ref = "${columnIndexToLetter(colIdx)}$excelRow"
                    when (cell) {
                        is ExportCellValue.WholeNumber -> {
                            val style = if (isZebra) 5 else 4
                            append("""<c r="$ref" s="$style"><v>${cell.value}</v></c>""")
                        }
                        is ExportCellValue.DecimalNumber -> {
                            val style = if (isZebra) 5 else 4
                            append("""<c r="$ref" s="$style"><v>${cell.value}</v></c>""")
                        }
                        is ExportCellValue.Text -> {
                            val style = if (isZebra) 3 else 2
                            val safe = escapeXml(cell.value)
                            append("""<c r="$ref" s="$style" t="inlineStr"><is><t xml:space="preserve">$safe</t></is></c>""")
                        }
                    }
                }
                append("""</row>""")
            }

            append("""</sheetData>""")
            append("""<autoFilter ref="A1:$lastColLetter$totalRows"/>""")
            append("""</worksheet>""")
        }
    }

    fun columnIndexToLetter(zeroBasedIndex: Int): String {
        var n = zeroBasedIndex + 1
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.append(('A'.code + rem).toChar())
            n = (n - 1) / 26
        }
        return sb.reverse().toString()
    }

    private fun escapeXml(input: String): String {
        if (input.isEmpty()) return ""
        val sb = StringBuilder(input.length + 16)
        for (ch in input) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> {
                    val code = ch.code
                    // Strip invalid XML 1.0 control characters while preserving tab, LF, CR
                    if (code == 0x9 || code == 0xA || code == 0xD || code >= 0x20) {
                        sb.append(ch)
                    }
                }
            }
        }
        return sb.toString()
    }
}
