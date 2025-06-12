package scr;

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
                if (val >= 0) { // ignora valori anomali come -1
                    if (val < featureMins[i])
                        featureMins[i] = val;
                    if (val > featureMaxs[i])
                        featureMaxs[i] = val;
                }
            }
            // Calcola i minimi e massimi per i targets
            // Ignora valori anomali come -1
            for (int i = 0; i < numTargets; i++) {
                double val = s.targets[i];
                if (val < targetMins[i])
                    targetMins[i] = val;
                if (val > targetMaxs[i])
                    targetMaxs[i] = val;
            }
        }
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
}
