package com.openpositioning.PositionMe.fragments;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.GroundOverlay;
import com.google.android.gms.maps.model.GroundOverlayOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Polyline;
import com.openpositioning.PositionMe.R;
import com.openpositioning.PositionMe.sensors.SensorFusion;

public class FloorOverlayManager {
    private boolean manualSelectionActive = false;
    public static boolean groundFloorVisible;

    public static boolean librarygroundfloorvisible;
    public static boolean firstFloorVisible;
    public static boolean libraryfirstfloorvisible;
    public static boolean secondFloorVisible;
    public static boolean librarysecondfloorvisible;
    public static boolean thirdFloorVisible;
    public static boolean librarythirdfloorvisible;
    public static boolean isUserNearGroundFloor;
    public static boolean isuserNearGroundFloorLibrary;

    private GoogleMap mMap;
    public static LatLng southwestcornerNucleus;
    public static LatLng northeastcornerNucleus;

    public static LatLng southwestcornerLibrary;
    public static LatLng northeastcornerLibrary;

    private Floor userSelectedFloor = null; // null indicates no floor has been manually selected

    private boolean userIsOnFirstFloor = false; // Default to ground floor
    private boolean userIsOnGroundFloor = false; //Ground floor is only visible when we are near the building
    private boolean userIsOnSecondFloor = false; // Default to ground floor
    private boolean userIsOnThirdFloor = false; // Default to ground floor

    public GroundOverlay groundflooroverlay;
    public GroundOverlay firstflooroverlay;
    public GroundOverlay secondflooroverlay;
    public GroundOverlay thirdflooroverlay;
    public GroundOverlay librarygroundflooroverlay;
    public GroundOverlay libraryfirstflooroverlay;
    public GroundOverlay librarysecondflooroverlay;
    public GroundOverlay librarythirdflooroverlay;

    public static LatLngBounds buildingBounds; //building bounds for the Nucleus

    public static LatLngBounds buildingBoundsLibrary; //building bounds for the Library
    private MapManager mapManager;
    private SensorFusion sensorFusion;

    // Define elevation thresholds for each floor level
    private final float GROUND_FLOOR_MAX_ELEVATION = 4.2f;
    private final float FIRST_FLOOR_MAX_ELEVATION = 8.6f;
    private final float SECOND_FLOOR_MAX_ELEVATION = 12.8f;

    // Current floor level for comparison during updates
    private Floor currentFloor = Floor.GROUND;

    public FloorOverlayManager(GoogleMap mMap, MapManager mapManager, SensorFusion sensorFusion) {
        this.mMap = mMap;
        this.mapManager = mapManager;
        this.sensorFusion = sensorFusion;
        checkAndUpdateFloorOverlay();
    }

    public void setUserSelectedFloor(Floor floor) {
        this.userSelectedFloor = floor;
        this.manualSelectionActive = true; // Indicate manual selection is active
        updateFloorOverlaysBasedOnUserSelection();
    }

    public void updateFloorOverlaysBasedOnUserSelection() {
        // Hide all overlays initially
        setFloorVisibility(false, false, false, false);

        if (userSelectedFloor != null) {
            switch (userSelectedFloor) {
                case GROUND:
                    if (groundflooroverlay != null) groundflooroverlay.setVisible(true);
                    if (librarygroundflooroverlay!=null) librarygroundflooroverlay.setVisible(true);
                    break;
                case FIRST:
                    if (firstflooroverlay != null) firstflooroverlay.setVisible(true);
                    if (libraryfirstflooroverlay!=null) libraryfirstflooroverlay.setVisible(true);
                    break;
                case SECOND:
                    if (secondflooroverlay != null) secondflooroverlay.setVisible(true);
                    if (librarysecondflooroverlay!=null) librarysecondflooroverlay.setVisible(true);
                    break;
                case THIRD:
                    if (thirdflooroverlay != null) thirdflooroverlay.setVisible(true);
                    if (librarythirdflooroverlay!=null) librarythirdflooroverlay.setVisible(true);
                    break;
            }
        }
    }

    public void updateFloorOverlays(float elevation) {
        // Determine the floor based on elevation
        Floor targetFloor = determineFloorByElevation(elevation);

        // Update overlays based on the current visibility flags
        setFloorVisibility(groundFloorVisible, firstFloorVisible, secondFloorVisible, thirdFloorVisible);
        setFloorVisibility(librarygroundfloorvisible, libraryfirstfloorvisible, librarysecondfloorvisible, librarythirdfloorvisible);

        // Update overlays only if the floor has changed
        if (targetFloor != currentFloor) {
            switch (targetFloor) {
                case GROUND:
                    setFloorVisibility(true, false, false, false);
                    break;
                case FIRST:
                    setFloorVisibility(false, true, false, false);
                    break;
                case SECOND:
                    setFloorVisibility(false, false, true, false);
                    break;
                case THIRD:
                    setFloorVisibility(false, false, false, true);
                    break;
            }
            currentFloor = targetFloor;
        }
    }

