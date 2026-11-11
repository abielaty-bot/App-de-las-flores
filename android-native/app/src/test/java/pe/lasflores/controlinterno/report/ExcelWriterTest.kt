package pe.lasflores.controlinterno.report

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class ExcelWriterTest {
    @Test fun workbookHasSheetsXmlEscapingAndNoInjectedFormula() {
        val out=ByteArrayOutputStream()
        ExcelWriter().write(listOf(
            ExcelSheet("Resumen","Reporte","2026-11-11",listOf("Concepto","Total"),
                listOf(listOf("Bajo <control> & seguro",3))),
            ExcelSheet("Funciones","Funciones","Mensual",listOf("Persona","Resultado"),
                listOf(listOf("Juan","=SUM(1,2)")))
        ),out)
        val parts=mutableMapOf<String,String>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { stream ->
            while(true) {
                val next=stream.nextEntry?:break
                parts[next.name]=stream.readBytes().toString(Charsets.UTF_8)
                stream.closeEntry()
            }
        }
        assertTrue(parts.keys.containsAll(listOf("[Content_Types].xml","xl/workbook.xml",
            "xl/styles.xml","xl/worksheets/sheet1.xml","xl/worksheets/sheet2.xml")))
        assertTrue(parts["xl/worksheets/sheet1.xml"]!!.contains("Bajo &lt;control&gt; &amp; seguro"))
        assertTrue(parts["xl/worksheets/sheet2.xml"]!!.contains("=SUM(1,2)"))
        assertFalse(parts["xl/worksheets/sheet2.xml"]!!.contains("<f>"))
        assertTrue(parts["xl/worksheets/sheet1.xml"]!!.contains("autoFilter"))
    }

    @Test fun wrongNumberOfCellsIsRejected() {
        val failed=runCatching { ExcelWriter().write(listOf(
            ExcelSheet("Test","T","",listOf("A","B"),listOf(listOf("única")))
        ),ByteArrayOutputStream()) }
        assertTrue(failed.isFailure)
    }
}
