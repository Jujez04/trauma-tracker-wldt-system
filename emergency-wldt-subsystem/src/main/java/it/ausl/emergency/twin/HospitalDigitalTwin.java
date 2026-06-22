package it.ausl.emergency.twin;

import it.ausl.emergency.adapter.configuration.HospitalAdapterConfiguration;
import it.ausl.emergency.adapter.digital.HospitalDigitalAdapter;
import it.ausl.emergency.adapter.physical.HospitalPhysicalAdapter;
import it.ausl.emergency.shadowing.HospitalShadowingFunction;
import it.wldt.core.engine.DigitalTwin;
import it.wldt.exception.EventBusException;
import it.wldt.exception.ModelException;
import it.wldt.exception.WldtConfigurationException;
import it.wldt.exception.WldtDigitalTwinStateException;
import it.wldt.exception.WldtRuntimeException;
import it.wldt.exception.WldtWorkerException;

public class HospitalDigitalTwin extends DigitalTwin {

    private final String id;
    private final HospitalPhysicalAdapter physicalAdapter;
    private final HospitalDigitalAdapter digitalAdapter;
    private final HospitalShadowingFunction shadowingFunction;

    public HospitalDigitalTwin(String digitalTwinId, HospitalShadowingFunction shadowingFunction)
            throws ModelException, EventBusException, WldtRuntimeException,
            WldtWorkerException, WldtDigitalTwinStateException {

        super(digitalTwinId, shadowingFunction);
        this.id = digitalTwinId;
        this.shadowingFunction = shadowingFunction;
        
        // Configurazione condivisa passata alle istanze degli adapter interni
        HospitalAdapterConfiguration sharedConfig = new HospitalAdapterConfiguration();
        this.physicalAdapter = new HospitalPhysicalAdapter(id, sharedConfig);
        this.digitalAdapter = new HospitalDigitalAdapter(id, sharedConfig);

        try {
            this.addPhysicalAdapter(physicalAdapter);
            this.addDigitalAdapter(digitalAdapter);
        } catch (WldtConfigurationException | WldtWorkerException e) {
            System.err.println("[HospitalDigitalTwin] Errore durante la registrazione degli adapter strutturali: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public HospitalPhysicalAdapter getPhysicalAdapter() {
        return physicalAdapter;
    }

    public HospitalDigitalAdapter getDigitalAdapter() {
        return digitalAdapter;
    }

    public HospitalShadowingFunction getShadowingFunction() {
        return shadowingFunction;
    }
}