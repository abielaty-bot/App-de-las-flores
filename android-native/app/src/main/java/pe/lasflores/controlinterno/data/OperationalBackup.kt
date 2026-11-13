package pe.lasflores.controlinterno.data
import org.json.JSONObject
import org.json.JSONArray

internal object OperationalBackup {
    private fun <T> array(rows:List<T>, encode:(T)->JSONObject)=JSONArray().apply{rows.forEach{put(encode(it))}}
    private fun <T> read(root:JSONObject,key:String,decode:(JSONObject)->T):List<T>{
        val a=root.getJSONArray(key)
        require(a.length()<=100000)
        return (0 until a.length()).map{decode(a.getJSONObject(it))}
    }
    suspend fun export(dao:OperationalDao)=JSONObject().apply {
        put("templates",array(dao.templates()){x->JSONObject().apply{put("id",x.id);put("version",x.version);put("groupName",x.groupName);put("titular",x.titular);put("location",x.location);put("frequency",x.frequency);put("deadline",x.deadline);put("label",x.label);put("evaluation",x.evaluation);put("standard",x.standard);put("priority",x.priority);put("evidencePolicy",x.evidencePolicy);put("source",x.source);put("validation",x.validation);put("active",x.active)}})
        put("instances",array(dao.allInstances()){x->JSONObject().apply{put("id",x.id);put("periodKey",x.periodKey);put("templateId",x.templateId);put("templateVersion",x.templateVersion);put("labelSnapshot",x.labelSnapshot);put("standardSnapshot",x.standardSnapshot);put("locationSnapshot",x.locationSnapshot);put("staffId",x.staffId ?: JSONObject.NULL);put("staffNameSnapshot",x.staffNameSnapshot);put("initialResult",x.initialResult);put("correction",x.correction);put("note",x.note);put("attributable",x.attributable);put("reviewedAtMillis",x.reviewedAtMillis ?: JSONObject.NULL);put("updatedAtMillis",x.updatedAtMillis)}})
        put("events",array(dao.allEvents()){x->JSONObject().apply{put("id",x.id);put("instanceId",x.instanceId);put("action",x.action);put("beforeValue",x.beforeValue);put("afterValue",x.afterValue);put("note",x.note);put("atMillis",x.atMillis)}})
    }
    suspend fun restore(root:JSONObject,dao:OperationalDao){
        val templates=read(root,"templates"){x->OperationalTemplateEntity(x.getString("id"),x.getInt("version"),x.getString("groupName"),x.getString("titular"),x.getString("location"),x.getString("frequency"),x.getString("deadline"),x.getString("label"),x.getString("evaluation"),x.getString("standard"),x.getString("priority"),x.getString("evidencePolicy"),x.getString("source"),x.getString("validation"),x.getBoolean("active"))}
        require(templates.map{it.id}.distinct().size==templates.size)
        val instances=read(root,"instances"){x->OperationalInstanceEntity(x.getString("id"),x.getString("periodKey"),x.getString("templateId"),x.getInt("templateVersion"),x.getString("labelSnapshot"),x.getString("standardSnapshot"),x.getString("locationSnapshot"),if(x.isNull("staffId"))null else x.getString("staffId"),x.getString("staffNameSnapshot"),x.getString("initialResult"),x.getString("correction"),x.getString("note"),x.getBoolean("attributable"),if(x.isNull("reviewedAtMillis"))null else x.getLong("reviewedAtMillis"),x.getLong("updatedAtMillis"))}
        require(instances.map{it.id}.distinct().size==instances.size)
        val events=read(root,"events"){x->OperationalEventEntity(x.getString("id"),x.getString("instanceId"),x.getString("action"),x.getString("beforeValue"),x.getString("afterValue"),x.getString("note"),x.getLong("atMillis"))}
        require(events.map{it.id}.distinct().size==events.size)
        require(instances.all{r->templates.any{it.id==r.templateId}})
        require(events.all{e->instances.any{it.id==e.instanceId}})
        dao.clearEvents();dao.clearInstances();dao.clearTemplates()
        templates.forEach{dao.saveTemplate(it)}
        instances.forEach{dao.saveInstance(it)}
        events.forEach{dao.addEvent(it)}
    }
}
