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
import java.io.File
import java.io.FileInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.math.BigDecimal
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.SAXParserFactory
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

/**
 * Native OOXML (.xlsx) multi-sheet student & attendance importer with direct cell fill color inspection.
 *
 * Uses constant-memory SAX streaming XML parsing for sharedStrings and worksheets plus selective
 * ZIP extraction with strict file-size and zip-bomb limits so Android devices never crash with OOM.
 *
 * Color priority rules (evaluated across all cells in a student row + row style):
 *  1. BLACK fill -> ExcelRowColorFlag.BLACK_SKIP (NEVER imported, NEVER counted in totals)
 *  2. RED fill   -> ExcelRowColorFlag.RED_AFK (imported with StudentBadgeStatus.AFK)
 *  3. Any other fill / white / none -> ignored, processed normally
 */
object ExcelStudentImportParser {

    const val MAX_XLSX_FILE_BYTES: Long = 15L * 1024L * 1024L
    private const val MAX_ZIP_ENTRIES: Int = 300
    private const val MAX_SINGLE_XML_ENTRY_BYTES: Int = 20 * 1024 * 1024
    private const val MAX_TOTAL_UNCOMPRESSED_BYTES: Long = 45L * 1024L * 1024L
    private const val MAX_PARSED_NON_EMPTY_ROWS_PER_SHEET: Int = 5_000
    private const val MAX_SHARED_STRINGS: Int = 100_000

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

