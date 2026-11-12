package pe.lasflores.controlinterno.domain

import pe.lasflores.controlinterno.data.UniformRequirementEntity
import pe.lasflores.controlinterno.data.UniformInspectionEntity

/** Catálogo común: el puesto no se infiere de la modalidad de horario. */
object UniformRoleRules {
    val PROPOSAL_ROLES=setOf("Caja","Runner")
    fun label(role:String)=when(role){
        "Mozo"->"Mozos";"Caja"->"Caja";"Runner"->"Runners";else->role
    }
    fun rolesCsv(roles:Set<String>):String {
        require(roles.isNotEmpty()&&roles.all{it in STAFF_ROLES})
        return "|"+STAFF_ROLES.filter{it in roles}.joinToString("|")+"|"
    }
    fun assigned(row:UniformRequirementEntity,role:String)=row.rolesCsv.contains("|"+role+"|")
    data class Suggested(val label:String,val category:String,val aliases:List<String> = emptyList())
    private val shared=listOf(
        Suggested("Uniforme limpio y planchado","Uniforme",listOf("Camisa limpia y planchada","Camisa o blusa limpia y planchada")),
        Suggested("Pantalón de uniforme","Uniforme"),
        Suggested("Calzado limpio","Uniforme"),
        Suggested("Uñas cortas y limpias","Presentación",listOf("Uñas cortas")),
        Suggested("Cabello corto o recogido","Presentación",listOf("Cabello ordenado"))
    )
    fun suggestions(role:String):List<Suggested>{
        require(role in PROPOSAL_ROLES)
        return shared+if(role=="Caja")listOf(Suggested("Lapiceros de caja","Implementos"))
        else listOf(Suggested("Toca o redecilla","Presentación"),Suggested("Mandil limpio","Uniforme"),Suggested("Trapo limpio","Implementos"))
    }
    private fun normalized(value:String)=StaffName.normalize(value).lowercase(java.util.Locale.ROOT)
    /** Operación aditiva e idempotente. No cambia cantidad, perfil, nombre ni estado
     * de requisitos existentes; un requisito personalizado ya asignado prevalece.
     * No se copian reglas de Mozo ni Apoyo por inferencia. */
    fun proposal(role:String,existing:List<UniformRequirementEntity>,now:Long,newId:()->String):List<UniformRequirementEntity>{
        val result=mutableListOf<UniformRequirementEntity>()
        var order=(existing.maxOfOrNull{it.sortOrder}?:-1)+1
        val allProfiles=UniformRules.profilesCsv(UniformRules.PROFILES.toSet())
        for(s in suggestions(role)){
            val labels=(listOf(s.label)+s.aliases).map(::normalized).toSet()
            val matches=existing.filter{normalized(it.label) in labels}
            if(matches.any{assigned(it,role)})continue
            val shared=matches.firstOrNull{it.isActive&&it.category==s.category&&it.minQty==0&&it.profilesCsv==allProfiles}
            if(shared!=null){
                val roles=STAFF_ROLES.filter{assigned(shared,it)}.toSet()+role
                result.add(shared.copy(rolesCsv=rolesCsv(roles),updatedAtMillis=now))
            }else if(matches.isEmpty()){
                result.add(UniformRequirementEntity(newId(),s.label,s.category,0,rolesCsv(setOf(role)),true,order++,now,allProfiles))
            }
            // Si hay una variante desactivada o personalizada, se configura manualmente.
        }
        return result
    }
    data class RoleStats(val role:String,val finalized:Int,val conforming:Int,val observed:Int,val drafts:Int){
        val percent:Int? get()=if(conforming+observed==0)null else (100L*conforming/(conforming+observed)).toInt()
    }
    fun stats(rows:List<UniformInspectionEntity>):List<RoleStats> = rows.groupBy{it.roleSnapshot}.map{(role,items)->
        val done=items.filter{it.workflow=="FINALIZED"}
        RoleStats(role,done.size,done.count{it.initialResult==UniformRules.CONFORME},
            done.count{it.initialResult==UniformRules.OBSERVADO},items.size-done.size)
    }.sortedBy{STAFF_ROLES.indexOf(it.role).let{n->if(n<0)Int.MAX_VALUE else n}}
}
