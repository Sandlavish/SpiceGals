package com.openpositioning.PositionMe.fragments;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;
import androidx.preference.PreferenceManager;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.GroundOverlay;
import com.google.android.gms.maps.model.GroundOverlayOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
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
 * @author Mate Stodulka
 */
public class RecordingFragment extends Fragment implements OnMapReadyCallback {

    private static final float DEFAULT_ACCURACY = 0.5f ;
    private Polyline pdrPolyline;
    private Polyline userTrajectory;

    private Handler gnssUpdateHandler;
    private Runnable gnssUpdateTask;
    private Handler lightLevelHandler;
    private Runnable lightLevelRunnable;

    private static final float INDOOR_OUTDOOR_THRESHOLD = 1000.0f;
    private boolean isOutdoor;

    private float[] gnssLocation;

    private PdrProcessing pdrProcessing;


    //Button to end PDR recording
    private Button stopButton;
    private Button cancelButton;
    //Recording icon to show user recording is in progress
    private ImageView recIcon;

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
    //?
    private Handler refreshDataHandler;

    //variables to store data of the trajectory
    private float distance;

    private Marker pdrMarker;
    private float previousPosX;
    private float previousPosY;

    private GoogleMap mMap;

    private Marker userLocationMarker;

    private Boolean wasPreviouslyOutdoor = null; // null indicates no previous status

    private LatLng PDRPOS;

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

    private static final int MAX_RECENT_LOCATIONS = 5;
    private static final double OUTLIER_THRESHOLD_METERS = 5;

    private KalmanLatLong kalmanFilter;
    //Particle Filter
    private ParticleFilter particleFilter;
    private Marker FusedMarker;

    private WifiFPManager wifiFPManager;
    private ServerCommunications serverCommunications;

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

    public class KalmanLatLong {
        private final float MinAccuracy = 1;

        private float Q_metres_per_second;
        private long TimeStamp_milliseconds;
        private double lat;
        private double lng;
        private float variance; // P matrix.  Negative means object uninitialised.

        public KalmanLatLong(float Q_metres_per_second) {
            this.Q_metres_per_second = Q_metres_per_second;
            variance = -1;
        }

        public double get_lat() {
            return lat;
        }

        public double get_lng() {
            return lng;
        }

        public float get_accuracy() {
            return (float) Math.sqrt(variance);
        }

        public void SetState(double lat, double lng, float accuracy, long TimeStamp_milliseconds) {
            this.lat = lat;
            this.lng = lng;
            variance = accuracy * accuracy;
            this.TimeStamp_milliseconds = TimeStamp_milliseconds;
        }

        public void Process(double lat_measurement, double lng_measurement, float accuracy, long TimeStamp_milliseconds, float Q_metres_per_second) {
            if (accuracy < MinAccuracy) accuracy = MinAccuracy;
            if (variance < 0) {
                // if variance < 0, object is unitialised, so initialise with current values
                SetState(lat_measurement, lng_measurement, accuracy, TimeStamp_milliseconds);
            } else {
                // else apply Kalman filter methodology

                long TimeInc_milliseconds = TimeStamp_milliseconds - this.TimeStamp_milliseconds;
                if (TimeInc_milliseconds > 0) {
                    // time has moved on, so the uncertainty in the current position increases
                    variance += TimeInc_milliseconds * Q_metres_per_second * Q_metres_per_second / 1000;
                    this.TimeStamp_milliseconds = TimeStamp_milliseconds;
                    // TO DO: USE VELOCITY INFORMATION HERE TO GET A BETTER ESTIMATE OF CURRENT POSITION
                }

                // Kalman gain matrix K = Covariance * Inverse(Covariance + MeasurementVariance)
                // because K is dimensionless, it doesn't matter that variance has different units to lat and lng
                float K = variance / (variance + accuracy * accuracy);
                // apply K
                lat += K * (lat_measurement - lat);
                lng += K * (lng_measurement - lng);
                // new Covariance matrix is (IdentityMatrix - K) * Covariance
                variance = (1 - K) * variance;
            }
        }
    }
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.sensorFusion = SensorFusion.getInstance();
        Context context = getActivity();
        this.settings = PreferenceManager.getDefaultSharedPreferences(context);
        this.refreshDataHandler = new Handler();
        this.kalmanFilter = new KalmanLatLong(Q_METRES_PER_SECOND); // Ensure you have defined Q_METRES_PER_SECOND appropriately
        this.wifiFPManager = WifiFPManager.getInstance();
        serverCommunications = new ServerCommunications(context);

