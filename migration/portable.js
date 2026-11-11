/* Las Flores — convertidor local Web → Android v1.
 * No consulta servidores, no modifica registros. Devuelve errores antes de descargar.
 * El JSON original se incluye íntegro para conservar cualquier dato no representable.
 */
(function(root) {
"use strict";
const ROLES=["Mozo","Caja","Recepción","Lavavasos","Apoyo","Otro"];
const AREAS=["Oficina I","Oficina II","Funciones generales"];
const GROUPS={
 "Oficina I":["Caja","Estrado","Entrada","Ventana","C","Vasos"],
 "Oficina II":["Caja","Entrada","O1","O2","T1","T2"],
 "Funciones generales":["Manteles","Heladería","Desperdicios","Jabas y utensilios","Exhibidor de vinos"]
};
const INCIDENT_AREAS=["Cocina","Azafate","Barra","Repostería","Salón/Mozos","Recepción","Caja/POS","Logística/Almacén","Otra"];
const INCIDENT_IMPACTS=["No determinado","Antes de servir","En mesa / cliente afectado","Después del consumo","Servicio interrumpido o retrasado","Otro"];
function str(v){return String(v == null ? "" : v);}
function arr(v){return Array.isArray(v)?v:[];}
function obj(v){return v && typeof v==="object" && !Array.isArray(v)?v:{};}
function n(v,defaultValue){let q=Number(v);return v!==""&&v!=null&&Number.isFinite(q)?q:defaultValue;}
function validDay(v){return /^\d{4}-\d{2}-\d{2}$/.test(v)&&!Number.isNaN(Date.parse(v+"T12:00:00Z")) && new Date(v+"T12:00:00Z").toISOString().slice(0,10)===v;}
function stamp(v,fallback,fail,context){if(v==null||v==="")return fallback;const q=typeof v==="number"?v:Date.parse(v);if(!Number.isFinite(q)){fail("Fecha/hora inválida en "+context);return fallback;}return q;}
function dateStamp(v,fail,context){if(!validDay(v)){fail("Fecha inválida en "+context);return 0;}return Date.parse(v+"T12:00:00Z");}
function normalizedName(s){return str(s).normalize("NFD").replace(/[\u0300-\u036f]/g,"").trim().replace(/\s+/g," ").toLowerCase();}
function reportCounts(input){const d=input?.app==="lasflores-transfer" && input.formatVersion===1?input.payload:input;return {staff:arr(d?.staff).length,inspections:arr(d?.inspections).length,checklists:arr(d?.checklists).length,briefings:arr(d?.briefings).length,functions:arr(d?.functionChecks).length,incidents:arr(d?.incidents).length,deliveries:arr(d?.deliveries).length};}
function convert(input, existingAndroid) {
 const errors=[], warnings=[];
 const error=s=>{if(errors.length<65)errors.push(s)};
 const warn=s=>{if(warnings.length<55)warnings.push(s)};
 const data=input?.app==="lasflores-transfer"&&input.formatVersion===1?input.payload:input;
 if(!data||!["lasflores-control","lasflores-uniformes"].includes(data.app))return {ok:false,errors:["El archivo no es un respaldo compatible de Las Flores Web."],warnings:[],counts:reportCounts(data)};
 if(!Array.isArray(data.staff)||!Array.isArray(data.inspections)||!data.rosters||Array.isArray(data.rosters)||typeof data.rosters!=="object")
   return {ok:false,errors:["El respaldo Web no tiene una estructura válida."],warnings:[],counts:reportCounts(data)};
 const out={app:"lasflores-android",formatVersion:5,exportedAtMillis:Date.now(),
  migration:{origin:"web",formatVersion:1,mode:existingAndroid?"merge":"replace",sourceApp:data.app},
  webSourceArchive:data,
  staff:[],vacations:[],overrides:[],inspections:[],answers:[],events:[],activityDays:[],
  requirements:[],snapshots:[],deliveries:[],briefingAttendance:[],briefingSessions:[],
  briefingEvents:[],functionTasks:[],functionChecks:[],functionEvidence:[],functionEvents:[],
  incidentTypes:[],incidents:[],incidentActions:[]};
 const ids=new Set(),personById=new Map();
 for(const p of arr(data.staff)){
  const id=str(p.id),role=str(p.position||"Mozo"),name=str(p.name).trim();
  if(!id||ids.has(id)||!name||name.length>120||!ROLES.includes(role)){error("Ficha de trabajador inválida/duplicada: "+(name||id||"(sin nombre)"));continue;}
  ids.add(id);personById.set(id,p);
  const cat=n(p.category,0),group=n(p.briefingGroup,0);
  if(!Number.isInteger(cat)||cat<0||cat>4||![0,11,12].includes(group))error("Categoría/grupo incorrecto: "+name);
  const days=arr(p.restDays),rest=days.reduce((m,d)=>m|(1<<n(d,0)),0);
  if(days.some(d=>!Number.isInteger(n(d,-1))||n(d,-1)<0||n(d,-1)>6))error("Días de descanso inválidos: "+name);
  out.staff.push({id,fullName:name,role,category:cat,briefingGroup:group,scheduleMode:p.scheduleMode==="weekend"?"weekend":"regular",
   restDaysMask:rest,isActive:p.active!==false,createdAtMillis:stamp(p.createdAt,Date.now(),error,"alta de "+name),
   updatedAtMillis:stamp(p.updatedAt,stamp(p.createdAt,Date.now(),error,"alta de "+name),error,"actualización de "+name)});
  for(const v of arr(p.vacations)){
    if(!validDay(str(v.start))||!validDay(str(v.end))||str(v.end)<str(v.start))error("Vacaciones incompatibles: "+name);
    out.vacations.push({id:str(v.id||"vac-"+id+"-"+v.start),staffId:id,startDateIso:str(v.start),endDateIso:str(v.end),
     note:str(v.note),createdAtMillis:stamp(v.createdAt,dateStamp(str(v.start),error,"vacaciones"),error,"vacaciones")});
  }
 }
 const personNames=new Map();
 for(const p of out.staff){const name=normalizedName(p.fullName);if(personNames.has(name))error("Trabajadores con el mismo nombre: "+p.fullName);personNames.set(name,p.id);}
 const verifyStaff=(id,context)=>{if(!personById.has(id)){error("Trabajador desconocido en "+context+": "+id);return false;}return true;};
 for(const [date,roster0] of Object.entries(obj(data.rosters))){
  if(!validDay(date)){error("Fecha incorrecta de programación: "+date);continue;}
  const roster=obj(roster0),ovs=obj(roster.overrides);
  const states=Object.keys(ovs).length?ovs:(!roster.overrides?obj(roster.staff):{});
  for(const [staffId,v] of Object.entries(states)){
   if(!verifyStaff(staffId,"programación"))continue;
   if(!["working","support","rest","vacation","absent","leave","off"].includes(v)){error("Situación desconocida: "+v);continue;}
   out.overrides.push({dateIso:date,staffId,status:v==="off"?"rest":v,
    updatedAtMillis:stamp(roster.updatedAt||roster.createdAt,dateStamp(date,error,"programación"),error,"programación")});
   if(v==="off")warn("«No programado» se muestra como Descanso en Android; el valor exacto queda en el archivo original.");
  }
 }
 const reqById=new Map(),sourceReq=arr(data.requirements);
 for(const [i,r] of sourceReq.entries()){
  const id=str(r.id),label=str(r.label).trim(),min=n(r.minQty,0),roles=arr(r.positions).length?arr(r.positions).filter(x=>ROLES.includes(x)):ROLES;
  const category=String(r.group||"Otro").startsWith("Presentación")?"Presentación":(["Uniforme","Implementos","Otro"].includes(r.group)?r.group:"Otro");
  if(!id||reqById.has(id)||label.length<3||label.length>100||min<0||min>100||!roles.length){error("Requisito incompatible: "+(label||id));continue;}
  const e={id,label,category,minQty:min,rolesCsv:"|"+roles.join("|")+"|",isActive:r.active!==false,
   sortOrder:i,updatedAtMillis:stamp(r.updatedAt||r.createdAt,0,error,"requisito")};
  reqById.set(id,e);out.requirements.push(e);
 }
 if(!out.requirements.length)error("El respaldo no contiene requisitos configurados; restaura primero la copia en la web.");
 const issues=arr(data.issueTypes);
 const issueTypeById=new Map(issues.map(t=>[str(t.id),t]));
 function legacyReq(issueId,label){
   const id="web-issue:"+str(issueId),name=str(label).trim().slice(0,100);
   if(name.length<3){error("Tipo histórico de incumplimiento sin descripción: "+issueId);return id;}
   if(!reqById.has(id)){const e={id,label:name,category:"Otro",minQty:0,rolesCsv:"|"+ROLES.join("|")+"|",isActive:false,sortOrder:out.requirements.length,updatedAtMillis:0};reqById.set(id,e);out.requirements.push(e);}
   return id;
 }
 const inspectionsByKey=new Map(),checksByKey=new Map();
 for(const check of arr(data.checklists)){
   const key=str(check.date)+"|"+str(check.staffId);
   if(checksByKey.has(key))error("Checklist duplicado: "+key);
   checksByKey.set(key,check);
 }
 for(const r of arr(data.inspections)){
   const key=str(r.date)+"|"+str(r.staffId);
   if(inspectionsByKey.has(key))error("Inspección duplicada: "+key);
   inspectionsByKey.set(key,r);
 }
 const keys=new Set([...inspectionsByKey.keys(),...checksByKey.keys()]);
 for(const key of keys){
  const r=inspectionsByKey.get(key),c=checksByKey.get(key),parts=key.split("|"),date=parts[0],pid=parts.slice(1).join("|");
  if(!validDay(date)||!verifyStaff(pid,"uniformes "+key))continue;
  if(r?.voidedAt){error("Inspección anulada no representable sin pérdida: "+key);continue;}
  const who=personById.get(pid),id=str(r?.id||c?.id||"web-inspection:"+key);
  const reqs=new Map(),answers=new Map();
  for(const item of arr(c?.items)){
    const reqId=str(item.reqId),meta=reqById.get(reqId);
    if(!meta){error("Requisito desconocido para "+key+": "+reqId);continue;}
    reqs.set(reqId,{inspectionId:id,requirementId:reqId,labelSnapshot:str(item.label||meta.label),
     categorySnapshot:str(item.group||meta.category).startsWith("Presentación")?"Presentación":str(item.group||meta.category),
     minQtySnapshot:n(item.minQty,meta.minQty)});
    if(item.status){
     if(!["ok","bad"].includes(item.status)){error("Estado de requisito inválido: "+key);continue;}
     answers.set(reqId,{inspectionId:id,requirementId:reqId,labelSnapshot:str(item.label||meta.label),
      groupSnapshot:meta.category,status:item.status==="ok"?"OK":"BAD",
      correctedAtMillis:item.resolvedAt?stamp(item.resolvedAt,null,error,"corrección"):null,
      correctedBy:item.resolvedAt?str(item.resolvedBy||"Migrado de Web"):null,
      quantity:item.quantity==null?null:n(item.quantity,null)});
    }
  }
  const issueIds=arr(r?.issues).map(str);
  for(const issue of issueIds){
   const isReq=issue.startsWith("req:")&&reqById.has(issue.slice(4));
   const reqId=isReq?issue.slice(4):legacyReq(issue,str(obj(r?.issueLabels)[issue]||issueTypeById.get(issue)?.label||issue));
   const meta=reqById.get(reqId);if(!meta)continue;
   if(!reqs.has(reqId))reqs.set(reqId,{inspectionId:id,requirementId:reqId,labelSnapshot:meta.label,categorySnapshot:meta.category,minQtySnapshot:0});
   const prior=answers.get(reqId);
   const detail=obj(obj(r?.correctionDetails)[issue]);
   answers.set(reqId,{inspectionId:id,requirementId:reqId,labelSnapshot:meta.label,groupSnapshot:meta.category,
    status:"BAD",correctedAtMillis:detail.at?stamp(detail.at,null,error,"corrección"):arr(r?.resolvedIssues).map(str).includes(issue)?stamp(r?.correctedAt,null,error,"corrección"):null,
    correctedBy:detail.by?str(detail.by):arr(r?.resolvedIssues).map(str).includes(issue)?"Migrado de Web":null,quantity:prior?.quantity??null});
  }
  if(!reqs.size && r){
   const possible=out.requirements.filter(q=>q.isActive&&q.rolesCsv.includes("|"+str(who.position||"Mozo")+"|"));
   for(const q of possible){reqs.set(q.id,{inspectionId:id,requirementId:q.id,labelSnapshot:q.label,categorySnapshot:q.category,minQtySnapshot:q.minQty});
    if(r.result==="conforme")answers.set(q.id,{inspectionId:id,requirementId:q.id,labelSnapshot:q.label,groupSnapshot:q.category,status:"OK",correctedAtMillis:null,correctedBy:null,quantity:q.minQty||null});
   }
  }
  if(!reqs.size){error("Inspección sin requisitos: "+key);continue;}
  const bad=[...answers.values()].some(a=>a.status==="BAD");
  const allReviewed=answers.size===reqs.size;
  const original=str(r?.result);
  if(r && original==="observado"&&!bad)error("Observación sin requisito identificable: "+key);
  if(r && original==="conforme"&&bad)error("Conflicto entre Conforme y checklist No cumple: "+key);
  if(r && !["conforme","observado"].includes(original))error("Resultado de inspección desconocido: "+key);
  if(r && !allReviewed){
   // El web admite una observación inicial antes de revisar el resto; Android no.
   // Nunca se cambia silenciosamente el estado histórico a un resultado distinto.
   error("Inspección incompleta con resultado original: "+key+". Completa los requisitos en Web antes de migrar.");
  }
  const finalized=!!r || allReviewed;
  out.inspections.push({id,dateIso:date,staffId:pid,roleSnapshot:str(r?.positionAt||who.position||"Mozo"),workflow:finalized?"FINALIZED":"DRAFT",
   initialResult:finalized?(bad?"OBSERVADO":"CONFORME"):null,
   createdAtMillis:stamp(r?.createdAt||c?.createdAt,dateStamp(date,error,"uniformes"),error,"inspección"),
   updatedAtMillis:stamp(r?.updatedAt||c?.updatedAt,dateStamp(date,error,"uniformes"),error,"inspección")});
  out.snapshots.push(...reqs.values());out.answers.push(...answers.values());
  for(const [idx,e] of arr(r?.changes).entries()){
   out.events.push({id:"web-uniform-change:"+id+":"+idx,inspectionId:id,requirementId:"",action:"MIGRACION_CAMBIO",
    fromStatus:null,toStatus:null,actor:str(e.by||""),note:JSON.stringify(e).slice(0,8000),
    atMillis:stamp(e.at,dateStamp(date,error,"evento"),error,"evento")});
  }
  for(const [idx,e] of arr(r?.correctionEvents).entries()){
   const issue=str(e.issueId),reqId=issue.startsWith("req:")?issue.slice(4):"web-issue:"+issue;
   out.events.push({id:"web-uniform-correction:"+id+":"+idx,inspectionId:id,requirementId:reqId,
    action:e.kind==="corregir"?"CORREGIR":"REVERTIR",fromStatus:"BAD",toStatus:null,
    actor:str(e.by||"Migrado de Web"),note:"Historial de corrección web",atMillis:stamp(e.at,dateStamp(date,error,"evento"),error,"evento")});
  }
  for(const item of arr(c?.items))for(const [idx,e] of arr(item.markingCorrections).entries()){
   out.events.push({id:"web-check-rectification:"+id+":"+str(item.reqId)+":"+idx,inspectionId:id,requirementId:str(item.reqId),
    action:"RECTIFICAR",fromStatus:"BAD",toStatus:null,actor:str(e.by||"Migrado de Web"),note:str(e.reason||"Error de marcación"),
    atMillis:stamp(e.at,dateStamp(date,error,"rectificación"),error,"rectificación")});
  }
 }
 for(const d of arr(data.deliveries)){
  const pid=str(d.staffId),date=str(d.date);if(!verifyStaff(pid,"entregas")||!validDay(date))error("Entrega incompatible: "+str(d.id));
  const status={entregada:"ENTREGADA",devuelta:"DEVUELTA",anulada:"ANULADA"}[d.status||"entregada"];
  if(!status)error("Estado de entrega desconocido: "+str(d.id));
  const reason=d.reason==="Otro"?str(d.reasonOther||"Otro"):str(d.reason||"Otro");
  const material=d.material==="Otro"?str(d.materialOther||"Otro"):str(d.material||"Otro");
  const note=status==="DEVUELTA"?str(d.returnedNote):status==="ANULADA"?str(d.voidReason):"";
  out.deliveries.push({id:str(d.id),staffId:pid,staffNameSnapshot:str(d.staffName||personById.get(pid)?.name||""),
   dateIso:date,material,quantity:n(d.quantity,0),reason,deliveredBy:str(d.deliveredBy),status,closingNote:note,
   closedAtMillis:stamp(d.returnedAt||d.voidedAt,null,error,"cierre entrega"),createdAtMillis:stamp(d.createdAt,dateStamp(date,error,"entrega"),error,"entrega")});
 }
 for(const b of arr(data.briefings)){
   const pid=str(b.staffId),date=str(b.date);if(!validDay(date)||!verifyStaff(pid,"briefing"))error("Briefing inválido: "+str(b.id));
   const presence={asistio:"ASISTIO",mesa:"MESA",falta:"FALTA"}[b.presence],group=n(b.group,0);
   if(!presence||![11,12].includes(group))error("Grupo/estado no válido en briefing "+str(b.id));
   const inform=presence==="MESA"?stamp(b.informedAt,null,error,"informado"):null;
   out.briefingAttendance.push({id:str(b.id),dateIso:date,staffId:pid,staffNameSnapshot:str(b.staffName||personById.get(pid)?.name||""),
    categorySnapshot:n(b.categoryAt,0),groupHour:group,presence,informedAtMillis:inform,
    informedBy:inform?"Migrado de Web (responsable no registrado)":null,
    createdAtMillis:stamp(b.createdAt,dateStamp(date,error,"briefing"),error,"briefing"),
    updatedAtMillis:stamp(b.updatedAt,dateStamp(date,error,"briefing"),error,"briefing")});
 }
 for(const [key,v] of Object.entries(obj(data.sessions))){
  const m=/^(\d{4}-\d{2}-\d{2}):(11|12)$/.exec(key),date=str(v.date||m?.[1]),group=n(v.group,m?Number(m[2]):0),topic=str(v.topics);
  if(!topic.trim())continue;
  if(!validDay(date)||![11,12].includes(group)||topic.length>3000)error("Sesión de briefing incompatible: "+key);
  out.briefingSessions.push({dateIso:date,groupHour:group,topics:topic,
   updatedAtMillis:stamp(v.updatedAt,dateStamp(date,error,"sesión"),error,"sesión")});
 }
 const taskIds=new Set();
 for(const [area,labels] of Object.entries(GROUPS))labels.forEach((label,i)=>{
  const slug=s=>s.normalize("NFD").replace(/[\u0300-\u036f]/g,"").toLowerCase().replace(/[^a-z0-9]+/g,"-").replace(/^-+|-+$/g,"");
  const id=slug(area)+"-"+slug(label);taskIds.add(id);
  out.functionTasks.push({id,area,label,periodicity:"DIARIA",isActive:true,sortOrder:i,updatedAtMillis:0});
 });
 for(const f of arr(data.functionChecks)){
  const pid=str(f.staffId),date=str(f.date),task=str(f.taskId);
  if(!verifyStaff(pid,"funciones")||!validDay(date))error("Función incompatible: "+str(f.id));
  if(!taskIds.has(task))error("Función desconocida: "+task+" ("+str(f.id)+")");
  if(!AREAS.includes(str(f.area)))error("Área de función desconocida: "+str(f.area));
  const result={ok:"OK",bad:"BAD",pending:"PENDING"}[f.result];
  if(!result)error("Resultado de función desconocido: "+str(f.id));
  out.functionChecks.push({id:str(f.id),dateIso:date,staffId:pid,staffNameSnapshot:str(f.staffName||personById.get(pid)?.name||""),
   taskId:task,taskLabelSnapshot:str(f.taskLabel),areaSnapshot:str(f.area),
   periodicitySnapshot:"DIARIA",result,note:str(f.note||""),correctedAtMillis:stamp(f.correctedAt,null,error,"función"),
   correctionNote:str(f.correctionNote||""),voidedAtMillis:stamp(f.voidedAt,null,error,"anulación función"),
   createdAtMillis:stamp(f.createdAt,dateStamp(date,error,"función"),error,"función"),
   updatedAtMillis:stamp(f.updatedAt,dateStamp(date,error,"función"),error,"función")});
  for(const [idx,event] of arr(f.changes).entries())out.functionEvents.push({id:"web-function-event:"+str(f.id)+":"+idx,checkId:str(f.id),
    action:"MIGRACION_CAMBIO",beforeValue:JSON.stringify(event.before||{}),afterValue:str(f.result||""),
    actor:str(event.by||""),note:JSON.stringify(event).slice(0,8000),
    atMillis:stamp(event.at,dateStamp(date,error,"cambio"),error,"cambio")});
 }
 const incidentTypeIds=new Set();
 for(const t of arr(data.incidentTypes)){
   const id=str(t.id);if(incidentTypeIds.has(id)||!id)error("Tipo de incidente duplicado.");
   incidentTypeIds.add(id);
   out.incidentTypes.push({id,category:str(t.category||"producto"),label:str(t.label),
    isActive:t.active!==false,createdAtMillis:stamp(t.createdAt,0,error,"tipo"),
    updatedAtMillis:stamp(t.updatedAt,0,error,"tipo")});
 }
 for(const x of arr(data.incidents)){
  const date=str(x.occurredDate),time=str(x.occurredTime),pid=str(x.personId||"");
  if(pid&&!verifyStaff(pid,"incidente"))error("Responsable sin ficha en incidente: "+str(x.code));
  if(!validDay(date)||!/^\d{2}:\d{2}$/.test(time))error("Fecha/hora incidente incorrecta: "+str(x.code));
  const act=arr(x.actions);const typeId=incidentTypeIds.has(str(x.typeId))?str(x.typeId):"";
  const status=str(x.status||"registrado");
  let actionError=false;
  for(const [idx,a] of act.entries()){
   const kind=str(a.kind||"correccion"),desc=str(a.description),actor=str(a.by||x.reporter);
   if(!["correccion","comunicacion","investigacion","prevencion","verificacion","reapertura","anulacion"].includes(kind)||desc.length<5||actor.length<2){error("Acción de incidente no compatible: "+str(x.code));actionError=true;}
   out.incidentActions.push({id:str(a.id||"web-action:"+x.id+":"+idx),incidentId:str(x.id),
    kind,description:desc,actor,atMillis:stamp(a.at,dateStamp(date,error,"acción"),error,"acción"),
    statusBefore:str(a.statusBefore||""),statusAfter:str(a.statusAfter||status),
    confirmedOrigin:str(a.confirmedOrigin||""),assignedStaffId:str(a.personId||"")});
  }
  if(!["registrado","en-atencion","verificacion","cerrado","anulado"].includes(status))error("Estado de incidente desconocido: "+str(x.code));
  const row={id:str(x.id),code:str(x.code),category:str(x.category),occurredDate:date,occurredTime:time,
   detectedArea:str(x.detectedArea),originArea:str(x.originArea||""),confirmedOrigin:str(x.confirmedOrigin||""),
   productOrProcess:str(x.product||x.productOrProcess),typeId,typeLabelSnapshot:str(x.typeLabel||x.typeLabelSnapshot||"Otro"),
   description:str(x.description),priority:str(x.priority),reporter:str(x.reporter),
   impact:str(x.impact||"No determinado"),immediateAction:str(x.immediateAction),
   assignedStaffId:pid,assignedStaffNameSnapshot:str(x.personName||personById.get(pid)?.name||""),
   status,createdAtMillis:stamp(x.createdAt,dateStamp(date,error,"incidente"),error,"incidente"),
   updatedAtMillis:stamp(x.updatedAt,dateStamp(date,error,"incidente"),error,"incidente"),
   closedAtMillis:stamp(x.closedAt,null,error,"cierre"),closedBy:str(x.closedBy||""),
   voidedAtMillis:stamp(x.voidedAt,null,error,"anulación"),voidReason:str(x.voidReason||"")};
  if(!/^INC-\d{4}-\d{4,}$/.test(row.code)||row.code.slice(4,8)!==date.slice(0,4))error("Código de incidente incompatible: "+row.code);
  if(!INCIDENT_AREAS.includes(row.detectedArea)||row.originArea&&!INCIDENT_AREAS.includes(row.originArea)||row.confirmedOrigin&&!INCIDENT_AREAS.includes(row.confirmedOrigin))error("Área de incidente incompatible: "+row.code);
  if(!INCIDENT_IMPACTS.includes(row.impact)||!["baja","media","alta","critica"].includes(row.priority))error("Impacto o prioridad incompatible: "+row.code);
  if(!actionError)out.incidents.push(row);
 }
 // Exactness: guard against duplicate primary/natural keys instead of silently dropping.
 for(const key of ["staff","vacations","inspections","events","requirements","deliveries","briefingAttendance",
   "briefingEvents","functionTasks","functionChecks","functionEvidence","functionEvents","incidentTypes","incidents","incidentActions"]){
   const seen=new Set();for(const item of out[key]){if(!item.id)error("Registro sin identificador: "+key);else if(seen.has(item.id))error("ID repetido en "+key+": "+item.id);seen.add(item.id);}
 }
 for(const [key,make] of [
   ["inspections",r=>r.dateIso+"|"+r.staffId],
   ["briefingAttendance",r=>r.dateIso+"|"+r.staffId],
   ["briefingSessions",r=>r.dateIso+"|"+r.groupHour],
   ["functionChecks",r=>r.voidedAtMillis?null:r.dateIso+"|"+r.staffId+"|"+r.taskId]
 ]){
   const seen=new Set();for(const item of out[key]){const k=make(item);if(k&&seen.has(k))error("Registros duplicados en "+key+": "+k);if(k)seen.add(k);}
 }
 if(existingAndroid){
   if(existingAndroid.app!=="lasflores-android"||existingAndroid.formatVersion!==5)error("La copia base Android debe ser JSON v5.");
   else{
    const base=existingAndroid;
    // Comparación exacta. Los registros que tienen el mismo ID y difieren son conflictos, no se sobreescriben.
    for(const key of ["staff","vacations","overrides","inspections","answers","events","activityDays","requirements","snapshots",
      "deliveries","briefingAttendance","briefingSessions","briefingEvents","functionTasks","functionChecks","functionEvidence",
      "functionEvents","incidentTypes","incidents","incidentActions"]){
      const fields={
       overrides:x=>x.dateIso+"|"+x.staffId,answers:x=>x.inspectionId+"|"+x.requirementId,
       activityDays:x=>x.dateIso+"|"+x.module,snapshots:x=>x.inspectionId+"|"+x.requirementId,
       briefingSessions:x=>x.dateIso+"|"+x.groupHour
      };
      const pk=fields[key]||(x=>x.id),indexed=new Map(arr(base[key]).map(x=>[pk(x),x]));
      for(const r of out[key]){const k=pk(r),old=indexed.get(k);
       if(old && JSON.stringify(old)!==JSON.stringify(r))error("Conflicto en "+key+": "+k);
       else if(!old)indexed.set(k,r);
      }
      out[key]=[...indexed.values()];
    }
    const names=new Map();
    for(const s of out.staff){const n=normalizedName(s.fullName);if(names.has(n)&&names.get(n)!==s.id)error("Nombre duplicado entre Web y Android: "+s.fullName);names.set(n,s.id);}
    if(base.webSourceArchive&&JSON.stringify(base.webSourceArchive)!==JSON.stringify(data))error("La copia Android ya tiene otro archivo web archivado. Revisa ambas copias antes de fusionar.");
    if(arr(base.inspections).length)warn("La fusión conserva registros Android existentes; verifica especialmente uniformes y reportes.");
   }
 }
 const checkSize=JSON.stringify(out).length;
 if(checkSize>29*1024*1024)error("El respaldo convertido supera el límite de tamaño para restaurar Android (30 MB).");
 const counts={staff:out.staff.length,inspections:out.inspections.length,checklists:arr(data.checklists).length,
  briefings:out.briefingAttendance.length,functions:out.functionChecks.length,incidents:out.incidents.length,
  deliveries:out.deliveries.length,uniformAnswers:out.answers.length};
 if(out.incidents.length||out.inspections.length)warn("El original Web queda incluido en el JSON convertido para consultar datos históricos no representables en Room.");
 return {ok:errors.length===0,errors,warnings,counts,converted:errors.length?null:out};
}
root.LasFloresPortable={convert,reportCounts};
})(typeof window!=="undefined"?window:globalThis);
