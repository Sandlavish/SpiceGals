package com.openpositioning.PositionMe.sensors;

import android.util.Log;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
/**
 * {@inheritDoc}
 * Class to create the wifi fingerprints
 * takes the wifi list from sensor fusion and creates a dictionary which only includes the BSSID and RSSI
 * this dictionary is then wrapped in a JSON @author Michalis Voudaskas
 */
public class WifiFPManager {
    private SensorFusion sensorFusion;

    public WifiFPManager(SensorFusion sensorFusion) {
        this.sensorFusion = sensorFusion;
    }

    private static WifiFPManager instance;

    private WifiFPManager() {
        // private constructor
    }

    public static WifiFPManager getInstance() {
        if (instance == null) {
            instance = new WifiFPManager(SensorFusion.getInstance());
        }
        return instance;
    }


    public String createWifiFingerprintJson() {
        try {
            List<Wifi> wifiList = sensorFusion.getWifiList();
            if (wifiList != null && !wifiList.isEmpty()) {
                Map<String, Integer> wifiReadings = new HashMap<>();

                for (Wifi wifi : wifiList) {
                    String bssidAsString = Long.toString(wifi.getBssid());
                    wifiReadings.put(bssidAsString, wifi.getLevel());
                }

                // Wrapping the dictionary in another map with key "wf"
                Map<String, Map<String, Integer>> finalStructure = new HashMap<>();
                finalStructure.put("wf", wifiReadings);

                Gson gson = new Gson();
                return gson.toJson(finalStructure);
            }
        } catch (Exception e) {
          // Log.e("WifiFPManager", "Error creating JSON", e);
        }
        return "{}";
    }
}