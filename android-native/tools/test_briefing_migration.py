#!/usr/bin/env python3
"""Regression test against the SQL actually used by Room for briefing migration."""
import re
import sqlite3
import unittest
from pathlib import Path

SOURCE = Path(__file__).resolve().parents[1] / "app/src/main/java/pe/lasflores/controlinterno/data/LasFloresDatabase.kt"

def sql_for(name):
    source = SOURCE.read_text(encoding="utf-8")
    body = source.split("val " + name + " =", 1)[1].split("\nval MIGRATION_", 1)[0]
    return [a or b for a, b in re.findall(r'connection\.execSQL\("""(.*?)"""(?:\.trimIndent\(\))?\)|connection\.execSQL\("([^"]*)"\)', body, re.S)]

class BriefingMigrationTest(unittest.TestCase):
    def test_existing_rows_preserved_and_new_sessions_supported(self):
        db=sqlite3.connect(":memory:")
        for sql in sql_for("MIGRATION_4_5"):
            db.execute(sql)
        db.execute("INSERT INTO briefing_sessions VALUES (?,?,?,?)",("2026-11-10",11,"Indicaciones anteriores",123))
        rows=[
            ("a","2026-11-10","worker-b","Prueba B",2,11,"ASISTIO",None,None,101,102),
            ("b","2026-11-10","worker-a","Prueba A",3,11,"MESA",110,"Responsable",103,111),
            ("c","2026-11-10","worker-c","Prueba C",4,12,"MESA",None,None,104,105)
        ]
        db.executemany("INSERT INTO briefing_attendance VALUES (?,?,?,?,?,?,?,?,?,?,?)",rows)
        db.execute("INSERT INTO briefing_events VALUES (?,?,?,?,?,?,?,?,?)",("event","2026-11-10","worker-a","COMUNICACION","PENDIENTE","INFORMADO","Responsable","",110))
        for sql in sql_for("MIGRATION_7_8"):
            db.execute(sql)
        original=db.execute("SELECT id,dateIso,staffId,staffNameSnapshot,categorySnapshot,groupHour,presence,informedAtMillis,informedBy,createdAtMillis,updatedAtMillis FROM briefing_attendance ORDER BY id").fetchall()
        self.assertEqual(original,rows)
        sessions=db.execute("SELECT groupHour,topics,scheduledTime,actualTime,status,participantIds,updatedAtMillis FROM briefing_sessions ORDER BY groupHour").fetchall()
        self.assertEqual(sessions,[(11,"Indicaciones anteriores","11:00",None,"HISTORICO","worker-a\nworker-b",123),(12,"","12:00",None,"HISTORICO","worker-c",105)])
        self.assertEqual(db.execute("SELECT communicationState,communicationResponsible,communicationUpdatedAtMillis FROM briefing_attendance ORDER BY id").fetchall(),[("NO_APLICA","",None),("INFORMADO_HISTORICO","Responsable",110),("PENDIENTE","",None)])
        self.assertEqual(db.execute("SELECT count(*) FROM briefing_events").fetchone()[0],1)
        # The same person can participate twice, but not twice in the same session.
        db.execute("INSERT INTO briefing_attendance(id,dateIso,staffId,staffNameSnapshot,categorySnapshot,groupHour,presence,createdAtMillis,updatedAtMillis) VALUES (?,?,?,?,?,?,?,?,?)",("d","2026-11-10","worker-a","Prueba A",3,12,"ASISTIO",120,120))
        with self.assertRaises(sqlite3.IntegrityError):
            db.execute("INSERT INTO briefing_attendance(id,dateIso,staffId,staffNameSnapshot,categorySnapshot,groupHour,presence,createdAtMillis,updatedAtMillis) VALUES (?,?,?,?,?,?,?,?,?)",("e","2026-11-10","worker-a","Prueba A",3,12,"ASISTIO",121,121))
        db.close()


    def test_personal_and_uniform_profiles_preserve_v8_rows(self):
        db=sqlite3.connect(":memory:")
        db.execute("""CREATE TABLE staff (id TEXT PRIMARY KEY,fullName TEXT,role TEXT,category INTEGER,briefingGroup INTEGER,scheduleMode TEXT,restDaysMask INTEGER,isActive INTEGER,createdAtMillis INTEGER,updatedAtMillis INTEGER)""")
        db.execute("INSERT INTO staff VALUES (?,?,?,?,?,?,?,?,?,?)",("p","Prueba","Mozo",3,11,"weekend",4,1,1,2))
        db.execute("""CREATE TABLE uniform_requirements (id TEXT PRIMARY KEY,label TEXT,category TEXT,minQty INTEGER,rolesCsv TEXT,isActive INTEGER,sortOrder INTEGER,updatedAtMillis INTEGER)""")
        db.execute("INSERT INTO uniform_requirements VALUES (?,?,?,?,?,?,?,?)",("m","Moñera","Implementos",1,"|Mozo|",1,0,1))
        db.execute("INSERT INTO uniform_requirements VALUES (?,?,?,?,?,?,?,?)",("u","Mandil","Uniforme",0,"|Mozo|",1,1,1))
        db.execute("""CREATE TABLE inspection_requirements (inspectionId TEXT,requirementId TEXT,labelSnapshot TEXT,categorySnapshot TEXT,minQtySnapshot INTEGER,PRIMARY KEY(inspectionId,requirementId))""")
        db.execute("INSERT INTO inspection_requirements VALUES (?,?,?,?,?)",("i","m","Moñera","Implementos",1))
        db.execute("""CREATE TABLE uniform_inspections (id TEXT PRIMARY KEY,dateIso TEXT,staffId TEXT,roleSnapshot TEXT,workflow TEXT,initialResult TEXT,createdAtMillis INTEGER,updatedAtMillis INTEGER)""")
        db.execute("INSERT INTO uniform_inspections VALUES (?,?,?,?,?,?,?,?)",("i","2026-11-10","p","Mozo","FINALIZED","OBSERVADO",1,2))
        db.execute("""CREATE TABLE uniform_answers (inspectionId TEXT,requirementId TEXT,status TEXT)""")
        db.execute("INSERT INTO uniform_answers VALUES (?,?,?)",("i","m","BAD"))
        for sql in sql_for("MIGRATION_8_9"):
            db.execute(sql)
        self.assertEqual(db.execute("SELECT scheduleMode,restDaysMask,weekendDaysMask,uniformProfile,scheduleHistory FROM staff").fetchone(),("weekend",4,65,"SIN_DEFINIR",""))
        self.assertEqual(db.execute("SELECT profilesCsv FROM uniform_requirements WHERE id='m'").fetchone()[0],"|FEMENINO|")
        self.assertEqual(db.execute("SELECT profilesCsv FROM uniform_requirements WHERE id='u'").fetchone()[0],"|MASCULINO|FEMENINO|GENERAL|")
        self.assertEqual(db.execute("SELECT applicable FROM inspection_requirements").fetchone()[0],1)
        self.assertEqual(db.execute("SELECT initialResult,uniformProfileSnapshot FROM uniform_inspections").fetchone(),("OBSERVADO","SIN_DEFINIR"))
        self.assertEqual(db.execute("SELECT * FROM uniform_answers").fetchall(),[("i","m","BAD")])
        db.execute("UPDATE staff SET uniformProfile='MASCULINO', weekendDaysMask=64")
        self.assertEqual(db.execute("SELECT applicable FROM inspection_requirements").fetchone()[0],1)
        db.close()

    def test_journey_migration_only_adds_new_tables(self):
        db=sqlite3.connect(":memory:")
        db.execute("CREATE TABLE staff (id TEXT PRIMARY KEY, fullName TEXT)")
        db.execute("INSERT INTO staff VALUES ('p','Prueba')")
        db.execute("CREATE TABLE uniform_inspections (id TEXT PRIMARY KEY, initialResult TEXT)")
        db.execute("INSERT INTO uniform_inspections VALUES ('i','OBSERVADO')")
        for sql in sql_for("MIGRATION_9_10"):
            db.execute(sql)
        self.assertEqual(db.execute("SELECT * FROM staff").fetchall(),[("p","Prueba")])
        self.assertEqual(db.execute("SELECT * FROM uniform_inspections").fetchall(),[("i","OBSERVADO")])
        for table in ("journey_preferences","journey_reminders","journey_occurrences","journey_notifications"):
            self.assertEqual(db.execute("SELECT COUNT(*) FROM "+table).fetchone()[0],0)
        db.execute("INSERT INTO journey_occurrences VALUES ('manual-x:2026-11-12','2026-11-12',10,NULL,NULL,NULL,0)")
        with self.assertRaises(sqlite3.IntegrityError):
            db.execute("INSERT INTO journey_occurrences VALUES ('manual-x:2026-11-12','2026-11-12',10,NULL,NULL,NULL,0)")
        db.close()

    def test_phone_migration_keeps_journey_and_staff(self):
        db=sqlite3.connect(":memory:")
        db.execute("CREATE TABLE staff (id TEXT PRIMARY KEY, fullName TEXT)")
        db.execute("INSERT INTO staff VALUES ('p','Prueba')")
        db.execute("CREATE TABLE journey_preferences (id INTEGER PRIMARY KEY, enabled INTEGER, startTime TEXT)")
        db.execute("INSERT INTO journey_preferences VALUES (1,1,'08:30')")
        for sql in sql_for("MIGRATION_10_11"):
            db.execute(sql)
        self.assertEqual(db.execute("SELECT * FROM staff").fetchall(),[("p","Prueba")])
        self.assertEqual(db.execute("SELECT * FROM journey_preferences").fetchone(),(1,1,"08:30",1,"12:30"))
        self.assertEqual(db.execute("SELECT COUNT(*) FROM phone_checks").fetchone()[0],0)
        db.execute("INSERT INTO phone_checks VALUES ('2026-11-12','p','Prueba','Mozo','YES','','',100)")
        with self.assertRaises(sqlite3.IntegrityError):
            db.execute("INSERT INTO phone_checks VALUES ('2026-11-12','p','Prueba','Mozo','NO','','',101)")
        db.close()

    def test_staff_delete_guard_checks_all_links_and_only_pristine_phone_rows(self):
        dao=(SOURCE.parent / "StaffDao.kt").read_text(encoding="utf-8")
        guard=dao.split('@Query("""SELECT \'Uniformes\'',1)[1].split('""")',1)[0]
        guard="SELECT 'Uniformes'"+guard
        cleanup=re.search(r'@Query\("(DELETE FROM phone_checks WHERE staffId=:id[^"]+)"\)',dao).group(1)
        delete=re.search(r'@Query\("(DELETE FROM staff WHERE id=:id)"\)',dao).group(1)
        db=sqlite3.connect(":memory:")
        # Minimal schema of the actual referenced fields, not a simulated policy.
        db.execute("CREATE TABLE staff(id TEXT PRIMARY KEY)")
        for table in ("uniform_inspections","briefing_attendance","briefing_events","function_checks","material_deliveries","staff_vacations","staff_day_overrides"):
            db.execute("CREATE TABLE "+table+"(staffId TEXT)")
        for table in ("incidents","incident_actions"):
            db.execute("CREATE TABLE "+table+"(assignedStaffId TEXT)")
        db.execute("CREATE TABLE briefing_sessions(participantIds TEXT)")
        db.execute("CREATE TABLE phone_checks(staffId TEXT,status TEXT,updatedAtMillis INTEGER,reason TEXT,note TEXT)")
        def blockers(id):
            return [row[0] for row in db.execute(guard,{"id":id})]
        for id in ("p","pp","other"):
            db.execute("INSERT INTO staff VALUES (?)",(id,))
        db.execute("INSERT INTO briefing_sessions VALUES ('pp\nother')")
        self.assertEqual(blockers("p"),[])
        self.assertIn("Convocatorias de briefings",blockers("pp"))
        db.execute("INSERT INTO phone_checks VALUES ('p','PENDING',0,'','')")
        self.assertEqual(blockers("p"),[])
        for table,column,label in [
            ("uniform_inspections","staffId","Uniformes"),("briefing_attendance","staffId","Asistencia de briefings"),
            ("briefing_events","staffId","Auditoría de briefings"),("function_checks","staffId","Funciones"),
            ("incidents","assignedStaffId","Incidentes"),("incident_actions","assignedStaffId","Historial de incidentes"),
            ("material_deliveries","staffId","Entrega de materiales"),("staff_vacations","staffId","Vacaciones"),
            ("staff_day_overrides","staffId","Situaciones diarias")
        ]:
            db.execute("INSERT INTO "+table+"("+column+") VALUES ('p')")
            self.assertEqual(blockers("p"),[label])
            db.execute("DELETE FROM "+table)
        for status,at,reason,note in [("YES",1,"",""),("NO",1,"",""),("NA",1,"Permiso",""),("PENDING",2,"","")]:
            db.execute("INSERT INTO phone_checks VALUES ('p',?,?,?,?)",(status,at,reason,note))
            self.assertEqual(blockers("p"),["Celulares"])
            db.execute(cleanup,{"id":"p"})
            self.assertEqual(db.execute("SELECT status,updatedAtMillis FROM phone_checks WHERE staffId='p'").fetchall(),[(status,at)])
            db.execute("DELETE FROM phone_checks WHERE staffId='p'")
        db.execute("INSERT INTO phone_checks VALUES ('p','PENDING',0,'','')")
        db.execute("INSERT INTO phone_checks VALUES ('other','YES',1,'','')")
        self.assertEqual(blockers("p"),[])
        db.execute(cleanup,{"id":"p"})
        db.execute(delete,{"id":"p"})
        self.assertEqual(db.execute("SELECT id FROM staff ORDER BY id").fetchall(),[("other",),("pp",)])
        self.assertEqual(db.execute("SELECT staffId FROM phone_checks").fetchall(),[("other",)])
        self.assertIn("Convocatorias de briefings",blockers("pp"))
        db.close()

if __name__=="__main__":
    unittest.main()
