package scr;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Locale;

public class SimpleDriver extends Controller {

	private KNNClassifier classifier;
	/* Costanti di cambio marcia */
	private long lastGearChange = 0;
	private static final long MIN_GEAR_INTERVAL_MS = 1500; // 1,5 secondo tra i cambi di marcia
	final int[] gearUp = { 5000, 6000, 6000, 6500, 7000, 0 };
	final int[] gearDown = { 0, 2500, 3000, 3000, 3500, 3500 };

	/* Constanti */
	final int stuckTime = 25;
	final float stuckAngle = (float) 0.523598775; // PI/6

	/* Costanti di accelerazione e di frenata */
	final float maxSpeedDist = 70;
	final float maxSpeed = 150;
	final float sin5 = (float) 0.08716;
	final float cos5 = (float) 0.99619;

	/* Costanti di sterzata */
	final float steerLock = (float) 0.785398;
	final float steerSensitivityOffset = (float) 80.0;
	final float wheelSensitivityCoeff = 1;

	/* Costanti del filtro ABS */
	final float wheelRadius[] = { (float) 0.3179, (float) 0.3179, (float) 0.3276, (float) 0.3276 };
	final float absSlip = (float) 2.0;
	final float absRange = (float) 3.0;
	final float absMinSpeed = (float) 3.0;

	/* Costanti da stringere */
	final float clutchMax = (float) 0.5;
	final float clutchDelta = (float) 0.05;
	final float clutchRange = (float) 0.82;
	final float clutchDeltaTime = (float) 0.02;
	final float clutchDeltaRaced = 10;
	final float clutchDec = (float) 0.01;
	final float clutchMaxModifier = (float) 1.3;
	final float clutchMaxTime = (float) 1.5;

	private int stuck = 0;

	// current clutch
	private float clutch = 0;

	public SimpleDriver() {
		classifier = new KNNClassifier("dataset_50_destra_sinistra_centro.csv", 5);
	}

	public void reset() {
		System.out.println("Restarting the race!");

	}

	public void shutdown() {
		System.out.println("Bye bye!");
	}

	private int getGear(SensorModel sensors) {
		long now = System.currentTimeMillis();
		int currentGear = sensors.getGear();
		double rpm = sensors.getRPM();

		if (currentGear < 1)
			return 1;

		if (now - lastGearChange < MIN_GEAR_INTERVAL_MS)
			return currentGear;

		if (currentGear < 6 && rpm >= gearUp[currentGear - 1]) {
			lastGearChange = now;
			return currentGear + 1;
		}

		if (currentGear > 1 && rpm <= gearDown[currentGear - 1]) {
			lastGearChange = now;
			return currentGear - 1;
		}

		return currentGear;
	}

	private float getSteer(SensorModel sensors) {
		/**
		 * L'angolo di sterzata viene calcolato correggendo l'angolo effettivo della
		 * vettura
		 * rispetto all'asse della pista [sensors.getAngle()] e regolando la posizione
		 * della vettura
		 * rispetto al centro della pista [sensors.getTrackPos()*0,5].
		 */
		float targetAngle = (float) (sensors.getAngleToTrackAxis() - sensors.getTrackPosition() * 0.5);
		// ad alta velocità ridurre il comando di sterzata per evitare di perdere il
		// controllo
		if (sensors.getSpeed() > steerSensitivityOffset)
			return (float) (targetAngle
					/ (steerLock * (sensors.getSpeed() - steerSensitivityOffset) * wheelSensitivityCoeff));
		else
			return (targetAngle) / steerLock;
	}

