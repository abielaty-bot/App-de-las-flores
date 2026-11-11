const test = require("node:test");
const assert = require("node:assert/strict");
const vm = require("node:vm");
const fs = require("node:fs");
const path = require("node:path");
const context = { window: {} };
vm.runInNewContext(fs.readFileSync(path.join(__dirname, "../migration/portable.js"), "utf8"), context);
const convert = context.window.LasFloresPortable.convert;
function fixture() {
 return {
  app: "lasflores-control", version: 2, rosters:{},
  staff:[{id:"p-1",name:"Trabajador de prueba",position:"Mozo",
    category:"4",briefingGroup:"11",restDays:[2],active:true,
    createdAt:"2026-11-01T10:00:00Z"}],
  inspections:[{id:"i-1",date:"2026-11-10",staffId:"p-1",
    result:"conforme",createdAt:"2026-11-10T12:00:00Z"}],
  requirements:[{id:"camisa",label:"Uniforme limpio",group:"Uniforme",
    positions:["Mozo"],minQty:0,active:true}],
  checklists:[],issueTypes:[],deliveries:[],briefings:[
    {id:"b-1",staffId:"p-1",date:"2026-11-10",presence:"mesa",group:"11",
      informedAt:"2026-11-10T16:00:00Z"}],
  sessions:{"2026-11-10:11":{date:"2026-11-10",group:"11",topics:"Repasar comandos"}},
  functionChecks:[],incidents:[],incidentTypes:[],audit:[{action:"Prueba",at:"2026-11-10T12:00:00Z"}]
 };
}
test("convierte fichas, uniformes y briefings y conserva original", () => {
 const web=fixture(),result=convert(web);
 assert.equal(result.ok,true,JSON.stringify(result.errors));
 assert.equal(result.converted.app,"lasflores-android");
 assert.equal(result.converted.formatVersion,5);
 assert.equal(result.converted.staff[0].category,4);
 assert.equal(result.converted.staff[0].restDaysMask,4);
 assert.equal(result.converted.inspections[0].initialResult,"CONFORME");
 assert.equal(result.converted.answers[0].status,"OK");
 assert.equal(result.converted.briefingAttendance[0].presence,"MESA");
 assert.equal(result.converted.briefingAttendance[0].informedBy,"Migrado de Web (responsable no registrado)");
 assert.deepEqual(JSON.stringify(result.converted.webSourceArchive),JSON.stringify(web));
 assert.equal(result.converted.webSourceArchive.audit.length,1);
});
test("conserva fuente en envoltorio portable", () => {
 const x=convert({app:"lasflores-transfer",formatVersion:1,payload:fixture()});
 assert.equal(x.ok,true,JSON.stringify(x.errors));
});
test("bloquea fuentes desconocidas y archivos corruptos", () => {
 assert.equal(convert({app:"unknown"}).ok,false);
 assert.equal(convert({app:"lasflores-control",staff:[],inspections:[]}).ok,false);
});
test("bloquea ID/persona duplicada", () => {
 const f=fixture();f.staff.push({...f.staff[0],id:"p-2"});
 const result=convert(f);assert.equal(result.ok,false);assert.match(result.errors.join(" "),/mismo nombre/);
});
test("bloquea inspecciones observadas e incompletas", () => {
 const f=fixture();
 f.inspections[0].result="observado";
 f.inspections[0].issues=["req:camisa"];
 f.checklists=[{id:"c-1",date:"2026-11-10",staffId:"p-1",items:[
   {reqId:"camisa",status:null,label:"Uniforme limpio",group:"Uniforme"}
 ]}];
 const x=convert(f);assert.equal(x.ok,false);
 assert.match(x.errors.join(" "),/incompleta/);
});
test("bloquea asignaciones a personas ausentes del archivo", () => {
 const f=fixture();f.briefings[0].staffId="ausente";
 const x=convert(f);assert.equal(x.ok,false);
});
test("bloquea conflictos en fusión, no pisa Android", () => {
 const f=fixture(),base=convert(f).converted;
 base.staff[0].category=2;
 const x=convert(f,base);assert.equal(x.ok,false);
 assert.match(x.errors.join(" "),/Conflicto/);
});
test("copia funciones y sus hechos corregidos", () => {
 const f=fixture();
 f.functionChecks=[{id:"f-1",date:"2026-11-10",staffId:"p-1",
   taskId:"oficina-i-caja",taskLabel:"Caja",area:"Oficina I",
   result:"bad",note:"Pendiente",correctedAt:"2026-11-10T16:00:00Z",
   correctionNote:"Se corrigió",changes:[{at:"2026-11-10T16:00:00Z",before:{result:"pending"}}]}];
 const x=convert(f);assert.equal(x.ok,true,JSON.stringify(x.errors));
 assert.equal(x.converted.functionChecks[0].result,"BAD");
 assert.equal(x.converted.functionEvents.length,1);
});
