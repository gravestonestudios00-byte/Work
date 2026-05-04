package com.orbitfoundry.game;

public class PartDefinition {
    public final PartType type;
    public final String name;
    public final float dryMass;
    public final float fuelCapacity;
    public final float maxThrust;
    public final float fuelBurnRate;
    public final float dragCoefficient;
    public final float crossSectionArea;
    public final float height;
    public final float radius;
    public final int color;

    public PartDefinition(PartType type, String name, float dryMass, float fuelCapacity, float maxThrust,
                          float fuelBurnRate, float dragCoefficient, float crossSectionArea,
                          float height, float radius, int color) {
        this.type = type;
        this.name = name;
        this.dryMass = dryMass;
        this.fuelCapacity = fuelCapacity;
        this.maxThrust = maxThrust;
        this.fuelBurnRate = fuelBurnRate;
        this.dragCoefficient = dragCoefficient;
        this.crossSectionArea = crossSectionArea;
        this.height = height;
        this.radius = radius;
        this.color = color;
    }
}