    /**
     * Memory-safe disk-streaming `.xlsx` parser designed for Android devices.
     * Uses `ZipFile` to read ONE XML entry stream at a time directly from disk into SAX parsers,
     * never holding the entire `.xlsx` byte array or all decompressed XML sheets in heap memory.
     */
    fun parseXlsxFile(
        fileName: String,
        file: File,
        existingStudents: List<StudentRecord>,
        studyPrograms: List<StudyProgram> = OfficialStudyPrograms.list,
        onProgress: ((progress: Float, statusText: String) -> Unit)? = null
    ): Result<ExcelImportPreviewSummary> {
        return try {
            if (!file.exists() || !file.isFile) {
                return Result.failure(IllegalArgumentException("File sementara Excel tidak ditemukan di penyimpanan perangkat."))
            }
            val fileLength = file.length()
            if (fileLength <= 0L) {
                return Result.failure(IllegalArgumentException("File Excel kosong."))
            }
            if (fileLength > MAX_XLSX_FILE_BYTES) {
                return Result.failure(
                    IllegalArgumentException(
                        "Ukuran file terlalu besar (${(fileLength / (1024L * 1024L)).coerceAtLeast(1L)} MB). Maksimal ukuran file Excel yang didukung adalah 15 MB."
                    )
                )
            }
            val magic = ByteArray(4)
            val magicRead = FileInputStream(file).use { it.read(magic) }
            validateXlsxMagicBytes(if (magicRead >= 4) magic else ByteArray(0))?.let { validationError ->
                return Result.failure(IllegalArgumentException(validationError))
            }

            onProgress?.invoke(0.10f, "Membuka struktur file Excel (.xlsx)...")

            ZipFile(file).use { zipFile ->
                val entryMap = linkedMapOf<String, ZipEntry>()
                var entryCount = 0
                val entriesEnum = zipFile.entries()
                while (entriesEnum.hasMoreElements()) {
                    val entry = entriesEnum.nextElement()
                    entryCount++
                    if (entryCount > MAX_ZIP_ENTRIES) {
                        return Result.failure(
                            IllegalArgumentException("File Excel memiliki terlalu banyak komponen internal (>$MAX_ZIP_ENTRIES).")
                        )
                    }
                    if (!entry.isDirectory) {
                        val normalizedKey = normalizeZipPath(entry.name)
                        if (isRequiredXmlEntry(normalizedKey)) {
                            entryMap[normalizedKey] = entry
                        }
                    }
                }

                val totalReadCounter = longArrayOf(0L)
                fun openBoundedStream(entry: ZipEntry): InputStream {
                    return BoundedZipEntryInputStream(
                        delegate = zipFile.getInputStream(entry),
                        maxEntryBytes = MAX_SINGLE_XML_ENTRY_BYTES.toLong(),
                        totalCounter = totalReadCounter,
                        maxTotalBytes = MAX_TOTAL_UNCOMPRESSED_BYTES
                    )
                }

                fun readSmallEntryBytes(key: String): ByteArray? {
                    val entry = entryMap[key] ?: return null
                    return openBoundedStream(entry).use { it.readBytes() }
                }

                val workbookBytes = readSmallEntryBytes("xl/workbook.xml")
                    ?: return Result.failure(
                        IllegalArgumentException("File bukan format Excel (.xlsx) yang valid: struktur xl/workbook.xml tidak ditemukan.")
                    )
                val relsBytes = readSmallEntryBytes("xl/_rels/workbook.xml.rels")
                    ?: return Result.failure(
                        IllegalArgumentException("File Excel (.xlsx) tidak lengkap: xl/_rels/workbook.xml.rels tidak ditemukan.")
                    )

                val sheets = parseWorkbookSheets(workbookBytes, relsBytes)
                require(sheets.isNotEmpty()) { "Tidak ada sheet ditemukan di dalam file Excel." }

                onProgress?.invoke(0.22f, "Membaca tabel teks (sharedStrings)...")
                val sharedStrings = entryMap["xl/sharedstrings.xml"]?.let { entry ->
                    openBoundedStream(entry).use(::parseSharedStringsStreaming)
                }.orEmpty()

                val themeColors = readSmallEntryBytes("xl/theme/theme1.xml")?.let(::parseThemeColors)
                    ?: DEFAULT_THEME_COLORS

                onProgress?.invoke(0.32f, "Menganalisis warna sel & gaya (AFK / Blok Hitam)...")
                val styleColorMap = entryMap["xl/styles.xml"]?.let { entry ->
                    openBoundedStream(entry).use { stream ->
                        parseCellStyleFillColorsStreaming(stream, themeColors)
                    }
                }.orEmpty()

                val sheetMappings = mutableListOf<ExcelSheetMapping>()
                val rawRows = mutableListOf<ParsedRawRow>()
                val totalSheets = sheets.size.coerceAtLeast(1)

                sheets.forEachIndexed { idx, sheet ->
                    val sheetProgress = 0.35f + (0.53f * (idx.toFloat() / totalSheets.toFloat()))
                    onProgress?.invoke(
                        sheetProgress,
                        "Membaca sheet ${sheet.name} (${idx + 1}/${sheets.size})..."
                    )
                    val mapping = mapSheetNameToClassAndProdi(sheet.name, studyPrograms)
                    sheetMappings.add(mapping)
                    val normalizedTarget = normalizeZipPath(sheet.targetPath)
                    val sheetEntry = entryMap[normalizedTarget]
                        ?: entryMap["xl/${normalizedTarget.removePrefix("xl/")}"]
                    if (sheetEntry != null) {
                        openBoundedStream(sheetEntry).use { sheetStream ->
                            rawRows.addAll(
                                parseWorksheetRowsStreaming(
                                    sheetName = sheet.name,
                                    mapping = mapping,
                                    sheetXmlStream = sheetStream,
                                    sharedStrings = sharedStrings,
                                    styleColorMap = styleColorMap
                                )
                            )
                        }
                    }
                }

                onProgress?.invoke(0.94f, "Memvalidasi NIM, prodi, warna AFK, dan duplikat...")
                val validatedRows = validateParsedRows(rawRows, existingStudents)
                onProgress?.invoke(1.0f, "Validasi selesai (${validatedRows.size} baris diperiksa).")
                Result.success(
                    ExcelImportPreviewSummary(
                        fileName = fileName,
                        sheetMappings = sheetMappings,
                        rows = validatedRows
                    )
                )
            }
        } catch (_: OutOfMemoryError) {
            Result.failure(
                IllegalStateException(
                    "File Excel terlalu besar atau kompleks untuk memori perangkat. Pisahkan file per kelas atau hapus gambar/format kosong lalu coba lagi."
                )
            )
        } catch (_: ZipException) {
            Result.failure(
                IllegalArgumentException(
                    "File Excel (.xlsx) rusak atau terenkripsi password. Buka di Excel/Google Sheets lalu simpan ulang sebagai .xlsx standar tanpa password."
                )
            )
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        } catch (e: IllegalStateException) {
            Result.failure(e)
        } catch (t: Throwable) {
            Result.failure(
                IllegalStateException(
                    "Gagal memproses file Excel (.xlsx): ${t.message?.take(140) ?: "format XML tidak dikenali"}."
                )
            )
        }
    }

