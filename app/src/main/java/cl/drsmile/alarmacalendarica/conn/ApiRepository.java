package cl.drsmile.alarmacalendarica.conn;

import java.io.IOException;
import java.util.List;

import cl.drsmile.alarmacalendarica.dto.CountryDTO;
import cl.drsmile.alarmacalendarica.dto.HolidayDTO;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ApiRepository {
    private final NagerDateService service;

    public ApiRepository() {
        service = ApiClient.getRetrofit().create(NagerDateService.class);
    }

    public void fetchCountries(Callback<List<CountryDTO>> callback) {
        Call<List<CountryDTO>> call = service.getAvailableCountries();
        call.enqueue(callback);
    }

    public void fetchHolidays(int year, String countryCode, Callback<List<HolidayDTO>> callback) {
        Call<List<HolidayDTO>> call = service.getPublicHolidays(year, countryCode);
        call.enqueue(new Callback<List<HolidayDTO>>() {
            @Override
            public void onResponse(Call<List<HolidayDTO>> call, Response<List<HolidayDTO>> response) {
                if (response != null && response.body() != null) {
                    android.util.Log.i("ApiRepository", "Fetched holidays: " + response.body().size() + " for " + year + "/" + countryCode);
                } else {
                    android.util.Log.w("ApiRepository", "Fetched holidays but body is null for " + year + "/" + countryCode);
                }
                callback.onResponse(call, response);
            }

            @Override
            public void onFailure(Call<List<HolidayDTO>> call, Throwable t) {
                android.util.Log.e("ApiRepository", "Failed fetch holidays: " + t);
                callback.onFailure(call, t);
            }
        });
    }

    // Resolve identifier (code or name) to countryCode synchronously using AvailableCountries
    public String resolveCountryCodeSync(String identifier) throws IOException {
        if (identifier == null) return null;
        String id = identifier.trim();
        if (id.length() == 2) return id.toUpperCase();
        Response<List<CountryDTO>> resp = service.getAvailableCountries().execute();
        if (!resp.isSuccessful() || resp.body() == null) return null;
        for (CountryDTO c : resp.body()) {
            if (c.getCountryCode() != null && c.getCountryCode().equalsIgnoreCase(id)) return c.getCountryCode();
            if (c.getName() != null && c.getName().equalsIgnoreCase(id)) return c.getCountryCode();
            // also match by localized name containing identifier
            if (c.getName() != null && c.getName().toLowerCase().contains(id.toLowerCase())) return c.getCountryCode();
        }
        return null;
    }

    // Fetch holidays resolving identifier asynchronously (identifier may be country code or full name)
    public void fetchHolidaysResolve(int year, String countryIdentifier, Callback<List<HolidayDTO>> callback) {
        if (countryIdentifier == null) {
            callback.onFailure(null, new Throwable("No country identifier"));
            return;
        }
        String id = countryIdentifier.trim();
        if (id.length() == 2) {
            fetchHolidays(year, id.toUpperCase(), callback);
            return;
        }
        // otherwise resolve via available countries then fetch
        service.getAvailableCountries().enqueue(new Callback<List<CountryDTO>>() {
            @Override
            public void onResponse(Call<List<CountryDTO>> call, Response<List<CountryDTO>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    // cannot pass a Call<List<CountryDTO>> to a Callback<List<HolidayDTO>>; report failure with null call
                    callback.onFailure(null, new Throwable("Failed to get countries"));
                    return;
                }
                String resolved = null;
                for (CountryDTO c : response.body()) {
                    if (c.getCountryCode() != null && c.getCountryCode().equalsIgnoreCase(id)) { resolved = c.getCountryCode(); break; }
                    if (c.getName() != null && c.getName().equalsIgnoreCase(id)) { resolved = c.getCountryCode(); break; }
                    if (c.getName() != null && c.getName().toLowerCase().contains(id.toLowerCase())) { resolved = c.getCountryCode(); break; }
                }
                if (resolved == null) {
                    callback.onFailure(null, new Throwable("Could not resolve country identifier: " + id));
                    return;
                }
                fetchHolidays(year, resolved, callback);
            }

            @Override
            public void onFailure(Call<List<CountryDTO>> call, Throwable t) {
                // country list fetch failed; forward failure to holiday callback with null call
                callback.onFailure(null, t);
            }
        });
    }

    // Blocking fetch that resolves identifier and returns list or null
    public List<HolidayDTO> fetchHolidaysSync(int year, String countryIdentifier) throws IOException {
        if (countryIdentifier == null) return null;
        String id = countryIdentifier.trim();
        String code = id.length() == 2 ? id.toUpperCase() : resolveCountryCodeSync(id);
        if (code == null) return null;
        Response<List<HolidayDTO>> resp = service.getPublicHolidays(year, code).execute();
        if (!resp.isSuccessful()) return null;
        return resp.body();
    }
}