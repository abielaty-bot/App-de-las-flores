package pe.lasflores.controlinterno.report

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ExcelSheet(
    val name: String, val title: String, val subtitle: String,
    val headers: List<String>, val rows: List<List<Any?>>
)

/** Archivo OOXML sin bibliotecas pesadas. Los textos nunca se interpretan como fórmulas. */
class ExcelWriter {
    fun write(sheets: List<ExcelSheet>, output: OutputStream) {
        require(sheets.isNotEmpty() && sheets.size <= 20)
        require(sheets.map { it.name }.toSet().size == sheets.size)
        sheets.forEach {
            require(it.name.length in 1..31 && it.name.none { c -> c in "[]:*?/\\" })
            require(it.headers.isNotEmpty() && it.headers.size <= 100)
            require(it.rows.size <= 1_048_572)
            require(it.rows.all { row -> row.size == it.headers.size })
        }
        ZipOutputStream(output).use { zip ->
            fun entry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            val prefix = "application/vnd.openxmlformats-officedocument"
            entry("[Content_Types].xml", buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
                append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/>""")
                append("""<Override PartName="/xl/workbook.xml" ContentType="$prefix.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/styles.xml" ContentType="$prefix.spreadsheetml.styles+xml"/>""")
                sheets.indices.forEach { i -> append("""<Override PartName="/xl/worksheets/sheet${i + 1}.xml" ContentType="$prefix.spreadsheetml.worksheet+xml"/>""") }
                append("</Types>")
            })
            entry("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            entry("xl/workbook.xml", buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
                sheets.forEachIndexed { i, sheet ->
                    append("""<sheet name="${xml(sheet.name)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""")
                }
                append("</sheets></workbook>")
            })
            entry("xl/_rels/workbook.xml.rels", buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
                sheets.indices.forEach { i -> append("""<Relationship Id="rId${i + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${i + 1}.xml"/>""") }
                append("""<Relationship Id="rId${sheets.size + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""")
            })
            entry("xl/styles.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="3"><font><sz val="11"/><name val="Calibri"/></font><font><b/><color rgb="FFFFFFFF"/><sz val="11"/><name val="Calibri"/></font><font><b/><color rgb="FF17584A"/><sz val="15"/><name val="Calibri"/></font></fonts><fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF17584A"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="3"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/><xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>""")
            sheets.forEachIndexed { i, sheet -> writeSheet(zip, i + 1, sheet) }
        }
    }

    private fun writeSheet(zip: ZipOutputStream, index: Int, sheet: ExcelSheet) {
        zip.putNextEntry(ZipEntry("xl/worksheets/sheet$index.xml"))
        fun emit(value: String) { zip.write(value.toByteArray(Charsets.UTF_8)) }
        val count = sheet.headers.size
        val lastCol = column(count)
        val end = sheet.rows.size + 4
        emit("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><dimension ref="A1:$lastCol$end"/><sheetViews><sheetView workbookViewId="0"><pane ySplit="4" topLeftCell="A5" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews><sheetFormatPr defaultRowHeight="17"/><cols>""")
        sheet.headers.forEachIndexed { i, h ->
            val width = (h.length + 4).coerceIn(17, 38)
            emit("""<col min="${i + 1}" max="${i + 1}" width="$width" customWidth="1"/>""")
        }
        emit("</cols><sheetData>")
        emit("""<row r="1" ht="28" customHeight="1">${cell("A1", sheet.title, 2)}</row>""")
        emit("""<row r="2">${cell("A2", sheet.subtitle, 0)}</row>""")
        emit("""<row r="4" ht="24" customHeight="1">""")
        sheet.headers.forEachIndexed { i, h -> emit(cell(column(i + 1) + "4", h, 1)) }
        emit("</row>")
        sheet.rows.forEachIndexed { i, row ->
            val number = i + 5
            emit("""<row r="$number">""")
            row.forEachIndexed { j, value -> emit(cell(column(j + 1) + number, value, 0)) }
            emit("</row>")
        }
        emit("</sheetData>")
        if(sheet.rows.isNotEmpty()) emit("""<autoFilter ref="A4:$lastCol$end"/>""")
        if(count > 1) emit("""<mergeCells count="2"><mergeCell ref="A1:$lastCol${1}"/><mergeCell ref="A2:$lastCol${2}"/></mergeCells>""")
        emit("</worksheet>")
        zip.closeEntry()
    }

    private fun cell(ref:String,value:Any?,style:Int):String = when(value) {
        is Int, is Long -> """<c r="$ref" s="$style" t="n"><v>$value</v></c>"""
        is Double -> if(value.isFinite()) """<c r="$ref" s="$style" t="n"><v>$value</v></c>"""
            else """<c r="$ref" s="$style" t="inlineStr"><is><t>—</t></is></c>"""
        else -> """<c r="$ref" s="$style" t="inlineStr"><is><t xml:space="preserve">${xml(value?.toString().orEmpty())}</t></is></c>"""
    }

    private fun column(n:Int):String {
        var k=n
        val s=StringBuilder()
        while(k>0) { k--; s.insert(0,('A'.code+k%26).toChar()); k/=26 }
        return s.toString()
    }

    private fun xml(s:String):String=buildString {
        s.forEach { c -> when(c) {
            '&'->append("&amp;"); '<'->append("&lt;"); '>'->append("&gt;")
            '"'->append("&quot;"); '\''->append("&apos;")
            else -> if(c=='\t'||c=='\n'||c=='\r'||c>=' ') append(c)
        } }
    }
}
