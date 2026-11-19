package com.example.data.importer

import com.example.data.auth.OfficialStudyPrograms
import com.example.ui.model.ExcelImportPreviewRow
import com.example.ui.model.ExcelImportPreviewSummary
import com.example.ui.model.ExcelImportValidationStatus
import com.example.ui.model.ExcelRowColorFlag
import com.example.ui.model.ExcelSheetMapping
import com.example.ui.model.StudentRecord
import com.example.ui.model.StudyProgram
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Native OOXML (.xlsx) multi-sheet student & attendance importer with direct cell fill color inspection.
 *
 * Color priority rules (evaluated across all cells in a student row + row style):
 *  1. BLACK fill -> ExcelRowColorFlag.BLACK_SKIP (NEVER imported, NEVER counted in totals)
 *  2. RED fill   -> ExcelRowColorFlag.RED_AFK (imported with StudentBadgeStatus.AFK)
 *  3. Any other fill / white / none -> ignored, processed normally
 */
object ExcelStudentImportParser {

    private data class RgbColor(val r: Int, val g: Int, val b: Int)

    private data class SheetDescriptor(
        val name: String,
        val targetPath: String
    )

    private data class ParsedRawRow(
        val sheetName: String,
        val rowNumber: Int,
        val mapping: ExcelSheetMapping,
        val studentNumber: String,
        val nim: String,
        val fullName: String,
        val colorFlag: ExcelRowColorFlag,
        val weeklyAttendance: Map<Int, String>
    )

    // Standard OOXML 64-entry indexed color palette (indices 0..63)
    private val DEFAULT_INDEXED_COLORS: List<String> = listOf(
        "000000", "FFFFFF", "FF0000", "00FF00", "0000FF", "FFFF00", "FF00FF", "00FFFF",
        "000000", "FFFFFF", "FF0000", "00FF00", "0000FF", "FFFF00", "FF00FF", "00FFFF",
        "800000", "008000", "000080", "808000", "800080", "008080", "C0C0C0", "808080",
        "9999FF", "993366", "FFFFCC", "CCFFFF", "660066", "FF8080", "0066CC", "CCCCFF",
        "000080", "FF00FF", "FFFF00", "00FFFF", "800080", "800000", "008080", "0000FF",
        "00CCFF", "CCFFFF", "CCFFCC", "FFFF99", "99CCFF", "FF99CC", "CC99FF", "FFCC99",
        "3366FF", "33CCCC", "99CC00", "FFCC00", "FF9900", "FF6600", "666699", "969696",
        "003366", "339966", "003300", "333300", "993300", "993366", "333399", "333333"
    )

    private val DEFAULT_THEME_COLORS: List<String> = listOf(
        "FFFFFF", // 0: lt1
        "000000", // 1: dk1
        "E7E6E6", // 2: lt2
        "44546A", // 3: dk2
        "4472C4", // 4: accent1
        "ED7D31", // 5: accent2
        "A5A5A5", // 6: accent3
        "FFC000", // 7: accent4
        "5B9BD5", // 8: accent5
        "70AD47", // 9: accent6
        "0563C1", // 10: hlink
        "954F72"  // 11: folHlink
    )

    fun parseXlsx(
        fileName: String,
        bytes: ByteArray,
        existingStudents: List<StudentRecord>,
        studyPrograms: List<StudyProgram> = OfficialStudyPrograms.list
    ): Result<ExcelImportPreviewSummary> = runCatching {
        require(bytes.isNotEmpty()) { "File Excel kosong." }

        val entries = unzipEntries(bytes)
        val workbookBytes = entries["xl/workbook.xml"]
            ?: error("File bukan format Excel (.xlsx) yang valid: xl/workbook.xml tidak ditemukan.")
        val relsBytes = entries["xl/_rels/workbook.xml.rels"]
            ?: error("File Excel (.xlsx) tidak lengkap: xl/_rels/workbook.xml.rels tidak ditemukan.")

        val sheets = parseWorkbookSheets(workbookBytes, relsBytes)
        require(sheets.isNotEmpty()) { "Tidak ada sheet ditemukan di dalam file Excel." }

        val sharedStrings = entries["xl/sharedStrings.xml"]?.let(::parseSharedStrings).orEmpty()
        val themeColors = entries["xl/theme/theme1.xml"]?.let(::parseThemeColors) ?: DEFAULT_THEME_COLORS
        val styleColorMap = entries["xl/styles.xml"]?.let { parseCellStyleFillColors(it, themeColors) }.orEmpty()

        val sheetMappings = mutableListOf<ExcelSheetMapping>()
        val rawRows = mutableListOf<ParsedRawRow>()

        sheets.forEach { sheet ->
            val mapping = mapSheetNameToClassAndProdi(sheet.name, studyPrograms)
            sheetMappings.add(mapping)
            val sheetBytes = entries[sheet.targetPath] ?: entries["xl/${sheet.targetPath.removePrefix("xl/")}"]
            if (sheetBytes != null) {
                rawRows.addAll(
                    parseWorksheetRows(
                        sheetName = sheet.name,
                        mapping = mapping,
                        sheetXmlBytes = sheetBytes,
                        sharedStrings = sharedStrings,
                        styleColorMap = styleColorMap
                    )
                )
            }
        }

        val validatedRows = validateParsedRows(rawRows, existingStudents)
        ExcelImportPreviewSummary(
            fileName = fileName,
            sheetMappings = sheetMappings,
            rows = validatedRows
        )
    }

