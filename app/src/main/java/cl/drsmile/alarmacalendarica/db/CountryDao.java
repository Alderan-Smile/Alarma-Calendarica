package cl.drsmile.alarmacalendarica.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface CountryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<CountryEntity> countries);

    @Query("SELECT * FROM countries ORDER BY name")
    List<CountryEntity> getAll();
}
