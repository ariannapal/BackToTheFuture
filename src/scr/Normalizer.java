package scr;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

public class Normalizer {
    // Normalizza i campioni di un dataset
    private double[] featureMins; // Minimo per ogni feature
    private double[] featureMaxs; // Massimo per ogni feature
    private double[] targetMins; // Minimo per ogni target
    private double[] targetMaxs; // Massimo per ogni target

    public void computeMinMax(List<Sample> samples) {

        // numFeatures e numTargets sono il numero di feature e target
        int numFeatures = samples.get(0).features.length;
        int numTargets = samples.get(0).targets.length;

        // Inizializza i minimi e massimi per le feature e i targets
        featureMins = new double[numFeatures];
        featureMaxs = new double[numFeatures];
        targetMins = new double[numTargets];
        targetMaxs = new double[numTargets];

        // Imposta i minimi e massimi iniziali
        // a valori estremi per poter trovare i minimi e massimi reali
        Arrays.fill(featureMins, Double.POSITIVE_INFINITY);
        Arrays.fill(featureMaxs, Double.NEGATIVE_INFINITY);
        Arrays.fill(targetMins, Double.POSITIVE_INFINITY);
        Arrays.fill(targetMaxs, Double.NEGATIVE_INFINITY);

        // Calcola i minimi e massimi per ogni feature e target
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

            // Calcola i minimi e massimi per i targets
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

    // normalizza i campioni di un dataset
    public void normalizeSamples(List<Sample> samples) {
        for (Sample s : samples) {
            s.features = normalizeFeatures(s.features);
            s.targets = normalizeTargets(s.targets);
        }
    }

    // normalizza un singolo campione
    public double[] normalizeFeatures(double[] features) {
        double[] normalized = new double[features.length];
        for (int i = 0; i < features.length; i++) {
            if (featureMaxs[i] == featureMins[i]) {
                // in caso di due valori uguali, restituisce 0
                normalized[i] = 0;
            } else {
                normalized[i] = (features[i] - featureMins[i]) / (featureMaxs[i] - featureMins[i]);
            }
        }
        return normalized;
    }

    // stessa cosa per i targets
    public double[] normalizeTargets(double[] targets) {
        double[] normalized = new double[targets.length];
        for (int i = 0; i < targets.length; i++) {
            if (targetMaxs[i] == targetMins[i]) {
                normalized[i] = 0;
            } else {
                normalized[i] = (targets[i] - targetMins[i]) / (targetMaxs[i] - targetMins[i]);
            }
        }
        return normalized;
    }

    // Perché denormalizzare?
    // Per poter interpretare i risultati del modello, è necessario denormalizzare i
    // valori
    // previsti dal modello, in modo da riportarli ai valori originali del dataset.

    // denormalizza i campioni di un dataset
    public double[] denormalizeTargets(double[] normalized) {
        double[] denormalized = new double[normalized.length];
        for (int i = 0; i < normalized.length; i++) {
            // Calcola il valore denormalizzato usando i minimi e massimi dei targets
            // Normalizzazione: (val - min) / (max - min)
            // Denormalizzazione: val = normalized * (max - min) + min
            denormalized[i] = normalized[i] * (targetMaxs[i] - targetMins[i]) + targetMins[i];
        }
        return denormalized;
    }

    /*
     * [0–18] Track0–Track18 → Distanze dai bordi (track sensors)
     * [19] TrackPosition → Posizione rispetto al centro pista ([-1,1])
     * [20] AngleToTrackAxis → Angolo auto rispetto all’asse della pista ([-π,π])
     * [21] RPM → Giri motore (>= 0, tipicamente < 20k)
     * [22] Speed → Velocità longitudinale ([-150, 150])
     * [23] SpeedY → Velocità laterale ([-100, 100])
     * [24] DistanceFromStartLine → distanza (>= 0, molto alta in gara lunga)
     * [25] DistanceRaced → distanza cumulativa (>= 0)
     * [26] Damage → danno subito (>= 0)
     * [27–30] Accelerate, Brake, Steering, Gear → TARGET
     */

    private boolean isValid(int index, double value) {
        // Track sensors (0–18): validi se diversi da -1
        if (index >= 0 && index <= 18) {
            return value != -1.0;
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
            return value >= 0;
        }

        // DistanceRaced: sempre >= 0
        if (index == 25) {
            return value >= 0;
        }

        // Damage: sempre >= 0
        if (index == 26) {
            return value >= 0;
        }

        // Targets (Accelerate, Brake, Steering, Gear): validi sempre qui
        return true;
    }

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