    /**
     * Maps Excel sheet names such as "PJKR-3B", "FARMASI-3", "HES-3B" into study program,
     * class name, class label, and inferred semester.
     */
    fun mapSheetNameToClassAndProdi(
        sheetName: String,
        studyPrograms: List<StudyProgram> = OfficialStudyPrograms.list
    ): ExcelSheetMapping {
        val cleanSheet = sheetName.trim()
        val upper = cleanSheet.uppercase(Locale.US)

        val matchedProdi = studyPrograms.firstOrNull { prodi ->
            val codeUpper = prodi.code.uppercase(Locale.US)
            upper == codeUpper ||
                upper.startsWith("$codeUpper-") ||
                upper.startsWith("$codeUpper ") ||
                upper.startsWith("${codeUpper}_") ||
                upper.contains(codeUpper)
        }

        val prodiCode = matchedProdi?.code ?: upper.substringBefore("-").substringBefore(" ").ifBlank { "TIDAK DIKENALI" }
        val suffix = if (matchedProdi != null) {
            val codeUpper = matchedProdi.code.uppercase(Locale.US)
            val idx = upper.indexOf(codeUpper)
            if (idx >= 0) {
                cleanSheet.substring(idx + codeUpper.length).trimStart('-', '_', ' ')
            } else ""
        } else {
            cleanSheet.substringAfter("-", "").trim()
        }

        val classLabel = suffix.ifBlank { cleanSheet }
        val semester = Regex("\\d+").find(classLabel)?.value?.toIntOrNull()?.coerceIn(1, 14) ?: 3

        return ExcelSheetMapping(
            sheetName = cleanSheet,
            studyProgramId = matchedProdi?.id,
            studyProgramCode = prodiCode,
            className = cleanSheet,
            classLabel = classLabel,
            semester = semester,
            isRecognized = matchedProdi != null
        )
    }

    internal fun classifyFillColor(r: Int, g: Int, b: Int): ExcelRowColorFlag {
        // Priority 1: BLACK / near-black solid block
        if (r <= 48 && g <= 48 && b <= 48) {
            return ExcelRowColorFlag.BLACK_SKIP
        }
        // Priority 2: RED fill (AFK marker) - catches pure red #FF0000, dark red #800000/#C00000, crimson, rose-red
        if (r >= 120 && (r - g) >= 55 && (r - b) >= 55 && g <= 135 && b <= 135) {
            return ExcelRowColorFlag.RED_AFK
        }
        // White / transparent-equivalent background
        if (r >= 245 && g >= 245 && b >= 245) {
            return ExcelRowColorFlag.NONE
        }
        // Priority 3: All other colors are explicitly ignored and treated as normal
        return ExcelRowColorFlag.OTHER_IGNORED
    }

    internal fun mergeRowColorPriority(flags: Collection<ExcelRowColorFlag>): ExcelRowColorFlag {
        if (flags.any { it == ExcelRowColorFlag.BLACK_SKIP }) return ExcelRowColorFlag.BLACK_SKIP
        if (flags.any { it == ExcelRowColorFlag.RED_AFK }) return ExcelRowColorFlag.RED_AFK
        if (flags.any { it == ExcelRowColorFlag.OTHER_IGNORED }) return ExcelRowColorFlag.OTHER_IGNORED
        return ExcelRowColorFlag.NONE
    }

