package scr;

import javax.swing.*;
import java.awt.event.*;
import java.io.*;
import scr.Controller;

public class ManualDriver extends Controller {

    // Dichiarazione variabili di stato
    private boolean accel = false, brake = false, left = false, right = false, recording = false, manual = false,
            automatic = true;
    private int gear = 0;
    private float currentAccel = 0f, currentBrake = 0f, steering = 0f, clutch = 0f;
    private long lastSaveTime = 0;

    private static final long MIN_SAVE_INTERVAL_MS = 100;

    final float clutchMax = (float) 0.5;
    final float clutchDelta = (float) 0.05;
    final float clutchRange = (float) 0.82;
    final float clutchDeltaTime = (float) 0.02;
    final float clutchDeltaRaced = 10;
    final float clutchDec = (float) 0.01;
    final float clutchMaxModifier = (float) 1.3;
    final float clutchMaxTime = (float) 1.5;

    /* Costanti di cambio marcia */
    final int[] gearUp = { 5000, 6000, 6000, 6500, 7000, 0 };
    final int[] gearDown = { 0, 2500, 3000, 3000, 3500, 3500 };

    public ManualDriver() {
        JFrame frame = new JFrame("Manual Driver");
        frame.setSize(200, 100);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setAlwaysOnTop(true);
        frame.setUndecorated(true);
        frame.setOpacity(0f);
        frame.setFocusable(true);
        frame.setVisible(true);
        frame.requestFocus();

        frame.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_W -> accel = true;
                    case KeyEvent.VK_S -> brake = true;
                    case KeyEvent.VK_A -> left = true;
                    case KeyEvent.VK_D -> right = true;
                    case KeyEvent.VK_UP -> {
                        automatic = true;
                        manual = false;
                    }
                    case KeyEvent.VK_DOWN -> {
                        manual = true;
                        automatic = false;
                    }
                    /*
                     * case KeyEvent.VK_UP -> {
                     * if (gear < 6)
                     * gear++;
                     * }
                     * case KeyEvent.VK_DOWN -> {
                     * if (gear > -1)
                     * gear--;
                     * }
                     */

                    case KeyEvent.VK_1 -> {
                        recording = true;
                        System.out.println("Scrittura attivata");
                    }
                    case KeyEvent.VK_0 -> {
                        recording = false;
                        System.out.println("Scrittura disattivata");
                    }
                }
            }

            public void keyReleased(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_W -> accel = false;
                    case KeyEvent.VK_S -> brake = false;
                    case KeyEvent.VK_A -> left = false;
                    case KeyEvent.VK_D -> right = false;
                }
            }
        });
    }

    @Override
    public Action control(SensorModel sensors) {
        Action action = new Action();

        updateState();

        action.accelerate = currentAccel;
        action.brake = currentBrake;
        double speed = sensors.getSpeed();
        double speedY = sensors.getLateralSpeed();
        action.steering = steering;
        action.gear = getGear(sensors);
        double distance = sensors.getDistanceFromStartLine();

        action.clutch = clutching(sensors, clutch);

        // Scrivi nel CSV solo se recording è attivo
        if (recording) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastSaveTime >= MIN_SAVE_INTERVAL_MS) {
                lastSaveTime = currentTime;
                try {
                    File file = new File("dataset.csv");
                    boolean fileExists = file.exists();
                    boolean fileIsEmpty = file.length() == 0;


                    try (BufferedWriter bw = new BufferedWriter(new FileWriter(file, true))) {
                        if (!fileExists || fileIsEmpty) {
                            bw.write(
                                    "Track5, Track7, Track9, Track11, Track13,TrackPosition,AngleToTrackAxis, RPM,Speed, SpeedY,Accelerate,Brake,Steering,Gear \n");


                        }
                        double[] trackSensors = sensors.getTrackEdgeSensors();


                        bw.write(
                                trackSensors[5] + "," +
                                        trackSensors[7] + "," +
                                        trackSensors[9] + "," +
                                        trackSensors[11] + "," +
                                        trackSensors[13] + "," +
                                        sensors.getTrackPosition() + "," +
                                        sensors.getAngleToTrackAxis() + "," +
                                        sensors.getRPM() + "," +
                                        speed + "," +
                                        speedY + "," +
                                        action.accelerate + "," +
                                        action.brake + "," +
                                        action.steering + "," +
                                        action.gear + "\n"; 
                    }


                } catch (IOException e) {
                    e.printStackTrace();
                }
            }


        }
        return action;
    }

    float clutching(SensorModel sensors, float clutch) {

        float maxClutch = clutchMax;

        // Controlla se la situazione attuale è l'inizio della gara
        if (sensors.getCurrentLapTime() < clutchDeltaTime && getStage() == Stage.RACE
                && sensors.getDistanceRaced() < clutchDeltaRaced)
            clutch = maxClutch;

        // Regolare il valore attuale della frizione
        if (clutch > 0) {
            double delta = clutchDelta;
            if (sensors.getGear() < 2) {

                // Applicare un'uscita più forte della frizione quando la marcia è una e la
                // corsa è appena iniziata.
                delta /= 2;
                maxClutch *= clutchMaxModifier;
                if (sensors.getCurrentLapTime() < clutchMaxTime)
                    clutch = maxClutch;
            }

            // Controllare che la frizione non sia più grande dei valori massimi
            clutch = Math.min(maxClutch, clutch);

            // Se la frizione non è al massimo valore, diminuisce abbastanza rapidamente
            if (clutch != maxClutch) {
                clutch -= delta;
                clutch = Math.max((float) 0.0, clutch);
            }
            // Se la frizione è al valore massimo, diminuirla molto lentamente.
            else
                clutch -= clutchDec;
        }
        return clutch;
    }

    private int getGear(SensorModel sensors) {
        int gear = sensors.getGear();
        double rpm = sensors.getRPM();

        // Se la marcia è 0 (N) o -1 (R) restituisce semplicemente 1
        /*
         * if (gear < 1)
         * return 1;
         */

        if (manual)
            return -1;
        if (automatic) {
            if (gear < 1)
                return 1;
            // Se il valore di RPM dell'auto è maggiore di quello suggerito
            // sale di marcia rispetto a quella attuale
            if (gear < 6 && rpm >= gearUp[gear - 1])
                return gear + 1;
            else

            // Se il valore di RPM dell'auto è inferiore a quello suggerito
            // scala la marcia rispetto a quella attuale
            if (gear > 1 && rpm <= gearDown[gear - 1])
                return gear - 1;
            else // Altrimenti mantenere l'attuale
                return gear;
        }
        return 1;

    }

    @Override
    public void reset() {
        System.out.println("Reset!");
    }

    @Override
    public void shutdown() {
        System.out.println("Shutdown!");
    }

    @Override
    public float[] initAngles() {
        return super.initAngles();
    }

    // funzione aggiunta da Giuliano
    private void updateState() {
        // --- Accelerazione e frenata ---

        if (accel) {
            currentAccel += 0.2f;
            if (currentAccel > 1f)
                currentAccel = 1f;
        }

        if (brake) {
            currentAccel = Math.max(0f, currentAccel - 0.4f); // frenata = riduzione accelerazione
            currentBrake += 0.2f;
            if (currentBrake > 1f)
                currentBrake = 1f;
        } else {
            currentBrake -= 0.3f;
            if (currentBrake < 0f)
                currentBrake = 0f;
        }

        // Decelerazione naturale se né accell né freno
        if (!accel && !brake) {
            currentAccel = Math.max(0f, currentAccel - 0.2f);
        }

        // --- Sterzo ---
        if (left) {
            steering += 0.2f;
            if (steering > 1f)
                steering = 1f;
        } else if (right) {
            steering -= 0.2f;
            if (steering < -1f)
                steering = -1f;
        } else {
            if (steering > 0f) {
                steering -= 0.5f;
                if (steering < 0f)
                    steering = 0f;
            } else if (steering < 0f) {
                steering += 0.5f;
                if (steering > 0f)
                    steering = 0f;
            }
        }
    }
}
