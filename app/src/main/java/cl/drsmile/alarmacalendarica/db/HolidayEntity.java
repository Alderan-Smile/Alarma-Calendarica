package cl.drsmile.alarmacalendarica.db;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity(tableName = "holidays", indices = {@Index(value = {"countryCode","year"})})
public class HolidayEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public Date date;
    public String localName;
    public String name;
    public String countryCode;
    public Integer year;
    // nuevo campo para marcar feriados creados por el usuario
    public boolean isCustom = false;
}