    private fun validateParsedRows(
        rawRows: List<ParsedRawRow>,
        existingStudents: List<StudentRecord>
    ): List<ExcelImportPreviewRow> {
        val existingNimSet = existingStudents.map { it.nim.trim().lowercase(Locale.US) }.toSet()

        // Track NIM occurrences ONLY among non-black rows so black-skipped rows never pollute valid rows
        val nonBlackByNim = rawRows
            .filter { it.colorFlag != ExcelRowColorFlag.BLACK_SKIP && it.nim.isNotBlank() }
            .groupBy { it.nim.trim().lowercase(Locale.US) }

        val seenNormalNimInFile = mutableSetOf<String>()

        return rawRows.map { raw ->
            // Rule: Black-highlighted rows MUST NEVER be imported
            if (raw.colorFlag == ExcelRowColorFlag.BLACK_SKIP) {
                return@map ExcelImportPreviewRow(
                    sheetName = raw.sheetName,
                    rowNumber = raw.rowNumber,
                    studentNumber = raw.studentNumber,
                    nim = raw.nim,
                    fullName = raw.fullName,
                    studyProgramId = raw.mapping.studyProgramId,
                    studyProgramCode = raw.mapping.studyProgramCode,
                    className = raw.mapping.className,
                    classLabel = raw.mapping.classLabel,
                    semester = raw.mapping.semester,
                    colorFlag = ExcelRowColorFlag.BLACK_SKIP,
                    validationStatus = ExcelImportValidationStatus.SKIPPED_BLACK,
                    issues = listOf("Dilewati - Blok Hitam (data tidak akan diimpor ke database)"),
                    weeklyAttendance = emptyMap()
                )
            }

            val issues = mutableListOf<String>()
            val cleanNim = raw.nim.trim()
            val cleanName = raw.fullName.trim()
            val nimKey = cleanNim.lowercase(Locale.US)

            if (!raw.mapping.isRecognized) {
                issues.add("Prodi pada sheet '${raw.sheetName}' tidak dikenali (harus PJKR, FARMASI, atau HES).")
            }
            if (cleanNim.isBlank()) {
                issues.add("NIM kosong (Baris ${raw.rowNumber}, Sheet ${raw.sheetName}).")
            } else {
                if (cleanNim.length < 5) {
                    issues.add("Format NIM '$cleanNim' tidak valid (minimal 5 karakter).")
                }
                if (cleanNim.any { it.isWhitespace() }) {
                    issues.add("Format NIM '$cleanNim' mengandung spasi.")
                }
                if (!cleanNim.all { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' }) {
                    issues.add("Format NIM '$cleanNim' mengandung karakter tidak valid.")
                }
            }
            if (cleanName.isBlank()) {
                issues.add("Nama mahasiswa kosong (Baris ${raw.rowNumber}, Sheet ${raw.sheetName}).")
            } else if (cleanName.length < 2) {
                issues.add("Nama mahasiswa '$cleanName' terlalu pendek.")
            }

            // Duplicate NIM detection across all sheets (including 1 NIM used by multiple different names)
            if (cleanNim.isNotBlank()) {
                val sameNimRows = nonBlackByNim[nimKey].orEmpty()
                if (sameNimRows.size > 1) {
                    val distinctNames = sameNimRows
                        .map { it.fullName.trim().uppercase(Locale.US) }
                        .filter { it.isNotBlank() }
                        .distinct()
                    val locations = sameNimRows.joinToString(", ") { "${it.sheetName} baris ${it.rowNumber}" }
                    if (distinctNames.size > 1) {
                        issues.add("NIM '$cleanNim' digunakan oleh lebih dari satu nama (${distinctNames.joinToString(" / ")}) pada: $locations.")
                    } else if (!seenNormalNimInFile.add(nimKey)) {
                        issues.add("NIM '$cleanNim' duplikat dalam file Excel ($locations).")
                    } else {
                        // Also block the first duplicate occurrence so Admin explicitly resolves duplicate NIM rows
                        issues.add("NIM '$cleanNim' duplikat dalam file Excel ($locations).")
                    }
                }
            }

            val status = when {
                issues.isNotEmpty() -> ExcelImportValidationStatus.INVALID_OR_DUPLICATE
                existingNimSet.contains(nimKey) -> {
                    issues.add("Mahasiswa dengan NIM '$cleanNim' sudah memiliki akun (tidak ditimpa).")
                    ExcelImportValidationStatus.ALREADY_EXISTS
                }
                raw.colorFlag == ExcelRowColorFlag.RED_AFK -> ExcelImportValidationStatus.VALID_AFK
                else -> ExcelImportValidationStatus.VALID_NORMAL
            }

            ExcelImportPreviewRow(
                sheetName = raw.sheetName,
                rowNumber = raw.rowNumber,
                studentNumber = raw.studentNumber,
                nim = cleanNim,
                fullName = cleanName,
                studyProgramId = raw.mapping.studyProgramId,
                studyProgramCode = raw.mapping.studyProgramCode,
                className = raw.mapping.className,
                classLabel = raw.mapping.classLabel,
                semester = raw.mapping.semester,
                colorFlag = raw.colorFlag,
                validationStatus = status,
                issues = issues,
                weeklyAttendance = raw.weeklyAttendance
            )
        }
    }

    private fun unzipEntries(bytes: ByteArray): Map<String, ByteArray> {
        val map = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val key = entry.name.trimStart('/')
                    map[key] = zis.readBytes()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return map
    }

    private fun parseXml(bytes: ByteArray): Element {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
        }
        val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
        return doc.documentElement
    }

