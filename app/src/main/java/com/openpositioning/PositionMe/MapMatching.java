package com.openpositioning.PositionMe;

import android.content.Context;
import android.content.res.AssetManager;

import com.google.android.gms.maps.model.LatLng;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;

public class MapMatching {

    private List<Location> radiomapData;

    // Define a Location class to hold each location data
    private static class Location {
        double lat;
        double lon;
        int floor_id;

        // Constructor, getters and setters are omitted for brevity
    }

    public MapMatching(Context context) {
        // Constructor to load and parse the radiomap JSON data
        try {
            AssetManager assetManager = context.getAssets();
            InputStream inputStream = assetManager.open("processed_radiomap.json");
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);

            // Use Gson to parse the JSON file into a list of Location objects
            Type listType = new TypeToken<List<Location>>() {
            }.getType();
            radiomapData = new Gson().fromJson(inputStreamReader, listType);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Location findNearestLocation(double userLat, double userLon, int userFloor) {
        // Find the nearest location in radiomap to the user location
        Location nearestLocation = null;
        double minDistance = Double.MAX_VALUE;

        for (Location location : radiomapData) {
            if (location.floor_id == userFloor) {
                double distance = distance(userLat, userLon, location.lat, location.lon);

                if (distance < minDistance) {
                    minDistance = distance;
                    nearestLocation = location;
                }
            }
        }

        return nearestLocation;
    }

    private double distance(double lat1, double lon1, double lat2, double lon2) {
        // Calculate distance between two lat-lon points using Haversine formula
        final int R = 6371; // Radius of the earth in kilometers

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = R * c * 1000; // convert to meters

        return distance;
    }
}
