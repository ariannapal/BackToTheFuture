package scr;

public class Sample {
    public double[] features; // Input dai sensori
    public double[] targets; // Output: accelerate, brake, steering

    // Costruttore da CSV: assume che le ultime 3 colonne siano gli output
    public Sample(String line) {
        String[] parts = line.split(",");
        int n = parts.length;

        features = new double[n - 4]; // Tutti tranne gli ultimi 3
        targets = new double[4]; // Ultimi 3: accelerate, brake, steering

        for (int i = 0; i < features.length; i++) {
            features[i] = Double.parseDouble(parts[i].trim());
        }

        targets[0] = Double.parseDouble(parts[n - 4].trim()); // accelerate
        targets[1] = Double.parseDouble(parts[n - 3].trim()); // brake
        targets[2] = Double.parseDouble(parts[n - 2].trim()); // steering
        targets[3] = Double.parseDouble(parts[n - 1].trim()); // gear

    }

    // Costruttore da array di features e array di target. Serve quando si
    // costruisce il dataset
    public Sample(double[] features, double[] targets) {
        this.features = features;
        this.targets = targets;
    }

    // costruttore che prende in ingresso solo le feature, usato quando ricevo gli
    // input
    public Sample(double[] features) {
        this.features = features;
        this.targets = new double[4];
    }

    //Calcolo distanza euclidea tra le features di due sample
    
      public double distance(Sample other) {
      double sum = 0;
      for (int i = 0; i < this.features.length; i++) {
     sum += Math.pow(this.features[i] - other.features[i], 2);
      }
      return Math.sqrt(sum);
      }
     /*
    public double distance(Sample other) {
        double sum = 0;

        // Pesi coerenti con le 10 feature
        double[] weights = new double[] {
                0.3, // TrackSensor[5]
                0.3, // TrackSensor[7]
                0.4, // TrackSensor[9] (centrale)
                0.3, // TrackSensor[11]
                0.3, // TrackSensor[13]
                1.0, // TrackPosition
                1.5, // AngleToTrackAxis → fondamentale!
                0.5, // RPM
                0.5, // Speed
                0.7 // LateralSpeed → utile per capire la deriva
        };

        for (int i = 0; i < features.length; i++) {
            double diff = this.features[i] - other.features[i];
            sum += weights[i] * diff * diff;
        }

        return Math.sqrt(sum);
    }*/

}