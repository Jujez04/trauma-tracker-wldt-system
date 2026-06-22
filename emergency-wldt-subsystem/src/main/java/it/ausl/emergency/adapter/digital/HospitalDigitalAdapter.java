package it.ausl.emergency.adapter.digital;

import it.ausl.emergency.adapter.configuration.HospitalAdapterConfiguration;
import it.ausl.emergency.utils.HospitalKeywords;
import it.wldt.adapter.digital.DigitalAdapter;
import it.wldt.core.state.DigitalTwinState;
import it.wldt.core.state.DigitalTwinStateChange;
import it.wldt.core.state.DigitalTwinStateEventNotification;
import it.wldt.exception.WldtDigitalTwinStatePropertyException;

import java.util.ArrayList;

/**
 * Hospital Digital Adapter.
 * Consumes and exposes synchronized core digital twin states of the hospital
 * infrastructure to external consumers, monitoring layers or analytical services.
 */
public class HospitalDigitalAdapter extends DigitalAdapter<HospitalAdapterConfiguration> {

    private volatile int patientAssistedCount = 0;

    public HospitalDigitalAdapter(String id, HospitalAdapterConfiguration configuration) {
        super(id, configuration);
    }

    @Override
    public void onAdapterStart() {
        System.out.println("[HospitalDigitalAdapter] -> Digital twin outbound exposure layer active for: " + getId());
    }

    @Override
    public void onAdapterStop() {
        System.out.println("[HospitalDigitalAdapter] -> Digital layer terminated: " + getId());
    }

    @Override
    public void onDigitalTwinSync(DigitalTwinState digitalTwinState) {
        System.out.println("[HospitalDigitalAdapter] -> Synchronization achieved. Binding state observers...");
        System.out.println();
        System.out.println("[HospitalDigitalAdapter] ── INITIAL SYNCHRONIZED HOSPITAL STATE ──");

        try {
            digitalTwinState.getProperty(HospitalKeywords.ASSISTANCE_LEVEL_PROPERTY_KEY)
                    .ifPresent(p -> System.out.println("   [PROPERTY] " + HospitalKeywords.ASSISTANCE_LEVEL_PROPERTY_KEY + "  = " + p.getValue()));
            digitalTwinState.getProperty(HospitalKeywords.PATIENT_ASSISTED_PROPERTY_KEY)
                .ifPresent(p -> System.out.println("   [PROPERTY] " + HospitalKeywords.PATIENT_ASSISTED_PROPERTY_KEY + "   = " + p.getValue()));
            digitalTwinState.getProperty(HospitalKeywords.TIMESTAMP_PROPERTY_KEY)
                .ifPresent(p -> System.out.println("   [PROPERTY] " + HospitalKeywords.TIMESTAMP_PROPERTY_KEY + "          = " + p.getValue()));
        
        } catch (WldtDigitalTwinStatePropertyException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        System.out.println();
    }

    @Override
    public void onDigitalTwinUnSync(DigitalTwinState digitalTwinState) {
        System.out.println("[HospitalDigitalAdapter] -> Digital twin desynchronized from physical asset: " + getId());
    }

    @Override
    public void onDigitalTwinCreate() {
        // Lifecycle callback del framework: Twin creato nel motore
    }

    @Override
    public void onDigitalTwinStart() {
        // Lifecycle callback del framework: Twin avviato nel motore
    }

    @Override
    public void onDigitalTwinStop() {
        // Lifecycle callback del framework: Twin stoppato
    }

    @Override
    public void onDigitalTwinDestroy() {
        // Lifecycle callback del framework: Twin rimosso e distruttore invocato
    }

    @Override
    protected void onStateUpdate(DigitalTwinState newDigitalTwinState, 
                                 DigitalTwinState previousDigitalTwinState, 
                                 ArrayList<DigitalTwinStateChange> digitalTwinStateChangeList) {
        // Invocato automaticamente ad ogni commit transazionale della Shadowing Function dell'ospedale
        System.out.println("[HospitalDigitalAdapter] -> Received dynamic state transaction commit update event for: " + getId());
    }

    @Override
    protected void onEventNotificationReceived(DigitalTwinStateEventNotification<?> notification) {
        if(notification == null) return;

        String eventKey = notification.getDigitalEventKey();
        Object body     = notification.getBody();

        if(HospitalKeywords.PATIENT_ASSISTED_PROPERTY_KEY.equals(eventKey)) {
            patientAssistedCount++;
        }
    }
}