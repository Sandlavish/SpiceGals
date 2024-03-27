package com.openpositioning.PositionMe.fragments;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;
import androidx.preference.PreferenceManager;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.GroundOverlay;
import com.google.android.gms.maps.model.GroundOverlayOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.openpositioning.PositionMe.PdrProcessing;
import com.openpositioning.PositionMe.R;
import com.openpositioning.PositionMe.sensors.SensorFusion;
import com.openpositioning.PositionMe.sensors.SensorTypes;

import java.util.List;

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

    private Polyline userTrajectory;

    LatLng southwestcornerNucleus;
    LatLng northeastcornerNucleus;

    private PdrProcessing pdrProcessing;

    LatLng southwestcornerLibrary;
    LatLng northeastcornerLibrary;

    private Polyline pdrPolyline;
    private float lastBearing = 0; // Store the last bearing to smooth transitions
    private GroundOverlay groundflooroverlay;
    private GroundOverlay firstflooroverlay;
    private GroundOverlay secondflooroverlay;
    private GroundOverlay thirdflooroverlay;
    private GroundOverlay librarygroundflooroverlay;
    private GroundOverlay libraryfirstflooroverlay;
    private GroundOverlay librarysecondflooroverlay;
    private GroundOverlay librarythirdflooroverlay;

    private final float GROUND_FLOOR_MAX_ELEVATION = 4.2f;
    private final float FIRST_FLOOR_MIN_ELEVATION = 4.2f;
    private final float FIRST_FLOOR_MAX_ELEVATION = 8.6f;
    private final float SECOND_FLOOR_MIN_ELEVATION = 8.6f;
    private final float SECOND_FLOOR_MAX_ELEVATION = 12.8f;
    private final float THIRD_FLOOR_MIN_ELEVATION = 12.8f;
    private final float THIRD_FLOOR_MAX_ELEVATION = 17f;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor magnetometer;
    float[] accelerometerReading = new float[3];
    float[] magnetometerReading = new float[3];
    float[] rotationMatrix = new float[9];
    float[] orientationAngles = new float[3];
    //Button to end PDR recording
    private Button stopButton;
    private Button cancelButton;
    //Recording icon to show user recording is in progress
    private ImageView recIcon;

    private LatLngBounds buildingBounds; //building bounds for the Nucleus

    private LatLngBounds buildingBoundsLibrary; //building bounds for the Library

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

    private double net_change;

    private boolean userIsOnFirstFloor = false; // Default to ground floor
    private boolean userIsOnGroundFloor = false; //Ground floor is only visible when we are near the building
    private boolean userIsOnSecondFloor = false; // Default to ground floor
    private boolean userIsOnThirdFloor = false; // Default to ground floor

    private Marker pdrMarker;
    private float previousPosX;
    private float previousPosY;

    private GoogleMap mMap;

    private Marker userLocationMarker;

    private FusedLocationProviderClient fusedLocationProviderClient;
    private LocationCallback locationCallback;

    private LatLng PDRPOS;

    boolean isUserNearGroundFloor;

    boolean isuserNearGroundFloorLibrary;

    boolean isUserNeartestingBounds;
    private static final float Q_METRES_PER_SECOND = 0.1f; // Adjust this value based on your needs

    private KalmanLatLong kalmanFilter;
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
        private double updateStepLength() {
            // Update and return the new step length
            return 0.75; // Example fixed value; replace with actual computation
        }

        private double updateHeading() {
            // Update and return the new heading
            return 45; // Example fixed value; replace with actual computation
        }
        // Method to update PDR position based on the current position, step length, and heading

        public long get_TimeStamp() {
            return TimeStamp_milliseconds;
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
        return rootView;
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

        // Set the map type from the global settings
        //mMap.setMapType(StartLocationFragment.type);

        int savedMapType = GlobalVariables.getMapType(); // Assuming you have a getter method in GlobalVariables
        mMap.setMapType(GlobalVariables.getMapType());
        mMap.getUiSettings().setCompassEnabled(true);
        mMap.getUiSettings().setTiltGesturesEnabled(true);
        mMap.getUiSettings().setRotateGesturesEnabled(true);
        mMap.getUiSettings().setScrollGesturesEnabled(true);

        setupMapComponents();
        setupGroundOverlays();

    }
    private void setupGroundOverlays() {
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

    private GroundOverlay addOverlay(int resourceId, LatLngBounds bounds) {
        return mMap.addGroundOverlay(new GroundOverlayOptions()
                .image(BitmapDescriptorFactory.fromResource(resourceId))
                .positionFromBounds(bounds)
                .transparency(0.5f)); // Adjust transparency as needed
    }

    private void updateFloorOverlay() {
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


    private void setupMapComponents() {
        userTrajectory = mMap.addPolyline(new PolylineOptions().width(7).color(Color.RED));
        pdrPolyline = mMap.addPolyline(new PolylineOptions().width(9).color(Color.GREEN));

        configureLocationUpdates();
    }

    private void configureLocationUpdates() {
        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocationRequest locationRequest = LocationRequest.create();
            locationRequest.setInterval(5000);
            locationRequest.setFastestInterval(2500);
            locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
            FusedLocationProviderClient fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(getContext());
            locationCallback = new LocationCallback() {
                @Override
                public void onLocationResult(LocationResult locationResult) {
                    handleLocationUpdates(locationResult);
                }
            };
            fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        }
    }

    private void handleLocationUpdates(LocationResult locationResult) {
        if (locationResult == null) {
            return;
        }
        List<LatLng> points = userTrajectory.getPoints();
        for (Location location : locationResult.getLocations()) {
            // Add a marker to show the GNSS position
            LatLng gnssLatLng = new LatLng(kalmanFilter.get_lat(), kalmanFilter.get_lng());

            if (kalmanFilter.get_accuracy() < 0) {
                kalmanFilter.SetState(location.getLatitude(), location.getLongitude(), location.getAccuracy(), location.getTime());
            } else {
                kalmanFilter.Process(location.getLatitude(), location.getLongitude(), location.getAccuracy(), location.getTime(), Q_METRES_PER_SECOND);
            }
            //Use the filtered coordinates
            LatLng newLocation = new LatLng(kalmanFilter.get_lat(), kalmanFilter.get_lng());


            if (userLocationMarker == null) {
                userLocationMarker = mMap.addMarker(new MarkerOptions()
                        .position(newLocation)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
            } else {
                userLocationMarker.setPosition(newLocation);
            }

            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(newLocation, 19));
            points.add(newLocation); // Use the already declared 'points' variable

            // Check if the new location is within the bounds of either building
            isUserNearGroundFloor = buildingBounds.contains(newLocation);
            isuserNearGroundFloorLibrary = buildingBoundsLibrary.contains(newLocation);
        }
        userTrajectory.setPoints(points);
    }

    private Bitmap getBitmapFromVector(Context context, int vectorResId) {
        Drawable vectorDrawable = ContextCompat.getDrawable(context, vectorResId);
        Bitmap bitmap = Bitmap.createBitmap(vectorDrawable.getIntrinsicWidth(), vectorDrawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        vectorDrawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        vectorDrawable.draw(canvas);
        return bitmap;
    }

    private enum Floor {
        GROUND, FIRST, SECOND, THIRD
    }

    private void selectFloor(Floor floor) {
        // Reset all flags
        userIsOnGroundFloor = false;
        userIsOnFirstFloor = false;
        userIsOnSecondFloor = false;
        userIsOnThirdFloor = false;

        // Set the flag for the selected floor to true
        switch (floor) {
            case GROUND:
                userIsOnGroundFloor = true;
                break;
            case FIRST:
                userIsOnFirstFloor = true;
                break;
            case SECOND:
                userIsOnSecondFloor = true;
                break;
            case THIRD:
                userIsOnThirdFloor = true;
                break;
        }

        // Update the floor overlay to reflect the current selection
        updateFloorOverlay();
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

        //isUserNearGroundFloor = true;
        //isuserNearGroundFloorLibrary = true;

        // Set visibility and enabled state for each button based on user proximity
        btnGroundFloor.setVisibility(isUserNearGroundFloor || isuserNearGroundFloorLibrary ? View.VISIBLE : View.GONE);
        btnFirstFloor.setVisibility(isUserNearGroundFloor || isuserNearGroundFloorLibrary ? View.VISIBLE : View.GONE);
        btnSecondFloor.setVisibility(isUserNearGroundFloor || isuserNearGroundFloorLibrary ? View.VISIBLE : View.GONE);
        btnThirdFloor.setVisibility(isUserNearGroundFloor || isuserNearGroundFloorLibrary ? View.VISIBLE : View.GONE);

        btnGroundFloor.setEnabled(isUserNearGroundFloor || isuserNearGroundFloorLibrary);
        btnFirstFloor.setEnabled(isUserNearGroundFloor || isuserNearGroundFloorLibrary);
        btnSecondFloor.setEnabled(isUserNearGroundFloor || isuserNearGroundFloorLibrary);
        btnThirdFloor.setEnabled(isUserNearGroundFloor || isuserNearGroundFloorLibrary);


        btnGroundFloor.setOnClickListener(v -> selectFloor(Floor.GROUND));
        btnFirstFloor.setOnClickListener(v -> selectFloor(Floor.FIRST));
        btnSecondFloor.setOnClickListener(v -> selectFloor(Floor.SECOND));
        btnThirdFloor.setOnClickListener(v -> selectFloor(Floor.THIRD));


        Spinner mapTypeSpinner = view.findViewById(R.id.mapTypeSpinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(getContext(),
                R.array.map_types, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mapTypeSpinner.setAdapter(adapter);

        int savedMapType = GlobalVariables.getMapType();

        // Determine the spinner position that corresponds to the saved map type
        int spinnerPosition = 0; // Default to 0 (assuming it's the 'Normal' map type)
        switch (savedMapType) {
            case GoogleMap.MAP_TYPE_SATELLITE:
                spinnerPosition = 1;
                break;
            case GoogleMap.MAP_TYPE_TERRAIN:
                spinnerPosition = 2;
                break;
            case GoogleMap.MAP_TYPE_HYBRID:
                spinnerPosition = 3;
                break;
            // Default case for MAP_TYPE_NORMAL is already set by initializing spinnerPosition to 0
        }
        mapTypeSpinner.setSelection(spinnerPosition, false); // The second argument 'false' ensures no callback is triggered

        mapTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (mMap != null) {
                    switch (position) {
                        case 0:
                            mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                            break;
                        case 1:
                            mMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
                            break;
                        case 2:
                            mMap.setMapType(GoogleMap.MAP_TYPE_TERRAIN);
                            break;
                        case 3:
                            mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
                            break;
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Can be left empty
            }
        });


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
                    compassIcon.setRotation((float) +Math.toDegrees(sensorFusion.passOrientation()));
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
            positionX.setText(getString(R.string.x, String.format("%.1f", pdrValues[0])));
            positionY.setText(getString(R.string.y, String.format("%.1f", pdrValues[1])));
            // Calculate distance travelled
            distance += Math.sqrt(Math.pow(pdrValues[0] - previousPosX, 2) + Math.pow(pdrValues[1] - previousPosY, 2));
            distanceTravelled.setText(getString(R.string.meter, String.format("%.2f", distance)));

            updatePDRPosition();

            previousPosX = pdrValues[0];
            previousPosY = pdrValues[1];
            // Display elevation and elevator icon when necessary
            elevationVal = sensorFusion.getElevation();

            updateFloorOverlay();

            elevation.setText(getString(R.string.elevation, String.format("%.1f", elevationVal)));
            if(sensorFusion.getElevator()) elevatorIcon.setVisibility(View.VISIBLE);
            else elevatorIcon.setVisibility(View.GONE);

            //Rotate compass image to heading angle
            compassIcon.setRotation((float) -Math.toDegrees(sensorFusion.passOrientation()));

            // Loop the task again to keep refreshing the data
            refreshDataHandler.postDelayed(refreshDataTask, 500);
        }
    };


    private void updatePDRPosition() {
        // Assuming getPDRCoordinates() returns the latest PDR coordinates as float[2] with x, y values
        float[] pdrCoordinates = sensorFusion.getSensorValueMap().get(SensorTypes.PDR);
        if (pdrCoordinates != null && PDRPOS != null) {
            LatLng pdrLatLng = convertMetersToLatLng(pdrCoordinates, PDRPOS);
            updatePDRMarker(pdrLatLng);
            updatePDRPath(pdrLatLng); // If you also want to draw the path

            isUserNearGroundFloor = ((pdrLatLng.latitude >= southwestcornerNucleus.latitude && pdrLatLng.latitude <= northeastcornerNucleus.latitude)
                    && (pdrLatLng.longitude >= southwestcornerNucleus.longitude && pdrLatLng.longitude <= northeastcornerNucleus.longitude));
            isuserNearGroundFloorLibrary = ((pdrLatLng.latitude >= southwestcornerLibrary.latitude && pdrLatLng.latitude <= northeastcornerLibrary.latitude)
                    && (pdrLatLng.longitude >= southwestcornerLibrary.longitude && pdrLatLng.longitude <= northeastcornerLibrary.longitude));
            updateFloorBasedOnElevation(elevationVal);
        }
    }

    private void updateFloorBasedOnElevation(float elevation) {
        // Only proceed if the user is near a relevant area
        if (!isUserNearGroundFloor && !isuserNearGroundFloorLibrary) return;

        // Determine the floor based on the elevation value
        if (elevation <= GROUND_FLOOR_MAX_ELEVATION) {
            selectFloor(Floor.GROUND);
        } else if (elevation >= FIRST_FLOOR_MIN_ELEVATION && elevation <= FIRST_FLOOR_MAX_ELEVATION) {
            selectFloor(Floor.FIRST);
        } else if (elevation >= SECOND_FLOOR_MIN_ELEVATION && elevation <= SECOND_FLOOR_MAX_ELEVATION) {
            selectFloor(Floor.SECOND);
        } else if (elevation >= THIRD_FLOOR_MIN_ELEVATION && elevation <= THIRD_FLOOR_MAX_ELEVATION) {
            selectFloor(Floor.THIRD);
        }
    }

    private void updatePDRMarker(LatLng position) {
        if (mMap != null) {
            if (pdrMarker == null) {
                // First time: create the marker
                pdrMarker = mMap.addMarker(new MarkerOptions().position(position).title("PDR Position")
                        .icon(BitmapDescriptorFactory.fromBitmap(getBitmapFromVector(getContext(), R.drawable.ic_baseline_navigation_24)))); // Customize as needed
            } else {
                // Subsequent times: just update the position
                pdrMarker.setPosition(position);
            }
        }
    }

    private LatLng convertMetersToLatLng(float[] pdrCoordinates, LatLng startLatLng) {
        // Constants
        final double metersInOneDegreeLatitude = 111111.0;

        // Calculate the change in degrees
        double deltaLat = pdrCoordinates[1] / metersInOneDegreeLatitude;
        double deltaLon = pdrCoordinates[0] / (metersInOneDegreeLatitude * Math.cos(Math.toRadians(startLatLng.latitude)));

        // Calculate the new position
        double newLat = startLatLng.latitude + deltaLat;
        double newLon = startLatLng.longitude + deltaLon;
        LatLng newloc = new LatLng(newLat, newLon);


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
        refreshDataHandler.removeCallbacks(refreshDataTask);
        super.onPause();
    }

    /**
     * {@inheritDoc}
     * Restarts UI refreshing task when no countdown task is in progress
     */
    @Override
    public void onResume() {
        if(!this.settings.getBoolean("split_trajectory", false)) {
            refreshDataHandler.postDelayed(refreshDataTask, 500);
        }
        super.onResume();
    }
}