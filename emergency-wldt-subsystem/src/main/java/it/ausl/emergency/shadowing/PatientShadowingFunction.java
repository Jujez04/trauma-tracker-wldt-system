package it.ausl.emergency.shadowing;

import java.util.Map;

import it.wldt.adapter.digital.event.DigitalActionWldtEvent;
import it.wldt.adapter.physical.PhysicalAssetDescription;
import it.wldt.adapter.physical.PhysicalAssetProperty;
import it.wldt.adapter.physical.event.PhysicalAssetEventWldtEvent;
import it.wldt.adapter.physical.event.PhysicalAssetPropertyWldtEvent;
import it.wldt.adapter.physical.event.PhysicalAssetRelationshipInstanceCreatedWldtEvent;
import it.wldt.adapter.physical.event.PhysicalAssetRelationshipInstanceDeletedWldtEvent;
import it.wldt.core.model.ShadowingFunction;
import it.wldt.core.state.DigitalTwinStateEvent;
import it.wldt.core.state.DigitalTwinStateEventNotification;
import it.wldt.core.state.DigitalTwinStateManager;
import it.wldt.core.state.DigitalTwinStateProperty;

/**
 * Shadowing Function del Paziente: è il componente che traduce lo stato
 * dell'agente Paziente della
 * simulazione (ricevuto tramite il
 * {@link it.ausl.emergency.adapter.physical.PatientPhysicalAdapter})
 * nello stato del Digital Twin, esposto poi verso l'esterno tramite i Digital
 * Adapter.
 *
 * Le proprietà rispecchiano 1:1 i campi di PatientTelemetryPayload (vedi
 * PatientKeywords), gli eventi
 * rispecchiano i Domain Events individuati nell'analisi DDD della tesi
 * (Riscontro Clinico Eseguito,
 * Deterioramento Clinico Rilevato, Handover Completato).
 */
public class PatientShadowingFunction extends ShadowingFunction {

    public PatientShadowingFunction(String id) {
        super(id);
    }

    //// Shadowing Function Management Callbacks ////

    @Override
    protected void onCreate() {
        System.out.println("[PatientShadowingFunction] -> onCreate()");
    }

    @Override
    protected void onStart() {
        System.out.println("[PatientShadowingFunction] -> onStart()");
    }

    @Override
    protected void onStop() {
        System.out.println("[PatientShadowingFunction] -> onStop()");
    }

    //// Bound LifeCycle State Management Callbacks ////