    // This method could be called periodically or in response to specific events, such as a significant change in elevation
    public void checkAndUpdateFloorOverlay() {
        if (!manualSelectionActive) { // Only update based on elevation if manual selection is not active
            float currentElevation = sensorFusion.getElevation();
            updateFloorOverlays(currentElevation);
        }
    }


    // Helper method to determine the floor based on elevation
    private Floor determineFloorByElevation(float elevation) {
        if (elevation <= GROUND_FLOOR_MAX_ELEVATION) {
            librarygroundfloorvisible=true;
            groundFloorVisible = true;
            return Floor.GROUND;
        } else if (elevation <= FIRST_FLOOR_MAX_ELEVATION) {
            libraryfirstfloorvisible=true;
            firstFloorVisible = true;
            return Floor.FIRST;
        } else if (elevation <= SECOND_FLOOR_MAX_ELEVATION) {
            librarysecondfloorvisible=true;
            secondFloorVisible = true;
            return Floor.SECOND;
        } else {
            librarythirdfloorvisible=true;
            thirdFloorVisible = true;
            return Floor.THIRD;
        }
    }


    public void setupGroundOverlays() {
        defineOverlayBounds();
        createAndAddOverlays();
    }

    private void createAndAddOverlays() {
        // Nucleus Overlays
        groundflooroverlay = addOverlay(R.drawable.nucleusg, buildingBounds);
        firstflooroverlay = addOverlay(R.drawable.nucleus1, buildingBounds);
        secondflooroverlay = addOverlay(R.drawable.nucleus2, buildingBounds);
        thirdflooroverlay = addOverlay(R.drawable.nucleus3, buildingBounds);

        // Library Overlays
        librarygroundflooroverlay = addOverlay(R.drawable.libraryg, buildingBoundsLibrary);
        libraryfirstflooroverlay = addOverlay(R.drawable.library1, buildingBoundsLibrary);
        librarysecondflooroverlay = addOverlay(R.drawable.library2, buildingBoundsLibrary);
        librarythirdflooroverlay = addOverlay(R.drawable.library3, buildingBoundsLibrary);

    }

    // Method to adjust the visibility of overlays
    public void setFloorVisibility(boolean ground, boolean first, boolean second, boolean third) {
        if (isUserNearGroundFloor || isuserNearGroundFloorLibrary) {
            if (groundflooroverlay != null) groundflooroverlay.setVisible(ground);
            if (firstflooroverlay != null) firstflooroverlay.setVisible(first);
            if (secondflooroverlay != null) secondflooroverlay.setVisible(second);
            if (thirdflooroverlay != null) thirdflooroverlay.setVisible(third);
            if (librarygroundflooroverlay != null) librarygroundflooroverlay.setVisible(ground);
            if (libraryfirstflooroverlay != null) libraryfirstflooroverlay.setVisible(first);
            if (librarysecondflooroverlay != null) librarysecondflooroverlay.setVisible(second);
            if (librarythirdflooroverlay != null) librarythirdflooroverlay.setVisible(third);
        }
    }

    private void defineOverlayBounds() {
        // Initialize LatLng for corners
        southwestcornerNucleus = new LatLng(55.92278, -3.17465);
        northeastcornerNucleus = new LatLng(55.92335, -3.173842);
        southwestcornerLibrary = new LatLng(55.922738, -3.17517);
        northeastcornerLibrary = new LatLng(55.923061, -3.174764);

        // Initialize LatLngBounds
        buildingBounds = new LatLngBounds(southwestcornerNucleus, northeastcornerNucleus);
        buildingBoundsLibrary = new LatLngBounds(southwestcornerLibrary, northeastcornerLibrary);
    }


    public GroundOverlay addOverlay(int resourceId, LatLngBounds bounds) {
        return mMap.addGroundOverlay(new GroundOverlayOptions()
                .image(BitmapDescriptorFactory.fromResource(resourceId))
                .positionFromBounds(bounds)
                .transparency(0.5f)); // Adjust transparency as needed
    }


    // Floor enumeration to represent different floor levels
    public static enum Floor {
        GROUND, FIRST, SECOND, THIRD
    }
}