	private float getAccel(SensorModel sensors) {
		// controlla se l'auto è fuori dalla carreggiata
		if (sensors.getTrackPosition() > -1 && sensors.getTrackPosition() < 1) {
			// lettura del sensore a +5 gradi rispetto all'asse dell'automobile
			float rxSensor = (float) sensors.getTrackEdgeSensors()[10];
			// lettura del sensore parallelo all'asse della vettura
			float sensorsensor = (float) sensors.getTrackEdgeSensors()[9];
			// lettura del sensore a -5 gradi rispetto all'asse dell'automobile
			float sxSensor = (float) sensors.getTrackEdgeSensors()[8];

			float targetSpeed;

			// Se la pista è rettilinea e abbastanza lontana da una curva, quindi va alla
			// massima velocità
			if (sensorsensor > maxSpeedDist || (sensorsensor >= rxSensor && sensorsensor >= sxSensor))
				targetSpeed = maxSpeed;
			else {
				// In prossimità di una curva a destra
				if (rxSensor > sxSensor) {

					// Calcolo dell'"angolo" di sterzata
					float h = sensorsensor * sin5;
					float b = rxSensor - sensorsensor * cos5;
					float sinAngle = b * b / (h * h + b * b);

					// Set della velocità in base alla curva
					targetSpeed = maxSpeed * (sensorsensor * sinAngle / maxSpeedDist);
				}
				// In prossimità di una curva a sinistra
				else {
					// Calcolo dell'"angolo" di sterzata
					float h = sensorsensor * sin5;
					float b = sxSensor - sensorsensor * cos5;
					float sinAngle = b * b / (h * h + b * b);

					// eSet della velocità in base alla curva
					targetSpeed = maxSpeed * (sensorsensor * sinAngle / maxSpeedDist);
				}
			}

			/**
			 * Il comando di accelerazione/frenata viene scalato in modo esponenziale
			 * rispetto
			 * alla differenza tra velocità target e quella attuale
			 */
			return (float) (2 / (1 + Math.exp(sensors.getSpeed() - targetSpeed)) - 1);
		} else
			// Quando si esce dalla carreggiata restituisce un comando di accelerazione
			// moderata
			return (float) 0.3;
	}

	// Nuova funzione per riprendersi
	public Action recover(SensorModel sensors) {
		float steer = (float) (-sensors.getAngleToTrackAxis() / steerLock);
		int gear = -1;

		// se siamo abbastanza allineati e centrati, proviamo ad avanzare invece di
		// retrocedere
		if (Math.abs(sensors.getAngleToTrackAxis()) < 0.1 && Math.abs(sensors.getTrackPosition()) < 0.5) {
			gear = 1;
			steer = 0;
		}

		clutch = clutching(sensors, clutch);
		Action recovery = new Action();
		recovery.gear = gear;
		recovery.steering = steer;
		recovery.accelerate = 1.0f;
		recovery.brake = 0f;
		recovery.clutch = clutch;

		System.out.println("Recovery mode: gear=" + gear + ", steer=" + steer);
		return recovery;
	}

	public Action control(SensorModel sensors) {
		// Gestione recupero in caso di auto bloccata
		
		  if (Math.abs(sensors.getAngleToTrackAxis()) > stuckAngle) {
		  stuck++;
		  } else {
		  stuck = 0;
		  }
		 
		/*if ((Math.abs(sensors.getAngleToTrackAxis()) > stuckAngle && sensors.getSpeed() < 5)
				|| sensors.getSpeed() < 1.5) {
			stuck++;
		} else {
			stuck = 0;
		}*/

		if (stuck > stuckTime) {
			// Recovery logic: retromarcia e sterzo per uscire dalla situazione di
			// difficoltà
			float steer = (float) (-sensors.getAngleToTrackAxis() / steerLock);
			int gear = -1;
			if (sensors.getAngleToTrackAxis() * sensors.getTrackPosition() > 0) {
				gear = 1;
				steer = -steer;
			}
			clutch = clutching(sensors, clutch);
			Action action = new Action();
			action.gear = gear;
			action.steering = steer;
			action.accelerate = 1.0f;
			action.brake = 0f;
			action.clutch = clutch;
			return action;
		}

		double[] features = new double[11]; // basato su CSV manual driver (24 features)
		double[] trackSensors = sensors.getTrackEdgeSensors();

		// Indici scelti coerenti con manual driver (6 sensori + trackPos + angle + rpm
		// + speed + speedY)
		// features[0] = trackSensors[0];
		// features[1] = trackSensors[1];
		features[0] = trackSensors[2];
		// features[3] = trackSensors[3];
		// features[2] = trackSensors[4];
		features[1] = trackSensors[5];
		// features[3] = trackSensors[6];
		// features[7] = trackSensors[7];
		features[2] = trackSensors[8];
		features[3] = trackSensors[9];
		features[4] = trackSensors[10];
		// features[11] = trackSensors[11];
		// features[7] = trackSensors[12];
		features[5] = trackSensors[13];
		// features[8] = trackSensors[14];
		// features[15] = trackSensors[15];
		features[6] = trackSensors[16];
		// features[17] = trackSensors[17];
		// features[10] = trackSensors[18];
		features[7] = sensors.getTrackPosition();
		features[8] = sensors.getAngleToTrackAxis();
		// features[9] = sensors.getRPM();
		features[9] = sensors.getSpeed();
		features[10] = sensors.getLateralSpeed();

		Sample currentSample = new Sample(features, new double[4]);

		// Ottieni la predizione dal KNN (accel, brake, steering)
		double[] prediction = classifier.predict(currentSample);

		// Costruisci l'azione
		Action action = new Action();
		action.accelerate = (float) prediction[0];
		action.brake = (float) prediction[1];
		action.steering = (float) prediction[2];
		action.gear = getGear(sensors);
		action.clutch = clutching(sensors, clutch);

		// Euristiche
		// applyHeuristics(sensors, action, (float) prediction[2]);

		return action;
	}