    fun parseXlsx(
        fileName: String,
        bytes: ByteArray,
        existingStudents: List<StudentRecord>,
        studyPrograms: List<StudyProgram> = OfficialStudyPrograms.list,
        onProgress: ((progress: Float, statusText: String) -> Unit)? = null
    ): Result<ExcelImportPreviewSummary> {
        return try {
            require(bytes.isNotEmpty()) { "File Excel kosong." }
            if (bytes.size.toLong() > MAX_XLSX_FILE_BYTES) {
                return Result.failure(
                    IllegalArgumentException(
                        "Ukuran file terlalu besar (${bytes.size / (1024 * 1024)} MB). Maksimal ukuran file Excel yang didukung adalah 15 MB."
                    )
                )
            }
            validateXlsxMagicBytes(bytes)?.let { validationError ->
                return Result.failure(IllegalArgumentException(validationError))
            }

            onProgress?.invoke(0.12f, "Membuka struktur file Excel (.xlsx)...")
            val entries = unzipRequiredXmlEntries(bytes)
            val workbookBytes = entries["xl/workbook.xml"]
                ?: return Result.failure(
                    IllegalArgumentException("File bukan format Excel (.xlsx) yang valid: struktur xl/workbook.xml tidak ditemukan.")
                )
            val relsBytes = entries["xl/_rels/workbook.xml.rels"]
                ?: return Result.failure(
                    IllegalArgumentException("File Excel (.xlsx) tidak lengkap: xl/_rels/workbook.xml.rels tidak ditemukan.")
                )

            val sheets = parseWorkbookSheets(workbookBytes, relsBytes)
            require(sheets.isNotEmpty()) { "Tidak ada sheet ditemukan di dalam file Excel." }

            onProgress?.invoke(0.25f, "Membaca tabel teks (sharedStrings)...")
            val sharedStrings = entries["xl/sharedstrings.xml"]?.let {
                ByteArrayInputStream(it).use(::parseSharedStringsStreaming)
            }.orEmpty()
            val themeColors = entries["xl/theme/theme1.xml"]?.let(::parseThemeColors) ?: DEFAULT_THEME_COLORS
            onProgress?.invoke(0.35f, "Menganalisis warna sel & gaya (AFK / Blok Hitam)...")
            val styleColorMap = entries["xl/styles.xml"]?.let {
                ByteArrayInputStream(it).use { stream -> parseCellStyleFillColorsStreaming(stream, themeColors) }
            }.orEmpty()

            val sheetMappings = mutableListOf<ExcelSheetMapping>()
            val rawRows = mutableListOf<ParsedRawRow>()
            val totalSheets = sheets.size.coerceAtLeast(1)

            sheets.forEachIndexed { idx, sheet ->
                val sheetProgress = 0.38f + (0.50f * (idx.toFloat() / totalSheets.toFloat()))
                onProgress?.invoke(sheetProgress, "Membaca sheet ${sheet.name} (${idx + 1}/${sheets.size})...")
                val mapping = mapSheetNameToClassAndProdi(sheet.name, studyPrograms)
                sheetMappings.add(mapping)
                val normalizedTarget = normalizeZipPath(sheet.targetPath)
                val sheetBytes = entries[normalizedTarget]
                    ?: entries["xl/${normalizedTarget.removePrefix("xl/")}"]
                if (sheetBytes != null) {
                    ByteArrayInputStream(sheetBytes).use { stream ->
                        rawRows.addAll(
                            parseWorksheetRowsStreaming(
                                sheetName = sheet.name,
                                mapping = mapping,
                                sheetXmlStream = stream,
                                sharedStrings = sharedStrings,
                                styleColorMap = styleColorMap
                            )
                        )
                    }
                }
            }

            onProgress?.invoke(0.94f, "Memvalidasi NIM, prodi, dan deteksi duplikat...")
            val validatedRows = validateParsedRows(rawRows, existingStudents)
            onProgress?.invoke(1.0f, "Validasi selesai (${validatedRows.size} baris diperiksa).")
            Result.success(
                ExcelImportPreviewSummary(
                    fileName = fileName,
                    sheetMappings = sheetMappings,
                    rows = validatedRows
                )
            )
        } catch (_: OutOfMemoryError) {
            Result.failure(
                IllegalStateException(
                    "File Excel terlalu besar atau kompleks untuk memori perangkat. Pisahkan file per kelas atau hapus gambar/format kosong lalu coba lagi."
                )
            )
        } catch (_: ZipException) {
            Result.failure(
                IllegalArgumentException(
                    "File Excel (.xlsx) rusak atau terenkripsi password. Buka di Excel/Google Sheets lalu simpan ulang sebagai .xlsx standar tanpa password."
                )
            )
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        } catch (e: IllegalStateException) {
            Result.failure(e)
        } catch (t: Throwable) {
            Result.failure(
                IllegalStateException(
                    "Gagal memproses file Excel (.xlsx): ${t.message?.take(140) ?: "format XML tidak dikenali"}."
                )
            )
        }
    }

