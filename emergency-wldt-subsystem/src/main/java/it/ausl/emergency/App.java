package it.ausl.emergency;

import it.ausl.emergency.adapter.physical.patient.PatientMqttIngestionAdapter;
import it.ausl.emergency.adapter.physical.VehicleMqttIngestionAdapter;
import it.ausl.emergency.adapter.physical.HospitalMqttIngestionAdapter;
import it.ausl.emergency.manager.PatientTwinManager;
import it.ausl.emergency.manager.VehicleTwinManager;
import it.ausl.emergency.manager.HospitalTwinManager;
import it.wldt.core.engine.DigitalTwinEngine;

public class App {

    // Cambiato il default a localhost per allinearsi al log della simulazione AnyLogic
    private static final String BROKER_URL = System.getenv().getOrDefault("MQTT_BROKER_URL", "tcp://localhost:1883");

    public static void main(String[] args) {

        // 1. Inizializzazione dell'Engine Core di WLDT
        DigitalTwinEngine engine = new DigitalTwinEngine();

        // 2. Setup Componenti Gestione Pazienti (Creazione dinamica on-demand)
        PatientTwinManager patientManager = new PatientTwinManager(engine);
        PatientMqttIngestionAdapter patientIngestionAdapter =
                new PatientMqttIngestionAdapter(BROKER_URL, patientManager);

        // 3. Setup Componenti Gestione Veicoli (Registrazione al boot e telemetria flotta)
        VehicleTwinManager vehicleManager = new VehicleTwinManager(engine);
        VehicleMqttIngestionAdapter vehicleIngestionAdapter =
                new VehicleMqttIngestionAdapter(BROKER_URL, vehicleManager);

        // 4. Setup Componenti Gestione Ospedali (Registrazione boot flotta ospedaliera e telemetria di stato)
        HospitalTwinManager hospitalManager = new HospitalTwinManager(engine);
        HospitalMqttIngestionAdapter hospitalIngestionAdapter =
                new HospitalMqttIngestionAdapter(BROKER_URL, hospitalManager);

        // 5. Avvio coordinato dei moduli di Ingestion
        try {
            // Avvio Ingestion Pazienti
            patientIngestionAdapter.start();
            System.out.println("[Main] Ingestion MQTT Pazienti avviata. In ascolto su: ces/patient/+/state");

            // Avvio Ingestion Veicoli (Registry + Telemetrie di stato)
            vehicleIngestionAdapter.start();
            System.out.println("[Main] Ingestion MQTT Veicoli avviata. In ascolto su: ces/registry e ces/+/+/state");

            // Avvio Ingestion Ospedali (Registry + Telemetrie di stato sanificate)
            hospitalIngestionAdapter.start();
            System.out.println("[Main] Ingestion MQTT Ospedali avviata. In ascolto su: ces/registry/hospital e ces/hospital/+/state");

        } catch (Exception e) {
            System.err.println("[Main] Errore critico in fase di avvio delle ingestion MQTT: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        // 6. Shutdown Hook: garantisce il rilascio controllato dei socket MQTT e dei thread WLDT
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[Main] Rilevato segnale di arresto. Shutdown in corso...");
            
            try {
                patientIngestionAdapter.stop();
                System.out.println("[Main] Ingestion Pazienti interrotta.");
            } catch (Exception e) {
                System.err.println("[Main] Errore durante lo stop dell'ingestion pazienti: " + e.getMessage());
            }

            try {
                vehicleIngestionAdapter.stop();
                System.out.println("[Main] Ingestion Veicoli interrotta.");
            } catch (Exception e) {
                System.err.println("[Main] Errore durante lo stop dell'ingestion veicoli: " + e.getMessage());
            }

            try {
                hospitalIngestionAdapter.stop();
                System.out.println("[Main] Ingestion Ospedali interrotta.");
            } catch (Exception e) {
                System.err.println("[Main] Errore durante lo stop dell'ingestion ospedali: " + e.getMessage());
            }

            try {
                engine.stopAll();
                System.out.println("[Main] Engine WLDT arrestato correttamente.");
            } catch (Exception e) {
                System.err.println("[Main] Errore durante l'arresto dell'engine WLDT: " + e.getMessage());
            }
            
            System.out.println("[Main] Processo di Shutdown completato.");
        }, "app-shutdown-hook"));

        // Mantiene vivo il processo principale finché non viene ricevuto un segnale di terminazione (es. CTRL+C)
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            System.out.println("[Main] Thread principale interrotto.");
            Thread.currentThread().interrupt();
        }
    }
}