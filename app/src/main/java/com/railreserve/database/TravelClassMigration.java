package com.railreserve.database;

import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.List;

/** SQLite's create/copy/drop/rename procedure, inside SQLiteOpenHelper's upgrade transaction. */
final class TravelClassMigration {
    static final String ALLOWED = "'SL','3A','2A','1A','3E','CC','EC','2S'";
    private TravelClassMigration() { }

    static void expand(SQLiteDatabase db) {
        if (!db.inTransaction() || DatabaseUtils.longForQuery(db,"PRAGMA foreign_keys",null) != 0)
            throw new IllegalStateException("Class migration requires the configured upgrade transaction.");
        Long sequence = null;
        try (Cursor c=db.rawQuery("SELECT seq FROM sqlite_sequence WHERE name='bookings'",null)) {
            if(c.moveToFirst()) sequence=c.getLong(0);
        }
        List<String[]> objects=new ArrayList<>();
        try(Cursor c=db.rawQuery("SELECT type,name,sql FROM sqlite_master WHERE sql IS NOT NULL AND "
                + "(type IN ('trigger','view') OR (type='index' AND tbl_name IN ('seats','bookings','simulation_scenarios'))) "
                + "ORDER BY CASE type WHEN 'view' THEN 0 WHEN 'index' THEN 1 ELSE 2 END",null)) {
            while(c.moveToNext()) objects.add(new String[]{c.getString(0),c.getString(1),c.getString(2)});
        }
        // Preserve their definitions; temporarily remove references while the tables are replaced.
        for(String[] object:objects) if(!"index".equals(object[0]))
            db.execSQL("DROP " + object[0] + " " + identifier(object[1]));
        for(String table:new String[]{"seats","bookings","simulation_scenarios"}) {
            String sql=DatabaseUtils.stringForQuery(db,"SELECT sql FROM sqlite_master WHERE type='table' AND name=?",new String[]{table});
            String revised=sql.replace("travel_class IN ('SL','3A')","travel_class IN ("+ALLOWED+")");
            if(revised.equals(sql) && !sql.contains("travel_class IN ("+ALLOWED+")"))
                throw new IllegalStateException("Unrecognized class constraint; the original database was preserved.");
            String copy="class_upgrade_"+table;
            revised=revised.replaceFirst("(?i)^CREATE TABLE\\s+(?:IF NOT EXISTS\\s+)?[\"`\\[]?"+table+"[\"`\\]]?", "CREATE TABLE "+copy);
            if(!revised.startsWith("CREATE TABLE "+copy)) throw new IllegalStateException("Unsupported table definition.");
            db.execSQL(revised);
            db.execSQL("INSERT INTO "+copy+" SELECT * FROM "+table);
            if(DatabaseUtils.queryNumEntries(db,copy)!=DatabaseUtils.queryNumEntries(db,table))
                throw new IllegalStateException("Class migration row count mismatch.");
            try(Cursor c=db.rawQuery("SELECT * FROM "+table+" EXCEPT SELECT * FROM "+copy,null)) {
                if(c.moveToFirst()) throw new IllegalStateException("Class migration changed a saved record.");
            }
            db.execSQL("DROP TABLE "+table);
            db.execSQL("ALTER TABLE "+copy+" RENAME TO "+table);
        }
        for(String[] object:objects) db.execSQL(object[2]);
        if(sequence!=null) {
            db.execSQL("DELETE FROM sqlite_sequence WHERE name='bookings'");
            db.execSQL("INSERT INTO sqlite_sequence(name,seq) VALUES('bookings',?)",new Object[]{sequence});
        }
        try(Cursor c=db.rawQuery("PRAGMA foreign_key_check",null)) {
            if(c.moveToFirst()) throw new IllegalStateException("Class migration failed its foreign-key check.");
        }
        if(!"ok".equals(DatabaseUtils.stringForQuery(db,"PRAGMA quick_check",null)))
            throw new IllegalStateException("Class migration failed its integrity check.");
    }
    private static String identifier(String name) { return "\""+name.replace("\"","\"\"")+"\""; }
}