    private class BoundedZipEntryInputStream(
        delegate: InputStream,
        private val maxEntryBytes: Long,
        private val totalCounter: LongArray,
        private val maxTotalBytes: Long
    ) : FilterInputStream(delegate) {
        private var entryRead = 0L

        override fun read(): Int {
            val b = super.read()
            if (b >= 0) account(1)
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = super.read(b, off, len)
            if (n > 0) account(n.toLong())
            return n
        }

        private fun account(delta: Long) {
            entryRead += delta
            totalCounter[0] += delta
            if (entryRead > maxEntryBytes || totalCounter[0] > maxTotalBytes) {
                throw IllegalArgumentException(
                    "Isi file Excel melebihi batas memori aman perangkat. Hapus baris kosong atau gambar di dalam Excel lalu coba lagi."
                )
            }
        }
    }

    private fun validateXlsxMagicBytes(bytes: ByteArray): String? {
        if (bytes.size < 4) {
            return "File terlalu kecil dan bukan dokumen Excel (.xlsx) yang valid."
        }
        val b0 = bytes[0].toInt() and 0xFF
        val b1 = bytes[1].toInt() and 0xFF
        val b2 = bytes[2].toInt() and 0xFF
        val b3 = bytes[3].toInt() and 0xFF
        // Legacy binary Excel (.xls) OLE2 Compound File header: D0 CF 11 E0
        if (b0 == 0xD0 && b1 == 0xCF && b2 == 0x11 && b3 == 0xE0) {
            return "Format Excel lama (.xls) atau file terproteksi password tidak didukung. Simpan ulang file sebagai Excel Workbook (.xlsx) tanpa password."
        }
        // Standard OOXML ZIP signature: PK\x03\x04 or PK\x05\x06
        if (b0 != 0x50 || b1 != 0x4B) {
            return "File yang dipilih bukan file Excel (.xlsx) yang valid. Pastikan file berformat .xlsx."
        }
        return null
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

    private fun normalizeZipPath(rawPath: String): String {
        val slashFixed = rawPath.replace('\\', '/').trimStart('/')
        val segments = mutableListOf<String>()
        slashFixed.split('/').forEach { part ->
            when {
                part.isEmpty() || part == "." -> Unit
                part == ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.lastIndex)
                else -> segments.add(part)
            }
        }
        return segments.joinToString("/").lowercase(Locale.US)
    }

