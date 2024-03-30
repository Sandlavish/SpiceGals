package com.openpositioning.PositionMe.fragments;

public class ExtendedKalmanFilter {
    private double[][] P; // State covariance matrix
    private double[][] Q; // Process noise covariance matrix
    private double[][] R; // Measurement noise covariance matrix
    private double[] x; // State estimate vector

    public ExtendedKalmanFilter(int stateSize, int measurementSize) {
        P = new double[stateSize][stateSize];
        Q = new double[stateSize][stateSize];
        R = new double[measurementSize][measurementSize];
        x = new double[stateSize];
    }

    // Initialize the EKF with initial state and covariance
    public void initialize(double[] initialState, double[][] initialCovariance) {
        System.arraycopy(initialState, 0, x, 0, initialState.length);
        for (int i = 0; i < P.length; i++) {
            System.arraycopy(initialCovariance[i], 0, P[i], 0, P[i].length);
        }
    }

    // Predict the next state and covariance
    public void predict(double[][] F, double[][] Q) {
        // x = f(x)
        x = f(x);

        // P = FPF' + Q
        double[][] FP = MatrixOperations.multiply(F, P);
        P = MatrixOperations.add(MatrixOperations.multiply(FP, MatrixOperations.transpose(F)), Q);
    }

    // Update the state estimate with a new measurement
    public void update(double[] z, double[][] H, double[][] R) {
        // y = z - h(x)
        double[] y = MatrixOperations.subtractVectors(z, h(x));

        // S = HPH' + R
        double[][] PHt = MatrixOperations.multiply(P, MatrixOperations.transpose(H));
        double[][] S = MatrixOperations.add(MatrixOperations.multiply(H, PHt), R);

        // K = PH'S^(-1)
        double[][] K = MatrixOperations.multiply(PHt, MatrixOperations.inverse(S));

        // x = x + Ky
        x = MatrixOperations.addVectors(x, MatrixOperations.multiplyMatrixAndVector(K, y));

        // P = (I - KH)P
        double[][] KH = MatrixOperations.multiply(K, H);
        double[][] I = MatrixOperations.identityMatrix(KH.length);
        double[][] I_KH = MatrixOperations.subtract(I, KH);
        P = MatrixOperations.multiply(I_KH, P);
    }

    // The state transition model function (needs to be defined for your system)
    private double[] f(double[] x) {
        // Time step in seconds
        double dt = 1; // Adjust this based on your update frequency

        // Current state
        double lat = x[0];
        double lon = x[1];
        double v_n = x[2]; // Velocity in the North direction
        double v_e = x[3]; // Velocity in the East direction

        // Constants for converting velocity to degrees
        double metersPerDegreeLat = 111320; // Approximate meters per degree of latitude (varies around the globe)
        double metersPerDegreeLon = Math.cos(Math.toRadians(lat)) * metersPerDegreeLat; // Meters per degree of longitude, depends on latitude

        // Update position based on velocity and time step
        double newLat = lat + (v_n / metersPerDegreeLat) * dt;
        double newLon = lon + (v_e / metersPerDegreeLon) * dt;

        // Assuming constant velocity for this simple model
        double newV_n = v_n;
        double newV_e = v_e;

        return new double[]{newLat, newLon, newV_n, newV_e};
    }

    // The observation model function (needs to be defined for your system)
    private double[] h(double[] x) {
        // Extract the position components from the state vector
        double observedLat = x[0]; // GNSS measures latitude directly
        double observedLon = x[1]; // GNSS measures longitude directly

        // The observation model returns the expected measurements given the current state
        // In this case, it's directly the latitude and longitude from the state vector
        return new double[]{observedLat, observedLon};
    }

    // Getter for the state estimate
    public double[] getStateEstimate() {
        return x;
    }

    // Getter for the state covariance matrix
    public double[][] getStateCovariance() {
        return P;
    }
}

