package org.qcri.rheem.profiler.core.api;

import org.qcri.rheem.core.types.DataSetType;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by migiwara on 07/07/17.
 */
public class OutputTopologySlot<T> extends TopologySlot<T> {

    /**
     * Output slot of another Topology that is connected to this output slot.
     */
    private final List<InputTopologySlot<T>> occupiedSlots = new LinkedList<>() ;

    protected OutputTopologySlot(String name, Topology owner) {
        super(name, owner);
    }

    /**
     * Connect this output slot to an input slot. The input slot must not be occupied already.
     *
     * @param inputSlot the input slot to connect to
     */
    public void connectTo(InputTopologySlot<T> inputSlot) {
        if (inputSlot.getOccupant() != null) {
            throw new IllegalStateException("Cannot connect: input slot is already occupied");
        }

        this.occupiedSlots.add(inputSlot);
        inputSlot.setOccupant(this);
    }

    public OutputTopologySlot<T> clone() {
        OutputTopologySlot newOutputTopologySlot = new OutputTopologySlot(this.getName(),this.getOwner());
        return newOutputTopologySlot;
    }
}