    private fun parseWorkbookSheets(workbookBytes: ByteArray, relsBytes: ByteArray): List<SheetDescriptor> {
        val relsRoot = parseXml(relsBytes)
        val relMap = mutableMapOf<String, String>()
        val relNodes = relsRoot.getElementsByTagName("Relationship")
        for (i in 0 until relNodes.length) {
            val el = relNodes.item(i) as? Element ?: continue
            val id = el.getAttribute("Id")
            val target = el.getAttribute("Target").trimStart('/')
            if (id.isNotBlank() && target.isNotBlank()) {
                val normalized = if (target.startsWith("xl/")) target else "xl/$target"
                relMap[id] = normalized
            }
        }

        val wbRoot = parseXml(workbookBytes)
        val sheetNodes = wbRoot.getElementsByTagName("sheet")
        val sheets = mutableListOf<SheetDescriptor>()
        for (i in 0 until sheetNodes.length) {
            val el = sheetNodes.item(i) as? Element ?: continue
            val name = el.getAttribute("name")
            val rId = el.getAttribute("r:id").ifBlank { el.getAttribute("id") }
            val target = relMap[rId] ?: continue
            sheets.add(SheetDescriptor(name = name, targetPath = target))
        }
        return sheets
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val root = parseXml(bytes)
        val siNodes = root.getElementsByTagName("si")
        val result = ArrayList<String>(siNodes.length)
        for (i in 0 until siNodes.length) {
            val si = siNodes.item(i) as? Element ?: continue
            val tNodes = si.getElementsByTagName("t")
            val sb = StringBuilder()
            for (j in 0 until tNodes.length) {
                sb.append(tNodes.item(j).textContent ?: "")
            }
            result.add(sb.toString())
        }
        return result
    }

    private fun parseThemeColors(bytes: ByteArray): List<String> {
        val root = parseXml(bytes)
        val clrSchemeList = root.getElementsByTagName("a:clrScheme")
        val schemeEl = (if (clrSchemeList.length > 0) clrSchemeList.item(0) else root.getElementsByTagName("clrScheme").item(0)) as? Element
            ?: return DEFAULT_THEME_COLORS

        val order = listOf("lt1", "dk1", "lt2", "dk2", "accent1", "accent2", "accent3", "accent4", "accent5", "accent6", "hlink", "folHlink")
        return order.mapIndexed { idx, tag ->
            val el = findFirstChildByLocalName(schemeEl, tag)
            extractThemeHex(el) ?: DEFAULT_THEME_COLORS[idx]
        }
    }

