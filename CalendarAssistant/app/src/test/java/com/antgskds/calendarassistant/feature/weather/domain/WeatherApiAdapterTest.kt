package com.antgskds.calendarassistant.feature.weather.domain

import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class WeatherApiAdapterTest {
    private val location = WeatherLocation(
        latitude = 39.92,
        longitude = 116.40,
        source = "manual",
        locationId = "test-location",
        name = "测试城市"
    )

    @Test
    fun caiyunRequestUsesLongitudeBeforeLatitude() {
        val url = WeatherApiAdapter.resolveCaiyunRequestUrl(
            rawValue = "https://api.caiyunapp.com/v2.7/",
            token = "test-token",
            location = location
        )

        assertEquals(
            "https://api.caiyunapp.com/v2.7/test-token/116.4000,39.9200/weather",
            url
        )
    }

    @Test
    fun caiyunResponseMapsToSharedWeatherModel() {
        val data = WeatherApiAdapter.parse(
            provider = WeatherApiAdapter.PROVIDER_CAIYUN,
            rawBody = CAIYUN_RESPONSE,
            location = location
        )

        assertEquals(WeatherApiAdapter.PROVIDER_CAIYUN, data.provider)
        assertEquals("32.8", data.temperature)
        assertEquals("多云", data.text)
        assertEquals("61", data.humidity)
        assertEquals("东南风", data.windDir)
        assertEquals("3", data.windScale)
        assertEquals("中雨", data.hourlyForecast.single().text)
        assertEquals("60", data.hourlyForecast.single().pop)
        assertEquals("2026-08-02", data.dailyForecast.single().fxDate)
        assertEquals("暴雨", data.alerts.single().eventName)
        assertEquals("orange", data.alerts.single().colorCode)
    }

    @Test
    fun caiyunErrorResponseIsRejected() {
        assertThrows(IllegalStateException::class.java) {
            WeatherApiAdapter.parse(
                provider = WeatherApiAdapter.PROVIDER_CAIYUN,
                rawBody = """{"status":"failed","message":"invalid token"}""",
                location = location
            )
        }
    }

    @Test
    fun providerConfigurationKeepsQWeatherHostRequirement() {
        val base = MySettings(weatherEnabled = true, weatherApiKey = "test-key")

        assertFalse(base.copy(weatherProvider = WeatherApiAdapter.PROVIDER_QWEATHER).hasWeatherConfig())
        assertTrue(base.copy(weatherProvider = WeatherApiAdapter.PROVIDER_CAIYUN).hasWeatherConfig())
    }

    private companion object {
        val CAIYUN_RESPONSE = """
            {
              "status": "ok",
              "server_time": 1785638798,
              "tzshift": 28800,
              "result": {
                "realtime": {
                  "temperature": 32.75,
                  "apparent_temperature": 39.2,
                  "humidity": 0.61,
                  "skycon": "CLOUDY",
                  "visibility": 24.14,
                  "pressure": 99148.16,
                  "wind": {"speed": 12.0, "direction": 135.0},
                  "precipitation": {"local": {"intensity": 0.5}}
                },
                "hourly": {
                  "temperature": [{"datetime":"2026-08-02T12:00+08:00","value":36.25}],
                  "skycon": [{"datetime":"2026-08-02T12:00+08:00","value":"MODERATE_RAIN"}],
                  "precipitation": [{"datetime":"2026-08-02T12:00+08:00","value":0.6,"probability":60}],
                  "humidity": [{"datetime":"2026-08-02T12:00+08:00","value":0.5}],
                  "wind": [{"datetime":"2026-08-02T12:00+08:00","speed":6.8,"direction":172.0}],
                  "pressure": [{"datetime":"2026-08-02T12:00+08:00","value":99060.0}],
                  "cloudrate": [{"datetime":"2026-08-02T12:00+08:00","value":0.46}]
                },
                "daily": {
                  "temperature": [{"date":"2026-08-02T00:00+08:00","max":37.15,"min":26.35}],
                  "skycon": [{"date":"2026-08-02T00:00+08:00","value":"LIGHT_RAIN"}],
                  "skycon_08h_20h": [{"date":"2026-08-02T00:00+08:00","value":"PARTLY_CLOUDY_DAY"}],
                  "skycon_20h_32h": [{"date":"2026-08-02T00:00+08:00","value":"LIGHT_RAIN"}],
                  "humidity": [{"date":"2026-08-02T00:00+08:00","avg":0.68}],
                  "precipitation": [{"date":"2026-08-02T00:00+08:00","avg":0.2}],
                  "wind_08h_20h": [{"date":"2026-08-02T00:00+08:00","avg":{"speed":8.5,"direction":156.0}}],
                  "wind_20h_32h": [{"date":"2026-08-02T00:00+08:00","avg":{"speed":6.3,"direction":324.0}}],
                  "life_index": {"ultraviolet":[{"date":"2026-08-02T00:00+08:00","index":"6"}]}
                },
                "alert": {
                  "content": [{
                    "alertId": "alert-1",
                    "title": "测试城市发布暴雨橙色预警",
                    "description": "预计将出现强降雨",
                    "status": "预警",
                    "pubtimestamp": 1785638798,
                    "city": "测试城市"
                  }]
                }
              }
            }
        """.trimIndent()
    }
}
