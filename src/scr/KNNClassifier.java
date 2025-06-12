package scr;

import java.io.*;
import java.util.*;

public class KNNClassifier {
    private List<Sample> trainingData;
    private KDTree kdtree;
    private int k;
    private Normalizer normalizer;
    private static final String LOG_FILE = "log_predizioni.csv";

    // Flag per sapere se ho scritto intestazione su log
    private boolean logHeaderWritten = false;

    public KNNClassifier(String filename, int k) {
        this.trainingData = new ArrayList<>();
        this.k = k;

        List<Sample> rawSamples = readRawSamples(filename);

        if (rawSamples.isEmpty()) {
            throw new RuntimeException("Dataset vuoto!");
        }
        this.normalizer = new Normalizer();
        normalizer.computeMinMax(rawSamples);
        normalizer.normalizeSamples(rawSamples);

        this.trainingData = rawSamples;
        this.kdtree = new KDTree(trainingData);

    }

    // Costruttore che accetta direttamente i dati di addestramento
    // già normalizzati e le etichette
    // (utilizzato per testare il classificatore con dati già pronti)
    public KNNClassifier(List<Sample> trainingData, int k) {
        this.k = k;
        this.normalizer = new Normalizer();
        this.normalizer.computeMinMax(trainingData);
        this.normalizer.normalizeSamples(trainingData);

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

        double[] normalized = normalizer.normalizeFeatures(testPoint.features.clone());
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

        double[] denormResult = normalizer.denormalizeTargets(result);

        // Logga sia normalizzati che denormalizzati
        logPrediction(allFeatures, normalized, result, denormResult);

        return denormResult;

    }

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
}