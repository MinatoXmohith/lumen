package com.mkdziner.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.Locale;

/**
 * Current weather for a fixed city from Open-Meteo (free, no account, no key). The city comes from
 * "weather_city" in the theme's config.json; its coordinates are looked up once and remembered.
 */
final class Weather {

    static final class Now {
        String city = "";
        int tempC;
        String text = "";
        String symbol = "";
        int feelsC, maxC, minC, cloud;
        double windKmh, uvMax;
        String windDir = "";
    }

    private Weather() {}

    /** Blocking: call on Net.IO. Returns null when the city is unknown or the network fails. */
    static Now fetch(Context ctx, String city) {
        if (city == null || city.trim().isEmpty()) return null;
        try {
            SharedPreferences p = ctx.getSharedPreferences("weather", Context.MODE_PRIVATE);
            String key = city.trim().toLowerCase(Locale.ROOT);
            double lat, lon;
            String name;
            if (key.equals(p.getString("city", ""))) {
                lat = Double.longBitsToDouble(p.getLong("lat", 0));
                lon = Double.longBitsToDouble(p.getLong("lon", 0));
                name = p.getString("name", city);
            } else {
                JSONObject g = Net.json("https://geocoding-api.open-meteo.com/v1/search?count=1&language=en&name="
                        + URLEncoder.encode(city.trim(), "UTF-8"), null);
                JSONArray r = g.optJSONArray("results");
                if (r == null || r.length() == 0) return null;
                JSONObject first = r.getJSONObject(0);
                lat = first.getDouble("latitude");
                lon = first.getDouble("longitude");
                name = first.optString("name", city);
                p.edit().putString("city", key).putLong("lat", Double.doubleToLongBits(lat))
                        .putLong("lon", Double.doubleToLongBits(lon)).putString("name", name).apply();
            }
            JSONObject f = Net.json(String.format(Locale.ROOT,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                            + "&current=temperature_2m,apparent_temperature,weather_code,is_day,wind_speed_10m,wind_direction_10m,cloud_cover"
                            + "&daily=temperature_2m_max,temperature_2m_min,uv_index_max&forecast_days=1&timezone=auto",
                    lat, lon), null);
            JSONObject cur = f.getJSONObject("current");
            Now n = new Now();
            n.city = name;
            n.tempC = (int) Math.round(cur.getDouble("temperature_2m"));
            describe(n, cur.getInt("weather_code"), cur.optInt("is_day", 1) == 1);
            n.feelsC = (int) Math.round(cur.optDouble("apparent_temperature", n.tempC));
            n.windKmh = cur.optDouble("wind_speed_10m", 0);
            n.windDir = compass(cur.optDouble("wind_direction_10m", 0));
            n.cloud = cur.optInt("cloud_cover", 0);
            JSONObject daily = f.optJSONObject("daily");
            if (daily != null) {
                n.maxC = (int) Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0));
                n.minC = (int) Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0));
                n.uvMax = daily.getJSONArray("uv_index_max").optDouble(0, 0);
            }
            return n;
        } catch (Exception e) {
            return null;
        }
    }

    static String compass(double deg) {
        String[] d = {"N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"};
        return d[(int) Math.round(((deg % 360) + 360) % 360 / 22.5) % 16];
    }

    /** UV index in words, WHO scale. */
    static String uvWord(double uv) {
        if (uv < 3) return "Low";
        if (uv < 6) return "Moderate";
        if (uv < 8) return "High";
        if (uv < 11) return "Very high";
        return "Extreme";
    }

    /** WMO weather codes, as Open-Meteo reports them. */
    private static void describe(Now n, int code, boolean day) {
        if (code == 0) { n.text = day ? "Clear sky" : "Clear night"; n.symbol = day ? "☀️" : "🌙"; }
        else if (code <= 2) { n.text = "Partly cloudy"; n.symbol = "⛅"; }
        else if (code == 3) { n.text = "Cloudy"; n.symbol = "☁️"; }
        else if (code == 45 || code == 48) { n.text = "Fog"; n.symbol = "🌫️"; }
        else if (code >= 51 && code <= 57) { n.text = "Drizzle"; n.symbol = "🌦️"; }
        else if (code >= 61 && code <= 67) { n.text = "Rain"; n.symbol = "🌧️"; }
        else if (code >= 71 && code <= 77) { n.text = "Snow"; n.symbol = "❄️"; }
        else if (code >= 80 && code <= 82) { n.text = "Showers"; n.symbol = "🌦️"; }
        else if (code >= 95) { n.text = "Thunderstorm"; n.symbol = "⛈️"; }
        else { n.text = "—"; n.symbol = "🌡️"; }
    }
}
