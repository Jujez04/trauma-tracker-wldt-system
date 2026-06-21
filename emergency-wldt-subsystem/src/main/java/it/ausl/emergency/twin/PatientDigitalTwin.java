package it.ausl.emergency.twin;

import it.ausl.emergency.adapter.digital.PatientDigitalAdapter;
import it.ausl.emergency.adapter.physical.PatientPhysicalAdapter;
import it.ausl.emergency.shadowing.PatientShadowingFunction;
import it.wldt.core.engine.DigitalTwin;
import it.wldt.exception.EventBusException;
import it.wldt.exception.ModelException;
import it.wldt.exception.WldtConfigurationException;
import it.wldt.exception.WldtDigitalTwinStateException;
import it.wldt.exception.WldtRuntimeException;
import it.wldt.exception.WldtWorkerException;

public class PatientDigitalTwin extends DigitalTwin {


    final private String id;
    final private PatientPhysicalAdapter physicalAdapter;
    final private PatientDigitalAdapter digitalAdapter;
    final private PatientShadowingFunction shadowingFunction;

    public PatientDigitalTwin(String digitalTwinId, PatientShadowingFunction shadowingFunction) throws ModelException,
    EventBusException, WldtRuntimeException, WldtWorkerException, WldtDigitalTwinStateException {
        super(digitalTwinId, shadowingFunction);
        this.id = digitalTwinId;
        this.physicalAdapter = new PatientPhysicalAdapter(id);
        this.digitalAdapter = new PatientDigitalAdapter(id);
        this.shadowingFunction = shadowingFunction;
        try {
            this.addPhysicalAdapter(physicalAdapter);
            this.addDigitalAdapter(digitalAdapter);
        } catch (WldtConfigurationException e) {
            e.printStackTrace();
        } catch (WldtWorkerException e) {
            e.printStackTrace();
        }
    }


    public PatientPhysicalAdapter getPhysicalAdapter() {
        return physicalAdapter;
    }


    public PatientDigitalAdapter getDigitalAdapter() {
        return digitalAdapter;
    }


    public PatientShadowingFunction getShadowingFunction() {
        return shadowingFunction;
    }




}
