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

if __name__=="__main__":
    unittest.main()