    private fun extractThemeHex(container: Element?): String? {
        if (container == null) return null
        val children = container.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            val local = child.tagName.substringAfter(':')
            if (local == "srgbClr") {
                val hex = child.getAttribute("val").trim()
                if (hex.length == 6) return hex.uppercase(Locale.US)
            } else if (local == "sysClr") {
                val lastClr = child.getAttribute("lastClr").trim()
                if (lastClr.length == 6) return lastClr.uppercase(Locale.US)
            }
        }
        return null
    }

    private fun parseCellStyleFillColors(
        stylesBytes: ByteArray,
        themeColors: List<String>
    ): Map<Int, ExcelRowColorFlag> {
        val root = parseXml(stylesBytes)

        // Optional custom indexed colors palette
        val indexedPalette = DEFAULT_INDEXED_COLORS.toMutableList()
        val indexedColorsContainer = findFirstChildByLocalName(
            findFirstChildByLocalName(root, "colors"),
            "indexedColors"
        )
        if (indexedColorsContainer != null) {
            val rgbColorEls = getDirectChildrenByLocalName(indexedColorsContainer, "rgbColor")
            rgbColorEls.forEachIndexed { idx, el ->
                val rgb = el.getAttribute("rgb").trim()
                val hex6 = if (rgb.length == 8) rgb.substring(2) else rgb
                if (hex6.length == 6) {
                    if (idx < indexedPalette.size) indexedPalette[idx] = hex6 else indexedPalette.add(hex6)
                }
            }
        }

        // Parse <fills>
        val fillsContainer = findFirstChildByLocalName(root, "fills")
        val fillFlags = mutableListOf<ExcelRowColorFlag>()
        if (fillsContainer != null) {
            val fillEls = getDirectChildrenByLocalName(fillsContainer, "fill")
            fillEls.forEach { fillEl ->
                val patternFill = findFirstChildByLocalName(fillEl, "patternFill")
                if (patternFill == null) {
                    fillFlags.add(ExcelRowColorFlag.NONE)
                } else {
                    val patternType = patternFill.getAttribute("patternType").lowercase(Locale.US)
                    if (patternType.isBlank() || patternType == "none" || patternType == "gray125") {
                        fillFlags.add(ExcelRowColorFlag.NONE)
                    } else {
                        val fgColorEl = findFirstChildByLocalName(patternFill, "fgColor")
                        val bgColorEl = findFirstChildByLocalName(patternFill, "bgColor")
                        val rgb = resolveExcelColorElement(fgColorEl, themeColors, indexedPalette)
                            ?: resolveExcelColorElement(bgColorEl, themeColors, indexedPalette)
                        val flag = if (rgb != null) {
                            classifyFillColor(rgb.r, rgb.g, rgb.b)
                        } else {
                            ExcelRowColorFlag.NONE
                        }
                        fillFlags.add(flag)
                    }
                }
            }
        }

        // Parse <cellXfs>
        val cellXfs = findFirstChildByLocalName(root, "cellXfs") ?: return emptyMap()
        val xfEls = getDirectChildrenByLocalName(cellXfs, "xf")
        val styleMap = HashMap<Int, ExcelRowColorFlag>(xfEls.size)
        xfEls.forEachIndexed { styleIdx, xf ->
            val fillId = xf.getAttribute("fillId").toIntOrNull() ?: 0
            val flag = fillFlags.getOrElse(fillId) { ExcelRowColorFlag.NONE }
            styleMap[styleIdx] = flag
        }
        return styleMap
    }

    private fun resolveExcelColorElement(
        colorEl: Element?,
        themeColors: List<String>,
        indexedPalette: List<String>
    ): RgbColor? {
        if (colorEl == null) return null
        if (colorEl.getAttribute("auto") == "1" || colorEl.getAttribute("auto").equals("true", ignoreCase = true)) {
            return null
        }

        val rgbAttr = colorEl.getAttribute("rgb").trim()
        if (rgbAttr.length == 6 || rgbAttr.length == 8) {
            val alpha = if (rgbAttr.length == 8) rgbAttr.substring(0, 2).toIntOrNull(16) ?: 255 else 255
            if (alpha == 0 && rgbAttr == "00000000") return null
            val hex6 = if (rgbAttr.length == 8) rgbAttr.substring(2) else rgbAttr
            val base = parseHexRgb(hex6) ?: return null
            val tint = colorEl.getAttribute("tint").toDoubleOrNull()
            return if (tint != null && tint != 0.0) applyTint(base, tint) else base
        }

        val themeAttr = colorEl.getAttribute("theme").toIntOrNull()
        if (themeAttr != null && themeAttr in themeColors.indices) {
            val base = parseHexRgb(themeColors[themeAttr]) ?: return null
            val tint = colorEl.getAttribute("tint").toDoubleOrNull()
            return if (tint != null && tint != 0.0) applyTint(base, tint) else base
        }

        val indexedAttr = colorEl.getAttribute("indexed").toIntOrNull()
        if (indexedAttr != null) {
            if (indexedAttr == 64) {
                // System foreground in a solid pattern fill is black
                return RgbColor(0, 0, 0)
            }
            if (indexedAttr == 65) {
                // System background is white / none
                return null
            }
            if (indexedAttr in indexedPalette.indices) {
                return parseHexRgb(indexedPalette[indexedAttr])
            }
        }
        return null
    }

    private fun parseHexRgb(hex6: String): RgbColor? {
        if (hex6.length != 6) return null
        val r = hex6.substring(0, 2).toIntOrNull(16) ?: return null
        val g = hex6.substring(2, 4).toIntOrNull(16) ?: return null
        val b = hex6.substring(4, 6).toIntOrNull(16) ?: return null
        return RgbColor(r, g, b)
    }

    private fun applyTint(rgb: RgbColor, tint: Double): RgbColor {
        // OOXML tint adjustment in HSL color space
        val rf = rgb.r / 255.0
        val gf = rgb.g / 255.0
        val bf = rgb.b / 255.0
        val maxC = max(rf, max(gf, bf))
        val minC = min(rf, min(gf, bf))
        val delta = maxC - minC

        var l = (maxC + minC) / 2.0
        val s = if (delta == 0.0) 0.0 else delta / (1.0 - abs(2.0 * l - 1.0))
        val h = when {
            delta == 0.0 -> 0.0
            maxC == rf -> 60.0 * (((gf - bf) / delta) % 6.0)
            maxC == gf -> 60.0 * (((bf - rf) / delta) + 2.0)
            else -> 60.0 * (((rf - gf) / delta) + 4.0)
        }.let { if (it < 0.0) it + 360.0 else it }

        l = if (tint < 0.0) {
            l * (1.0 + tint)
        } else {
            l * (1.0 - tint) + tint
        }.coerceIn(0.0, 1.0)

        val c = (1.0 - abs(2.0 * l - 1.0)) * s
        val x = c * (1.0 - abs(((h / 60.0) % 2.0) - 1.0))
        val m = l - c / 2.0

        val (r1, g1, b1) = when {
            h < 60.0 -> Triple(c, x, 0.0)
            h < 120.0 -> Triple(x, c, 0.0)
            h < 180.0 -> Triple(0.0, c, x)
            h < 240.0 -> Triple(0.0, x, c)
            h < 300.0 -> Triple(x, 0.0, c)
            else -> Triple(c, 0.0, x)
        }
        return RgbColor(
            r = ((r1 + m) * 255.0).roundToInt().coerceIn(0, 255),
            g = ((g1 + m) * 255.0).roundToInt().coerceIn(0, 255),
            b = ((b1 + m) * 255.0).roundToInt().coerceIn(0, 255)
        )
    }

    private fun parseWorksheetRows(
        sheetName: String,
        mapping: ExcelSheetMapping,
        sheetXmlBytes: ByteArray,
        sharedStrings: List<String>,
        styleColorMap: Map<Int, ExcelRowColorFlag>
    ): List<ParsedRawRow> {
        val root = parseXml(sheetXmlBytes)
        val sheetData = findFirstChildByLocalName(root, "sheetData") ?: return emptyList()
        val rowElements = getDirectChildrenByLocalName(sheetData, "row")

        data class ExtractedSheetRow(
            val rowNumber: Int,
            val cells: Map<Int, String>,
            val rowColorFlag: ExcelRowColorFlag
        )

        val extractedRows = rowElements.mapIndexed { idx, rowEl ->
            val rowNum = rowEl.getAttribute("r").toIntOrNull() ?: (idx + 1)
            val rowFlags = mutableListOf<ExcelRowColorFlag>()
            rowEl.getAttribute("s").toIntOrNull()?.let { rowStyleIdx ->
                styleColorMap[rowStyleIdx]?.let { rowFlags.add(it) }
            }

            val cellMap = linkedMapOf<Int, String>()
            val cellEls = getDirectChildrenByLocalName(rowEl, "c")
            var fallbackCol = 0
            cellEls.forEach { cellEl ->
                val ref = cellEl.getAttribute("r")
                val colIndex = if (ref.isNotBlank()) columnIndexFromRef(ref) else fallbackCol
                fallbackCol = colIndex + 1

                cellEl.getAttribute("s").toIntOrNull()?.let { sIdx ->
                    styleColorMap[sIdx]?.let { rowFlags.add(it) }
                }

                val value = extractCellStringValue(cellEl, sharedStrings).trim()
                if (value.isNotEmpty()) {
                    cellMap[colIndex] = value
                }
            }
            ExtractedSheetRow(
                rowNumber = rowNum,
                cells = cellMap,
                rowColorFlag = mergeRowColorPriority(rowFlags)
            )
        }

        // Locate header row (first row containing both NIM and Nama, or fallback defaults)
        var headerRowIndex = -1
        var noCol = 0
        var nimCol = 1
        var nameCol = 2
        val weekCols = linkedMapOf<Int, Int>() // colIndex -> weekNumber

        for (i in extractedRows.indices.take(12)) {
            val cells = extractedRows[i].cells
            val foundNimCol = cells.entries.firstOrNull { (_, v) ->
                v.equals("NIM", ignoreCase = true) || v.contains("NIM", ignoreCase = true)
            }?.key
            val foundNameCol = cells.entries.firstOrNull { (_, v) ->
                v.equals("Nama", ignoreCase = true) ||
                    v.contains("Nama", ignoreCase = true) ||
                    v.contains("Mahasiswa", ignoreCase = true)
            }?.key

            if (foundNimCol != null && foundNameCol != null) {
                headerRowIndex = i
                nimCol = foundNimCol
                nameCol = foundNameCol
                noCol = cells.entries.firstOrNull { (c, v) ->
                    c != nimCol && c != nameCol && (v.equals("No", true) || v.equals("No.", true) || v.contains("Nomor", true))
                }?.key ?: (if (nimCol > 0) 0 else -1)

                cells.entries.forEach { (col, headerText) ->
                    if (col != noCol && col != nimCol && col != nameCol) {
                        extractWeekNumber(headerText)?.let { wk ->
                            weekCols[col] = wk
                        }
                    }
                }
                break
            }
        }

        if (weekCols.isEmpty()) {
            // Default Weeks 1..8 columns right after Nama
            for (wk in 1..8) {
                weekCols[nameCol + wk] = wk
            }
        }

        val dataStartIdx = if (headerRowIndex >= 0) headerRowIndex + 1 else 0
        val parsed = mutableListOf<ParsedRawRow>()
        for (i in dataStartIdx until extractedRows.size) {
            val row = extractedRows[i]
            val noText = if (noCol >= 0) row.cells[noCol].orEmpty().trim() else ""
            val nimRaw = normalizeNimString(row.cells[nimCol].orEmpty())
            val nameRaw = row.cells[nameCol].orEmpty().trim()

            // Skip header repetitions across page breaks
            if (nimRaw.equals("NIM", ignoreCase = true) && nameRaw.equals("NAMA", ignoreCase = true)) {
                continue
            }

            val weekly = linkedMapOf<Int, String>()
            weekCols.forEach { (colIdx, wkNum) ->
                val rawStatus = row.cells[colIdx]?.trim().orEmpty()
                if (rawStatus.isNotEmpty()) {
                    // Preserve EXACT original Excel attendance status (HADIR, IZIN, ALPHA, TIDAK HADIR, DISPEN, KIP, etc.)
                    weekly[wkNum] = rawStatus
                }
            }

            // Skip completely empty rows
            if (noText.isBlank() && nimRaw.isBlank() && nameRaw.isBlank() && weekly.isEmpty()) {
                continue
            }

            parsed.add(
                ParsedRawRow(
                    sheetName = sheetName,
                    rowNumber = row.rowNumber,
                    mapping = mapping,
                    studentNumber = noText.trimEnd('.'),
                    nim = nimRaw,
                    fullName = nameRaw,
                    colorFlag = row.rowColorFlag,
                    weeklyAttendance = weekly
                )
            )
        }
        return parsed
    }

    private fun extractWeekNumber(headerText: String): Int? {
        val clean = headerText.trim().lowercase(Locale.US)
        val weekRegex = Regex("(?:minggu|pertemuan|week|m|p)\\s*(?:ke[-\\s]*)?(\\d+)")
        val match = weekRegex.find(clean)
        if (match != null) return match.groupValues[1].toIntOrNull()
        return clean.toIntOrNull()?.takeIf { it in 1..32 }
    }

    private fun normalizeNimString(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        // Handle Excel numeric formatting like "250701046.0" or "2.50701046E8"
        if (Regex("^\\d+\\.0+$").matches(trimmed) || Regex("^\\d+(?:\\.\\d+)?[eE][+-]?\\d+$").matches(trimmed)) {
            return runCatching {
                BigDecimal(trimmed).stripTrailingZeros().toPlainString()
            }.getOrDefault(trimmed)
        }
        return trimmed
    }

    private fun extractCellStringValue(cellEl: Element, sharedStrings: List<String>): String {
        val type = cellEl.getAttribute("t")
        return when (type) {
            "s" -> {
                val idx = findFirstChildByLocalName(cellEl, "v")?.textContent?.trim()?.toIntOrNull()
                if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else ""
            }
            "inlineStr" -> {
                val isEl = findFirstChildByLocalName(cellEl, "is")
                if (isEl != null) {
                    val tNodes = isEl.getElementsByTagName("t")
                    val sb = StringBuilder()
                    for (j in 0 until tNodes.length) sb.append(tNodes.item(j).textContent ?: "")
                    sb.toString()
                } else ""
            }
            else -> {
                findFirstChildByLocalName(cellEl, "v")?.textContent?.trim().orEmpty()
            }
        }
    }

    private fun columnIndexFromRef(cellRef: String): Int {
        var col = 0
        for (ch in cellRef) {
            if (ch.isLetter()) {
                col = col * 26 + (ch.uppercaseChar() - 'A' + 1)
            } else break
        }
        return (col - 1).coerceAtLeast(0)
    }

    private fun findFirstChildByLocalName(parent: Element?, localName: String): Element? {
        if (parent == null) return null
        val nodes = parent.childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                val el = node as Element
                val name = el.tagName.substringAfter(':')
                if (name == localName) return el
            }
        }
        return null
    }

    private fun getDirectChildrenByLocalName(parent: Element?, localName: String): List<Element> {
        if (parent == null) return emptyList()
        val result = mutableListOf<Element>()
        val nodes = parent.childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                val el = node as Element
                val name = el.tagName.substringAfter(':')
                if (name == localName) result.add(el)
            }
        }
        return result
    }

    /**
     * Generates a UTF-8 (with BOM) Excel-compatible CSV export of student data.
     */
    fun buildStudentsCsvExport(students: List<StudentRecord>): String {
        fun esc(value: String): String {
            val escaped = value.replace("\"", "\"\"")
            return if (escaped.contains(',') || escaped.contains('"') || escaped.contains('\n')) "\"$escaped\"" else escaped
        }
        val sb = StringBuilder("\uFEFF")
        sb.appendLine("No,NIM,Nama Lengkap,Program Studi,Kelas,Semester,Angkatan,Status Akun,Status Cap,Wajib Ganti Password,Email")
        students.forEachIndexed { index, stu ->
            sb.appendLine(
                listOf(
                    (index + 1).toString(),
                    esc(stu.nim),
                    esc(stu.fullName),
                    esc(stu.studyProgramName),
                    esc(stu.className ?: "Belum ada kelas"),
                    stu.semester.toString(),
                    stu.cohortYear.toString(),
                    esc(stu.status.label),
                    esc(stu.badgeStatus.label),
                    if (stu.mustChangePassword) "Ya (Password Awal)" else "Tidak",
                    esc(stu.email)
                ).joinToString(",")
            )
        }
        return sb.toString()
    }

    /**
     * Builds a clean 3-sheet .xlsx template (PJKR-3B, FARMASI-3, HES-3B) for Admin download.
     */
    fun buildBlankTemplateXlsx(): ByteArray {
        val headers = listOf(
            "No.", "NIM", "Nama",
            "Minggu ke-1", "Minggu ke-2", "Minggu ke-3", "Minggu ke-4",
            "Minggu ke-5", "Minggu ke-6", "Minggu ke-7", "Minggu ke-8"
        )
        return buildStyledXlsx(
            sheets = listOf(
                XlsxSheetBuildSpec("PJKR-3B", listOf(XlsxRowBuildSpec(headers, fillArgb = null))),
                XlsxSheetBuildSpec("FARMASI-3", listOf(XlsxRowBuildSpec(headers, fillArgb = null))),
                XlsxSheetBuildSpec("HES-3B", listOf(XlsxRowBuildSpec(headers, fillArgb = null)))
            )
        )
    }

    data class XlsxRowBuildSpec(
        val values: List<String>,
        val fillArgb: String? = null,
        val cellFillArgbs: Map<Int, String> = emptyMap()
    )

    data class XlsxSheetBuildSpec(
        val name: String,
        val rows: List<XlsxRowBuildSpec>
    )

    /**
     * Builds a standards-compliant OOXML .xlsx binary with real cell fill colors (RGB ARGB fills in styles.xml).
     * Used both for downloadable Excel templates and deterministic end-to-end automated unit testing.
     */
    fun buildStyledXlsx(sheets: List<XlsxSheetBuildSpec>): ByteArray {
        val uniqueFills = linkedSetOf<String>()
        sheets.forEach { sheet ->
            sheet.rows.forEach { row ->
                row.fillArgb?.let { uniqueFills.add(it.uppercase(Locale.US)) }
                row.cellFillArgbs.values.forEach { uniqueFills.add(it.uppercase(Locale.US)) }
            }
        }
        val fillList = uniqueFills.toList()
        // style index 0 = default (fillId 0 none); fillList[i] -> fillId (i + 2) -> style index (i + 1)
        val styleIndexForArgb = fillList.mapIndexed { idx, argb -> argb to (idx + 1) }.toMap()

        fun xmlEsc(text: String): String = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

        fun colLetters(colIdx: Int): String {
            var n = colIdx + 1
            val sb = StringBuilder()
            while (n > 0) {
                val rem = (n - 1) % 26
                sb.insert(0, ('A' + rem))
                n = (n - 1) / 26
            }
            return sb.toString()
        }

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            fun putEntry(path: String, content: String) {
                zos.putNextEntry(ZipEntry(path))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }

            val contentTypes = buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
                append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
                append("""<Default Extension="xml" ContentType="application/xml"/>""")
                append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
                append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
                sheets.forEachIndexed { idx, _ ->
                    append("""<Override PartName="/xl/worksheets/sheet${idx + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
                }
                append("</Types>")
            }
            putEntry("[Content_Types].xml", contentTypes)

            putEntry(
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                </Relationships>""".trimIndent()
            )

            val workbookXml = buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
                sheets.forEachIndexed { idx, sheet ->
                    append("""<sheet name="${xmlEsc(sheet.name)}" sheetId="${idx + 1}" r:id="rId${idx + 1}"/>""")
                }
                append("</sheets></workbook>")
            }
            putEntry("xl/workbook.xml", workbookXml)

            val workbookRels = buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
                sheets.forEachIndexed { idx, _ ->
                    append("""<Relationship Id="rId${idx + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${idx + 1}.xml"/>""")
                }
                append("""<Relationship Id="rId${sheets.size + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
                append("</Relationships>")
            }
            putEntry("xl/_rels/workbook.xml.rels", workbookRels)

            val stylesXml = buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                append("""<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
                append("""<fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>""")
                append("""<fills count="${fillList.size + 2}">""")
                append("""<fill><patternFill patternType="none"/></fill>""")
                append("""<fill><patternFill patternType="gray125"/></fill>""")
                fillList.forEach { argb ->
                    append("""<fill><patternFill patternType="solid"><fgColor rgb="$argb"/><bgColor indexed="64"/></patternFill></fill>""")
                }
                append("</fills>")
                append("""<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>""")
                append("""<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""")
                append("""<cellXfs count="${fillList.size + 1}">""")
                append("""<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>""")
                fillList.forEachIndexed { idx, _ ->
                    append("""<xf numFmtId="0" fontId="0" fillId="${idx + 2}" borderId="0" xfId="0" applyFill="1"/>""")
                }
                append("</cellXfs></styleSheet>")
            }
            putEntry("xl/styles.xml", stylesXml)

            sheets.forEachIndexed { sheetIdx, sheet ->
                val sheetXml = buildString {
                    append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                    append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
                    sheet.rows.forEachIndexed { rowIdx, row ->
                        val rNum = rowIdx + 1
                        val rowStyleAttr = row.fillArgb?.uppercase(Locale.US)?.let { styleIndexForArgb[it] }?.let { """ s="$it" customFormat="1"""" } ?: ""
                        append("""<row r="$rNum"$rowStyleAttr>""")
                        row.values.forEachIndexed { colIdx, value ->
                            val cellRef = "${colLetters(colIdx)}$rNum"
                            val effectiveArgb = (row.cellFillArgbs[colIdx] ?: row.fillArgb)?.uppercase(Locale.US)
                            val sAttr = effectiveArgb?.let { styleIndexForArgb[it] }?.let { """ s="$it"""" } ?: ""
                            if (value.isNotEmpty() || sAttr.isNotEmpty()) {
                                append("""<c r="$cellRef" t="inlineStr"$sAttr><is><t>${xmlEsc(value)}</t></is></c>""")
                            }
                        }
                        append("</row>")
                    }
                    append("</sheetData></worksheet>")
                }
                putEntry("xl/worksheets/sheet${sheetIdx + 1}.xml", sheetXml)
            }
        }
        return baos.toByteArray()
    }
}
