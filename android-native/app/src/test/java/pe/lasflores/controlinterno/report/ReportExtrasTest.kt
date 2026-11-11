package pe.lasflores.controlinterno.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import pe.lasflores.controlinterno.data.MaterialDeliveryEntity

class ReportExtrasTest {
    private fun delivery(id:String,day:String,status:String,qty:Int,person:String="p1") =
        MaterialDeliveryEntity(id,person,"Trabajador",day,"Mandil",qty,
            "Dotación","Maître",status,"Motivo",null,1L)

    private fun snapshot(deliveries:List<MaterialDeliveryEntity>) =
        ReportSnapshot(emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),
            emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),
            deliveries=deliveries)

    @Test fun deliveriesInPeriodAreDistinctFromCurrentActiveBalance() {
        val s=snapshot(listOf(
            delivery("old","2026-10-25","ENTREGADA",3),
            delivery("nov","2026-11-11","ENTREGADA",2),
            delivery("returned","2026-11-10","DEVUELTA",5),
            delivery("voided","2026-11-11","ANULADA",7)))
        val sel=ReportSelection(ReportPeriod.MONTH,LocalDate.parse("2026-11-11"))
        val count=ReportBuilder.counts(s,sel)
        assertEquals(3,count.deliveriesRegistered)
        assertEquals(7,count.deliveryUnits) // 2 + 5, la devolución ocurrió después de entregar
        val book=ReportBuilder.build(s,sel)
        assertEquals(19,book.size)
        val detail=book.single{it.name=="Entregas"}
        assertEquals(3,detail.rows.size)
        val byWorker=book.single{it.name=="Entregas por trabajador"}
        assertEquals(1,byWorker.rows.size)
        assertEquals(5,byWorker.rows.single()[7]) // vigente actual = 3 + 2
        val summary=book.single{it.name=="Resumen"}
        assertTrue(summary.rows.any { it[0]=="Materiales: unidades actualmente vigentes" &&
            it[1]==5 })
        assertTrue(book.single{it.name=="Programación"}.subtitle.contains("no acredita asistencia"))
    }

    @Test fun perEmployeeFilterCannotLeakOthersMovements() {
        val s=snapshot(listOf(
            delivery("a","2026-11-11","ENTREGADA",1),
            delivery("b","2026-11-11","ENTREGADA",3,person="p2")))
        val sel=ReportSelection(ReportPeriod.DAY,LocalDate.parse("2026-11-11"),"p1")
        val result=ReportBuilder.build(s,sel)
        assertEquals(1,result.single{it.name=="Entregas"}.rows.size)
        assertEquals(1,result.single{it.name=="Entregas por trabajador"}.rows.size)
    }

    @Test fun xlsxContainsAll19SheetsWithoutFormulas() {
        val s=snapshot(emptyList())
        val book=ReportBuilder.build(s,
            ReportSelection(ReportPeriod.WEEK,LocalDate.parse("2026-11-11")))
        val out=ByteArrayOutputStream()
        ExcelWriter().write(book,out)
        val paths=mutableSetOf<String>()
        val workbookXml=mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use{zip->
            while(true){
                val entry=zip.nextEntry?:break
                paths.add(entry.name)
                val bytes=zip.readBytes()
                if(entry.name=="xl/workbook.xml")workbookXml.add(bytes.toString(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        assertEquals(19,paths.count{it.matches(Regex("""xl/worksheets/sheet\d+\.xml"""))})
        assertTrue(workbookXml.single().contains("Entregas por trabajador"))
        assertFalse(workbookXml.single().contains("<f>"))
    }
}
