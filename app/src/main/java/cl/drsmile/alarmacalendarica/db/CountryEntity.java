package cl.drsmile.alarmacalendarica.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity(tableName = "countries")
public class CountryEntity {
    @PrimaryKey
    @NonNull
    public String countryCode;
    public String name;
}
