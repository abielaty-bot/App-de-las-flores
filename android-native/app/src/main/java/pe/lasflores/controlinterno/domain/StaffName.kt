package pe.lasflores.controlinterno.domain

object StaffName {
    fun normalize(raw: String): String = raw.trim().replace(Regex("\\s+"), " ")
    fun isValid(raw: String): Boolean = normalize(raw).length in 2..120
}

val STAFF_ROLES = listOf("Mozo", "Caja", "Runner", "Recepción", "Lavavasos", "Apoyo", "Otro")