    private fun isRequiredXmlEntry(normalizedLowerPath: String): Boolean {
        return normalizedLowerPath == "xl/workbook.xml" ||
            normalizedLowerPath == "xl/_rels/workbook.xml.rels" ||
            normalizedLowerPath == "xl/sharedstrings.xml" ||
            normalizedLowerPath == "xl/styles.xml" ||
            normalizedLowerPath == "xl/theme/theme1.xml" ||
            (normalizedLowerPath.startsWith("xl/worksheets/") && normalizedLowerPath.endsWith(".xml"))
    }

    /**
     * Selectively extracts ONLY the XML entries required for sheet/style/cell parsing,
     * skipping heavy embedded images/drawings/calcChain and enforcing strict zip-bomb caps.
     */
    private fun unzipRequiredXmlEntries(bytes: ByteArray): Map<String, ByteArray> {
        val map = linkedMapOf<String, ByteArray>()
        var entryCount = 0
        var totalUncompressedBytes = 0L
        val buffer = ByteArray(16 * 1024)

        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entryCount++
                if (entryCount > MAX_ZIP_ENTRIES) {
                    throw IllegalArgumentException("File Excel memiliki terlalu banyak komponen internal (>$MAX_ZIP_ENTRIES).")
                }
                if (!entry.isDirectory) {
                    val normalizedKey = normalizeZipPath(entry.name)
                    if (isRequiredXmlEntry(normalizedKey)) {
                        val out = ByteArrayOutputStream()
                        var entryBytes = 0
                        while (true) {
                            val read = zis.read(buffer)
                            if (read <= 0) break
                            entryBytes += read
                            totalUncompressedBytes += read
                            if (entryBytes > MAX_SINGLE_XML_ENTRY_BYTES || totalUncompressedBytes > MAX_TOTAL_UNCOMPRESSED_BYTES) {
                                throw IllegalArgumentException(
                                    "Isi file Excel melebihi batas memori aman perangkat. Hapus baris kosong atau gambar di dalam Excel lalu coba lagi."
                                )
                            }
                            out.write(buffer, 0, read)
                        }
                        map[normalizedKey] = out.toByteArray()
                    }
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
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
        return doc.documentElement
    }

    private fun createSecureSaxParserFactory(): SAXParserFactory =
        SAXParserFactory.newInstance().apply {
            isNamespaceAware = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }

    private fun parseWorkbookSheets(workbookBytes: ByteArray, relsBytes: ByteArray): List<SheetDescriptor> {
        val relsRoot = parseXml(relsBytes)
        val relMap = mutableMapOf<String, String>()
        val relNodes = relsRoot.getElementsByTagName("Relationship")
        for (i in 0 until relNodes.length) {
            val el = relNodes.item(i) as? Element ?: continue
            val id = el.getAttribute("Id")
            val rawTarget = el.getAttribute("Target").trim()
            if (id.isNotBlank() && rawTarget.isNotBlank()) {
                val combined = if (rawTarget.startsWith("/") || rawTarget.lowercase(Locale.US).startsWith("xl/")) {
                    rawTarget
                } else {
                    "xl/$rawTarget"
                }
                relMap[id] = normalizeZipPath(combined)
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

    /**
     * Constant-memory SAX streaming parser for `xl/sharedStrings.xml`.
     * Avoids building a full DOM tree on Android devices.
     */
    private fun parseSharedStringsStreaming(stream: InputStream): List<String> {
        val result = ArrayList<String>(256)
        val saxParser = createSecureSaxParserFactory().newSAXParser()
        val handler = object : DefaultHandler() {
            private var inSi = false
            private var inPhonetic = false
            private var inT = false
            private val currentText = StringBuilder()

            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
                val tag = (qName ?: localName).orEmpty().substringAfter(':')
                when (tag) {
                    "si" -> {
                        inSi = true
                        inPhonetic = false
                        currentText.setLength(0)
                    }
                    "rPh" -> if (inSi) inPhonetic = true
                    "t" -> if (inSi && !inPhonetic) inT = true
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inT && inSi && !inPhonetic) {
                    currentText.append(ch, start, length)
                }
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                val tag = (qName ?: localName).orEmpty().substringAfter(':')
                when (tag) {
                    "t" -> inT = false
                    "rPh" -> inPhonetic = false
                    "si" -> {
                        if (inSi && result.size < MAX_SHARED_STRINGS) {
                            result.add(currentText.toString())
                        }
                        inSi = false
                        inT = false
                        inPhonetic = false
                    }
                }
            }
        }
        saxParser.parse(stream, handler)
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

    private data class RawColorSpec(
        val auto: String?,
        val rgb: String?,
        val theme: Int?,
        val indexed: Int?,
        val tint: Double?
    )

    private data class RawFillSpec(
        val patternType: String,
        val fgColor: RawColorSpec?,
        val bgColor: RawColorSpec?
    )

    /**
     * Constant-memory SAX streaming parser for `xl/styles.xml`.
     * Real MS Excel & WPS Office workbooks can have multi-megabyte `styles.xml` files with
     * thousands of styles; using SAX instead of DOM prevents 8–12x heap expansion & OOM kills on Android.
     */
    private fun parseCellStyleFillColorsStreaming(
        stylesStream: InputStream,
        themeColors: List<String>
    ): Map<Int, ExcelRowColorFlag> {
        val indexedPalette = DEFAULT_INDEXED_COLORS.toMutableList()
        val rawFills = ArrayList<RawFillSpec>(64)
        val cellXfFillIds = ArrayList<Int>(128)

        val saxParser = createSecureSaxParserFactory().newSAXParser()
        val handler = object : DefaultHandler() {
            private var inColors = false
            private var inIndexedColors = false
            private var indexedColorIdx = 0

            private var inFills = false
            private var inFill = false
            private var inPatternFill = false
            private var currentPatternType = ""
            private var currentFg: RawColorSpec? = null
            private var currentBg: RawColorSpec? = null

            private var inCellXfs = false

            private fun extractColorSpec(attrs: Attributes?): RawColorSpec {
                return RawColorSpec(
                    auto = attrs?.getValue("auto")?.trim(),
                    rgb = attrs?.getValue("rgb")?.trim(),
                    theme = attrs?.getValue("theme")?.trim()?.toIntOrNull(),
                    indexed = attrs?.getValue("indexed")?.trim()?.toIntOrNull(),
                    tint = attrs?.getValue("tint")?.trim()?.toDoubleOrNull()
                )
            }

            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
                val tag = (qName ?: localName).orEmpty().substringAfter(':')
                when (tag) {
                    "colors" -> inColors = true
                    "indexedColors" -> if (inColors) {
                        inIndexedColors = true
                        indexedColorIdx = 0
                    }
                    "rgbColor" -> if (inIndexedColors) {
                        val rgb = attributes?.getValue("rgb")?.trim().orEmpty()
                        val hex6 = if (rgb.length == 8) rgb.substring(2) else rgb
                        if (hex6.length == 6) {
                            if (indexedColorIdx < indexedPalette.size) {
                                indexedPalette[indexedColorIdx] = hex6
                            } else {
                                indexedPalette.add(hex6)
                            }
                        }
                        indexedColorIdx++
                    }
                    "fills" -> inFills = true
                    "fill" -> if (inFills) {
                        inFill = true
                        inPatternFill = false
                        currentPatternType = ""
                        currentFg = null
                        currentBg = null
                    }
                    "patternFill" -> if (inFill) {
                        inPatternFill = true
                        currentPatternType = attributes?.getValue("patternType")?.trim()?.lowercase(Locale.US).orEmpty()
                    }
                    "fgColor" -> if (inPatternFill) {
                        currentFg = extractColorSpec(attributes)
                    }
                    "bgColor" -> if (inPatternFill) {
                        currentBg = extractColorSpec(attributes)
                    }
                    "cellXfs" -> inCellXfs = true
                    "xf" -> if (inCellXfs) {
                        val fillId = attributes?.getValue("fillId")?.trim()?.toIntOrNull() ?: 0
                        cellXfFillIds.add(fillId)
                    }
                }
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                val tag = (qName ?: localName).orEmpty().substringAfter(':')
                when (tag) {
                    "indexedColors" -> inIndexedColors = false
                    "colors" -> inColors = false
                    "patternFill" -> inPatternFill = false
                    "fill" -> if (inFill) {
                        rawFills.add(RawFillSpec(currentPatternType, currentFg, currentBg))
                        inFill = false
                    }
                    "fills" -> inFills = false
                    "cellXfs" -> inCellXfs = false
                }
            }
        }
        saxParser.parse(stylesStream, handler)

        val fillFlags = rawFills.map { spec ->
            if (spec.patternType.isBlank() || spec.patternType == "none" || spec.patternType == "gray125") {
                ExcelRowColorFlag.NONE
            } else {
                val rgb = resolveExcelColorSpec(spec.fgColor, themeColors, indexedPalette)
                    ?: resolveExcelColorSpec(spec.bgColor, themeColors, indexedPalette)
                if (rgb != null) classifyFillColor(rgb.r, rgb.g, rgb.b) else ExcelRowColorFlag.NONE
            }
        }

        val styleMap = HashMap<Int, ExcelRowColorFlag>(cellXfFillIds.size)
        cellXfFillIds.forEachIndexed { styleIdx, fillId ->
            styleMap[styleIdx] = fillFlags.getOrElse(fillId) { ExcelRowColorFlag.NONE }
        }
        return styleMap
    }

    private fun resolveExcelColorSpec(
        spec: RawColorSpec?,
        themeColors: List<String>,
        indexedPalette: List<String>
    ): RgbColor? {
        if (spec == null) return null
        if (spec.auto == "1" || spec.auto.equals("true", ignoreCase = true)) {
            return null
        }

        val rgbAttr = spec.rgb.orEmpty()
        if (rgbAttr.length == 6 || rgbAttr.length == 8) {
            val alpha = if (rgbAttr.length == 8) rgbAttr.substring(0, 2).toIntOrNull(16) ?: 255 else 255
            if (alpha == 0 && rgbAttr == "00000000") return null
            val hex6 = if (rgbAttr.length == 8) rgbAttr.substring(2) else rgbAttr
            val base = parseHexRgb(hex6) ?: return null
            val tint = spec.tint
            return if (tint != null && tint != 0.0) applyTint(base, tint) else base
        }

        val themeAttr = spec.theme
        if (themeAttr != null && themeAttr in themeColors.indices) {
            val base = parseHexRgb(themeColors[themeAttr]) ?: return null
            val tint = spec.tint
            return if (tint != null && tint != 0.0) applyTint(base, tint) else base
        }

        val indexedAttr = spec.indexed
        if (indexedAttr != null) {
            if (indexedAttr == 64) {
                return RgbColor(0, 0, 0)
            }
            if (indexedAttr == 65) {
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

    /**
     * Constant-memory SAX streaming parser for worksheet XML (`xl/worksheets/sheet*.xml`).
     * Discards empty styled rows immediately so real Excel/WPS/Google Sheets workbooks with
     * thousands of empty formatted cells never exhaust Android memory.
     */
    private fun parseWorksheetRowsStreaming(
        sheetName: String,
        mapping: ExcelSheetMapping,
        sheetXmlStream: InputStream,
        sharedStrings: List<String>,
        styleColorMap: Map<Int, ExcelRowColorFlag>
    ): List<ParsedRawRow> {
        data class ExtractedSheetRow(
            val rowNumber: Int,
            val cells: Map<Int, String>,
            val rowColorFlag: ExcelRowColorFlag
        )

        val extractedRows = ArrayList<ExtractedSheetRow>(128)
        val saxParser = createSecureSaxParserFactory().newSAXParser()

        val handler = object : DefaultHandler() {
            private var inSheetData = false
            private var inRow = false
            private var inCell = false
            private var inValue = false
            private var inInlineStr = false
            private var inInlineT = false
            private var rowOrdinal = 0
            private var currentRowNumber = 0
            private var fallbackCol = 0
            private var currentCellCol = 0
            private var currentCellType = ""
            private val currentRowFlags = mutableListOf<ExcelRowColorFlag>()
            private var currentCellMap = linkedMapOf<Int, String>()
            private val currentCellText = StringBuilder()

            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
                val tag = (qName ?: localName).orEmpty().substringAfter(':')
                when (tag) {
                    "sheetData" -> inSheetData = true
                    "row" -> if (inSheetData && extractedRows.size < MAX_PARSED_NON_EMPTY_ROWS_PER_SHEET) {
                        inRow = true
                        rowOrdinal++
                        currentRowNumber = attributes?.getValue("r")?.toIntOrNull() ?: rowOrdinal
                        fallbackCol = 0
                        currentRowFlags.clear()
                        currentCellMap = linkedMapOf()
                        attributes?.getValue("s")?.toIntOrNull()?.let { rowStyleIdx ->
                            styleColorMap[rowStyleIdx]?.let { currentRowFlags.add(it) }
                        }
                    }
                    "c" -> if (inRow) {
                        inCell = true
                        val ref = attributes?.getValue("r").orEmpty()
                        currentCellCol = if (ref.isNotBlank()) columnIndexFromRef(ref) else fallbackCol
                        fallbackCol = currentCellCol + 1
                        currentCellType = attributes?.getValue("t").orEmpty()
                        currentCellText.setLength(0)
                        attributes?.getValue("s")?.toIntOrNull()?.let { sIdx ->
                            styleColorMap[sIdx]?.let { currentRowFlags.add(it) }
                        }
                    }
                    "v" -> if (inCell) {
                        inValue = true
                        currentCellText.setLength(0)
                    }
                    "is" -> if (inCell) {
                        inInlineStr = true
                        currentCellText.setLength(0)
                    }
                    "t" -> if (inCell && inInlineStr) {
                        inInlineT = true
                    }
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inValue || inInlineT) {
                    currentCellText.append(ch, start, length)
                }
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                val tag = (qName ?: localName).orEmpty().substringAfter(':')
                when (tag) {
                    "v" -> inValue = false
                    "t" -> inInlineT = false
                    "is" -> inInlineStr = false
                    "c" -> if (inCell) {
                        val rawVal = currentCellText.toString()
                        val resolved = when (currentCellType) {
                            "s" -> {
                                val idx = rawVal.trim().toIntOrNull()
                                if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else ""
                            }
                            "inlineStr" -> rawVal
                            else -> rawVal.trim()
                        }.trim()
                        if (resolved.isNotEmpty()) {
                            currentCellMap[currentCellCol] = resolved
                        }
                        inCell = false
                        inValue = false
                        inInlineStr = false
                        inInlineT = false
                    }
                    "row" -> if (inRow) {
                        // Immediately discard completely empty rows (saves massive RAM on styled Excel sheets)
                        if (currentCellMap.isNotEmpty()) {
                            extractedRows.add(
                                ExtractedSheetRow(
                                    rowNumber = currentRowNumber,
                                    cells = currentCellMap,
                                    rowColorFlag = mergeRowColorPriority(currentRowFlags)
                                )
                            )
                        }
                        inRow = false
                    }
                    "sheetData" -> inSheetData = false
                }
            }
        }
        saxParser.parse(sheetXmlStream, handler)

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
