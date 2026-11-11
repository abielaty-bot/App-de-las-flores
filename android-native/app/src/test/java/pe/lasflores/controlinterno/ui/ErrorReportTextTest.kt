package pe.lasflores.controlinterno.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorReportTextTest {
    @Test fun stripsWholeJsonFromMalformedFileError() {
        val raw = "Unterminated string at character 234147 of {\\"app\\":\\"lasflores-android\\",\\"staff\\":[{\\"fullName\\":\\"Nombre de prueba\\"}]}"
        val result = ErrorReportText.compact(raw)
        assertEquals("Unterminated string at character 234147", result)
        assertFalse(result.contains("fullName"))
        assertFalse(result.contains("Nombre de prueba"))
    }

    @Test fun noSensitiveJsonWhenMessageContainsMultilineDump() {
        val raw = "No se importó ningún dato: JSON malformado of {\n \\"staff\\": [\\"Datos privados\\"]}"
        val result = ErrorReportText.compact(raw)
        assertEquals("No se importó ningún dato: JSON malformado", result)
        assertFalse(result.contains("Datos privados"))
    }

    @Test fun preventsHugeErrorCardAndClipboard() {
        assertTrue(ErrorReportText.compact("E".repeat(500_000)).length <= 380)
    }

    @Test fun recognizesErrorsFromDifferentModules() {
        assertTrue(ErrorReportText.isError("Error al exportar: sin permiso"))
        assertTrue(ErrorReportText.isError("No se importó ningún dato: respaldo inválido"))
        assertTrue(ErrorReportText.isError("No se pudo preparar la actualización"))
        assertFalse(ErrorReportText.isError("Guardando…"))
        assertFalse(ErrorReportText.isError("Restauración finalizada correctamente."))
    }

    @Test fun producesCopyableStructuredReportWithoutJsonDump() {
        val report = ErrorReport("Respaldos",
            ErrorReportText.compact("Unterminated string at character 12 of {\\"staff\\":[\\"Privado\\"]}"),
            0L)
        val output = report.clipboardText("4.0012")
        assertTrue(output.contains("Módulo: Respaldos"))
        assertTrue(output.contains("Versión Android: 4.0012"))
        assertTrue(output.contains("character 12"))
        assertFalse(output.contains("Privado"))
    }
}
