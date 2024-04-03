package com.openpositioning.PositionMe.fragments;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;
import androidx.preference.PreferenceManager;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.navigation.NavigationView;
import com.openpositioning.PositionMe.MapMatching;
import com.openpositioning.PositionMe.ParticleFilter;
import com.openpositioning.PositionMe.PdrProcessing;
import com.openpositioning.PositionMe.R;
import com.openpositioning.PositionMe.ServerCommunications;
import com.openpositioning.PositionMe.Traj;
import com.openpositioning.PositionMe.sensors.LocationResponse;
import com.openpositioning.PositionMe.sensors.SensorFusion;
import com.openpositioning.PositionMe.sensors.SensorTypes;
import com.openpositioning.PositionMe.sensors.WifiFPManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * A simple {@link Fragment} subclass. The recording fragment is displayed while the app is actively
 * saving data, with some UI elements indicating current PDR status.
 *
 * @see HomeFragment the previous fragment in the nav graph.
 * @see CorrectionFragment the next fragment in the nav graph.
 * @see SensorFusion the class containing sensors and recording.
 *
 * The RecordingFragment class extends the Fragment class and implements OnMapReadyCallback to manage
 * a recording session's UI and logic within the PositionMe application. This class is responsible for
 * initializing and updating the map view with real-time data, handling user interactions
 * and managing sensor data for indoor/outdoor detection, WiFi positioning, and GNSS/PDR updates.
 * It showcases various UI elements such as buttons, and custom markers, to provide feedback
 * on the recording process, including the current location, elevation, and distance traveled.
 *
 * Key features include:
 * - Displaying and updating a Google Map with the user's trajectory, using polyline overlays to represent
 * the path taken based on PDR (Pedestrian Dead Reckoning), GNSS (Global Navigation Satellite System), and
 * WiFi positioning data.
 * - Processing and filtering location data through algorithms such as Kalman filters and  Particle Filter
 * to improve accuracy.
 * - Dynamically updating the UI based on sensor data, including light levels for indoor/outdoor detection
 * and user elevation.
 * - Offering interactive elements like buttons to tag locations manually, switch map views, and control
 * the recording session.
 * - Handling map matching to snap the user's path to a predefined map dataset, enhancing accuracy in
 * representing the user's movements within the app's context.
 *
 * This class integrates closely with other components of the PositionMe app, including SensorFusion for
 * accessing sensor data, MapMatching for aligning recorded paths with actual map paths, and server communication
 * modules for tasks such as fetching WiFi-based positions. It plays a crucial role in the app's functionality
 * by enabling detailed and accurate recording of user paths in various environments.
 *
 * @author Michalis Voudaskas
 * @author Batu Bayram
 * @author Apoorv Tewari
 *
 */

public class RecordingFragment extends Fragment implements OnMapReadyCallback {

    //Instantiations
    private static final float DEFAULT_ACCURACY = 0.5f ;
    private Polyline pdrPolyline;
    private Polyline userTrajectory;

    private long lastUpdateTime = 0;

    // Handlers/Runnables Refresh Tasks
    private Handler gnssUpdateHandler;
    private Runnable gnssUpdateTask;
    private Handler lightLevelHandler;
    private Runnable lightLevelRunnable;

    //Locations
    private LatLng filteredLocation_ekf;
    private LatLng fusedLocation;
    private LatLng filteredLocation;
    private float[] gnssLocation;
    private LatLng PDRPOS;
    private LatLng wifiLocation;

    //Indoor/Outdoor detection helpers
    private static final float INDOOR_OUTDOOR_THRESHOLD = 1000.0f;
    private boolean isOutdoor;

    //PDR class instantiation
    private PdrProcessing pdrProcessing;

    private MapMatching mapMatcher;
    private int currentFloor;

    //Button to end PDR recording
    private Button stopButton;
    private Button cancelButton;
    //Recording icon to show user recording is in progress
    private ImageView recIcon, rcntr;

    private LatLngBounds TestingBounds;
    //Compass icon to show user direction of heading
    private ImageView compassIcon;

    float elevationVal;
    // Elevator icon to show elevator usage
    private ImageView elevatorIcon;
    //Loading bar to show time remaining before recording automatically ends
    private ProgressBar timeRemaining;
    //Text views to display user position and elevation since beginning of recording
    private TextView positionX;
    private TextView positionY;
    private TextView elevation;
    private TextView distanceTravelled;

    private FloorOverlayManager floorOverlayManager;
    private Traj.Trajectory.Builder trajectory;

    private MapManager mapManager;

    //App settings
    private SharedPreferences settings;
    //Singleton class to collect all sensor data
    private SensorFusion sensorFusion;
    //Timer to end recording
    private CountDownTimer autoStop;
    private Handler refreshDataHandler;

    //variables to store data of the trajectory
    private float distance;
    private float previousPosX;
    private float previousPosY;

    private GoogleMap mMap;
    private Marker ekfmarker;
    private Marker mapMatched;

    private List<LatLng> recentFusedLocations = new ArrayList<>();
    private static final int MOVING_AVERAGE_WINDOW = 5; // Number of locations to average

    private ExtendedKalmanFilter ekf;
    private Spinner floorSelectionSpinner;


    private Boolean wasPreviouslyOutdoor = null; // null indicates no previous status

    private static final float Q_METRES_PER_SECOND = 1f; // Adjust this value based on your needs

    // Fields to store recent locations
    private List<LatLng> recentGNSSLocations = new ArrayList<>();
    private List<LatLng> recentWifiLocations = new ArrayList<>();
    private List<LatLng> recentPDRLocations = new ArrayList<>();
    private List<Marker> gnssMarkers = new ArrayList<>();
    private List<Marker> wifiMarkers = new ArrayList<>();
    private List<Marker> pdrMarkers = new ArrayList<>();

    private boolean areGnssMarkersVisible = true;
    private boolean areWifiMarkersVisible = true;
    private boolean arePDRMarkersVisible = true;
    private boolean isEKFMarkerVisible = true;
    private boolean isMatchedMarkerVisible = true;

    private static final int MAX_RECENT_LOCATIONS = 5;
    private static final double OUTLIER_THRESHOLD_METERS = 1;
    //Particle Filter
    private ParticleFilter particleFilter;
    private Marker FusedMarker;

