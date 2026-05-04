package com.orbitfoundry.game;

public class RocketPart {
    public final PartDefinition definition;
    public final RocketPart parent;
    public final int gridY;
    public float fuel;

    public RocketPart(PartDefinition definition, RocketPart parent, int gridY) {
        this.definition = definition;
        this.parent = parent;
        this.gridY = gridY;
        this.fuel = definition.fuelCapacity;
    }

    public float currentMass() {
        return definition.dryMass + fuel;
    }
}
