package pe.lasflores.controlinterno.data

import androidx.room3.Database
import androidx.room3.RoomDatabase

/**
 * La versión 1 contiene solo Personal. Los próximos módulos agregarán tablas mediante
 * migraciones explícitas. Nunca usar fallbackToDestructiveMigration().
 */
@Database(entities = [StaffEntity::class], version = 1, exportSchema = true)
abstract class LasFloresDatabase : RoomDatabase() {
    abstract fun staffDao(): StaffDao
}