    @Override
    protected void onDigitalTwinBound(Map<String, PhysicalAssetDescription> adaptersPhysicalAssetDescriptionMap) {

        try {

            System.out.println(
                    "[PatientShadowingFunction] -> onDigitalTwinBound(): " + adaptersPhysicalAssetDescriptionMap);

            this.digitalTwinStateManager.startStateTransaction();

            adaptersPhysicalAssetDescriptionMap.values().forEach(pad -> {

                // Le proprietà del Paziente non sono tutte dello stesso tipo (String, int,
                // boolean,
                // double): si crea quindi la DigitalTwinStateProperty corretta in base al tipo
                // effettivo del valore iniziale dichiarato nella PAD.
                pad.getProperties().forEach(property -> {
                    try {

                        createDigitalTwinStateProperty(property);
                        this.observePhysicalAssetProperty(property);

                        System.out.println(
                                "[PatientShadowingFunction] -> onDigitalTwinBound() -> Property Created & Observed: "
                                        + property.getKey());

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });

                pad.getEvents().forEach(event -> {
                    try {

                        DigitalTwinStateEvent dtStateEvent = new DigitalTwinStateEvent(event.getKey(), event.getType());
                        this.digitalTwinStateManager.registerEvent(dtStateEvent);
                        this.observePhysicalAssetEvent(event);

                        System.out.println(
                                "[PatientShadowingFunction] -> onDigitalTwinBound() -> Event Created & Observed: "
                                        + event.getKey());

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });

                // Nessuna azione fisica è attualmente dichiarata nella PAD del Paziente: il
                // blocco è
                // lasciato per coerenza/estendibilità futura (es. richiesta manuale di
                // refresh).
                pad.getActions().forEach(action -> {
                    try {

                        it.wldt.core.state.DigitalTwinStateAction dtStateAction = new it.wldt.core.state.DigitalTwinStateAction(
                                action.getKey(), action.getType(), action.getContentType());
                        this.digitalTwinStateManager.enableAction(dtStateAction);

                        System.out.println("[PatientShadowingFunction] -> onDigitalTwinBound() -> Action Enabled: "
                                + action.getKey());

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });

            });

            this.digitalTwinStateManager.commitStateTransaction();

            observeDigitalActionEvents();

            notifyShadowingSync();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDigitalTwinUnBound(Map<String, PhysicalAssetDescription> map, String detachReason) {
        System.out.println("[PatientShadowingFunction] -> onDigitalTwinUnBound(): " + detachReason);
    }

    @Override
    protected void onPhysicalAdapterBidingUpdate(String adapterId, PhysicalAssetDescription physicalAssetDescription) {
        // Non gestito: la PAD del Paziente è statica per tutta la missione (le
        // proprietà non
        // cambiano "forma", cambiano solo i valori tramite
        // onPhysicalAssetPropertyVariation).
    }

    //// Physical Property Variation Callback ////

    @Override
    protected void onPhysicalAssetPropertyVariation(PhysicalAssetPropertyWldtEvent<?> physicalAssetPropertyWldtEvent) {

        try {

            System.out.println(
                    "[PatientShadowingFunction] -> onPhysicalAssetPropertyVariation() -> Variation on Property: "
                            + physicalAssetPropertyWldtEvent.getPhysicalPropertyId());

            this.digitalTwinStateManager.startStateTransaction();

            updateDigitalTwinStateProperty(
                    physicalAssetPropertyWldtEvent.getPhysicalPropertyId(),
                    physicalAssetPropertyWldtEvent.getBody());

            this.digitalTwinStateManager.commitStateTransaction();

            System.out.println(
                    "[PatientShadowingFunction] -> onPhysicalAssetPropertyVariation() -> DT State UPDATE Property: "
                            + physicalAssetPropertyWldtEvent.getPhysicalPropertyId());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //// Physical Event Notification Callback ////

    @Override
    protected void onPhysicalAssetEventNotification(PhysicalAssetEventWldtEvent<?> physicalAssetEventWldtEvent) {
        try {

            System.out.println(
                    "[PatientShadowingFunction] -> onPhysicalAssetEventNotification() -> Notification for Event: "
                            + physicalAssetEventWldtEvent.getPhysicalEventKey());

            this.digitalTwinStateManager.notifyDigitalTwinStateEvent(new DigitalTwinStateEventNotification<>(
                    physicalAssetEventWldtEvent.getPhysicalEventKey(),
                    physicalAssetEventWldtEvent.getBody(),
                    physicalAssetEventWldtEvent.getCreationTimestamp()));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //// Physical Relationships Notification Callbacks ////
    // Il Paziente non dichiara relazioni nella propria PAD (a differenza, ad es.,
    //// del Veicolo di
    // Soccorso che potrebbe dichiarare una relazione "trasporta" verso il
    //// Paziente): i metodi sono
    // lasciati come no-op per coerenza con il contratto della ShadowingFunction.

    @Override
    protected void onPhysicalAssetRelationshipEstablished(
            PhysicalAssetRelationshipInstanceCreatedWldtEvent<?> physicalAssetRelationshipInstanceCreatedWldtEvent) {
    }

    @Override
    protected void onPhysicalAssetRelationshipDeleted(
            PhysicalAssetRelationshipInstanceDeletedWldtEvent<?> physicalAssetRelationshipInstanceDeletedWldtEvent) {
    }

    //// Digital Action Received Callback ////

    @Override
    protected void onDigitalActionEvent(DigitalActionWldtEvent<?> digitalActionWldtEvent) {
        // Nessuna azione fisica è attualmente abilitata sul Paziente: si logga per
        // individuare
        // facilmente eventuali richieste non previste provenienti dai Digital Adapter.
        System.out.println("[PatientShadowingFunction] -> onDigitalActionEvent() -> Unsupported Digital Action: "
                + (digitalActionWldtEvent != null ? digitalActionWldtEvent.getActionKey() : "null"));
    }

    //// Helper Methods ////

    /**
     * Crea la DigitalTwinStateProperty con il tipo corretto a partire dal valore
     * iniziale dichiarato
     * nella PhysicalAssetProperty, dato che i campi del Paziente non sono tutti
     * dello stesso tipo.
     */
    private void createDigitalTwinStateProperty(PhysicalAssetProperty<?> property) throws Exception {

        Object initialValue = property.getInitialValue();

        if (initialValue instanceof Double) {
            this.digitalTwinStateManager
                    .createProperty(new DigitalTwinStateProperty<>(property.getKey(), (Double) initialValue));
        } else if (initialValue instanceof Integer) {
            this.digitalTwinStateManager
                    .createProperty(new DigitalTwinStateProperty<>(property.getKey(), (Integer) initialValue));
        } else if (initialValue instanceof Boolean) {
            this.digitalTwinStateManager
                    .createProperty(new DigitalTwinStateProperty<>(property.getKey(), (Boolean) initialValue));
        } else if (initialValue instanceof String) {
            this.digitalTwinStateManager
                    .createProperty(new DigitalTwinStateProperty<>(property.getKey(), (String) initialValue));
        } else {
            throw new IllegalArgumentException(
                    "[PatientShadowingFunction] -> Unsupported property type for key: " + property.getKey());
        }
    }

    /**
     * Aggiorna la DigitalTwinStateProperty con il tipo corretto a partire dal body
     * dell'evento fisico
     * di variazione, simmetrico a {@link #createDigitalTwinStateProperty}.
     */
    private void updateDigitalTwinStateProperty(String propertyKey, Object value) throws Exception {

        if (value instanceof Double) {
            this.digitalTwinStateManager.updateProperty(new DigitalTwinStateProperty<>(propertyKey, (Double) value));
        } else if (value instanceof Integer) {
            this.digitalTwinStateManager.updateProperty(new DigitalTwinStateProperty<>(propertyKey, (Integer) value));
        } else if (value instanceof Boolean) {
            this.digitalTwinStateManager.updateProperty(new DigitalTwinStateProperty<>(propertyKey, (Boolean) value));
        } else if (value instanceof String) {
            this.digitalTwinStateManager.updateProperty(new DigitalTwinStateProperty<>(propertyKey, (String) value));
        } else {
            throw new IllegalArgumentException(
                    "[PatientShadowingFunction] -> Unsupported value type for property key: " + propertyKey);
        }
    }

    public DigitalTwinStateManager getDigitalTwinStateManager() {
        return this.digitalTwinStateManager;
    }
}