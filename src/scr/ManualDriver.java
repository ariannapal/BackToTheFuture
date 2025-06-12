package scr;

import javax.swing.*;
import java.awt.event.*;
import java.io.*;
import scr.Controller;

public class ManualDriver extends Controller {

    // Dichiarazione variabili di stato
    private boolean accel = false, brake = false, left = false, right = false;
    private boolean recording = false, manual = false, automatic = true;
    private int gear = 0;
    private float currentAccel = 0f, currentBrake = 0f, steering = 0f, clutch = 0f;
    private long lastSaveTime = 0;

    private static final long MIN_SAVE_INTERVAL_MS = 10;

    final float clutchMax = 0.5f;
    final float clutchDelta = 0.05f;
    final float clutchRange = 0.82f;
    final float clutchDeltaTime = 0.02f;
    final float clutchDeltaRaced = 10f;
    final float clutchDec = 0.01f;
    final float clutchMaxModifier = 1.3f;
    final float clutchMaxTime = 1.5f;

    /* Costanti di cambio marcia */
    final int[] gearUp = { 5000, 6000, 6000, 6500, 7000, 0 };
    final int[] gearDown = { 0, 2500, 3000, 3000, 3500, 3500 };

    public ManualDriver() {
        ContinuousCharReader reader = new ContinuousCharReader();
        reader.addCharListener(new ContinuousCharReader.CharListener() {
            @Override
            public void charChanged(char ch, boolean pressed) {
                switch (ch) {
                    case 'w' -> accel = pressed;
                    case 's' -> brake = pressed;
                    case 'a' -> left = pressed;
                    case 'd' -> right = pressed;

                    case 'e' -> {
                        if (pressed) {
                            automatic = true;
                            manual = false;
                            System.out.println("Modalità automatica attivata");
                        }
                    }

                    case 'r' -> {
                        if (pressed) {
                            manual = true;
                            automatic = false;
                            System.out.println("Modalità manuale attivata");
                        }
                    }

                    case '1' -> {
                        if (pressed) {
                            recording = true;
                            System.out.println("Scrittura attivata");
                        }
                    }

                    case '0' -> {
                        if (pressed) {
                            recording = false;
                            System.out.println("Scrittura disattivata");
                        }
                    }

                    default -> {
                        if (pressed) {
                            System.out.println("Comando non riconosciuto: " + ch);
                        }
                    }
                }
            }
        });
    } // <--- Qui chiudo il costruttore ManualDriver

    @Override
    public Action control(SensorModel sensors) {
        Action action = new Action();

        updateState(sensors);

        action.accelerate = currentAccel;
        action.brake = currentBrake;
        action.steering = steering;
        action.gear = getGear(sensors);
        action.clutch = clutching(sensors, clutch);

        double speed = sensors.getSpeed();
        double speedY = sensors.getLateralSpeed();
        double distanceFromStartLine = sensors.getDistanceFromStartLine(); 
        double distanceRaced = sensors.getDistanceRaced(); 
        double damage = sensors.getDamage(); 
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
                                    "Track0,Track1,Track2,Track3,Track4,Track5,Track6,Track7,Track8,Track9,Track10,Track11,Track12,Track13,Track14,Track15,Track16,Track17,Track18,TrackPosition,AngleToTrackAxis,RPM,Speed,SpeedY,DistanceFromStartLine,DistanceRaced,Damage,Accelerate,Brake,Steering,Gear\n");
                        }
                        double[] trackSensors = sensors.getTrackEdgeSensors();
                        bw.write(
                                trackSensors[0] + "," +
                                trackSensors[1] + "," +
                                trackSensors[2] + "," +
                                trackSensors[3] + "," +
                                trackSensors[4] + "," +
                                trackSensors[5] + "," +
                                trackSensors[6] + "," +
                                        trackSensors[7] + "," +
                                        trackSensors[8] + "," +
                                        trackSensors[9] + "," +
                                        trackSensors[10] + "," +
                                        trackSensors[11] + "," +
                                        trackSensors[12] + "," +
                                        trackSensors[13] + "," +
                                        trackSensors[14] + "," +
                                        trackSensors[15] + "," +
                                        trackSensors[16] + "," +
                                        trackSensors[17] + "," +
                                        trackSensors[18] + "," +
                                        sensors.getTrackPosition() + "," +
                                        sensors.getAngleToTrackAxis() + "," +
                                        sensors.getRPM() + "," +
                                        speed + "," +
                                        speedY + "," +
                                        distanceFromStartLine + ","+
                                        distanceRaced + "," +
                                        damage + "," +
                                        action.accelerate + "," +
                                        action.brake + "," +
                                        action.steering + "," +
                                        action.gear + "\n");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return action;
    }

    private float clutching(SensorModel sensors, float clutch) {
        float maxClutch = clutchMax;

        if (sensors.getCurrentLapTime() < clutchDeltaTime && getStage() == Stage.RACE
                && sensors.getDistanceRaced() < clutchDeltaRaced) {
            clutch = maxClutch;
        }

        if (clutch > 0) {
            double delta = clutchDelta;
            if (sensors.getGear() < 2) {
                delta /= 2;
                maxClutch *= clutchMaxModifier;
                if (sensors.getCurrentLapTime() < clutchMaxTime)
                    clutch = maxClutch;
            }

            clutch = Math.min(maxClutch, clutch);

            if (clutch != maxClutch) {
                clutch -= delta;
                clutch = Math.max(0f, clutch);
            } else {
                clutch -= clutchDec;
            }
        }
        return clutch;
    }

    private int getGear(SensorModel sensors) {
        int gear = sensors.getGear();
        double rpm = sensors.getRPM();

        if (manual)
            return -1;

        if (automatic) {
            if (gear < 1)
                return 1;
            if (gear < 6 && rpm >= gearUp[gear - 1])
                return gear + 1;
            if (gear > 1 && rpm <= gearDown[gear - 1])
                return gear - 1;
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

    private void updateState(SensorModel sensor) {
        // Gestione acceleratore
        if (accel && !brake) {
            currentAccel = 1f;
            currentBrake = 0f;
        } else if (brake) {
            currentAccel = 0f;
            currentBrake = 0.6f;
        } else {
            // né accel né brake premuti
            currentAccel = 0f;
            currentBrake = 0f;
        }

        // Gestione sterzo
        if (left) {
            steering = (sensor.getSpeed() < 40) ? 0.6f : 0.3f;
        } else if (right) {
            steering = (sensor.getSpeed() < 40) ? -0.6f : -0.3f;
        } else {
            steering = 0f;
        }

    }

}