package cl.drsmile.alarmacalendarica.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.List;

import cl.drsmile.alarmacalendarica.conn.ApiRepository;
import cl.drsmile.alarmacalendarica.conn.LocalRepository;
import cl.drsmile.alarmacalendarica.dto.CountryDTO;
import cl.drsmile.alarmacalendarica.R;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CountrySelectionActivity extends AppCompatActivity {
    public static final String EXTRA_SELECTED = "selected_country";

    private ListView lv;
    private List<CountryDTO> countries = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_country_selection);

        lv = findViewById(R.id.lv_countries);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        lv.setAdapter(adapter);

        ApiRepository api = new ApiRepository();
        api.fetchCountries(new Callback<List<CountryDTO>>() {
            @Override
            public void onResponse(Call<List<CountryDTO>> call, Response<List<CountryDTO>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    countries = response.body();
                    List<String> names = new ArrayList<>();
                    for (CountryDTO c : countries) names.add(c.getName() + " (" + c.getCountryCode() + ")");
                    runOnUiThread(() -> {
                        adapter.clear(); adapter.addAll(names); adapter.notifyDataSetChanged();
                    });
                }
            }

            @Override
            public void onFailure(Call<List<CountryDTO>> call, Throwable t) { /* ignore for now */ }
        });

        lv.setOnItemClickListener((parent, view, position, id) -> {
            CountryDTO sel = countries.get(position);
            // save locally countries list as entities
            new LocalRepository(CountrySelectionActivity.this).saveCountries(countries);

            // ensure we store the ISO country code (not name). The CountryDTO uses countryCode field for ISO codes.
            String code = sel.getCountryCode();
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(CountrySelectionActivity.this);
            prefs.edit().putString("pref_country", code).apply();

            Intent result = new Intent();
            result.putExtra(EXTRA_SELECTED, sel.getCountryCode());
            setResult(RESULT_OK, result);
            finish();
        });
    }
}
