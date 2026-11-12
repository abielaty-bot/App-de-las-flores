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

if __name__=="__main__":
    unittest.main()
