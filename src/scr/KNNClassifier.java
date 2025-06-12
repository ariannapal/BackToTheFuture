package scr;

import java.io.*;
import java.util.*;

public class KNNClassifier {
    private List<Sample> trainingData;
    private KDTree kdtree;
    private int k;
    private static final String LOG_FILE = "log_predizioni.csv";

    // Flag per sapere se ho scritto intestazione su log
    private boolean logHeaderWritten = false;

    // Definizione delle feature
    public static final String[] featureNames = {
            "Track2", "Track5", "Track8", "Track9", "Track10", "Track13", "Track16",
            "TrackPosition", "AngleToTrackAxis", "Speed", "SpeedY"
    };

    // Valori massimi per le features (ad esempio 200 per i sensori)
    private static final double[] featureMaxValues = {
            200, 200, 200, 200, 200, 200, 200, 1, Math.PI, 300, 100
    };
    // Minimi e massimi per i target
    private double[] targetMins;
    private double[] targetMaxs;

    public KNNClassifier(String filename, int k) {
        this.trainingData = new ArrayList<>();
        this.k = k;

        List<Sample> rawSamples = readRawSamples(filename);

        if (rawSamples.isEmpty()) {
            throw new RuntimeException("Dataset vuoto!");
        }

        computeTargetMinMax(rawSamples);
        normalizeSamples(rawSamples);

        this.trainingData = rawSamples;
        this.kdtree = new KDTree(trainingData);

    }

    // Costruttore che accetta direttamente i dati di addestramento
    // già normalizzati e le etichette
    // (utilizzato per testare il classificatore con dati già pronti)
    public KNNClassifier(List<Sample> trainingData, int k) {
        this.k = k;

        computeTargetMinMax(trainingData);
        normalizeSamples(trainingData);

        this.trainingData = new ArrayList<>(trainingData);
        this.kdtree = new KDTree(this.trainingData);
    }

    private List<Sample> readRawSamples(String filename) {
        List<Sample> rawSamples = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (first) {
                    first = false; // salta intestazione
                    continue;
                }
                rawSamples.add(new Sample(line));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return rawSamples;
    }

    private List<Sample> findKNearest(Sample testPoint) {
        return kdtree.kNearestNeighbors(testPoint, k);
    }

    public double[] predict(Sample testPoint) {
        double[] allFeatures = testPoint.features;

        double[] normalized = normalizeFeatures(testPoint.features.clone());
        testPoint.features = normalized;

        List<Sample> neighbors = findKNearest(testPoint);

        double[] result = new double[4]; // accelerazione, frenata, sterzata, marcia

        // Media semplice dei target per i primi 3
        for (Sample s : neighbors) {
            for (int i = 0; i < (result.length - 1); i++) {
                result[i] += s.targets[i];
            }
        }

        for (int i = 0; i < (result.length - 1); i++) {
            result[i] /= neighbors.size();
        }

        // per gear, prendo il valore medio (mediana)
        List<Integer> gears = new ArrayList<>();
        for (Sample s : neighbors) { // per ogni sample vicino
            gears.add((int) s.targets[s.targets.length - 1]); // estraggo il gear come valore intero
        }
        gears.sort(null); // ordino i gear
        // Prendo il valore mediano
        // Se il numero di gear è dispari, prendo il valore centrale
        // Se è pari, prendo il valore più vicino al centro
        int medianIndex = (gears.size() - 1) / 2;
        result[result.length - 1] = gears.get(medianIndex); // attribuisco il valore mediano al risultato

        double[] denormResult = denormalizeTargets(result);

        // Logga sia normalizzati che denormalizzati
        logPrediction(allFeatures, normalized, result, denormResult);

        return denormResult;

    }

    // debrah dnsiajifd
    private synchronized void logPrediction(double[] originalFeatures, double[] normalizedFeatures,
            double[] prediction, double[] denormalized) {

        try (PrintWriter writer = new PrintWriter(new FileWriter(LOG_FILE, true))) {
            if (!logHeaderWritten) {
                StringBuilder header = new StringBuilder();
                for (int i = 0; i < originalFeatures.length; i++) {
                    header.append("orig_feat_").append(i).append(",");
                }
                for (int i = 0; i < normalizedFeatures.length; i++) {
                    header.append("norm_feat_").append(i).append(",");
                }
                for (int i = 0; i < prediction.length; i++) {
                    header.append("pred_norm_").append(i).append(",");
                }
                for (int i = 0; i < denormalized.length; i++) {
                    header.append("pred_real_").append(i);
                    if (i < denormalized.length - 1)
                        header.append(",");
                }
                writer.println(header.toString());
                logHeaderWritten = true;
            }

            StringBuilder line = new StringBuilder();
            for (double f : originalFeatures)
                line.append(f).append(",");
            for (double f : normalizedFeatures)
                line.append(f).append(",");
            for (double f : prediction)
                line.append(f).append(",");
            for (int i = 0; i < denormalized.length; i++) {
                line.append(denormalized[i]);
                if (i < denormalized.length - 1)
                    line.append(",");
            }
            writer.println(line.toString());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Calcola minimi e massimi per i target
    private void computeTargetMinMax(List<Sample> samples) {
        int numTargets = samples.get(0).targets.length;

        targetMins = new double[numTargets];
        targetMaxs = new double[numTargets];

        Arrays.fill(targetMins, Double.POSITIVE_INFINITY);
        Arrays.fill(targetMaxs, Double.NEGATIVE_INFINITY);

        for (Sample s : samples) {
            for (int i = 0; i < numTargets; i++) {
                double val = s.targets[i];
                if (val < targetMins[i])
                    targetMins[i] = val;
                if (val > targetMaxs[i])
                    targetMaxs[i] = val;
            }
        }
    }

    // Normalizza un campione (features e targets)
    // Normalizza tutte le features e i target di ogni campione
    public void normalizeSamples(List<Sample> samples) {
        for (Sample s : samples) {
            s.features = normalizeFeatures(s.features); // Normalizza le features
            s.targets = normalizeTargets(s.targets); // Normalizza i target
        }
    }

    // Normalizza le features (feature / xmax)
    public double[] normalizeFeatures(double[] features) {
        if (features.length != featureNames.length) {
            throw new IllegalArgumentException("Mismatch: features.length = " + features.length
                    + " ma featureNames.length = " + featureNames.length);
        }

        double[] normalized = new double[features.length];
        for (int i = 0; i < features.length; i++) {
            // Usa il massimo definito per ogni feature
            normalized[i] = features[i] / featureMaxValues[i]; // Normalizzazione feature / xmax
        }
        return normalized;
    }

    // Normalizza i target
    public double[] normalizeTargets(double[] targets) {
        double[] normalized = new double[targets.length];
        for (int i = 0; i < targets.length; i++) {
            if (targetMaxs[i] == targetMins[i]) {
                normalized[i] = 0; // Se min == max, il target è costante
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

}