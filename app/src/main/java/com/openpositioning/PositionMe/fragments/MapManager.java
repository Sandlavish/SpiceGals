package com.openpositioning.PositionMe.fragments;

import android.graphics.Color;
import androidx.fragment.app.Fragment;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.GroundOverlay;
import com.google.android.gms.maps.model.GroundOverlayOptions;
import com.google.android.gms.maps.model.LatLngBounds;
import com.openpositioning.PositionMe.R;

public class MapManager implements OnMapReadyCallback {
    private GoogleMap mMap;
    private Fragment fragment;
    // Overlay references
    private GroundOverlay groundflooroverlay, firstflooroverlay, secondflooroverlay, thirdflooroverlay;

    public MapManager(Fragment fragment) {
        this.fragment = fragment;
        initializeMap();
    }

    private void initializeMap() {
        SupportMapFragment mapFragment = (SupportMapFragment) fragment.getChildFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        int savedMapType = GlobalVariables.getMapType();
        mMap.setMapType(savedMapType);
        configureMapSettings();
    }

    private void configureMapSettings() {

        mMap.setMapType(GlobalVariables.getMapType());
        mMap.getUiSettings().setCompassEnabled(true);
        mMap.getUiSettings().setTiltGesturesEnabled(true);
        mMap.getUiSettings().setRotateGesturesEnabled(true);
        mMap.getUiSettings().setScrollGesturesEnabled(true);
        // Optionally, initialize a polyline for user's trajectory, if needed
    }
    // You may want to add methods to update user location, handle map interactions, etc.
}
