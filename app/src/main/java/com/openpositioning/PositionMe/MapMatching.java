package com.openpositioning.PositionMe;

import android.content.Context;
import com.google.android.gms.maps.model.LatLng;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MapMatching {

    private List<WifiFingerprint> radiomap;
    private Context context; // Context is needed to access the assets

    public MapMatching(Context context) {
        this.context = context;
        this.radiomap = new ArrayList<>();
        loadRadiomapData(); // Load the radiomap data when the system is initialized
    }

    // Method to load radiomap data from a JSON file in the assets directory
    private void loadRadiomapData() {
        try {
            InputStream is = context.getAssets().open("radiomap_filtered.json");
            Type listType = new TypeToken<List<WifiFingerprint>>() {}.getType();
            this.radiomap = new Gson().fromJson(new InputStreamReader(is), listType);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public LatLng predictLocation(Map<String, Integer> liveScan) {
        WifiFingerprint liveDataFingerprint = new WifiFingerprint(liveScan, 0.0, 0.0); // Dummy lat-lng
        int k = 3; // For example, you choose 3 nearest neighbors
        List<WifiFingerprint> neighbors = findNearestNeighbors(liveDataFingerprint, radiomap, k);
        return calculateAverageLatLng(neighbors);
    }

    private List<WifiFingerprint> findNearestNeighbors(WifiFingerprint liveDataFingerprint, List<WifiFingerprint> radiomap, int k) {
        return radiomap.stream()
                .sorted((fp1, fp2) -> Double.compare(calculateDistance(liveDataFingerprint, fp1), calculateDistance(liveDataFingerprint, fp2)))
                .limit(k)
                .collect(Collectors.toList());
    }

    private double calculateDistance(WifiFingerprint fp1, WifiFingerprint fp2) {
        double sum = 0.0;
        for (String mac : fp1.getSignalStrengths().keySet()) {
            int strength1 = fp1.getSignalStrengths().getOrDefault(mac, -100);
            int strength2 = fp2.getSignalStrengths().getOrDefault(mac, -100);
            sum += Math.pow(strength1 - strength2, 2);
        }
        return Math.sqrt(sum);
    }

    private LatLng calculateAverageLatLng(List<WifiFingerprint> neighbors) {
        double sumLat = 0.0, sumLon = 0.0;
        for (WifiFingerprint neighbor : neighbors) {
            sumLat += neighbor.getLatitude();
            sumLon += neighbor.getLongitude();
        }
        return new LatLng(sumLat / neighbors.size(), sumLon / neighbors.size());
    }

    class WifiFingerprint {
        private Map<String, Integer> wf; // Wi-Fi signal strengths
        private double lat; // Latitude
        private double lon; // Longitude

        public WifiFingerprint(Map<String, Integer> wf, double lat, double lon) {
            this.wf = wf;
            this.lat = lat;
            this.lon = lon;
        }

        public Map<String, Integer> getSignalStrengths() {
            return wf;
        }

        public double getLatitude() {
            return lat;
        }

        public double getLongitude() {
            return lon;
        }
    }
}
