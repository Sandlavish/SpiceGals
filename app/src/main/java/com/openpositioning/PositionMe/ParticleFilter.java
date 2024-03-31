package com.openpositioning.PositionMe;

import com.google.android.gms.maps.model.LatLng;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/*
 * it's normal and often expected for the estimated position produced by a particle filter to be very close to
 * , or even seemingly overlapping with, the position indicated by a predictive marker
 * (like those derived from PDR, GNSS, or another tracking system) under certain conditions.
 * This outcome can be considered a sign that the particle filter is performing well,
 * particularly if the predictive markers are accurate representations of the true position.
 * */

public class ParticleFilter {
    private List<Particle> particles;
    private int numberOfParticles;
    private Random random;
    private LatLng lastPDRUpdate; // Track the last PDR update for displacement calculation

    // Inner class to represent particle
    private class Particle {
        LatLng position; // Assuming a LatLng class exists to represent latitude and longitude
        double weight;

        Particle(LatLng position, double weight) {
            this.position = position;
            this.weight = weight;
        }
    }

    public ParticleFilter(int numberOfParticles, LatLng initialPosition) {
        this.numberOfParticles = numberOfParticles;
        this.particles = new ArrayList<>(numberOfParticles);
        this.random = new Random();
        this.lastPDRUpdate = initialPosition;
        initializeParticles(initialPosition);
    }

    private void initializeParticles(LatLng initialPosition) {
        double spreadRadius = 20; // Meters, adjust based on the scale of your environment
        for (int i = 0; i < numberOfParticles; i++) {
            double offsetLat = (random.nextDouble() - 0.5) * spreadRadius / 111111; // Convert meters to degrees latitude
            double offsetLng = (random.nextDouble() - 0.5) * spreadRadius / (111111 * Math.cos(Math.toRadians(initialPosition.latitude))); // Convert meters to degrees longitude
            particles.add(new Particle(new LatLng(initialPosition.latitude + offsetLat, initialPosition.longitude + offsetLng), 1.0 / numberOfParticles));
        }
    }

    // Update the filter based on new PDR, GNSS, and WiFi positions
    public void updateFilter(LatLng pdrPosition, LatLng gnssPosition, LatLng wifiPosition, double measurementNoise) {
        // Predict movement based on the most reliable
//        predict(pdrPosition);
        predict(gnssPosition);

        // Update based on the rest
//        update(gnssPosition, measurementNoise);
        update(pdrPosition,measurementNoise);
        update(wifiPosition, measurementNoise);

        // Resample particles to focus on more probable states
        resample();
    }

    private void predict(LatLng currentPosition) {
        // Calculate displacement since the last PDR update
        LatLng displacement = new LatLng(currentPosition.latitude - lastPDRUpdate.latitude,
                currentPosition.longitude - lastPDRUpdate.longitude);
        lastPDRUpdate = currentPosition; // Update last PDR position for the next prediction

        // Move each particle according to the displacement
        for (Particle particle : particles) {
            particle.position = new LatLng(particle.position.latitude + displacement.latitude,
                    particle.position.longitude + displacement.longitude);
        }
    }

    private void update(LatLng measurement, double measurementNoise) {
        // Update each particle's weight based on its distance to the measurement
        double totalWeight = 0.0;
        for (Particle particle : particles) {
            double distance = Math.sqrt(Math.pow(particle.position.latitude - measurement.latitude, 2) +
                    Math.pow(particle.position.longitude - measurement.longitude, 2));
            particle.weight = calculateLikelihood(distance, measurementNoise);
            totalWeight += particle.weight;
        }

        // Normalize the weights
        for (Particle particle : particles) {
            particle.weight /= totalWeight;
        }
    }

    // Resampling method remains the same as previously defined

    public LatLng getFusedPosition() {
        // Calculate the fused position as the weighted average of all particles
        double sumLat = 0.0;
        double sumLon = 0.0;
        double totalWeight = 0.0;

        for (Particle particle : particles) {
            sumLat += particle.position.latitude * particle.weight;
            sumLon += particle.position.longitude * particle.weight;
            totalWeight += particle.weight;
        }

        return new LatLng(sumLat / totalWeight, sumLon / totalWeight);
    }

    private double calculateLikelihood(double distance, double measurementNoise) {
        double variance = measurementNoise * measurementNoise;
        return Math.exp(-(distance * distance) / (2 * variance)) / Math.sqrt(2 * Math.PI * variance);
    }

    // Systematic resampling method
    public void resample() {
        List<Particle> newParticles = new ArrayList<>(numberOfParticles);
        double B = 0.0;
        double increment = 1.0 / numberOfParticles;
        double r = random.nextDouble() * increment;

        int index = 0;
        for (int i = 0; i < numberOfParticles; i++) {
            B += r + i * increment;
            while (B > particles.get(index).weight) {
                B -= particles.get(index).weight;
                index = (index + 1) % numberOfParticles;
            }
            newParticles.add(new Particle(particles.get(index).position, 1.0 / numberOfParticles));
        }
        particles = newParticles;
    }

}
