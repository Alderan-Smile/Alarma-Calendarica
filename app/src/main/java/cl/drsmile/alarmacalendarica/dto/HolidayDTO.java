package cl.drsmile.alarmacalendarica.dto;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HolidayDTO {

    private Date date;
    private String localName;
    private String name;
    private String countryCode;
    private Integer year;
}
