package it.ausl.preh.shadowing;

import java.util.Map;

import it.wldt.adapter.digital.event.DigitalActionWldtEvent;
import it.wldt.adapter.physical.PhysicalAssetDescription;
import it.wldt.adapter.physical.event.PhysicalAssetEventWldtEvent;
import it.wldt.adapter.physical.event.PhysicalAssetPropertyWldtEvent;
import it.wldt.adapter.physical.event.PhysicalAssetRelationshipInstanceCreatedWldtEvent;
import it.wldt.adapter.physical.event.PhysicalAssetRelationshipInstanceDeletedWldtEvent;
import it.wldt.core.model.ShadowingFunction;

public class PatientShadowingFunction extends ShadowingFunction {

    public PatientShadowingFunction(String id) {
        super(id);
        //TODO Auto-generated constructor stub
    }

    @Override
    protected void onCreate() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'onCreate'");
    }

    @Override
    protected void onDigitalActionEvent(DigitalActionWldtEvent<?> arg0) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'onDigitalActionEvent'");
    }

    @Override
    protected void onDigitalTwinBound(Map<String, PhysicalAssetDescription> arg0) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'onDigitalTwinBound'");
    }

    @Override
    protected void onDigitalTwinUnBound(Map<String, PhysicalAssetDescription> arg0, String arg1) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'onDigitalTwinUnBound'");
    }

    @Override
    protected void onPhysicalAdapterBidingUpdate(String arg0, PhysicalAssetDescription arg1) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'onPhysicalAdapterBidingUpdate'");
    }

    @Override
    protected void onPhysicalAssetEventNotification(PhysicalAssetEventWldtEvent<?> arg0) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'onPhysicalAssetEventNotification'");
    }

    @Override
    protected void onPhysicalAssetPropertyVariation(PhysicalAssetPropertyWldtEvent<?> arg0) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'onPhysicalAssetPropertyVariation'");
    }

    @Override
    protected void onPhysicalAssetRelationshipDeleted(PhysicalAssetRelationshipInstanceDeletedWldtEvent<?> arg0) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'onPhysicalAssetRelationshipDeleted'");
    }

    @Override
    protected void onPhysicalAssetRelationshipEstablished(PhysicalAssetRelationshipInstanceCreatedWldtEvent<?> arg0) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'onPhysicalAssetRelationshipEstablished'");
    }

    @Override
    protected void onStart() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'onStart'");
    }

    @Override
    protected void onStop() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'onStop'");
    }
    
}
