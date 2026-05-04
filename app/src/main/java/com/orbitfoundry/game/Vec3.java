package com.orbitfoundry.game;

public class Vec3 {
    public float x, y, z;

    public Vec3() { this(0, 0, 0); }
    public Vec3(float x, float y, float z) { this.x = x; this.y = y; this.z = z; }

    public Vec3 set(float x, float y, float z) { this.x = x; this.y = y; this.z = z; return this; }
    public Vec3 copy() { return new Vec3(x, y, z); }
    public Vec3 add(Vec3 v) { x += v.x; y += v.y; z += v.z; return this; }
    public Vec3 sub(Vec3 v) { x -= v.x; y -= v.y; z -= v.z; return this; }
    public Vec3 mul(float s) { x *= s; y *= s; z *= s; return this; }
    public float dot(Vec3 v) { return x * v.x + y * v.y + z * v.z; }
    public float length() { return (float)Math.sqrt(x * x + y * y + z * z); }
    public Vec3 normalize() { float l = length(); if (l > 0.00001f) mul(1f / l); return this; }

    public static Vec3 add(Vec3 a, Vec3 b) { return new Vec3(a.x + b.x, a.y + b.y, a.z + b.z); }
    public static Vec3 sub(Vec3 a, Vec3 b) { return new Vec3(a.x - b.x, a.y - b.y, a.z - b.z); }
    public static Vec3 mul(Vec3 a, float s) { return new Vec3(a.x * s, a.y * s, a.z * s); }
}
