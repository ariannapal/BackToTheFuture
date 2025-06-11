package scr;

import java.io.*;
import java.util.*;

public class KNNClassifier {
    private List<Sample> trainingData;
    private KDTree kdtree;
    private int k;

    public KNNClassifier(String filename, int k) {
        this.trainingData = new ArrayList<>();
        this.k = k;
        readPointsFromCSV(filename);
        kdtree = new KDTree(trainingData);
    }

    private void readPointsFromCSV(String filename) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (first) {
                    first = false; // salta intestazione
                    continue;
                }
                trainingData.add(new Sample(line));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Trova i k vicini più simili usando KDTree
    private List<Sample> findKNearest(Sample testPoint) {
        return kdtree.kNearestNeighbors(testPoint, k);
    }

    // Regressione KNN → restituisce [accelerate, brake, steering]
    public double[] predict(Sample testPoint) {
        List<Sample> neighbors = findKNearest(testPoint);
        double[] result = new double[3]; // accelerate, brake, steering

        for (Sample s : neighbors) {
            result[0] += s.targets[0];
            result[1] += s.targets[1];
            result[2] += s.targets[2];
        }

        for (int i = 0; i < result.length; i++) {
            result[i] /= k;
        }

        return result;
    }
}
