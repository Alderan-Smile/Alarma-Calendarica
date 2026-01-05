package cl.drsmile.alarmacalendarica.conn;

import java.util.List;

import cl.drsmile.alarmacalendarica.dto.CountryDTO;
import cl.drsmile.alarmacalendarica.dto.HolidayDTO;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

public interface NagerDateService {
    @GET("api/v3/AvailableCountries")
    Call<List<CountryDTO>> getAvailableCountries();

    @GET("api/v3/PublicHolidays/{year}/{countryCode}")
    Call<List<HolidayDTO>> getPublicHolidays(@Path("year") int year, @Path("countryCode") String countryCode);
}