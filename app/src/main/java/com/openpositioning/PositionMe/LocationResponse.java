package com.openpositioning.PositionMe.sensors;


/**
 * {@inheritDoc}
 *
 *Class to handle the server response for the wifi fingerprints
 */
public class LocationResponse {
    private double latitude;
    private double longitude;
    private String floor;

    // Constructors, getters, and setters
    public LocationResponse(double latitude, double longitude, String floor) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.floor = floor;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public String getFloor() {
        return floor;
    }
}
