package scr;

import java.io.*;
import java.util.*;

public class KNNClassifier {
    private List<Sample> trainingData;
    private KDTree kdtree;
    private int k;

    private double[] featureMins;
    private double[] featureMaxs;

    private static final String MINMAX_FILE = "minmax.csv";
    private static final String LOG_FILE = "log_predizioni.csv";

    // Flag per sapere se ho scritto intestazione su log
    private boolean logHeaderWritten = false;

    private int[] selectedFeatureIndices;

    public KNNClassifier(String filename, int k, int[] selectedFeatureIndices) {
        this.trainingData = new ArrayList<>();
        this.k = k;
        this.selectedFeatureIndices = selectedFeatureIndices;

        List<Sample> rawSamples = readRawSamples(filename);

        if (rawSamples.isEmpty()) {
            throw new RuntimeException("Dataset vuoto!");
        }

        computeMinMax(rawSamples);
        saveMinMaxToCSV(MINMAX_FILE);
        normalizeSamples(rawSamples);

        this.trainingData = rawSamples;
        this.kdtree = new KDTree(trainingData);
    }

    // Costruttore che accetta direttamente i dati di addestramento
    // già normalizzati e le etichette
    // (utilizzato per testare il classificatore con dati già pronti)
    public KNNClassifier(List<Sample> trainingData, int k) {
        this.trainingData = new ArrayList<>(trainingData);
        this.k = k;

        computeMinMax(this.trainingData);
        normalizeSamples(this.trainingData);
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

    private void computeMinMax(List<Sample> samples) {
        int numFeatures = samples.get(0).features.length;
        featureMins = new double[numFeatures];
        featureMaxs = new double[numFeatures];
        Arrays.fill(featureMins, Double.POSITIVE_INFINITY);
        Arrays.fill(featureMaxs, Double.NEGATIVE_INFINITY);

        for (Sample s : samples) {
            for (int i = 0; i < numFeatures; i++) {
                if (s.features[i] < featureMins[i])
                    featureMins[i] = s.features[i];
                if (s.features[i] > featureMaxs[i])
                    featureMaxs[i] = s.features[i];
            }
        }
    }

    private void normalizeSamples(List<Sample> samples) {
        int numFeatures = featureMins.length;
        for (Sample s : samples) {
            for (int i = 0; i < numFeatures; i++) {
                if (featureMaxs[i] == featureMins[i]) {
                    s.features[i] = 0;
                } else {
                    s.features[i] = (s.features[i] - featureMins[i]) / (featureMaxs[i] - featureMins[i]);
                }
            }
        }
    }

    public double[] normalizeFeatures(double[] features) {
        double[] normalized = new double[features.length];
        for (int i = 0; i < features.length; i++) {
            if (featureMaxs[i] == featureMins[i]) {
                normalized[i] = 0;
            } else {
                normalized[i] = (features[i] - featureMins[i]) / (featureMaxs[i] - featureMins[i]);
            }
        }
        return normalized;
    }

    private void saveMinMaxToCSV(String filename) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            for (int i = 0; i < featureMins.length; i++) {
                writer.print(featureMins[i]);
                if (i < featureMins.length - 1)
                    writer.print(",");
            }
            writer.println();

            for (int i = 0; i < featureMaxs.length; i++) {
                writer.print(featureMaxs[i]);
                if (i < featureMaxs.length - 1)
                    writer.print(",");
            }
            writer.println();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private List<Sample> findKNearest(Sample testPoint) {
        return kdtree.kNearestNeighbors(testPoint, k);
    }

    public double[] predict(Sample testPoint) {
        double[] allFeatures = testPoint.features;
        double[] originalFeatures = testPoint.features.clone(); // copia dati originali
        double[] normalizedFeatures = normalizeFeatures(originalFeatures);
        testPoint.features = normalizedFeatures;

        List<Sample> neighbors = findKNearest(testPoint);
        double[] result = new double[3];
        // Aggiunta una media Pesata
        double totalWeight = 0.0;

        // Calcolo la media pesata delle etichette dei vicini
        // utilizzando la distanza come peso
        // per evitare divisione per zero, aggiungo un piccolo valore
        for (Sample s : neighbors) {
            double dist = testPoint.distance(s);
            double weight = 1.0 / (dist + 1e-6); // evita divisione per zero
            totalWeight += weight;

            for (int i = 0; i < 3; i++) {
                result[i] += weight * s.targets[i];
            }
        }

        for (int i = 0; i < result.length; i++) {
            result[i] /= totalWeight;
        }

        // Logga la predizione
        logPrediction(allFeatures, normalizedSelected, result);

        return result;
    }

    private synchronized void logPrediction(double[] originalFeatures, double[] normalizedFeatures,
            double[] prediction) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(LOG_FILE, true))) {
            // Scrivo intestazione solo la prima volta
            if (!logHeaderWritten) {
                StringBuilder header = new StringBuilder();
                for (int i = 0; i < originalFeatures.length; i++) {
                    header.append("orig_feat_").append(i).append(",");
                }
                for (int i = 0; i < normalizedFeatures.length; i++) {
                    header.append("norm_feat_").append(i).append(",");
                }
                for (int i = 0; i < prediction.length; i++) {
                    header.append("pred_").append(i);
                    if (i < prediction.length - 1)
                        header.append(",");
                }
                writer.println(header.toString());
                logHeaderWritten = true;
            }

            // Scrivo la riga con dati
            StringBuilder line = new StringBuilder();
            for (double f : originalFeatures) {
                line.append(f).append(",");
            }
            for (double f : normalizedFeatures) {
                line.append(f).append(",");
            }
            for (int i = 0; i < prediction.length; i++) {
                line.append(prediction[i]);
                if (i < prediction.length - 1)
                    line.append(",");
            }
            writer.println(line.toString());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
