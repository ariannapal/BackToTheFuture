package scr;

import java.io.*;
import java.util.*;

public class Normalizer {
    // Inizializza i minimi e massimi per le feature e i targets
    private double[] featureMins;
    private double[] featureMaxs;
    private double[] targetMins;
    private double[] targetMaxs;

    // Nome delle feature
    private static final String[] featureNames = {
            "Track0", "Track1", "Track2", "Track3", "Track4", "Track5", "Track6", "Track7",
            "Track8", "Track9", "Track10", "Track11", "Track12", "Track13", "Track14", "Track15",
            "Track16", "Track17", "Track18", "TrackPosition", "AngleToTrackAxis", "RPM", "Speed",
            "SpeedY", "DistanceFromStartLine", "DistanceRaced", "Damage"
    };

    // prendo il nome della feature e il valore associato e
    // Funzione di normalizzazione con valori fissi
    public double normalizeFeature(String featureName, double value) {
        switch (featureName) {
            case "Track0":
            case "Track1":
            case "Track2":
            case "Track3":
            case "Track4":
            case "Track5":
            case "Track6":
            case "Track7":
            case "Track8":
            case "Track9":
            case "Track10":
            case "Track11":
            case "Track12":
            case "Track13":
            case "Track14":
            case "Track15":
            case "Track16":
            case "Track17":
            case "Track18":
                return value / 200.0; // Normalizza [0, 200] → [0, 1]
            case "angleToTrackAxis":
                return (value + Math.PI) / (2 * Math.PI); // Normalizza [-π, π] → [0, 1]
            case "distRaced":
                return value % 5000 / 5000.0; // Normalizza [0, 5000] → [0, 1]
            case "rpm":
                return value / 10000.0; // Normalizza [0, 10000] → [0, 1]
            case "speed":
                return value / 300.0; // Normalizza [0, 300] → [0, 1]
            case "lateralSpeed":
                return (value + 100.0) / 200.0; // Normalizza [-100, 100] → [0, 1]
            case "trackPosition":
                return (value + 1.0) / 2.0; // Normalizza [-1, 1] → [0, 1]
            default:
                return value; // Se non è una feature che normalizziamo, ritorna il valore originale
        }
    }

    // Calcola minimi e massimi per le feature e i target
    public void computeMinMax(List<Sample> samples) {
        int numFeatures = samples.get(0).features.length;
        int numTargets = samples.get(0).targets.length;

        featureMins = new double[numFeatures];
        featureMaxs = new double[numFeatures];
        targetMins = new double[numTargets];
        targetMaxs = new double[numTargets];

        Arrays.fill(featureMins, Double.POSITIVE_INFINITY);
        Arrays.fill(featureMaxs, Double.NEGATIVE_INFINITY);
        Arrays.fill(targetMins, Double.POSITIVE_INFINITY);
        Arrays.fill(targetMaxs, Double.NEGATIVE_INFINITY);

        for (Sample s : samples) {
            for (int i = 0; i < numFeatures; i++) {
                double val = s.features[i];
                if (isValid(i, val)) {
                    if (val < featureMins[i])
                        featureMins[i] = val;
                    if (val > featureMaxs[i])
                        featureMaxs[i] = val;
                }
            }

            for (int i = 0; i < numTargets; i++) {
                double val = s.targets[i];
                if (val < targetMins[i])
                    targetMins[i] = val;
                if (val > targetMaxs[i])
                    targetMaxs[i] = val;
            }
        }
        logTargetMinMaxToFile("log_normalizzazione.txt");
    }

    // Normalizza i campioni di un dataset
    public void normalizeSamples(List<Sample> samples) {
        for (Sample s : samples) {
            s.features = normalizeFeatures(s.features);
            s.targets = normalizeTargets(s.targets);
        }
    }

    // Normalizza un singolo campione per le feature
    public double[] normalizeFeatures(double[] features) {
        double[] normalized = new double[features.length];
        for (int i = 0; i < features.length; i++) {
            // Usando il nome della feature per la normalizzazione
            normalized[i] = normalizeFeature(featureNames[i], features[i]);
        }
        return normalized;
    }

    // Normalizza i target con valori fissi
    public double[] normalizeTargets(double[] targets) {
        double[] normalized = new double[targets.length];
        for (int i = 0; i < targets.length; i++) {
            if (targetMaxs[i] == targetMins[i]) {
                normalized[i] = 0; // Gestione dei target costanti
            } else {
                normalized[i] = (targets[i] - targetMins[i]) / (targetMaxs[i] - targetMins[i]);
            }
        }
        return normalized;
    }

    // Denormalizza i target
    public double[] denormalizeTargets(double[] normalized) {
        double[] denormalized = new double[normalized.length];
        for (int i = 0; i < normalized.length; i++) {
            denormalized[i] = normalized[i] * (targetMaxs[i] - targetMins[i]) + targetMins[i];
        }
        return denormalized;
    }

    // Verifica se il valore è valido, controllando i sensori e i target
    private boolean isValid(int index, double value) {
        // Track sensors (0–18): validi se non sono NaN, Inf o -1.0
        if (index >= 0 && index <= 18) {
            return value != -1.0 && !Double.isNaN(value) && !Double.isInfinite(value);
        }

        // TrackPosition: atteso tra -1 e 1
        if (index == 19) {
            return value >= -1.0 && value <= 1.0;
        }

        // AngleToTrackAxis: atteso tra -π e π
        if (index == 20) {
            return value >= -Math.PI && value <= Math.PI;
        }

        // RPM: da 0 a 20000 (massimo ipotetico)
        if (index == 21) {
            return value >= 0 && value <= 20000;
        }

        // Speed (longitudinale): da -150 a +150 m/s (~540 km/h)
        if (index == 22) {
            return value >= -150 && value <= 150;
        }

        // SpeedY (laterale): da -100 a +100 m/s
        if (index == 23) {
            return value >= -100 && value <= 100;
        }

        // DistanceFromStartLine: sempre >= 0
        if (index == 24) {
            return value >= 0 && value <= 10000; // Limita la distanza a 10000m
        }

        // DistanceRaced: sempre >= 0
        if (index == 25) {
            return value >= 0 && value <= 10000; // Limita la distanza a 10000m
        }

        // Damage: sempre >= 0
        if (index == 26) {
            return value >= 0 && value <= 100; // Danno massimo ragionevole
        }

        // Targets (Accelerate, Brake, Steering, Gear): validi sempre qui
        return true;
    }

    // Log dei minimi e massimi dei target in un file
    private void logTargetMinMaxToFile(String filename) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename, true))) {
            writer.println("=== Target Min/Max ===");
            writer.println("Min: " + Arrays.toString(targetMins));
            writer.println("Max: " + Arrays.toString(targetMaxs));
            writer.println(); // riga vuota
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
