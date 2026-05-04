package com.orbitfoundry.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {
    enum Scene { SPACE_CENTER, SETTINGS, BUILDER, LAUNCH }

    interface Click { void run(); }
    static class GameButton {
        String label;
        RectF rect;
        Click click;
        GameButton(String label, RectF rect, Click click) { this.label = label; this.rect = rect; this.click = click; }
    }

    private static final float TOUCH_MIN = 88f;
    private static final float PITCH_LIMIT = 0.95f;

    private boolean running;
    private Thread thread;
    private long lastTime = System.nanoTime();
    private Scene scene = Scene.SPACE_CENTER;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint smallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<GameButton> buttons = new ArrayList<>();
    private final VesselManager vessel = new VesselManager();

    private final PartDefinition pod = new PartDefinition(PartType.COMMAND_POD, "Command Pod", 1.2f, 0f, 0f, 0f, 0.25f, 0.9f, 1.1f, 0.45f, Color.rgb(218, 226, 236));
    private final PartDefinition tank = new PartDefinition(PartType.FUEL_TANK, "Fuel Tank", 0.8f, 3.8f, 0f, 0f, 0.34f, 0.75f, 1.25f, 0.42f, Color.rgb(235, 166, 63));
    private final PartDefinition engine = new PartDefinition(PartType.ENGINE, "Engine", 1.0f, 0f, 145000f, 0.42f, 0.42f, 0.75f, 0.75f, 0.48f, Color.rgb(158, 164, 174));
    private final PartDefinition[] catalog = new PartDefinition[] { pod, tank, engine };
    private int selectedPart = 0;
    private float menuOrbit = 0f;
    private float builderOrbit = 0.45f;
    private float touchStartX, touchStartY;
    private boolean adjustingThrottle = false;
    private boolean adjustingPitch = false;

    public GameView(Context context) {
        super(context);
        getHolder().addCallback(this);
        setFocusable(true);
        titlePaint.setColor(Color.WHITE);
        titlePaint.setTextSize(46f);
        titlePaint.setTypeface(Typeface.DEFAULT_BOLD);
        smallPaint.setColor(Color.WHITE);
        smallPaint.setTextSize(28f);
        vessel.clearToDefault(pod, tank, engine);
    }

    @Override public void surfaceCreated(SurfaceHolder holder) { running = true; thread = new Thread(this); thread.start(); }
    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { }
    @Override public void surfaceDestroyed(SurfaceHolder holder) { running = false; try { if (thread != null) thread.join(800); } catch (InterruptedException ignored) { } }

    @Override public void run() {
        while (running) {
            long now = System.nanoTime();
            float dt = Math.min(0.033f, (now - lastTime) / 1000000000f);
            lastTime = now;
            update(dt);
            drawFrame();
        }
    }

    private void update(float dt) {
        if (scene == Scene.SPACE_CENTER) menuOrbit += dt * 0.22f;
        if (scene == Scene.BUILDER) builderOrbit += dt * 0.08f;
        if (scene == Scene.LAUNCH) vessel.update(dt);
    }

    private void drawFrame() {
        Canvas c = getHolder().lockCanvas();
        if (c == null) return;
        buttons.clear();
        drawSky(c);
        if (scene == Scene.SPACE_CENTER) drawSpaceCenter(c);
        else if (scene == Scene.SETTINGS) drawSettings(c);
        else if (scene == Scene.BUILDER) drawBuilder(c);
        else drawLaunch(c);
        getHolder().unlockCanvasAndPost(c);
    }

    private void drawSky(Canvas c) {
        paint.setShader(new LinearGradient(0, 0, 0, getHeight(), Color.rgb(82, 154, 218), Color.rgb(20, 36, 78), Shader.TileMode.CLAMP));
        c.drawRect(0, 0, getWidth(), getHeight(), paint);
        paint.setShader(null);
        paint.setShader(new RadialGradient(getWidth() * .78f, getHeight() * .16f, 250f, Color.argb(130, 255, 242, 180), Color.TRANSPARENT, Shader.TileMode.CLAMP));
        c.drawCircle(getWidth() * .78f, getHeight() * .16f, 250f, paint);
        paint.setShader(null);
    }

    private void drawSpaceCenter(Canvas c) {
        float cam = menuOrbit;
        drawHills(c, cam);
        drawGridGround(c, cam, Color.rgb(72, 112, 74));
        drawLowPolyBox(c, -2.2f, 0.8f, 3.1f, 1.4f, 1.6f, 2.0f, Color.rgb(86, 96, 108), cam);
        drawLowPolyBox(c, -2.2f, 2.2f, 3.1f, 1.2f, 0.9f, 1.1f, Color.rgb(116, 126, 138), cam);
        drawLowPolyCylinder(c, 2.2f, 0.12f, 2.2f, 1.1f, .18f, Color.rgb(126, 126, 126), cam);
        drawLowPolyBox(c, 2.2f, 0.55f, 2.2f, 1.3f, .12f, 1.3f, Color.rgb(92, 92, 96), cam);
        drawLowPolyBox(c, 2.9f, 1.15f, 2.2f, .15f, 1.4f, .15f, Color.rgb(120, 130, 136), cam);

        c.drawText("ORBIT FOUNDRY", 34, 78, titlePaint);
        c.drawText("3D mobile space-flight prototype", 36, 116, smallPaint);
        button(c, "Build", .25f, .86f, () -> scene = Scene.BUILDER);
        button(c, "Launch", .50f, .86f, () -> { vessel.resetFlight(); scene = Scene.LAUNCH; });
        button(c, "Settings", .75f, .86f, () -> scene = Scene.SETTINGS);
    }

    private void drawSettings(Canvas c) {
        drawHills(c, menuOrbit);
        title(c, "SETTINGS", "Prototype options");
        c.drawText("Renderer: low-poly software 3D", 44, getHeight() * .34f, smallPaint);
        c.drawText("Physics: single rigidbody vessel", 44, getHeight() * .40f, smallPaint);
        c.drawText("Scope: sub-orbital only", 44, getHeight() * .46f, smallPaint);
        button(c, "Back", .5f, .78f, () -> scene = Scene.SPACE_CENTER);
    }

    private void drawBuilder(Canvas c) {
        float cam = builderOrbit;
        drawGridGround(c, cam, Color.rgb(55, 58, 68));
        drawLowPolyBox(c, 0, 0.05f, 4.0f, 3.0f, .1f, 3.0f, Color.rgb(72, 76, 88), cam);
        drawSnapGrid(c, cam);
        drawVessel3D(c, cam, 0f, 0.1f, 4.0f, false);

        c.drawText("ROCKET BUILDER", 28, 56, titlePaint);
        c.drawText("Snap grid: vertical stack | Root: Command Pod", 30, 94, smallPaint);
        c.drawText("Selected: " + catalog[selectedPart].name, 30, getHeight() - 250, smallPaint);
        c.drawText("Mass " + fmt(vessel.totalMass()) + "t  Fuel " + fmt(vessel.totalFuel()) + "  TWR " + fmt(twr()), 30, getHeight() - 214, smallPaint);

        button(c, "Part", .18f, .90f, () -> selectedPart = (selectedPart + 1) % catalog.length);
        button(c, "Add / Stack", .50f, .90f, () -> addSelectedPart());
        button(c, "Launch", .82f, .90f, () -> { vessel.resetFlight(); scene = Scene.LAUNCH; });
        button(c, "Menu", .16f, .07f, () -> scene = Scene.SPACE_CENTER);
    }

    private void drawLaunch(Canvas c) {
        float altitude = vessel.altitude();
        float cam = 0.25f + vessel.pitchRadians * 0.35f;
        drawGridGround(c, cam, Color.rgb(61, 86, 72));
        drawLowPolyCylinder(c, 0, 0.08f, 4.0f, 1.4f, .16f, Color.rgb(126, 126, 126), cam);
        drawVessel3D(c, cam, 0f, Math.max(0.1f, 0.1f + altitude * 0.0006f), 4.0f, vessel.launched && vessel.throttle > 0.01f && vessel.totalFuel() > 0.01f);

        c.drawText("LAUNCH", 28, 54, titlePaint);
        c.drawText("Alt " + (int)altitude + "m  VSpeed " + (int)vessel.verticalSpeed() + "m/s", 30, 94, smallPaint);
        c.drawText("Mass " + fmt(vessel.totalMass()) + "t  Fuel " + fmt(vessel.totalFuel()), 30, 130, smallPaint);
        c.drawText("Origin offset X " + (int)vessel.worldOffset.x + "m", 30, 166, smallPaint);

        slider(c, "Thrust", .16f, .78f, .68f, vessel.throttle);
        slider(c, "Pitch", .16f, .88f, .68f, (vessel.pitchRadians + PITCH_LIMIT) / (PITCH_LIMIT * 2f));
        button(c, vessel.launched ? "Cut / Reset" : "Ignite", .82f, .78f, () -> {
            if (vessel.launched) vessel.resetFlight(); else { vessel.launched = true; vessel.throttle = 1f; }
        });
        button(c, "VAB", .82f, .90f, () -> scene = Scene.BUILDER);
    }

    private void addSelectedPart() {
        PartDefinition def = catalog[selectedPart];
        if (vessel.parts.isEmpty() && def.type != PartType.COMMAND_POD) return;
        if (!vessel.parts.isEmpty() && def.type == PartType.COMMAND_POD) return;
        vessel.addPart(def);
    }

    private float twr() {
        float thrust = vessel.bottomEngineThrust();
        float weight = vessel.totalMass() * 1000f * 9.81f;
        return weight <= 0f ? 0f : thrust / weight;
    }

    private void drawHills(Canvas c, float cam) {
        paint.setColor(Color.rgb(62, 118, 72));
        Path p = new Path();
        p.moveTo(0, getHeight() * .70f);
        for (int x = 0; x <= getWidth(); x += 80) {
            float y = getHeight() * .65f + (float)Math.sin(x * .012f + cam * 3f) * 44f;
            p.lineTo(x, y);
        }
        p.lineTo(getWidth(), getHeight());
        p.lineTo(0, getHeight());
        p.close();
        c.drawPath(p, paint);
    }

    private void drawGridGround(Canvas c, float cam, int color) {
        drawHills(c, cam);
        paint.setColor(color);
        Path ground = new Path();
        ground.moveTo(0, getHeight() * .68f);
        ground.lineTo(getWidth(), getHeight() * .68f);
        ground.lineTo(getWidth(), getHeight());
        ground.lineTo(0, getHeight());
        ground.close();
        c.drawPath(ground, paint);
        paint.setColor(Color.argb(95, 255, 255, 255));
        paint.setStrokeWidth(2f);
        for (int i = -8; i <= 8; i++) {
            float sx = getWidth() * .5f + i * 64f + (float)Math.sin(cam) * 28f;
            c.drawLine(sx, getHeight() * .68f, getWidth() * .5f + i * 140f, getHeight(), paint);
        }
        for (int i = 0; i < 8; i++) {
            float y = getHeight() * (.70f + i * .04f);
            c.drawLine(0, y, getWidth(), y, paint);
        }
    }

    private void drawSnapGrid(Canvas c, float cam) {
        paint.setColor(Color.argb(120, 110, 190, 255));
        paint.setStrokeWidth(3f);
        for (int i = 0; i < 7; i++) {
            float[] p = project(0, 0.5f + i * .65f, 4.0f, cam);
            c.drawCircle(p[0], p[1], 8f, paint);
        }
    }

    private void drawVessel3D(Canvas c, float cam, float wx, float wy, float wz, boolean flame) {
        float y = wy;
        for (RocketPart part : vessel.parts) {
            PartDefinition d = part.definition;
            if (d.type == PartType.COMMAND_POD) drawLowPolyCone(c, wx, y + d.height * .5f, wz, d.radius, d.height, d.color, cam);
            else drawLowPolyCylinder(c, wx, y + d.height * .5f, wz, d.radius, d.height, d.color, cam);
            y += d.height;
        }
        if (flame) drawEngineFlame(c, wx, wy - .35f, wz, cam);
    }

    private void drawEngineFlame(Canvas c, float x, float y, float z, float cam) {
        float[] a = project(x - .28f, y + .25f, z, cam);
        float[] b = project(x + .28f, y + .25f, z, cam);
        float[] tip = project(x, y - .8f - vessel.throttle * .7f, z, cam);
        paint.setColor(Color.rgb(255, 170, 36));
        Path p = new Path();
        p.moveTo(a[0], a[1]);
        p.lineTo(b[0], b[1]);
        p.lineTo(tip[0], tip[1]);
        p.close();
        c.drawPath(p, paint);
    }

    private void drawLowPolyBox(Canvas c, float x, float y, float z, float sx, float sy, float sz, int color, float cam) {
        float[][] pts = new float[][] {
                project(x-sx/2, y-sy/2, z-sz/2, cam), project(x+sx/2, y-sy/2, z-sz/2, cam),
                project(x+sx/2, y+sy/2, z-sz/2, cam), project(x-sx/2, y+sy/2, z-sz/2, cam),
                project(x-sx/2, y-sy/2, z+sz/2, cam), project(x+sx/2, y-sy/2, z+sz/2, cam),
                project(x+sx/2, y+sy/2, z+sz/2, cam), project(x-sx/2, y+sy/2, z+sz/2, cam)
        };
        drawPoly(c, colorShade(color, 1.0f), pts[4], pts[5], pts[6], pts[7]);
        drawPoly(c, colorShade(color, .78f), pts[1], pts[5], pts[6], pts[2]);
        drawPoly(c, colorShade(color, .62f), pts[0], pts[4], pts[7], pts[3]);
        drawPoly(c, colorShade(color, 1.18f), pts[3], pts[2], pts[6], pts[7]);
    }

    private void drawLowPolyCylinder(Canvas c, float x, float y, float z, float r, float h, int color, float cam) {
        int sides = 8;
        float[][] top = new float[sides][2];
        float[][] bot = new float[sides][2];
        for (int i = 0; i < sides; i++) {
            double a = Math.PI * 2.0 * i / sides;
            top[i] = project(x + (float)Math.cos(a)*r, y + h/2, z + (float)Math.sin(a)*r, cam);
            bot[i] = project(x + (float)Math.cos(a)*r, y - h/2, z + (float)Math.sin(a)*r, cam);
        }
        for (int i = 0; i < sides; i++) {
            int j = (i + 1) % sides;
            drawPoly(c, colorShade(color, i % 2 == 0 ? .88f : .68f), bot[i], bot[j], top[j], top[i]);
        }
        drawPoly(c, colorShade(color, 1.15f), top);
    }

    private void drawLowPolyCone(Canvas c, float x, float y, float z, float r, float h, int color, float cam) {
        int sides = 8;
        float[][] base = new float[sides][2];
        float[] tip = project(x, y + h/2, z, cam);
        for (int i = 0; i < sides; i++) {
            double a = Math.PI * 2.0 * i / sides;
            base[i] = project(x + (float)Math.cos(a)*r, y - h/2, z + (float)Math.sin(a)*r, cam);
        }
        for (int i = 0; i < sides; i++) {
            int j = (i + 1) % sides;
            drawPoly(c, colorShade(color, i % 2 == 0 ? 1.05f : .76f), base[i], base[j], tip);
        }
    }

    private float[] project(float x, float y, float z, float cam) {
        float cos = (float)Math.cos(cam);
        float sin = (float)Math.sin(cam);
        float rx = x * cos - z * sin;
        float rz = x * sin + z * cos;
        float depth = 6.5f + rz;
        float f = Math.max(70f, getWidth() * .78f) / Math.max(1.2f, depth);
        float sx = getWidth() * .5f + rx * f;
        float sy = getHeight() * .68f - y * f;
        return new float[] { sx, sy };
    }

    private void drawPoly(Canvas c, int color, float[]... points) {
        if (points.length == 0) return;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        Path p = new Path();
        p.moveTo(points[0][0], points[0][1]);
        for (int i = 1; i < points.length; i++) p.lineTo(points[i][0], points[i][1]);
        p.close();
        c.drawPath(p, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(Color.argb(65, 0, 0, 0));
        c.drawPath(p, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private int colorShade(int color, float shade) {
        int r = Math.min(255, Math.max(0, (int)(Color.red(color) * shade)));
        int g = Math.min(255, Math.max(0, (int)(Color.green(color) * shade)));
        int b = Math.min(255, Math.max(0, (int)(Color.blue(color) * shade)));
        return Color.rgb(r, g, b);
    }

    private void title(Canvas c, String a, String b) {
        c.drawText(a, 40, getHeight() * .18f, titlePaint);
        c.drawText(b, 42, getHeight() * .23f, smallPaint);
    }

    private void button(Canvas c, String label, float xPct, float yPct, Click click) {
        float w = getWidth() * .24f;
        if (label.length() > 8) w = getWidth() * .46f;
        float h = TOUCH_MIN;
        RectF r = new RectF(getWidth()*xPct-w/2, getHeight()*yPct-h/2, getWidth()*xPct+w/2, getHeight()*yPct+h/2);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(220, 26, 36, 52));
        c.drawRoundRect(r, 26, 26, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f);
        paint.setColor(Color.rgb(142, 203, 255));
        c.drawRoundRect(r, 26, 26, paint);
        paint.setStyle(Paint.Style.FILL);
        smallPaint.setTextAlign(Paint.Align.CENTER);
        c.drawText(label, r.centerX(), r.centerY() + 10, smallPaint);
        smallPaint.setTextAlign(Paint.Align.LEFT);
        buttons.add(new GameButton(label, r, click));
    }

    private void slider(Canvas c, String label, float xPct, float yPct, float wPct, float value) {
        float x = getWidth() * xPct;
        float y = getHeight() * yPct;
        float w = getWidth() * wPct;
        float h = 44f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(210, 24, 30, 42));
        c.drawRoundRect(new RectF(x, y - h/2, x + w, y + h/2), 22, 22, paint);
        paint.setColor(Color.rgb(135, 202, 255));
        c.drawRoundRect(new RectF(x, y - h/2, x + w * clamp01(value), y + h/2), 22, 22, paint);
        smallPaint.setTextAlign(Paint.Align.LEFT);
        c.drawText(label, x, y - 34f, smallPaint);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX();
        float y = e.getY();
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            touchStartX = x;
            touchStartY = y;
            adjustingThrottle = scene == Scene.LAUNCH && y > getHeight() * .72f && y < getHeight() * .83f && x < getWidth() * .78f;
            adjustingPitch = scene == Scene.LAUNCH && y > getHeight() * .82f && y < getHeight() * .94f && x < getWidth() * .78f;
            for (int i = buttons.size() - 1; i >= 0; i--) {
                GameButton b = buttons.get(i);
                if (b.rect.contains(x, y)) { b.click.run(); return true; }
            }
            if (adjustingThrottle || adjustingPitch) updateSliderTouch(x);
        } else if (e.getAction() == MotionEvent.ACTION_MOVE) {
            if (adjustingThrottle || adjustingPitch) updateSliderTouch(x);
            else if (scene == Scene.BUILDER) builderOrbit += (x - touchStartX) * 0.002f;
        } else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
            adjustingThrottle = false;
            adjustingPitch = false;
        }
        return true;
    }

    private void updateSliderTouch(float x) {
        float start = getWidth() * .16f;
        float end = start + getWidth() * .68f;
        float v = clamp01((x - start) / (end - start));
        if (adjustingThrottle) vessel.throttle = v;
        if (adjustingPitch) vessel.pitchRadians = (v * 2f - 1f) * PITCH_LIMIT;
    }

    private float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }
    private String fmt(float v) { return String.format(Locale.US, "%.1f", v); }
}