    private WifiFPManager wifiFPManager;
    private ServerCommunications serverCommunications;

    private KalmanFilter.KalmanLatLong kalmanFilter;

    /**
     * Public Constructor for the class.
     * Left empty as not required
     */
    public RecordingFragment() {
        // Required empty public constructor
    }

    /**
     * {@inheritDoc}
     * Gets an instance of the {@link SensorFusion} class, and initialises the context and settings.
     * Creates a handler for periodically updating the displayed data.
     *
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.sensorFusion = SensorFusion.getInstance();
        Context context = getActivity();
        this.settings = PreferenceManager.getDefaultSharedPreferences(context);
        this.refreshDataHandler = new Handler();
        this.kalmanFilter = new KalmanFilter.KalmanLatLong(Q_METRES_PER_SECOND);
        this.wifiFPManager = WifiFPManager.getInstance();
        serverCommunications = new ServerCommunications(context);

        // Initialize trajectory
        trajectory = Traj.Trajectory.newBuilder();
        mapMatcher = new MapMatching(getContext());


        // Initialize the Extended Kalman Filter
        int stateSize = 4; // For [lat, lon, v_n, v_e]
        int measurementSize = 2; // For [lat, lon] measurements from GNSS
        ekf = new ExtendedKalmanFilter(stateSize, measurementSize);

        if (gnssLocation == null) {
            return;
        }
        double latitude = gnssLocation[0];
        double longitude = gnssLocation[1];
        // Initial state: [lat, lon, v_n, v_e]
        double[] initialState = {latitude, longitude, 0.0, 0.0}; // Initialize with your first GNSS reading or a known starting point

        // Initial covariance matrix (P), small values for the initial state's certainty
        double[][] initialCovariance = new double[stateSize][stateSize];
        for (int i = 0; i < stateSize; i++) {
            initialCovariance[i][i] = 1.0; // Adjust these values based on the initial uncertainty of each state variable
        }

        // Initialize the EKF
        ekf.initialize(initialState, initialCovariance);
    }

    /**
     * {@inheritDoc}
     * Set title in action bar to "Recording"
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.fragment_recording, container, false);
        // Inflate the layout for this fragment
        ((AppCompatActivity)getActivity()).getSupportActionBar().hide();
        getActivity().setTitle("Recording...");
        initializeMap();
        // Set up the "Add Tag" button
        Button addTagButton = rootView.findViewById(R.id.button_add_tag);

        NavigationView navigationView = rootView.findViewById(R.id.navigation_view);
        navigationView.setNavigationItemSelectedListener(item -> {
            // Handle navigation view item clicks here.
            int id = item.getItemId();
            if (id == R.id.nav_stop) {
                sensorFusion.stopRecording();
                // Define the navigation action
                NavDirections action = RecordingFragmentDirections.actionRecordingFragmentToCorrectionFragment();
                // Use the action to navigate
                Navigation.findNavController(getView()).navigate(action);

            } else if (id == R.id.nav_cancel) {
                    sensorFusion.stopRecording();
                    NavDirections action = RecordingFragmentDirections.actionRecordingFragmentToHomeFragment();
                    Navigation.findNavController(getView()).navigate(action);
                    if(autoStop != null) autoStop.cancel();
                // Handle the cancel action
            }
            // Add more else if statements for other menu items
            DrawerLayout drawer = rootView.findViewById(R.id.drawer_layout);
            drawer.closeDrawer(GravityCompat.START);
            return true;
        });

        addTagButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onAddTagClicked();
            }
        });

        return rootView;
    }

    /**
     * Handles the logic when the "Add tag" button is clicked in the recording fragment.
     * This method calculates the relative timestamp based on the current system time and
     * the absolute start time of the recording obtained from the SensorFusion instance.
     * It then retrieves the current fused location (latitude and longitude) and elevation
     * from the SensorFusion class. After acquiring these details, it constructs a new
     * GNSS sample with this data, sets the provider to "fusion", and adds this sample
     * to the trajectory being built. If the current location data is not available,
     * it logs an error message.
     * @author Michalis Voudaskas
     */

    private void onAddTagClicked() {
        Log.d("RecordingFragment", "Add tag button clicked");

        if (trajectory == null) {
            Log.e("RecordingFragment", "Trajectory builder is null. Cannot add GNSS sample.");
            return; // Consider initializing it here if that's appropriate
        }

        long currentTimestamp = System.currentTimeMillis();
        long relativeTimestamp = currentTimestamp - sensorFusion.getAbsoluteStartTime();

        if (fusedLocation != null) {
            //Log.d("RecordingFragment", "Current Fused Location retrieved: Latitude = "
                  //  + fusedLocation.latitude + ", Longitude = " + fusedLocation.longitude);

            float currentElevation = sensorFusion.getElevation();
          //  Log.d("RecordingFragment", "Current Elevation retrieved: " + currentElevation);

            // Build the GNSS sample
            Traj.GNSS_Sample.Builder sampleBuilder = Traj.GNSS_Sample.newBuilder()
                    .setAltitude(currentElevation)
                    .setLatitude((float) fusedLocation.latitude)
                    .setLongitude((float) fusedLocation.longitude)
                    .setProvider("fusion")
                    .setRelativeTimestamp(relativeTimestamp);

            // Log the details of the sample being added
            Traj.GNSS_Sample gnssSample = sampleBuilder.build();
           // Log.d("RecordingFragment", "GNSS Sample built: " + gnssSample.toString());

            // Add the new GNSS_Sample to the gnss_data list
            trajectory.addGnssData(gnssSample);
          //  Log.d("RecordingFragment", "GNSS Sample added to trajectory");
            Toast.makeText(getContext(), "Location Tagged", Toast.LENGTH_SHORT).show();

        } else {
          //  Log.e("RecordingFragment", "Current GNSS location is null. GNSS Sample not added.");
        }
    }

    private void initializeMap() {
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map);

        if (mapFragment != null) {
            mapFragment.getMapAsync(this::onMapReady);
        }
        PDRPOS = StartLocationFragment.StartLocation;
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {

        mMap = googleMap;
        mMap.setMapType(GlobalVariables.getMapType());


        mMap.getUiSettings().setCompassEnabled(true);
        mMap.getUiSettings().setTiltGesturesEnabled(true);
        mMap.getUiSettings().setRotateGesturesEnabled(true);
        mMap.getUiSettings().setScrollGesturesEnabled(true);
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(PDRPOS, 19f ));