        // Initialize trajectory
        trajectory = Traj.Trajectory.newBuilder();
        // If Trajectory.Builder requires more parameters or a starting timestamp, set them here.
        trajectory.setStartTimestamp(System.currentTimeMillis());
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
        addTagButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onAddTagClicked();
            }
        });

        return rootView;
    }

    private void onAddTagClicked() {
        Log.d("RecordingFragment", "Add tag button clicked");

        if (trajectory == null) {
            Log.e("RecordingFragment", "Trajectory builder is null. Cannot add GNSS sample.");
            return; // Consider initializing it here if that's appropriate
        }

        long currentTimestamp = System.currentTimeMillis();
        long relativeTimestamp = currentTimestamp - trajectory.getStartTimestamp();

        Log.d("RecordingFragment", "Current timestamp: " + currentTimestamp);
        Log.d("RecordingFragment", "Start timestamp: " + trajectory.getStartTimestamp());
        Log.d("RecordingFragment", "Relative timestamp calculated: " + relativeTimestamp);

        // Get the current location
        float [] currentLocation = sensorFusion.getGNSSLatitude(false); // Replace with fused location if necessary

        if (currentLocation != null) {
            Log.d("RecordingFragment", "Current GNSS location retrieved: Latitude = "
                    + currentLocation[0] + ", Longitude = " + currentLocation[1]);

            float currentElevation = sensorFusion.getElevation();
            Log.d("RecordingFragment", "Current Elevation retrieved: " + currentElevation);

            // Build the GNSS sample
            Traj.GNSS_Sample.Builder sampleBuilder = Traj.GNSS_Sample.newBuilder()
                    .setAltitude(currentElevation)
                    .setLatitude(currentLocation[0])
                    .setLongitude(currentLocation[1])
                    .setProvider("fusion")
                    .setRelativeTimestamp(relativeTimestamp);

            // Log the details of the sample being added
            Traj.GNSS_Sample gnssSample = sampleBuilder.build();
            Log.d("RecordingFragment", "GNSS Sample built: " + gnssSample.toString());

            // Add the new GNSS_Sample to the gnss_data list
            trajectory.addGnssData(gnssSample);
            Log.d("RecordingFragment", "GNSS Sample added to trajectory");

        } else {
            Log.e("RecordingFragment", "Current GNSS location is null. GNSS Sample not added.");
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
        mMap.getUiSettings().setCompassEnabled(true);
        mMap.getUiSettings().setTiltGesturesEnabled(true);
        mMap.getUiSettings().setRotateGesturesEnabled(true);
        mMap.getUiSettings().setScrollGesturesEnabled(true);
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(PDRPOS, 16f ));

        setupMapComponents();

        mapManager = new MapManager(this);


        floorOverlayManager = new FloorOverlayManager(mMap, mapManager, sensorFusion);
        floorOverlayManager.setupGroundOverlays();
        // After setting up overlays, check and update them based on the current elevation
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

        // Create a new LatLng object for the GNSS location
        LatLng newLocation = new LatLng(latitude, longitude);
        updateGnssLocations(newLocation);
        updateLocationMarkers();
        // Update the map with the new locationw
        // updateMap(newLocation);
        //updateMap(newLocation); // Update the map with the new location
        floorOverlayManager.isUserNearGroundFloor = floorOverlayManager.buildingBounds.contains(newLocation);
        floorOverlayManager.isuserNearGroundFloorLibrary = floorOverlayManager.buildingBounds.contains(newLocation);
    }

    private Bitmap getBitmapFromVector(Context context, int vectorResId) {
        Drawable vectorDrawable = ContextCompat.getDrawable(context, vectorResId);
        Bitmap bitmap = Bitmap.createBitmap(vectorDrawable.getIntrinsicWidth(), vectorDrawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        vectorDrawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        vectorDrawable.draw(canvas);
        return bitmap;
    }

    private void processLocationWithKalmanFilter(float[] gnssLocation) {
        double latitude = gnssLocation[0];
        double longitude = gnssLocation[1];

        if (kalmanFilter.get_accuracy() < 0) {
            // If Kalman filter is uninitialized, initialize with the current GNSS data
            kalmanFilter.SetState(latitude, longitude, DEFAULT_ACCURACY, System.currentTimeMillis());
        } else {
            // Process the new GNSS data through the Kalman filter
            kalmanFilter.Process(latitude, longitude, DEFAULT_ACCURACY, System.currentTimeMillis(), Q_METRES_PER_SECOND);
        }

        // Use the filtered coordinates to update the map or UI
        LatLng filteredLocation = new LatLng(kalmanFilter.get_lat(), kalmanFilter.get_lng());
        //updateMap(filteredLocation);
    }

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

    private void updateIndoorOutdoorStatus() {
        // Fetch the latest GNSS location from sensor fusion
        float[] gnssLocation = sensorFusion.getGNSSLatitude(false);
        LatLng currentLocation = new LatLng(gnssLocation[0], gnssLocation[1]);

        // Check if user is within any building bounds
        boolean isUserInsideAnyBuilding = floorOverlayManager.buildingBounds.contains(currentLocation) || floorOverlayManager.buildingBoundsLibrary.contains(currentLocation);

        // Fetch current light level from sensor fusion
        float currentLightLevel = sensorFusion.getSensorValueMap().get(SensorTypes.LIGHT)[0];
        Log.d("RecordingFragment", "Current light level: " + currentLightLevel + " lux");

        // Determine indoor/outdoor status based on light level and building bounds
        boolean currentlyOutdoor = currentLightLevel > INDOOR_OUTDOOR_THRESHOLD || !isUserInsideAnyBuilding;

        // Check for a change in the indoor/outdoor status
        if (wasPreviouslyOutdoor == null || wasPreviouslyOutdoor != currentlyOutdoor) {
            // If the status has changed, show a toast message
            String environment = currentlyOutdoor ? "Outdoor" : "Indoor";
            Toast.makeText(getContext(), environment, Toast.LENGTH_SHORT).show();

            // Update the previous state
            wasPreviouslyOutdoor = currentlyOutdoor;
        }

        // Update the global isOutdoor variable based on the current status
        isOutdoor = currentlyOutdoor;
        Log.d("RecordingFragment", "Detected as " + (isOutdoor ? "outdoor environment" : "indoor environment"));
    }

//    private void updateMap(LatLng newLocation) {
//        if (userLocationMarker == null) {
//            userLocationMarker = mMap.addMarker(new MarkerOptions()
//                    .position(newLocation)
//                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
//        } else {
//            // Update the marker's position to the new, filtered location
//            userLocationMarker.setPosition(newLocation);
//        }
//        // Consider not animating the camera every update to avoid jitter
//        float zoom = 17;
//        //mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(newLocation, zoom));
//    }

    private void setupGnssUpdates() {
        // Initialize the Handler and Runnable for GNSS updates
        gnssUpdateHandler = new Handler(Looper.getMainLooper());
        gnssUpdateTask = new Runnable() {
            @Override
            public void run() {
                // Fetch GNSS data from SensorFusion
                gnssLocation = sensorFusion.getGNSSLatitude(false); // False indicates we're not fetching the initial start location

                // Process GNSS data through the Kalman filter
                processLocationWithKalmanFilter(gnssLocation);

                handleLocationUpdates();

                // Schedule the next execution of this task
                gnssUpdateHandler.postDelayed(this, 500); // Adjust the delay as needed
            }
        };
    }

    private void updateFloorButtonStates(boolean isUserNearAnyFloor, Button... buttons) {
        for (Button button : buttons) {
            button.setVisibility(isUserNearAnyFloor ? View.VISIBLE : View.GONE);
            button.setEnabled(isUserNearAnyFloor);
        }
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
            }
        });
    }


    private int getMapTypeFromIndex(int index) {
        switch (index) {
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
        //float currentElevation = sensorFusion.getElevation();

        Button btnGroundFloor = view.findViewById(R.id.btnGroundFloor);
        Button btnFirstFloor = view.findViewById(R.id.btnFirstFloor);
        Button btnSecondFloor = view.findViewById(R.id.btnSecondFloor);
        Button btnThirdFloor = view.findViewById(R.id.btnThirdFloor);

        // Setup GNSS updates
        setupGnssUpdates();

        // Setup visibility and enabled state based on user proximity
        updateFloorButtonStates(FloorOverlayManager.isUserNearGroundFloor || FloorOverlayManager.isuserNearGroundFloorLibrary,
                btnGroundFloor, btnFirstFloor, btnSecondFloor, btnThirdFloor);

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

        // Stop button to save trajectory and move to corrections
        this.stopButton = getView().findViewById(R.id.stopButton);
        this.stopButton.setOnClickListener(new View.OnClickListener() {
            /**
             * {@inheritDoc}
             * OnClick listener for button to go to next fragment.
             * When button clicked the PDR recording is stopped and the {@link CorrectionFragment} is loaded.
             */
            @Override
            public void onClick(View view) {
                if(autoStop != null) autoStop.cancel();
                sensorFusion.stopRecording();
                NavDirections action = RecordingFragmentDirections.actionRecordingFragmentToCorrectionFragment();
                Navigation.findNavController(view).navigate(action);
            }
        });

        // Cancel button to discard trajectory and return to Home
        this.cancelButton = getView().findViewById(R.id.cancelButton);
        this.cancelButton.setOnClickListener(new View.OnClickListener() {
            /**
             * {@inheritDoc}
             * OnClick listener for button to go to home fragment.
             * When button clicked the PDR recording is stopped and the {@link HomeFragment} is loaded.
             * The trajectory is not saved.
             */
            @Override
            public void onClick(View view) {
                sensorFusion.stopRecording();
                NavDirections action = RecordingFragmentDirections.actionRecordingFragmentToHomeFragment();
                Navigation.findNavController(view).navigate(action);
                if(autoStop != null) autoStop.cancel();
            }
        });

        // Display the progress of the recording when a max record length is set
        this.timeRemaining = getView().findViewById(R.id.timeRemainingBar);

        // Display a blinking red dot to show recording is in progress
        blinkingRecording();

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
        }
        else {
            // No time limit - use a repeating task to refresh UI.
            this.refreshDataHandler.post(refreshDataTask);
        }

        Button btnToggleMarkers = view.findViewById(R.id.btnToggleMarkers);
        btnToggleMarkers.setOnClickListener(v -> showToggleMarkersDialog());
    }

    /**
     * Runnable task used to refresh UI elements with live data.
     * Has to be run through a Handler object to be able to alter UI elements
     */
    private final Runnable refreshDataTask = new Runnable() {
        @Override
        public void run() {
            // Get new position
            float[] pdrValues = sensorFusion.getSensorValueMap().get(SensorTypes.PDR);
            float[] gnssValues = sensorFusion.getGNSSLatitude(false);

            LatLng PDRFilter = convertMetersToLatLng(pdrValues, PDRPOS);
            LatLng GNSSFilter = new LatLng(gnssValues[0], gnssValues[1]);

            positionX.setText(getString(R.string.x, String.format("%.1f", pdrValues[0])));
            positionY.setText(getString(R.string.y, String.format("%.1f", pdrValues[1])));
            // Calculate distance travelled
            distance += Math.sqrt(Math.pow(pdrValues[0] - previousPosX, 2) + Math.pow(pdrValues[1] - previousPosY, 2));
            distanceTravelled.setText(getString(R.string.meter, String.format("%.2f", distance)));
            floorOverlayManager.checkAndUpdateFloorOverlay();
            updatePDRPosition();
            fetchLocation();

//            if (isOutdoor) {
//                updateParticleFilterPositions(PDRFilter, GNSSFilter, GNSSFilter);
//            }
//            else {
//                updateParticleFilterPositions(GNSSFilter, PDRFilter, GNSSFilter);
//            }

            updateParticleFilterPositions(GNSSFilter, PDRFilter, GNSSFilter);
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
            //updatePDRMarker(pdrLatLng);
            updatePDRLocations(pdrLatLng);
            updatePDRPath(pdrLatLng); // If you also want to draw the path

            floorOverlayManager.isUserNearGroundFloor = ((pdrLatLng.latitude >= FloorOverlayManager.southwestcornerNucleus.latitude && pdrLatLng.latitude <= FloorOverlayManager.northeastcornerNucleus.latitude)
                    && (pdrLatLng.longitude >= FloorOverlayManager.southwestcornerNucleus.longitude && pdrLatLng.longitude <= FloorOverlayManager.northeastcornerNucleus.longitude));
            floorOverlayManager.isuserNearGroundFloorLibrary = ((pdrLatLng.latitude >= FloorOverlayManager.southwestcornerLibrary.latitude && pdrLatLng.latitude <= FloorOverlayManager.northeastcornerLibrary.latitude)
                    && (pdrLatLng.longitude >= FloorOverlayManager.southwestcornerLibrary.longitude && pdrLatLng.longitude <= FloorOverlayManager.northeastcornerLibrary.longitude));
            floorOverlayManager.checkAndUpdateFloorOverlay();
        }
    }
    /**
    Method to send the json fingerprint to the server and receive the latlong resposne
    Also checks if the wifi list is empty and notifies the user that there is no wifi coverage @author Michalis Voudaskas
     */

    private void fetchLocation() {
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                String wifiFingerprintJson = wifiFPManager.createWifiFingerprintJson();
                LocationResponse locationResponse = serverCommunications.sendWifiFingerprintToServer(wifiFingerprintJson);

                getActivity().runOnUiThread(() -> {
                    Log.d("RecordingFragment", "Received Wi-Fi location.");

                    LatLng wifiLocation = new LatLng(locationResponse.getLatitude(), locationResponse.getLongitude());

                    // Simple no coverage detection based on invalid LatLng
                    if (sensorFusion.getWifiList() == null) {
                        Log.e("RecordingFragment", "No coverage: Invalid Wi-Fi location.");
                        Toast.makeText(getContext(), "No Wi-Fi coverage detected", Toast.LENGTH_LONG).show();
                        return; // Exit early
                    }

//                    // Outlier detection: only add marker if within a reasonable distance from previous locations
//                    if (isOutlier(wifiLocation)) {
//                        Log.e("RecordingFragment", "Detected outlier Wi-Fi location.");
//                        //Toast.makeText(getContext(), "Outlier detected, location not updated", Toast.LENGTH_LONG).show();
//                    } else {
//                        // Not an outlier, update the map
//                        if (wifiMarker == null) {
//                            wifiMarker = mMap.addMarker(new MarkerOptions()
//                                    .position(wifiLocation)
//                                    .icon(BitmapDescriptorFactory.fromBitmap(getBitmapFromVector(getContext(), R.drawable.ic_baseline_add_location_24)))
//                                    .visible(true)
//                            );
//                        } else {
//                            wifiMarker.setPosition(wifiLocation);
//                        }
//                    }

                    // Update the list of recent locations
                    updateWifiLocations(wifiLocation);
                    updateLocationMarkers();
                });
            } catch (Exception e) {
                Log.e("RecordingFragment", "Exception while fetching location: " + e.getMessage(), e);
            }
        });
    }
    // check if the most recent wifi is an outlier by comparing with the average of the last 5 wifi positions
    private boolean isOutlier(LatLng newLocation) {
        //consider a location an outlier if it's too far from the average of recent locations
        if (recentWifiLocations.isEmpty()) {
            return false; // No history to compare against
        }

        LatLng averageLocation = getAverageLocation(recentWifiLocations);
        double distanceToAverage = calculateDistanceBetween(averageLocation, newLocation);
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
    private double calculateDistanceBetween(LatLng pos1, LatLng pos2) {
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

//    private void updatePDRMarker(LatLng position) {
//        if (mMap != null) {
//            float bearing = sensorFusion.passOrientation(); // Replace with actual method to get bearing
//            if (pdrMarker == null) {
//                pdrMarker = mMap.addMarker(new MarkerOptions()
//                        .position(position)
//                        .title("PDR Position")
//                        .rotation(bearing)
//                        .icon(BitmapDescriptorFactory.fromBitmap(getBitmapFromVector(getContext(), R.drawable.ic_baseline_navigation_24)))
//                        .anchor(0.5f, 0.5f)); // Ensure the marker rotates around its center
//            } else {
//                pdrMarker.setRotation((float) +Math.toDegrees(sensorFusion.passOrientation()));
//                pdrMarker.setPosition(position);
//            }
//        }
//    }

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

    private void updatePDRPath(LatLng newPosition) {
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

    //Particle Filter BS - Batu
    public void filterSetup(float[] startloc) {
        if (startloc != null) {
            double latitude = startloc[0];
            double longitude = startloc[1];
            // Create a new LatLng object for the GNSS location
            LatLng filterstartloc = new LatLng(latitude, longitude);
            particleFilter = new ParticleFilter(50, filterstartloc);
        }
    }

//    private void applyParticleFilter(){
//        float[] gnssFilter = sensorFusion.getGNSSLatitude(false);
//        float[] pdrFilter = sensorFusion.getSensorValueMap().get(SensorTypes.PDR);
//
//        LatLng PDRFilter = convertMetersToLatLng(pdrFilter, PDRPOS);
//        LatLng GNSSFilter = new LatLng(gnssFilter[0], gnssFilter[1]);
//
//        updateParticleFilterPositions(GNSSFilter, PDRFilter, GNSSFilter);
//    }

    private void updateParticleFilterPositions(LatLng predictPos, LatLng UpdatePos1, LatLng UpdatePos2) {
        if (particleFilter != null) {
            // Assuming a small measurement noise for demonstration. Adjust based on actual data quality.
            float measurementNoise = 5.0f; // Meters, adjust as needed

            // Update particle filter with GNSS, PDR, and WiFi positions
            // You can adjust the measurement noise for each type of update depending on their reliability
            particleFilter.updateFilter(predictPos, UpdatePos1, UpdatePos2, measurementNoise);

            // Optionally, get and use the fused position
            LatLng fusedPosition = particleFilter.getFusedPosition();
            updateMapWithFusedPosition(fusedPosition);
        }
    }
    private void updateMapWithFusedPosition(LatLng position) {
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
        boolean[] checkedItems = {areGnssMarkersVisible, areWifiMarkersVisible, arePDRMarkersVisible};

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Toggle Marker Visibility")
                .setMultiChoiceItems(new CharSequence[]{"GNSS Markers", "Wi-Fi Markers", "PDR Markers"}, checkedItems, (dialog, which, isChecked) -> {
                    if (which == 0) { // GNSS Markers
                        areGnssMarkersVisible = isChecked;
                    } else if (which == 1) { // Wi-Fi Markers
                        areWifiMarkersVisible = isChecked;
                    } else if (which ==2) {//pdr MARKERS
                        arePDRMarkersVisible = isChecked;
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
}