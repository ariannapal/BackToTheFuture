package scr;

import java.io.*;
import java.util.*;

public class KNNTester {

    private static final String DATASET_PATH = "dataset.csv";
    private static final String OUTPUT_LOG = "predizioni_test.csv";

    public static void main(String[] args) {
        int k = 21;
        double testRatio = 0.2;

        // Caricamento e preparazione del dataset
        List<Sample> allSamples = readSamples(DATASET_PATH);
        Collections.shuffle(allSamples, new Random(42));

        // Divisione in set di addestramento e test
        int testSize = (int) (allSamples.size() * testRatio);
        List<Sample> testSet = allSamples.subList(0, testSize);
        List<Sample> trainSet = allSamples.subList(testSize, allSamples.size());

        // Normalizzazione dei campioni
        KNNClassifier classifier = new KNNClassifier(trainSet, k);

        // Test del classificatore
        System.out.println("Inizio test con K = " + k + " su " + testSet.size() + " campioni...");
        System.out.println("Dataset di addestramento: " + trainSet.size() + " campioni");
        System.out.println("Dataset di test: " + testSet.size() + " campioni");
        System.out.println("Scrittura log su " + OUTPUT_LOG);
        System.out.println("--------------------------------------------------");
        // Variabili per calcolo dell'errore
        // quadratico medio e accuratezza discreta
        // delle 3 azioni
        // (accelerazione, frenata, sterzata)
        // (valori discreti: 0, 1, 2)
        // 0 = nessuna azione, 1 = azione bassa, 2 = azione alta
        double totalMSE = 0;
        int correctDiscrete = 0;
        int total = 0;

        // Scrittura dei risultati su file
        // di log
        // (predizioni e valori reali)
        // Intestazione:
        // pred_accel,pred_brake,pred_steer,true_accel,true_brake,true_steer
        try (PrintWriter writer = new PrintWriter(new FileWriter(OUTPUT_LOG))) {
            // intestazione
            writer.println("pred_accel,pred_brake,pred_steer,true_accel,true_brake,true_steer");

            for (Sample s : testSet) {
                Sample test = new Sample(s.features.clone(), s.targets.clone());
                double[] prediction = classifier.predict(test);

                totalMSE += meanSquaredError(prediction, s.targets);
                if (matchDiscrete(prediction, s.targets))
                    correctDiscrete++;

                // log
                writer.printf(Locale.US, "%.4f,%.4f,%.4f,%.4f,%.4f,%.4f\n",
                        prediction[0], prediction[1], prediction[2],
                        s.targets[0], s.targets[1], s.targets[2]);

                total++;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Calcolo dell'errore quadratico medio
        // e dell'accuratezza discreta
        double avgMSE = totalMSE / total;
        double discreteAccuracy = 100.0 * correctDiscrete / total;

        // Stampa dei risultati
        System.out.println("Test completato!");
        System.out.println("--------------------------------------------------");
        System.out.printf("Errore quadratico medio: %.6f\n", avgMSE);
        System.out.printf("Accuratezza discreta sulle 3 azioni: %.2f%% (%d/%d)\n",
                discreteAccuracy, correctDiscrete, total);
    }

    // Legge i campioni dal file CSV
    // Ignora la prima riga (intestazione)
    // e restituisce una lista di oggetti Sample
    // Ogni Sample contiene le feature e i target
    // (accelerazione, frenata, sterzata)
    // Le feature sono normalizzate tra 0 e 1
    // I target sono i valori reali delle azioni
    private static List<Sample> readSamples(String filename) {
        List<Sample> list = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            boolean skipHeader = true;
            while ((line = reader.readLine()) != null) {
                if (skipHeader) {
                    skipHeader = false;
                    continue;
                }
                list.add(new Sample(line));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return list;
    }

    // Calcola l'errore quadratico medio tra
    // le previsioni e i valori reali
    // delle azioni (accelerazione, frenata, sterzata)
    private static double meanSquaredError(double[] pred, double[] actual) {
        double sum = 0;
        for (int i = 0; i < pred.length; i++) {
            double diff = pred[i] - actual[i];
            sum += diff * diff;
        }
        return sum / pred.length;
    }

    // discretizzazione (soglie empiriche)
    // delle azioni (accelerazione, frenata, sterzata)
    // per il confronto tra le previsioni e i valori reali
    // Restituisce true se le azioni previste
    // corrispondono a quelle reali
    // (considerando le soglie discrete)
    private static boolean matchDiscrete(double[] pred, double[] actual) {
        return discretize(pred[0]) == discretize(actual[0]) &&
                discretize(pred[1]) == discretize(actual[1]) &&
                discretize(pred[2]) == discretize(actual[2]);
    }

    // Discretizza un valore tra 0 e 1 in 3 categorie:
    // 0 = nessuna azione (0.0 - 0.1),
    // 1 = azione bassa (0.1 - 0.6),
    // 2 = azione alta (0.6 - 1.0)
    private static int discretize(double val) {
        if (val < 0.1)
            return 0;
        if (val < 0.6)
            return 1;
        return 2;
    }
}
