package cl.drsmile.alarmacalendarica.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import java.util.Date;
import java.util.List;

@Dao
public interface HolidayDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertAll(List<HolidayEntity> holidays);

    @Query("SELECT * FROM holidays WHERE countryCode = :countryCode AND year = :year ORDER BY date")
    List<HolidayEntity> getByCountryAndYear(String countryCode, int year);

    @Query("DELETE FROM holidays WHERE countryCode = :countryCode AND year = :year")
    void deleteByCountryAndYear(String countryCode, int year);

    // delete only non-custom (official) holidays for a country/year so custom ones remain
    @Query("DELETE FROM holidays WHERE countryCode = :countryCode AND year = :year AND isCustom = 0")
    void deleteNonCustomByCountryAndYear(String countryCode, int year);

    @Query("SELECT * FROM holidays WHERE date BETWEEN :start AND :end ORDER BY date")
    List<HolidayEntity> getInRange(Date start, Date end);

    @Query("SELECT * FROM holidays WHERE date BETWEEN :start AND :end AND countryCode = :countryCode ORDER BY date")
    List<HolidayEntity> getInRangeForCountry(Date start, Date end, String countryCode);

    @Query("DELETE FROM holidays WHERE isCustom = 1 AND date BETWEEN :start AND :end AND countryCode = :countryCode")
    int deleteCustomInRange(Date start, Date end, String countryCode);

    @Query("DELETE FROM holidays WHERE id = :id")
    void deleteById(long id);

    @Query("DELETE FROM holidays WHERE date BETWEEN :start AND :end AND countryCode = :countryCode")
    void deleteInRangeForCountry(Date start, Date end, String countryCode);

    @Update(onConflict = OnConflictStrategy.REPLACE)
    void update(HolidayEntity holiday);
}
