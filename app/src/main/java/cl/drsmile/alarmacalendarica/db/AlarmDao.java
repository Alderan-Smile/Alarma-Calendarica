package cl.drsmile.alarmacalendarica.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface AlarmDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(AlarmEntity alarm);

    @Update
    void update(AlarmEntity alarm);

    @Delete
    void delete(AlarmEntity alarm);

    @Query("SELECT * FROM alarms ORDER BY nextTriggerAt")
    List<AlarmEntity> getAll();

    @Query("SELECT * FROM alarms WHERE id = :id LIMIT 1")
    AlarmEntity getById(long id);
}