	private void applyHeuristics(SensorModel sensors, Action action, float predictedSteering) {
		boolean isCurving = Math.abs(getCurveRatio(sensors)) > 0.2
				|| Math.abs(action.steering) > 0.3
				|| Math.abs(sensors.getAngleToTrackAxis()) > 0.2;

		if (isCurving && sensors.getSpeed() > 90) {
			float curveRatio = Math.abs(getCurveRatio(sensors));
			action.brake = Math.max(action.brake, Math.min(1.0f, curveRatio * 1.5f));
		}

		if (isCurving && Math.abs(action.steering) > 0.3 && sensors.getSpeed() > 80) {
			action.accelerate *= 0.6f;
		}

	}

	private float getCurveRatio(SensorModel sensors) {
		double[] t = sensors.getTrackEdgeSensors();
		float left = (float) (t[5] + t[7]);
		float right = (float) (t[11] + t[13]);
		float center = (float) (t[9] + 1e-5); // prevenzione divisione per 0
		return (right - left) / center; // positivo = curva a destra, negativo = a sinistra
	}

	private float filterABS(SensorModel sensors, float brake) {
		// Converte la velocità in m/s
		float speed = (float) (sensors.getSpeed() / 3.6);

		// Quando la velocità è inferiore alla velocità minima per l'abs non interviene
		// in caso di frenata
		if (speed < absMinSpeed)
			return brake;

		// Calcola la velocità delle ruote in m/s
		float slip = 0.0f;
		for (int i = 0; i < 4; i++) {
			slip += sensors.getWheelSpinVelocity()[i] * wheelRadius[i];
		}

		// Lo slittamento è la differenza tra la velocità effettiva dell'auto e la
		// velocità media delle ruote
		slip = speed - slip / 4.0f;

		// Quando lo slittamento è troppo elevato, si applica l'ABS
		if (slip > absSlip) {
			brake = brake - (slip - absSlip) / absRange;
		}

		// Controlla che il freno non sia negativo, altrimenti lo imposta a zero
		if (brake < 0)
			return 0;
		else
			return brake;
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

	public float[] initAngles() {

		float[] angles = new float[19];

		/*
		 * set angles as
		 * {-90,-75,-60,-45,-30,-20,-15,-10,-5,0,5,10,15,20,30,45,60,75,90}
		 */
		for (int i = 0; i < 5; i++) {
			angles[i] = -90 + i * 15;
			angles[18 - i] = 90 - i * 15;
		}

		for (int i = 5; i < 9; i++) {
			angles[i] = -20 + (i - 5) * 5;
			angles[18 - i] = 20 - (i - 5) * 5;
		}
		angles[9] = 0;
		return angles;
	}
}
