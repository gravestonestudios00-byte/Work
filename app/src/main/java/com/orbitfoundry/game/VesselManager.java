package com.orbitfoundry.game;

import java.util.ArrayList;
import java.util.List;

public class VesselManager {
    private static final float SURFACE_GRAVITY = 9.81f;
    private static final float PLANET_RADIUS = 600000f;
    private static final float SEA_LEVEL_DENSITY = 1.225f;
    private static final float ATMOSPHERE_SCALE_HEIGHT = 8500f;
    private static final float ORIGIN_RECENTER_THRESHOLD = 10000f;

    public final List<RocketPart> parts = new ArrayList<>();
    public final Vec3 position = new Vec3(0, PLANET_RADIUS, 0);
    public final Vec3 velocity = new Vec3();
    public final Vec3 worldOffset = new Vec3();

    public float pitchRadians = 0f;
    public float throttle = 0f;
    public boolean launched = false;

    public void clearToDefault(PartDefinition pod, PartDefinition tank, PartDefinition engine) {
        parts.clear();
        RocketPart root = new RocketPart(pod, null, 0);
        parts.add(root);
        parts.add(new RocketPart(tank, root, 1));
        parts.add(new RocketPart(engine, root, 2));
        resetFlight();
    }

    public boolean addPart(PartDefinition definition) {
        if (parts.isEmpty() && definition.type != PartType.COMMAND_POD) return false;
        RocketPart parent = parts.isEmpty() ? null : parts.get(0);
        parts.add(new RocketPart(definition, parent, parts.size()));
        return true;
    }

    public void resetFlight() {
        position.set(0, PLANET_RADIUS, 0);
        velocity.set(0, 0, 0);
        worldOffset.set(0, 0, 0);
        pitchRadians = 0f;
        throttle = 0f;
        launched = false;
        for (RocketPart p : parts) p.fuel = p.definition.fuelCapacity;
    }

    public void update(float dt) {
        if (!launched) return;
        float mass = Math.max(0.01f, totalMass());
        Vec3 up = position.copy().normalize();
        Vec3 east = new Vec3(1, 0, 0);
        Vec3 thrustDir = up.copy().mul((float)Math.cos(pitchRadians)).add(east.mul((float)Math.sin(pitchRadians))).normalize();

        float thrust = bottomEngineThrust() * throttle;
        float burn = bottomEngineBurnRate() * throttle * dt;
        consumeFuel(burn);
        if (totalFuel() <= 0.001f) throttle = 0f;

        Vec3 accel = new Vec3();
        accel.add(thrustDir.mul(thrust / mass));

        float r = position.length();
        float gravity = SURFACE_GRAVITY * (PLANET_RADIUS * PLANET_RADIUS) / Math.max(1f, r * r);
        accel.add(position.copy().normalize().mul(-gravity));

        float speed = velocity.length();
        if (speed > 0.001f) {
            float altitude = altitude();
            float rho = SEA_LEVEL_DENSITY * (float)Math.exp(-Math.max(0f, altitude) / ATMOSPHERE_SCALE_HEIGHT);
            float drag = 0.5f * rho * speed * speed * averageDragCoefficient() * crossSectionArea();
            accel.add(velocity.copy().normalize().mul(-drag / mass));
        }

        velocity.add(accel.mul(dt));
        position.add(velocity.copy().mul(dt));

        if (position.length() < PLANET_RADIUS) {
            position.normalize().mul(PLANET_RADIUS);
            velocity.set(0, 0, 0);
            launched = false;
            throttle = 0f;
        }

        if (Math.abs(position.x) > ORIGIN_RECENTER_THRESHOLD || Math.abs(position.z) > ORIGIN_RECENTER_THRESHOLD) {
            worldOffset.add(new Vec3(position.x, 0, position.z));
            position.x = 0;
            position.z = 0;
        }
    }

    public float totalMass() {
        float mass = 0f;
        for (RocketPart p : parts) mass += p.currentMass();
        return mass;
    }

    public float totalFuel() {
        float fuel = 0f;
        for (RocketPart p : parts) fuel += p.fuel;
        return fuel;
    }

    public float bottomEngineThrust() {
        for (int i = parts.size() - 1; i >= 0; i--) {
            RocketPart p = parts.get(i);
            if (p.definition.type == PartType.ENGINE) return p.definition.maxThrust;
        }
        return 0f;
    }

    public float bottomEngineBurnRate() {
        for (int i = parts.size() - 1; i >= 0; i--) {
            RocketPart p = parts.get(i);
            if (p.definition.type == PartType.ENGINE) return p.definition.fuelBurnRate;
        }
        return 0f;
    }

    public float altitude() {
        return position.length() - PLANET_RADIUS;
    }

    public float verticalSpeed() {
        Vec3 up = position.copy().normalize();
        return velocity.dot(up);
    }

    private void consumeFuel(float amount) {
        float remaining = amount;
        for (RocketPart p : parts) {
            if (p.definition.type != PartType.FUEL_TANK || p.fuel <= 0f) continue;
            float used = Math.min(p.fuel, remaining);
            p.fuel -= used;
            remaining -= used;
            if (remaining <= 0f) return;
        }
    }

    private float averageDragCoefficient() {
        if (parts.isEmpty()) return 0.5f;
        float sum = 0f;
        for (RocketPart p : parts) sum += p.definition.dragCoefficient;
        return sum / parts.size();
    }

    private float crossSectionArea() {
        float area = 0.1f;
        for (RocketPart p : parts) area = Math.max(area, p.definition.crossSectionArea);
        return area;
    }
}
