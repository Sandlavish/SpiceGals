package com.openpositioning.PositionMe;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.openpositioning.PositionMe.sensors.LocationResponse;

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

    private List<LocationResponse> radiomapData;


    public MapMatching(Context context) {
        try {
            AssetManager assetManager = context.getAssets();
            InputStream inputStream = assetManager.open("processed_radiomap.json");
            Scanner scanner = new Scanner(inputStream).useDelimiter("\\A");
            String jsonContent = scanner.hasNext() ? scanner.next() : "";

            JSONArray radiomapArray = new JSONArray(jsonContent);
            radiomapData = new ArrayList<>();

            for (int i = 0; i < radiomapArray.length(); i++) {
                JSONObject jsonLocation = radiomapArray.getJSONObject(i);
                double lat = jsonLocation.getDouble("lat");
                double lon = jsonLocation.getDouble("lon");
                int floorId = jsonLocation.getInt("floor_id"); // Assuming floor_id is a string

                LocationResponse location = new LocationResponse(lat, lon, floorId);
                radiomapData.add(location);
            }

            // Optional: Log the first location as a test
            if (!radiomapData.isEmpty()) {
                LocationResponse firstLocation = radiomapData.get(0);
                Log.d("MapMatching", "First entry - Lat: " + firstLocation.getLatitude() + ", Lon: " + firstLocation.getLongitude() + ", Floor ID: " + firstLocation.getFloor());
            } else{
                Log.d("MapMatching", "Empty");

            }

        } catch (Exception e) {
            e.printStackTrace();
            Log.e("MapMatching", "Error parsing JSON", e);
        }
    }

    public LocationResponse findNearestLocation(double userLat, double userLon, int userFloor) {
        // Find the nearest location in radiomap to the user location
        LocationResponse nearestLocation = null;
        double minDistance = Double.MAX_VALUE;

        for (LocationResponse location : radiomapData) {
            if (location.getFloor() == userFloor) {
                double distance = euclideanDistance(userLat, userLon, location.getLatitude(), location.getLongitude());

                if (distance < minDistance) {
                    minDistance = distance;
                    nearestLocation = location;
                }
            }
        }

        return nearestLocation;
    }

    private double euclideanDistance(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371; // Radius of the earth in kilometers
        double latDistance = lat2 - lat1;
        double lonDistance = lon2 - lon1;
        double distance = Math.sqrt(latDistance * latDistance + lonDistance * lonDistance) * R;

        // Convert to meters, since 1 degree of latitude is approximately 111 kilometers
        return distance * 1000;
    }
}
