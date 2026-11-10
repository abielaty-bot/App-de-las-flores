package pe.lasflores.controlinterno.domain

/** Reglas puras: no dependen de Android ni de la base de datos. */
data class UniformRequirement(val id: String, val label: String, val group: String)

object UniformRules {
    const val OK = "OK"
    const val BAD = "BAD"
    const val CONFORME = "CONFORME"
    const val OBSERVADO = "OBSERVADO"

    fun toggle(current: String?, tapped: String): String? {
        require(tapped == OK || tapped == BAD)
        return if (current == tapped) null else tapped
    }

    fun result(statuses: Collection<String?>): String {
        require(statuses.isNotEmpty() && statuses.none { it == null })
        return if (statuses.any { it == BAD }) OBSERVADO else CONFORME
    }

    /** Catálogo inicial: se hará configurable por puesto en una fase posterior. */
    private val personal = listOf(
        UniformRequirement("unas", "Uñas cortas y limpias", "Presentación"),
        UniformRequirement("cabello", "Cabello corto o recogido", "Presentación")
    )
    private val uniforme = listOf(UniformRequirement("camisa", "Uniforme limpio y planchado", "Uniforme"))
    private val mozo = listOf(
        UniformRequirement("toca", "Toca o redecilla", "Presentación"),
        UniformRequirement("mandil", "Mandil limpio", "Uniforme"),
        UniformRequirement("trapo", "Trapo limpio", "Implementos"),
        UniformRequirement("lapiceros", "Lapiceros", "Implementos"),
        UniformRequirement("sacacorchos", "Sacacorchos", "Implementos"),
        UniformRequirement("encendedor", "Encendedor", "Implementos")
    )

    fun forRole(role: String): List<UniformRequirement> = when (role) {
        "Mozo", "Apoyo" -> (personal + uniforme + mozo).distinctBy { it.id }
        "Caja", "Recepción", "Otro" -> personal + uniforme
        "Lavavasos" -> personal + UniformRequirement("toca", "Toca o redecilla", "Presentación")
        else -> personal + uniforme
    }
}
