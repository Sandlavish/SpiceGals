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
    public static boolean groundFloorVisible;
    public static boolean firstFloorVisible;
    public static boolean secondFloorVisible;
    public static boolean thirdFloorVisible;

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
    private void resetOverlayVisibilityFlags() {
        // Reset all visibility flags to false
        groundFloorVisible = false;
        firstFloorVisible = false;
        secondFloorVisible = false;
        thirdFloorVisible = false;
    }

    public void setUserSelectedFloor(Floor floor) {
        this.userSelectedFloor = floor;
        // Call a method to update the floor overlays based on this selection
        updateFloorOverlaysBasedOnUserSelection();
    }

    private void updateFloorOverlaysBasedOnUserSelection() {
        resetOverlayVisibilityFlags();

        if (userSelectedFloor != null) {
            switch (userSelectedFloor) {
                case GROUND:
                    groundFloorVisible = true;
                    break;
                case FIRST:
                    firstFloorVisible = true;
                    break;
                case SECOND:
                    secondFloorVisible = true;
                    break;
                case THIRD:
                    thirdFloorVisible = true;
                    break;
            }
            // Update overlays based on the current visibility flags
            setFloorVisibility(groundFloorVisible, firstFloorVisible, secondFloorVisible, thirdFloorVisible);
        }
        // Otherwise, maintain current behavior or add additional logic as needed
    }

    public void updateFloorOverlays(float elevation) {
        // Determine the floor based on elevation
        Floor targetFloor = determineFloorByElevation(elevation);

        // Update overlays based on the current visibility flags
        setFloorVisibility(groundFloorVisible, firstFloorVisible, secondFloorVisible, thirdFloorVisible);

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
        float currentElevation = sensorFusion.getElevation();
        updateFloorOverlays(currentElevation);
    }


    // Helper method to determine the floor based on elevation
    private Floor determineFloorByElevation(float elevation) {
        if (elevation <= GROUND_FLOOR_MAX_ELEVATION) {
            groundFloorVisible = true;
            return Floor.GROUND;
        } else if (elevation <= FIRST_FLOOR_MAX_ELEVATION) {
            firstFloorVisible = true;
            return Floor.FIRST;
        } else if (elevation <= SECOND_FLOOR_MAX_ELEVATION) {
            secondFloorVisible = true;
            return Floor.SECOND;
        } else {
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
    public void setFloorVisibility(boolean ground, boolean first,
                                   boolean second, boolean third) {
        if (groundflooroverlay != null) groundflooroverlay.setVisible(ground);
        if (firstflooroverlay != null) firstflooroverlay.setVisible(first);
        if (secondflooroverlay != null) secondflooroverlay.setVisible(second);
        if (thirdflooroverlay != null) thirdflooroverlay.setVisible(third);
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

    public void updateFloorOverlay() {
        if (groundflooroverlay != null && firstflooroverlay != null && secondflooroverlay!=null && thirdflooroverlay!=null) {
            groundflooroverlay.setVisible(userIsOnGroundFloor);
            firstflooroverlay.setVisible(userIsOnFirstFloor);
            secondflooroverlay.setVisible(userIsOnSecondFloor);
            thirdflooroverlay.setVisible(userIsOnThirdFloor);
            librarygroundflooroverlay.setVisible(userIsOnGroundFloor);
            libraryfirstflooroverlay.setVisible(userIsOnFirstFloor);
            librarysecondflooroverlay.setVisible(userIsOnSecondFloor);
            librarythirdflooroverlay.setVisible(userIsOnThirdFloor);
        }
    }


    // Floor enumeration to represent different floor levels
    public static enum Floor {
        GROUND, FIRST, SECOND, THIRD
    }
}
