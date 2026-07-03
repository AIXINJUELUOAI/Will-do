package com.antgskds.calendarassistant.feature.weather.domain

import android.content.Context
import java.text.Collator
import java.util.Locale

data class WeatherCatalogProvince(
    val id: String,
    val name: String,
    val sortName: String,
    val cities: List<WeatherCatalogCity>
)

data class WeatherCatalogCity(
    val id: String,
    val name: String,
    val sortName: String,
    val locations: List<WeatherCatalogLocation>
)

data class WeatherCatalogLocation(
    val id: String,
    val name: String,
    val provinceName: String,
    val cityName: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    val adCode: String,
    val sortName: String
) {
    fun toWeatherLocation(source: String = "manual"): WeatherLocation {
        return WeatherLocation(
            latitude = latitude,
            longitude = longitude,
            source = source,
            locationId = id,
            name = name,
            adm1 = provinceName,
            adm2 = cityName,
            country = country
        )
    }
}

object WeatherLocationCatalog {
    private const val ASSET_FILE = "qweather_china_city_list.csv"
    private val collator: Collator = Collator.getInstance(Locale.CHINA)

    @Volatile
    private var cached: List<WeatherCatalogProvince>? = null

    fun load(context: Context): List<WeatherCatalogProvince> {
        return cached ?: synchronized(this) {
            cached ?: parse(context).also { cached = it }
        }
    }

    private fun parse(context: Context): List<WeatherCatalogProvince> {
        val lines = context.assets.open(ASSET_FILE).bufferedReader(Charsets.UTF_8).useLines { sequence ->
            sequence.drop(2).filter { it.isNotBlank() }.toList()
        }

        val locations = lines.mapNotNull(::parseLocation)
        return locations
            .groupBy { it.provinceName }
            .map { (provinceName, provinceLocations) ->
                val cities = provinceLocations
                    .groupBy { it.cityName.ifBlank { provinceName } }
                    .map { (cityName, cityLocations) ->
                        WeatherCatalogCity(
                            id = cityName,
                            name = displayCityName(provinceName, cityName),
                            sortName = cityName,
                            locations = cityLocations
                                .distinctBy { it.id }
                                .sortedWith(compareByCollator { it.sortName })
                        )
                    }
                    .sortedWith(compareByCollator { it.sortName })
                WeatherCatalogProvince(
                    id = provinceName,
                    name = provinceName,
                    sortName = provinceName,
                    cities = cities
                )
            }
            .sortedWith(compareByCollator { it.sortName })
    }

    private fun parseLocation(line: String): WeatherCatalogLocation? {
        val parts = parseCsvLine(line)
        if (parts.size < 14) return null
        val iso = parts[3]
        if (iso !in supportedRegions) return null
        val province = parts[7].ifBlank { parts[5] }
        val city = parts[9].ifBlank { province }
        val name = parts[2]
        val latitude = parts[11].toDoubleOrNull() ?: return null
        val longitude = parts[12].toDoubleOrNull() ?: return null

        return WeatherCatalogLocation(
            id = parts[0],
            name = name,
            provinceName = province,
            cityName = city,
            country = parts[5],
            latitude = latitude,
            longitude = longitude,
            adCode = parts[13],
            sortName = name
        )
    }

    private fun displayCityName(provinceName: String, cityName: String): String {
        return if (provinceName == cityName && isDirectAdmin(provinceName)) "市辖区" else cityName
    }

    private fun isDirectAdmin(name: String): Boolean {
        return name == "北京市" || name == "上海市" || name == "天津市" || name == "重庆市" ||
            name.contains("香港") || name.contains("澳门")
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0
        while (index < line.length) {
            val ch = line[index]
            when {
                ch == '"' && inQuotes && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index++
                }
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(ch)
            }
            index++
        }
        result.add(current.toString())
        return result
    }

    private fun <T> compareByCollator(selector: (T) -> String): Comparator<T> {
        return Comparator { left, right -> collator.compare(selector(left), selector(right)) }
    }

    private val supportedRegions = setOf("CN", "HK", "MO", "TW")
}
