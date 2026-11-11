package pe.lasflores.controlinterno.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import pe.lasflores.controlinterno.data.UniformAnswerEntity
import pe.lasflores.controlinterno.data.UniformInspectionEntity
import pe.lasflores.controlinterno.data.UniformEventEntity

class UniformRectificationTest {
    private val inspection = UniformInspectionEntity("i","2026-11-10","s","Mozo",
        "FINALIZED",UniformRules.OBSERVADO,1,2)

    @Test fun rectificationPreservesOriginalResult() {
        val answer = UniformAnswerEntity("i","trapo","Trapo limpio","Implementos",
            UniformRules.OK,null,null)
        val event = UniformEventEntity("e","i","trapo","RECTIFICAR",
            UniformRules.BAD,UniformRules.OK,"Supervisor","Error inicial",3)
        val day = UniformDaySnapshot("2026-11-10",
            inspections = listOf(inspection), answers = listOf(answer), events = listOf(event))
        assertEquals(UniformRules.OBSERVADO, inspection.initialResult)
        assertEquals("Rectificado", day.statusFor(inspection))
    }

    @Test fun actualCorrectionIsNotMistakenForRectification() {
        val answer = UniformAnswerEntity("i","trapo","Trapo limpio","Implementos",
            UniformRules.BAD,4,"Supervisor")
        val day = UniformDaySnapshot("2026-11-10",
            inspections = listOf(inspection), answers = listOf(answer))
        assertEquals("Corregido", day.statusFor(inspection))
    }
}