        setupMapComponents();

        mapManager = new MapManager(this);


        floorOverlayManager = new FloorOverlayManager(mMap, mapManager, sensorFusion);
        // Setup overlays after map is ready
        floorOverlayManager.setupGroundOverlays();
        // Initially check and update overlays
        floorOverlayManager.checkAndUpdateFloorOverlay();
    }


    private void setupMapComponents() {
        userTrajectory = mMap.addPolyline(new PolylineOptions().width(7).color(Color.RED));
        pdrPolyline = mMap.addPolyline(new PolylineOptions().width(9).color(Color. GREEN));
        gnssLocation = sensorFusion.getGNSSLatitude(false);
        filterSetup(gnssLocation);
    }

    private void handleLocationUpdates() {
        if (gnssLocation == null) {
            return;
        }
        double latitude = gnssLocation[0];
        double longitude = gnssLocation[1];
        LatLng gnssLatLng = new LatLng(gnssLocation[0], gnssLocation[1]);

        // Create a new LatLng object for the GNSS location
        LatLng newLocation = new LatLng(latitude, longitude);
        updateGnssLocations(newLocation);
        updateLocationMarkers();
        updateMap(newLocation, newLocation);
        //updateMap(newLocation); // Update the map with the new location
        floorOverlayManager.isUserNearGroundFloor = floorOverlayManager.buildingBounds.contains(newLocation);
        floorOverlayManager.isuserNearGroundFloorLibrary = floorOverlayManager.buildingBounds.contains(newLocation);

        if (floorSelectionSpinner == null) {
            return; // Spinner not initialized yet, so we exit the method early
        }
        boolean inBuilding = floorOverlayManager.buildingBounds.contains(gnssLatLng) || floorOverlayManager.buildingBoundsLibrary.contains(gnssLatLng);
        floorSelectionSpinner.setVisibility(inBuilding ? View.VISIBLE : View.GONE);
        floorOverlayManager.checkUserInbounds();
        floorOverlayManager.checkAndUpdateFloorOverlay();
    }

    private Bitmap getBitmapFromVector(Context context, int vectorResId) {
        Drawable vectorDrawable = ContextCompat.getDrawable(context, vectorResId);
        Bitmap bitmap = Bitmap.createBitmap(vectorDrawable.getIntrinsicWidth(), vectorDrawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        vectorDrawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        vectorDrawable.draw(canvas);
        return bitmap;
    }

    private LatLng processLocationWithKalmanFilter(float[] gnssLocation) {
        double latitude = gnssLocation[0];
        double longitude = gnssLocation[1];
        long timeStamp = System.currentTimeMillis(); // Current time in milliseconds

        // Check if Kalman filter is initialized
        if (kalmanFilter.get_accuracy() < 0) {
            // Initialize Kalman filter with current GNSS data
            kalmanFilter.SetState(latitude, longitude, DEFAULT_ACCURACY, timeStamp);
        } else {
            // Process new GNSS data through the Kalman filter
            kalmanFilter.Process(latitude, longitude, DEFAULT_ACCURACY, timeStamp);
        }

        // Prepare the measurement array [lat, lon]
        double[] z = {latitude, longitude};

        // Measurement matrix (H), identity if directly measuring [lat, lon]
        double[][] H = {{1, 0, 0, 0}, {0, 1, 0, 0}};

        // Measurement noise covariance matrix (R), adjust based on GNSS accuracy
        double[][] R = {{5.0, 0}, {0, 5.0}}; // Example values, adjust based on your GNSS receiver's specs

        // Get the filtered state estimate
        double[] stateEstimate = ekf.getStateEstimate();

        // Extract the latitude and longitude from the state estimate
        double filteredLat = stateEstimate[0];
        double filteredLon = stateEstimate[1];

        // Use the filtered coordinates to update the map
        filteredLocation = new LatLng(kalmanFilter.get_lat(), kalmanFilter.get_lng());
        predictUserLocation();

        LatLng filteredLocation_ekf = new LatLng(filteredLat, filteredLon);
        // Update the EKF with the new measurement
        ekf.update(z, H, R);

        lastUpdateTime = System.currentTimeMillis();
        return filteredLocation_ekf;
    }
    private void predictUserLocation() {
        // Calculate the time step (dt) in seconds
        long currentTime = System.currentTimeMillis();
        double dt = (currentTime - lastUpdateTime) / 1000.0; // Convert milliseconds to seconds
        lastUpdateTime = currentTime; // Update the last update time

        // State transition matrix (F), adapted for the time step
        double[][] F = {{1, 0, dt, 0}, {0, 1, 0, dt}, {0, 0, 1, 0}, {0, 0, 0, 1}};

        // Process noise covariance matrix (Q), adjust based on expected process noise
        double[][] Q = {{1, 0, 0, 0}, {0, 1, 0, 0}, {0, 0, 1, 0}, {0, 0, 0, 1}};

        // Predict the next state
        ekf.predict(F, Q);
    }
    /**
     * Conducts map matching based on the user's current fused coordinates. This method is responsible
     * for finding the nearest point of interest within a predefined map data set that corresponds
     * to the user's real-world location. The method retrieves the user's latitude and longitude
     * from the fusedLocation variable and invokes the mapMatching's findNearestLocation method
     * to locate the closest point based on the user's current floor.
     */


    private void performMapMatching() {
        // Assume LocationResponse.getFloor() gives you the current floor number
        // And fusedLocation gives you the current lat and lon
        double currentLatitude = fusedLocation.latitude;
        double currentLongitude = fusedLocation.longitude;
        //Log.d("MapMatching", "User Location: Latitude = " + currentLatitude + ", Longitude = " + currentLongitude + ", Floor = " + currentFloor);
        LocationResponse nearestLocation = mapMatcher.findNearestLocation(currentLatitude, currentLongitude, currentFloor);
        //LocationResponse nearestLocation = mapMatcher.findKNNLocation(currentLatitude, currentLongitude, currentFloor, 3);
        if (nearestLocation != null) {
             //Use the getter methods to access the location's latitude and longitude
           // Log.d("MapMatching", "Nearest Location: Latitude = " + nearestLocation.getLatitude() + ", Longitude = " + nearestLocation.getLongitude());

            addMarkerToMap(nearestLocation.getLatitude(), nearestLocation.getLongitude());
        } else {
           // Log.d("MapMatching", "No location found on the given floor.");
        }
    }
    //method to add the mapmatched marker to the map
    private void addMarkerToMap(double lat, double lon) {
        if (mMap != null) {
            // Remove the previous marker if it exists
            if (mapMatched != null) {
                mapMatched.remove();
            }

            LatLng position = new LatLng(lat, lon);
            mapMatched = mMap.addMarker(new MarkerOptions()
                    .position(position)
                    .icon(BitmapDescriptorFactory.fromBitmap(getBitmapFromVector(getContext(), R.drawable.ic_baseline_red_dot_24)))
                    .visible(isMatchedMarkerVisible));
            //mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 15)); // Zoom level can be adjusted
        }
    }

    private void updateMap(LatLng newLocation, LatLng filteredLocation_ekf) {
        if (ekfmarker == null) {
            ekfmarker = mMap.addMarker(new MarkerOptions()
                    .position(filteredLocation_ekf)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)));

        } else {
            // Update the marker's position to the new, filtered location
            ekfmarker.setPosition(filteredLocation_ekf);
            ekfmarker.setVisible(isEKFMarkerVisible);
        }
        // Consider not animating the camera every update to avoid jitter
        //mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(newLocation, 19));
    }

    /**
     * Initializes and starts the periodic checking of the light level to determine
     * if the user is indoors or outdoors.
     */
    private void startIndoorOutdoorDetection() {
        lightLevelHandler = new Handler();
        lightLevelRunnable = new Runnable() {
            @Override
            public void run() {
                updateIndoorOutdoorStatus();
                lightLevelHandler .postDelayed(this, 5000); // Check every 5 seconds
            }
        };
        lightLevelHandler.post(lightLevelRunnable );
    }
    /**
     * Updates the status of whether the user is indoors or outdoors based on light sensor readings
     * and the user's presence within defined building bounds.
     */
    private void updateIndoorOutdoorStatus() {

        // Check if user is within any building bounds
        boolean isUserInsideAnyBuilding = floorOverlayManager.buildingBounds.contains(fusedLocation) || floorOverlayManager.buildingBoundsLibrary.contains(fusedLocation);

        // Fetch current light level from sensor fusion
        float currentLightLevel = sensorFusion.getSensorValueMap().get(SensorTypes.LIGHT)[0];
        //Log.d("RecordingFragment", "Current light level: " + currentLightLevel + " lux");

        // Determine indoor/outdoor status based on light level and building bounds
        boolean currentlyOutdoor = currentLightLevel > INDOOR_OUTDOOR_THRESHOLD || !isUserInsideAnyBuilding;
        // Update the global isOutdoor variable based on the current status
        isOutdoor = currentlyOutdoor;
        // Check for a change in the indoor/outdoor status
        if (wasPreviouslyOutdoor == null || wasPreviouslyOutdoor != currentlyOutdoor) {
            // Update the previous state
            wasPreviouslyOutdoor = currentlyOutdoor;
            // If the status has changed, show a toast message
            Toast.makeText(getContext(), "Detected as " + (isOutdoor ? "outdoor environment" : "indoor environment"), Toast.LENGTH_SHORT).show();
        }
       // Log.d("RecordingFragment", "Detected as " + (isOutdoor ? "outdoor environment" : "indoor environment"));
    }


    private void setupGnssUpdates() {
        // Initialize the Handler and Runnable for GNSS updates
        gnssUpdateHandler = new Handler(Looper.getMainLooper());
        gnssUpdateTask = new Runnable() {
            @Override
            public void run() {
                // Fetch GNSS data from SensorFusion
                gnssLocation = sensorFusion.getGNSSLatitude(false); // False indicates we're not fetching the initial start location
                double latitude = gnssLocation[0];
                double longitude = gnssLocation[1];
                // Convert the newLocation to your measurement vector z
                double[] z = { latitude, longitude};

                // Construct the H and R matrices based on your system model and measurement noise
                double[][] H = {{1, 0, 0, 0}, {0, 1, 0, 0}};
                double[][] R = {{10, 0}, {0, 10}};
                // Process GNSS data through the Kalman filter
                filteredLocation_ekf = processLocationWithKalmanFilter(gnssLocation);
                updateMap(filteredLocation, filteredLocation_ekf);

                handleLocationUpdates();

                // Schedule the next execution of this task
                gnssUpdateHandler.postDelayed(this, 500); // Adjust the delay as needed
            }
        };
    }

    private void setupMapTypeSpinner(View view) {
        Spinner mapTypeSpinner = view.findViewById(R.id.mapTypeSpinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(getContext(),
                R.array.map_types, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mapTypeSpinner.setAdapter(adapter);

        // Fetch the array of map types from resources
        String[] mapTypes = getResources().getStringArray(R.array.map_types);

        // Set the spinner to the current global map type
        mapTypeSpinner.setSelection(Arrays.asList(mapTypes).indexOf(String.valueOf(GlobalVariables.getMapType())), false);

        mapTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // Update the global map type based on user selection
                GlobalVariables.setMapType(getMapTypeFromIndex(position));
                if (mMap != null) {
                    mMap.setMapType(GlobalVariables.getMapType());
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                floorOverlayManager.setUserSelectedFloor(null);
            }
        });
    }


    private int getMapTypeFromIndex(int index) {
        switch (index) {
            case 0: return GlobalVariables.getMapType();
            case 1: return GoogleMap.MAP_TYPE_NORMAL;
            case 2: return GoogleMap.MAP_TYPE_SATELLITE;
            case 3: return GoogleMap.MAP_TYPE_TERRAIN;
            case 4: return GoogleMap.MAP_TYPE_HYBRID;
            default: return GlobalVariables.getMapType();
        }
    }


    /**
     * {@inheritDoc}
     * Text Views and Icons initialised to display the current PDR to the user. A Button onClick
     * listener is enabled to detect when to go to next fragment and allow the user to correct PDR.
     * A runnable thread is called to update the UI every 0.5 seconds.
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        pdrProcessing = new PdrProcessing(getContext());

        Button openDrawerButton = view.findViewById(R.id.open_drawer_button);
        DrawerLayout drawerLayout = view.findViewById(R.id.drawer_layout);
        openDrawerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                drawerLayout.openDrawer(GravityCompat.START); // Use GravityCompat.END for right-sided drawer
            }
        });

        Spinner floorSelectionSpinner = view.findViewById(R.id.floorSelectionSpinner);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(getContext(),
                R.array.floor_names, android.R.layout.simple_spinner_item);
        // Specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        floorSelectionSpinner.setAdapter(adapter);
        floorSelectionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                FloorOverlayManager.Floor selectedFloor;
                switch (position + 1) {
                    case 1:
                        floorOverlayManager.checkAndUpdateFloorOverlay();
                    case 2:
                        selectedFloor = FloorOverlayManager.Floor.GROUND;
                        floorOverlayManager.setUserSelectedFloor(selectedFloor);
                        break;
                    case 3:
                        selectedFloor = FloorOverlayManager.Floor.FIRST;
                        floorOverlayManager.setUserSelectedFloor(selectedFloor);
                        break;
                    case 4:
                        selectedFloor = FloorOverlayManager.Floor.SECOND;
                        floorOverlayManager.setUserSelectedFloor(selectedFloor);
                        break;
                    case 5:
                        selectedFloor = FloorOverlayManager.Floor.THIRD;
                        floorOverlayManager.setUserSelectedFloor(selectedFloor);
                        break;
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // Setup GNSS updates
        setupGnssUpdates();

        // Initialize the map type spinner
        setupMapTypeSpinner(view);


        // Set autoStop to null for repeat recordings
        this.autoStop = null;

        //Initialise UI components
        this.positionX = getView().findViewById(R.id.currentXPos);
        this.positionY = getView().findViewById(R.id.currentYPos);
        this.elevation = getView().findViewById(R.id.currentElevation);
        this.distanceTravelled = getView().findViewById(R.id.currentDistanceTraveled);
        this.compassIcon = getView().findViewById(R.id.compass);
        this.elevatorIcon = getView().findViewById(R.id.elevatorImage);

        //Set default text of TextViews to 0
        this.positionX.setText(getString(R.string.x, "0"));
        this.positionY.setText(getString(R.string.y, "0"));
        this.positionY.setText(getString(R.string.elevation, "0"));
        this.distanceTravelled.setText(getString(R.string.meter, "0"));

        //Reset variables to 0
        this.distance = 0f;
        this.previousPosX = 0f;
        this.previousPosY = 0f;

        // Display the progress of the recording when a max record length is set
        //this.timeRemaining = getView().findViewById(R.id.timeRemainingBar);

        // Display a blinking red dot to show recording is in progress

        // Check if there is manually set time limit:
        if(this.settings.getBoolean("split_trajectory", false)) {
            // If that time limit has been reached:
            long limit = this.settings.getInt("split_duration", 30) * 60000L;
            // Set progress bar
            this.timeRemaining.setMax((int) (limit/1000));
            this.timeRemaining.setScaleY(3f);

            // Create a CountDownTimer object to adhere to the time limit
            this.autoStop = new CountDownTimer(limit, 1000) {
                /**
                 * {@inheritDoc}
                 * Increment the progress bar to display progress and remaining time. Update the
                 * observed PDR values, and animate icons based on the data.
                 */
                @Override
                public void onTick(long l) {
                    // increment progress bar
                    timeRemaining.incrementProgressBy(1);
                    // Get new position
                    float[] pdrValues = sensorFusion.getSensorValueMap().get(SensorTypes.PDR);
                    positionX.setText(getString(R.string.x, String.format("%.1f", pdrValues[0])));
                    positionY.setText(getString(R.string.y, String.format("%.1f", pdrValues[1])));
                    // Calculate distance travelled
                    distance += Math.sqrt(Math.pow(pdrValues[0] - previousPosX, 2) + Math.pow(pdrValues[1] - previousPosY, 2));
                    distanceTravelled.setText(getString(R.string.meter, String.format("%.2f", distance)));
                    previousPosX = pdrValues[0];
                    previousPosY = pdrValues[1];
                    // Display elevation and elevator icon when necessary
                    float elevationVal = sensorFusion.getElevation();
                    elevation.setText(getString(R.string.elevation, String.format("%.1f", elevationVal)));
                    if(sensorFusion.getElevator()) elevatorIcon.setVisibility(View.VISIBLE);
                    else elevatorIcon.setVisibility(View.GONE);

                    //Rotate compass image to heading angle
                    compassIcon.setRotation((float) - Math.toDegrees(sensorFusion.passOrientation()));
                }

                /**
                 * {@inheritDoc}
                 * Finish recording and move to the correction fragment.
                 *
                 * @see CorrectionFragment
                 */
                @Override
                public void onFinish() {
                    // Timer done, move to next fragment automatically - will stop recording
                    sensorFusion.stopRecording();
                    NavDirections action = RecordingFragmentDirections.actionRecordingFragmentToCorrectionFragment();
                    Navigation.findNavController(view).navigate(action);
                }
            }.start();
            blinkingRecording();
        }
        else {
            // No time limit - use a repeating task to refresh UI.
            this.refreshDataHandler.post(refreshDataTask);
        }

        Button btnToggleMarkers = view.findViewById(R.id.btnToggleMarkers);
        btnToggleMarkers.setOnClickListener(v -> showToggleMarkersDialog());

        rcntr = view.findViewById(R.id.rcntr);
        rcntr.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {


                // Center the map on the current fused location of the user
                if (mMap != null) {
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(fusedLocation, 19f));
                }
            }
        });
    }

    /**
     * Runnable task used to refresh UI elements with live data.
     * Has to be run through a Handler object to be able to alter UI elements
     */
    private final Runnable refreshDataTask = new Runnable() {
        @Override
        public void run() {
            //Getting positions from all 3 sources and processing (if needed) to LatLng
            float[] pdrValues = sensorFusion.getSensorValueMap().get(SensorTypes.PDR);
            float[] gnssValues = sensorFusion.getGNSSLatitude(false);

            LatLng PDRFilter = convertMetersToLatLng(pdrValues, PDRPOS);
            LatLng GNSSFilter = new LatLng(gnssValues[0], gnssValues[1]);
            LatLng WifiFilter = wifiLocation;

            positionX.setText(getString(R.string.x, String.format("%.1f", pdrValues[0])));
            positionY.setText(getString(R.string.y, String.format("%.1f", pdrValues[1])));
            // Calculate distance travelled
            distance += Math.sqrt(Math.pow(pdrValues[0] - previousPosX, 2) + Math.pow(pdrValues[1] - previousPosY, 2));
            distanceTravelled.setText(getString(R.string.meter, String.format("%.2f", distance)));
            floorOverlayManager.checkAndUpdateFloorOverlay();
            updatePDRPosition();
            fetchWifiLocationFromServer();

            if (WifiFilter == null) {
                fusedLocation = updateParticleFilterPositions(GNSSFilter, PDRFilter, GNSSFilter);
            }
            else {
                if (isOutdoor) {
                    fusedLocation = updateParticleFilterPositions(GNSSFilter, PDRFilter, GNSSFilter);
                }
                else {
                    fusedLocation = updateParticleFilterPositions(WifiFilter, PDRFilter, GNSSFilter);
                }
            }
            //Map Matching
            performMapMatching();



            if (fusedLocation != null && WifiFilter != null && GNSSFilter != null && PDRFilter != null) {
                double errorFusedWifi = calculateDistanceBetweenLatLng(fusedLocation, WifiFilter);
                TextView errorWifi = getView().findViewById(R.id.tvErrorFusedWifi);
                errorWifi.setText(String.format("Fused-WiFi Error: %.2f m", errorFusedWifi));

                double errorFusedGnss = calculateDistanceBetweenLatLng(fusedLocation, GNSSFilter);
                TextView errorGnss = getView().findViewById(R.id.tvErrorFusedGnss);
                errorGnss.setText(String.format("Fused-GNSS Error: %.2f m", errorFusedGnss));

                double errorFusedPdr = calculateDistanceBetweenLatLng(fusedLocation, PDRFilter);
                TextView errorPdr = getView().findViewById(R.id.tvErrorFusedPdr);
                errorPdr.setText(String.format("Fused-PDR Error: %.2f m", errorFusedPdr));
            }

//            LatLng fusedPosition = updateParticleFilterPositions(WifiFilter, PDRFilter, GNSSFilter);
            recentFusedLocations.add(fusedLocation);
            while (recentFusedLocations.size() > MOVING_AVERAGE_WINDOW) {
                recentFusedLocations.remove(0); // Remove the oldest location to maintain window size
            }

            LatLng movingAverageFusedLocation = getAverageLocation(recentFusedLocations);

            updateMapWithFusedPosition(movingAverageFusedLocation);
            updatePath(movingAverageFusedLocation);

            previousPosX = pdrValues[0];
            previousPosY = pdrValues[1];
            // Display elevation and elevator icon when necessary
            elevationVal = sensorFusion.getElevation();

            floorOverlayManager.checkAndUpdateFloorOverlay();

            elevation.setText(getString(R.string.elevation, String.format("%.1f", elevationVal)));
            if(sensorFusion.getElevator()) elevatorIcon.setVisibility(View.VISIBLE);
            else elevatorIcon.setVisibility(View.GONE);

            //Rotate compass image to heading angle
            compassIcon.setRotation((float) - Math.toDegrees(sensorFusion.passOrientation()));

            // Loop the task again to keep refreshing the data
            refreshDataHandler.postDelayed(refreshDataTask, 500);
        }
    };


    private void updatePDRPosition() {
        // Assuming getPDRCoordinates() returns the latest PDR coordinates as float[2] with x, y values
        float[] pdrCoordinates = sensorFusion.getSensorValueMap().get(SensorTypes.PDR);
        if (pdrCoordinates != null && PDRPOS != null) {
            LatLng pdrLatLng = convertMetersToLatLng(pdrCoordinates, PDRPOS);
            updatePDRLocations(pdrLatLng);
//            updatePDRPath(pdrLatLng); // If you also want to draw the path

            floorOverlayManager.isUserNearGroundFloor = ((pdrLatLng.latitude >= floorOverlayManager.southwestcornerNucleus.latitude && pdrLatLng.latitude <= floorOverlayManager.northeastcornerNucleus.latitude)
                    && (pdrLatLng.longitude >= floorOverlayManager.southwestcornerNucleus.longitude && pdrLatLng.longitude <= floorOverlayManager.northeastcornerNucleus.longitude));
            floorOverlayManager.isuserNearGroundFloorLibrary = ((pdrLatLng.latitude >= floorOverlayManager.southwestcornerLibrary.latitude && pdrLatLng.latitude <= floorOverlayManager.northeastcornerLibrary.latitude)
                    && (pdrLatLng.longitude >= floorOverlayManager.southwestcornerLibrary.longitude && pdrLatLng.longitude <= floorOverlayManager.northeastcornerLibrary.longitude));
            floorOverlayManager.checkAndUpdateFloorOverlay();
        }
    }
    /**
     Method to send the json fingerprint to the server and receive the latlong resposne
     Also checks if the wifi list is empty or the server response is null and notifies the user that there is no wifi coverage
     @author Michalis Voudaskas
     */

    private void fetchWifiLocationFromServer() {
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                String wifiFingerprintJson = wifiFPManager.createWifiFingerprintJson();
                LocationResponse locationResponse = serverCommunications.sendWifiFingerprintToServer(wifiFingerprintJson);
                 currentFloor = locationResponse.getFloor();

                getActivity().runOnUiThread(() -> {
                    // Check if locationResponse is null, which indicates no WiFi coverage
                    if (locationResponse == null || sensorFusion.getWifiList() == null) {
                       // Log.e("RecordingFragment", "No Wi-Fi coverage detected.");
                        //Toast.makeText(getContext(), "No Wi-Fi coverage detected", Toast.LENGTH_LONG).show();

                        // Trigger blinking animation
                        startBlinkingAnimation();
                        return; // Exit early
                    }

                    // Stop blinking animation if valid WiFi location is received
                    stopBlinkingAnimation();

                  //  Log.d("RecordingFragment", "Received Wi-Fi location.");
                    wifiLocation = new LatLng(locationResponse.getLatitude(), locationResponse.getLongitude());

                    // Update the list of recent locations
                    updateWifiLocations(wifiLocation);
                    updateLocationMarkers();
                });
            } catch (Exception e) {
               // Log.e("RecordingFragment", "Exception while fetching location: " + e.getMessage(), e);
                getActivity().runOnUiThread(() -> {
                    // Check if the fragment's view is still valid
                    if (getView() == null) {
                        return; // Fragment view is no longer valid, exit early
                    }
                    startBlinkingAnimation(); // Ensure this method also checks getView() is not null
                });
            }
        });
    }

    private void startBlinkingAnimation() {
        if (getView() == null) return;
        // find the wifi animation view
        View uiElement = getView().findViewById(R.id.no_wiifi_id);
        if (uiElement != null) {
            // Make the UI element visible
            uiElement.setVisibility(View.VISIBLE);

            // Set up the blinking animation
            ObjectAnimator animator = ObjectAnimator.ofFloat(uiElement, "alpha", 0f, 1f);
            animator.setDuration(800); // Duration of one blink
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setRepeatMode(ValueAnimator.REVERSE);
            animator.start();
        }
    }

    private void stopBlinkingAnimation() {
        if (getView() == null) return;
        View rootView = getView();
        if (rootView != null) {
            View uiElement = rootView.findViewById(R.id.no_wiifi_id);
            uiElement.clearAnimation();
            uiElement.setVisibility(View.GONE);
        }
    }
    // check if the most recent wifi is an outlier by comparing with the average of the last 5 wifi positions
    private boolean isOutlier(LatLng newLocation) {
        //consider a location an outlier if it's too far from the average of recent locations
        if (recentWifiLocations.isEmpty()) {
            return false; // No history to compare against
        }

        LatLng averageLocation = getAverageLocation(recentWifiLocations);
        double distanceToAverage = calculateDistanceBetweenLatLng(averageLocation, newLocation);
        return distanceToAverage > OUTLIER_THRESHOLD_METERS;
    }

    //store the last 5 wifi positions
    private void updateWifiLocations(LatLng newLocation) {
        recentWifiLocations.add(newLocation);
        if (recentWifiLocations.size() > MAX_RECENT_LOCATIONS) {
            recentWifiLocations.remove(0); // Keep the list size fixed
        }
    }

    //store the last 5 pdr positions
    private void updatePDRLocations(LatLng newLocation) {
        recentPDRLocations.add(newLocation);
        if (recentPDRLocations.size() > MAX_RECENT_LOCATIONS) {
            recentPDRLocations.remove(0); // Keep the list size fixed
        }
    }

    //store the last 5 gnss positions
    private void updateGnssLocations(LatLng newLocation) {
        recentGNSSLocations.add(newLocation);
        if (recentGNSSLocations.size() > MAX_RECENT_LOCATIONS) {
            recentGNSSLocations.remove(0); // Keep the list size fixed
        }
    }

    //get the average positions from latlng
    private LatLng getAverageLocation(List<LatLng> locations) {
        double sumLat = 0;
        double sumLng = 0;
        for (LatLng loc : locations) {
            sumLat += loc.latitude;
            sumLng += loc.longitude;
        }
        return new LatLng(sumLat / locations.size(), sumLng / locations.size());
    }

    // Distance between 2 latlongs
    private double calculateDistanceBetweenLatLng(LatLng pos1, LatLng pos2) {
        final int R = 6371; // Radius of the earth in kilometers

        double lat1 = pos1.latitude;
        double lon1 = pos1.longitude;
        double lat2 = pos2.latitude;
        double lon2 = pos2.longitude;

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = R * c * 1000; // convert to meters

        return distance;
    }


    //haversine formula to get the distance between 2 latlongs
    private LatLng convertMetersToLatLng(float[] pdrCoordinates, LatLng startLatLng) {
        // Constants
        final double metersInOneDegreeLatitude = 111111.0;

        // Calculate the change in degrees
        double deltaLat = pdrCoordinates[1] / metersInOneDegreeLatitude;
        double deltaLon = pdrCoordinates[0] / (metersInOneDegreeLatitude * Math.cos(Math.toRadians(startLatLng.latitude)));

        // Calculate the new position
        double newLat = startLatLng.latitude + deltaLat;
        double newLon = startLatLng.longitude + deltaLon;


        return new LatLng(newLat, newLon);
    }

    private Polyline pdrPath; // Field to hold the PDR path

    private void updatePath(LatLng newPosition) {
        if (mMap != null) {
            if (pdrPath == null) {
                // First time: create the polyline
                pdrPath = mMap.addPolyline(new PolylineOptions().add(newPosition)
                        .width(5).color(Color.BLUE)); // Customize as needed
            } else {
                // Subsequent times: add the new position to the existing polyline
                List<LatLng> points = pdrPath.getPoints();
                points.add(newPosition);
                pdrPath.setPoints(points);
            }
        }
    }

    //method to display the last 5 positions of pdr gnss and wifi @author: Michalis Voudaskas
    private void updateLocationMarkers() {
        // Clear previous markers
        for (Marker marker : gnssMarkers) {
            marker.remove();
        }
        gnssMarkers.clear();

        for (Marker marker : wifiMarkers) {
            marker.remove();
        }
        wifiMarkers.clear();

        for (Marker marker : pdrMarkers) {
            marker.remove();
        }
        pdrMarkers.clear();

        // Add new PDR markers
        for (LatLng location : recentPDRLocations) {
            Marker marker = mMap.addMarker(new MarkerOptions()
                    .position(location)
                    .icon(BitmapDescriptorFactory.fromBitmap(getBitmapFromVector(getContext(), R.drawable.ic_baseline_green_dot_24)))
                    .visible(arePDRMarkersVisible));
            pdrMarkers.add(marker);
        }

        // Add new GNSS markers
        for (LatLng location : recentGNSSLocations) {
            Marker marker = mMap.addMarker(new MarkerOptions()
                    .position(location)
                    .icon(BitmapDescriptorFactory.fromBitmap(getBitmapFromVector(getContext(), R.drawable.ic_baseline_pink_dot_24)))
                    .visible(areGnssMarkersVisible));
            gnssMarkers.add(marker);
        }

        // Add new Wi-Fi markers
        for (LatLng location : recentWifiLocations) {
            boolean isOutlierLocation = isOutlier(location);
            Marker marker = mMap.addMarker(new MarkerOptions()
                    .position(location)
                    .icon(BitmapDescriptorFactory.fromBitmap(getBitmapFromVector(getContext(),
                            isOutlierLocation ? R.drawable.ic_baseline_yellow_dot_24 : R.drawable.ic_baseline_purple_dot_24
                    )))
                    .visible(areWifiMarkersVisible)); // Yellow for outlier, otherwise purple
            wifiMarkers.add(marker);
        }
    }

    // Filter Setup Method for Particle Filter
    public void filterSetup(float[] startloc) {
        if (startloc != null) {
            double latitude = startloc[0];
            double longitude = startloc[1];
            // Create a new LatLng object for the GNSS location
            LatLng filterstartloc = new LatLng(latitude, longitude);
            particleFilter = new ParticleFilter(50, filterstartloc);
        }
    }

    //Method to update Particle Filter to get an estimated position
    private LatLng updateParticleFilterPositions(LatLng predictPos, LatLng UpdatePos1, LatLng UpdatePos2) {
        if (particleFilter != null) {
            // Assuming a small measurement noise for demonstration. Adjust based on actual data quality.
            float measurementNoise = 5.0f; // Meters, adjust as needed

            // Update particle filter with GNSS, PDR, and WiFi positions
            // You can adjust the measurement noise for each type of update depending on their reliability
            particleFilter.updateFilter(predictPos, UpdatePos1, UpdatePos2, measurementNoise);

            // Optionally, get and use the fused position
            LatLng fusedPosition = particleFilter.getFusedPosition();
            return fusedPosition;
//            updateMapWithFusedPosition(fusedPosition);
        }
        return null;
    }

    // Updates the map with the fused position
    private void updateMapWithFusedPosition(LatLng position) {

        if (position == null) {
            return;
        }
        float mapBearing = mMap.getCameraPosition().bearing; // Map's bearing in degrees
        float azimuthInRadians = sensorFusion.passOrientation();
        float azimuthInDegrees = (float) Math.toDegrees(azimuthInRadians);
        float adjustedAzimuth = azimuthInDegrees - mapBearing;
        adjustedAzimuth = (adjustedAzimuth + 360) % 360;
        if (mMap != null) {
            if (FusedMarker == null) {
                // Create the marker if it doesn't exist
                FusedMarker = mMap.addMarker(new MarkerOptions()
                        .position(position)
                        .icon(BitmapDescriptorFactory.fromBitmap(getBitmapFromVector(getContext(), R.drawable.ic_baseline_navigation_24)))
                        .rotation(adjustedAzimuth)
                        .anchor(0.5f, 0.5f));
            } else {
                // Update the marker's position
                FusedMarker.setPosition(position);
                FusedMarker.setRotation(adjustedAzimuth);

            }
            //mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 18)); // Adjust zoom as needed
        }
    }

    /**
     * Displays a blinking red dot to signify an ongoing recording.
     *
     * @see Animation for makin the red dot blink.
     */
    private void blinkingRecording() {
        //Initialise Image View
        this.recIcon = getView().findViewById(R.id.redDot);
        //Configure blinking animation
        Animation blinking_rec = new AlphaAnimation(1, 0);
        blinking_rec.setDuration(800);
        blinking_rec.setInterpolator(new LinearInterpolator());
        blinking_rec.setRepeatCount(Animation.INFINITE);
        blinking_rec.setRepeatMode(Animation.REVERSE);
        recIcon.startAnimation(blinking_rec);
    }

    /**
     * {@inheritDoc}
     * Stops ongoing refresh task, but not the countdown timer which stops automatically
     */
    @Override
    public void onPause() {
        super.onPause();
        gnssUpdateHandler.removeCallbacks(gnssUpdateTask);
        if (lightLevelHandler != null && lightLevelRunnable != null) {
            lightLevelHandler.removeCallbacks(lightLevelRunnable);
        }
    }

    /**
     * {@inheritDoc}
     * Restarts UI refreshing task when no countdown task is in progress
     */
    @Override
    public void onResume() {
        super.onResume();
        if (gnssUpdateTask != null) {
            gnssUpdateHandler.post(gnssUpdateTask);
        }
        startIndoorOutdoorDetection();
    }

    //display the user the option to choose which markers they want to see @author: Michalis Voudaskas
    private void showToggleMarkersDialog() {
        // Current visibility states
        boolean[] checkedItems = {areGnssMarkersVisible, areWifiMarkersVisible, arePDRMarkersVisible, isEKFMarkerVisible, isMatchedMarkerVisible};

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Toggle Marker Visibility")
                .setMultiChoiceItems(new CharSequence[]{"GNSS Markers", "Wi-Fi Markers", "PDR Markers", "EKF Marker", "Map Matched Marker"}, checkedItems, (dialog, which, isChecked) -> {
                    if (which == 0) { // GNSS Markers
                        areGnssMarkersVisible = isChecked;
                    } else if (which == 1) { // Wi-Fi Markers
                        areWifiMarkersVisible = isChecked;
                    } else if (which ==2) {//pdr MARKERS
                        arePDRMarkersVisible = isChecked;
                    } else if (which ==3 ){
                        isEKFMarkerVisible = isChecked;
                    } else if (which == 4) {
                        isMatchedMarkerVisible = isChecked;
                    }
                })

                .setPositiveButton("OK", (dialog, id) -> {
                    toggleMarkerVisibility();
                })
                .setNegativeButton("Cancel", (dialog, id) -> {
                    // User cancelled the dialog
                });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    //adjust the marker visibility based on the user's preference
    private void toggleMarkerVisibility() {
        for (Marker marker : gnssMarkers) {
            marker.setVisible(areGnssMarkersVisible);
        }
        for (Marker marker : wifiMarkers) {
            marker.setVisible(areWifiMarkersVisible);
        }
        for (Marker marker : pdrMarkers) {
            marker.setVisible(arePDRMarkersVisible);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        // Remove the scheduled Runnable tasks
        if (refreshDataHandler != null) {
            refreshDataHandler.removeCallbacks(refreshDataTask);
        }
        if (gnssUpdateHandler != null) {
            gnssUpdateHandler.removeCallbacks(gnssUpdateTask);
        }
        if (lightLevelHandler != null && lightLevelRunnable != null) {
            lightLevelHandler.removeCallbacks(lightLevelRunnable);
        }
    }

}

