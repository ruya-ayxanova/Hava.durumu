package com.example.havadurumu

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.AdapterView
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.location.LocationManagerCompat
import com.google.android.material.tabs.TabLayout
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

data class Place(val name: String, val lat: Double, val lon: Double)

class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var tabs: TabLayout
    private lateinit var spinner: Spinner
    private lateinit var adapter: ArrayAdapter<String>

    private val places = mutableListOf<Place>()
    private var myLoc: Place? = null
    private val sites = listOf(
        "MGM", "Yr.no", "Meteoblue", "AccuWeather", "Weather.com", "Windy", "Ventusky"
    )

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        web = findViewById(R.id.web)
        tabs = findViewById(R.id.tabs)
        spinner = findViewById(R.id.spinner)

        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webViewClient = WebViewClient()

        sites.forEach { tabs.addTab(tabs.newTab().setText(it)) }
        loadPlaces()

        adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, mutableListOf())
        spinner.adapter = adapter
        refreshSpinner()

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) = show()
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) = show()
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) = web.reload()
        })

        findViewById<Button>(R.id.btnAdd).setOnClickListener { addDialog() }
        findViewById<Button>(R.id.btnDel).setOnClickListener {
            val i = spinner.selectedItemPosition
            if (i > 0) {
                places.removeAt(i - 1)
                savePlaces()
                refreshSpinner()
                spinner.setSelection(0)
            }
        }

        fetchLocation()
    }

    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    // ---------- Yerler ----------
    private fun refreshSpinner() {
        adapter.clear()
        adapter.add("📍 Konumum")
        places.forEach { adapter.add(it.name) }
        adapter.notifyDataSetChanged()
    }

    private fun loadPlaces() {
        val s = getSharedPreferences("p", MODE_PRIVATE).getString("places", "[]")
        val arr = JSONArray(s)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            places.add(Place(o.getString("n"), o.getDouble("a"), o.getDouble("o")))
        }
    }

    private fun savePlaces() {
        val arr = JSONArray()
        places.forEach { arr.put(JSONObject().put("n", it.name).put("a", it.lat).put("o", it.lon)) }
        getSharedPreferences("p", MODE_PRIVATE).edit().putString("places", arr.toString()).apply()
    }

    private fun addDialog() {
        val input = EditText(this).apply { hint = "Şehir veya ilçe adı" }
        AlertDialog.Builder(this)
            .setTitle("Yer ekle")
            .setView(input)
            .setPositiveButton("Ekle") { _, _ ->
                val q = input.text.toString().trim()
                if (q.isNotEmpty()) geocode(q)
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    private fun geocode(q: String) {
        Thread {
            val r = try {
                @Suppress("DEPRECATION")
                Geocoder(this, Locale("tr", "TR")).getFromLocationName(q, 1)?.firstOrNull()
            } catch (e: Exception) { null }
            runOnUiThread {
                if (r == null) {
                    Toast.makeText(this, "Yer bulunamadı", Toast.LENGTH_SHORT).show()
                } else {
                    places.add(Place(q, r.latitude, r.longitude))
                    savePlaces()
                    refreshSpinner()
                    spinner.setSelection(places.size)
                }
            }
        }.start()
    }

    // ---------- Konum ----------
    @SuppressLint("MissingPermission")
    private fun fetchLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 1
            )
            return
        }
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        val providers = lm.getProviders(true)
        val last = providers.mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
        if (last != null) {
            setMy(last)
        } else if (providers.isNotEmpty()) {
            LocationManagerCompat.getCurrentLocation(
    lm, providers.first(), null as androidx.core.os.CancellationSignal?, mainExecutor,
    androidx.core.util.Consumer<Location> { loc -> if (loc != null) setMy(loc) }
)
        } else {
            Toast.makeText(this, "Konum kapalı", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (res.isNotEmpty() && res[0] == PackageManager.PERMISSION_GRANTED) fetchLocation()
    }

    private fun setMy(loc: Location) {
        Thread {
            val name = try {
                @Suppress("DEPRECATION")
                val a = Geocoder(this, Locale("tr", "TR")).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()
                a?.adminArea ?: a?.locality ?: "Konumum"
            } catch (e: Exception) { "Konumum" }
            runOnUiThread {
                myLoc = Place(name, loc.latitude, loc.longitude)
                show()
            }
        }.start()
    }

    // ---------- Gösterim ----------
    private fun current(): Place? {
        val i = spinner.selectedItemPosition
        return if (i <= 0) myLoc else places.getOrNull(i - 1)
    }

    private fun show() {
        val p = current()
        if (p == null) {
            web.loadData("<h3 style='font-family:sans-serif'>Konum alınıyor…</h3>", "text/html", "UTF-8")
            return
        }
        web.loadUrl(url(tabs.selectedTabPosition, p))
    }

    private fun ascii(s: String) = s.uppercase(Locale("tr", "TR"))
        .replace('Ç', 'C').replace('Ğ', 'G').replace('İ', 'I').replace('Ö', 'O')
        .replace('Ş', 'S').replace('Ü', 'U')

    private fun url(site: Int, p: Place): String {
        val la = String.format(Locale.US, "%.4f", p.lat)
        val lo = String.format(Locale.US, "%.4f", p.lon)
        val enc = URLEncoder.encode(p.name, "UTF-8")
        return when (site) {
            0 -> "https://www.mgm.gov.tr/tahmin/il-ve-ilceler.aspx?m=" + URLEncoder.encode(ascii(p.name), "UTF-8")
            1 -> "https://www.yr.no/en/forecast/daily-table/$la,$lo"
            2 -> "https://www.meteoblue.com/en/weather/week/${la}N${lo}E"
            3 -> "https://www.accuweather.com/en/search-locations?query=$enc"
            4 -> "https://weather.com/weather/today/l/$la,$lo"
            5 -> "https://www.windy.com/$la/$lo?$la,$lo,8"
            else -> "https://www.ventusky.com/?p=$la;$lo;8&l=temperature-2m"
        }
    }
}
