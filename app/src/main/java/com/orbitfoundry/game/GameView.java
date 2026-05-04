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

public class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {
    enum Scene { MENU, SETTINGS, VAB, PAD, FLIGHT }

    static class PartDef {
        String name; float mass; float thrust; float fuel; int color; float w; float h;
        PartDef(String name, float mass, float thrust, float fuel, int color, float w, float h) {
            this.name = name; this.mass = mass; this.thrust = thrust; this.fuel = fuel; this.color = color; this.w = w; this.h = h;
        }
    }

    static class Part { PartDef def; Part(PartDef def) { this.def = def; } }
    static class Rocket { float x, y, vx, vy, angle, fuel, throttle; boolean launched; }
    interface Click { void run(); }
    static class GameButton { String label; RectF rect; Click click; GameButton(String label, RectF rect, Click click) { this.label = label; this.rect = rect; this.click = click; } }

    private boolean running;
    private Thread thread;
    private Scene scene = Scene.MENU;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint smallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<GameButton> buttons = new ArrayList<>();
    private final List<Part> parts = new ArrayList<>();
    private final PartDef[] catalog = new PartDef[] {
            new PartDef("Command Pod", 1.5f, 0f, 0f, Color.rgb(210,220,230), 70f, 60f),
            new PartDef("Fuel Tank", 2.0f, 0f, 80f, Color.rgb(240,180,70), 58f, 90f),
            new PartDef("Engine", 1.2f, 45f, 0f, Color.rgb(170,170,180), 66f, 50f),
            new PartDef("Booster", 2.4f, 70f, 50f, Color.rgb(220,90,70), 44f, 120f)
    };
    private int selectedPart = 0;
    private boolean lighting = true;
    private boolean shadows = true;
    private int quality = 2;
    private final Rocket rocket = new Rocket();
    private long lastTime = System.nanoTime();

    public GameView(Context context) {
        super(context);
        getHolder().addCallback(this);
        setFocusable(true);
        titlePaint.setColor(Color.WHITE);
        titlePaint.setTextSize(46f);
        titlePaint.setTypeface(Typeface.DEFAULT_BOLD);
        smallPaint.setColor(Color.WHITE);
        smallPaint.setTextSize(28f);
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
        if (scene != Scene.FLIGHT || !rocket.launched) return;
        float mass = Math.max(1f, rocketMass());
        float thrust = rocketThrust() * rocket.throttle;
        if (rocket.fuel <= 0f) rocket.throttle = 0f;
        float ax = (float)Math.sin(rocket.angle) * thrust / mass;
        float ay = -(float)Math.cos(rocket.angle) * thrust / mass + 9.81f;
        rocket.vx += ax * dt * 34f;
        rocket.vy += ay * dt * 34f;
        rocket.x += rocket.vx * dt;
        rocket.y += rocket.vy * dt;
        rocket.fuel = Math.max(0f, rocket.fuel - rocket.throttle * dt * 12f);
        if (rocket.y > getHeight() - 170f) { rocket.y = getHeight() - 170f; rocket.vy = 0f; }
    }

    private void drawFrame() {
        Canvas c = getHolder().lockCanvas();
        if (c == null) return;
        drawSky(c);
        buttons.clear();
        if (scene == Scene.MENU) drawMenu(c);
        else if (scene == Scene.SETTINGS) drawSettings(c);
        else if (scene == Scene.VAB) drawVab(c);
        else if (scene == Scene.PAD) drawPad(c, false);
        else drawPad(c, true);
        getHolder().unlockCanvasAndPost(c);
    }

    private void drawSky(Canvas c) {
        paint.setShader(new LinearGradient(0, 0, 0, getHeight(), Color.rgb(10,16,38), Color.rgb(20,42,66), Shader.TileMode.CLAMP));
        c.drawRect(0, 0, getWidth(), getHeight(), paint);
        paint.setShader(null);
        paint.setColor(Color.WHITE);
        int safeW = Math.max(1, getWidth());
        int safeH = Math.max(1, getHeight() / 2);
        for (int i = 0; i < 45; i++) c.drawCircle((i * 83) % safeW, (i * 191) % safeH, (i % 3) + 1, paint);
        if (lighting) {
            paint.setShader(new RadialGradient(getWidth() * .75f, getHeight() * .16f, 260f, Color.argb(100,255,230,150), Color.TRANSPARENT, Shader.TileMode.CLAMP));
            c.drawCircle(getWidth() * .75f, getHeight() * .16f, 260f, paint);
            paint.setShader(null);
        }
    }

    private void drawMenu(Canvas c) {
        title(c, "ORBIT FOUNDRY", "Native mobile rocket sandbox");
        button(c, "Vehicle Assembly", .5f, .42f, () -> scene = Scene.VAB);
        button(c, "Launch Pad", .5f, .55f, () -> { prepareLaunch(); scene = Scene.PAD; });
        button(c, "Settings", .5f, .68f, () -> scene = Scene.SETTINGS);
    }

    private void drawSettings(Canvas c) {
        title(c, "SETTINGS", "Simple engine options");
        button(c, "Quality: " + quality, .5f, .38f, () -> quality = quality % 3 + 1);
        button(c, "Lighting: " + (lighting ? "On" : "Off"), .5f, .51f, () -> lighting = !lighting);
        button(c, "Shadows: " + (shadows ? "On" : "Off"), .5f, .64f, () -> shadows = !shadows);
        button(c, "Back", .5f, .80f, () -> scene = Scene.MENU);
    }

    private void drawVab(Canvas c) {
        drawBuilding(c);
        c.drawText("VAB - add parts, then launch", 24, 52, smallPaint);
        button(c, "Add " + catalog[selectedPart].name, .5f, .82f, () -> parts.add(new Part(catalog[selectedPart])));
        button(c, "Next Part", .25f, .91f, () -> selectedPart = (selectedPart + 1) % catalog.length);
        button(c, "Launch", .75f, .91f, () -> { prepareLaunch(); scene = Scene.PAD; });
        button(c, "Menu", .13f, .06f, () -> scene = Scene.MENU);
        drawRocket(c, getWidth() / 2f, getHeight() * .55f);
        c.drawText("Mass " + one(rocketMass()) + "t  Thrust " + (int)rocketThrust() + "  Fuel " + (int)rocketFuel(), 26, getHeight() - 170, smallPaint);
    }

    private void drawPad(Canvas c, boolean flight) {
        drawGround(c);
        drawLaunchComplex(c);
        float baseX = flight ? rocket.x : getWidth() / 2f;
        float baseY = flight ? rocket.y : getHeight() - 170f;
        drawRocket(c, baseX, baseY);
        if (flight && rocket.throttle > 0f && rocket.fuel > 0f) drawFlame(c, baseX, baseY);
        if (!flight) {
            c.drawText("Launch Pad", 30, 52, titlePaint);
            button(c, "Ignite / Launch", .5f, .88f, () -> { scene = Scene.FLIGHT; rocket.launched = true; rocket.throttle = 1f; });
            button(c, "Back to VAB", .22f, .77f, () -> scene = Scene.VAB);
        } else {
            int alt = (int)Math.max(0f, getHeight() - 170f - rocket.y);
            c.drawText("Altitude " + alt + "m   Fuel " + (int)rocket.fuel, 24, 52, smallPaint);
            button(c, "Throttle +", .25f, .90f, () -> rocket.throttle = Math.min(1f, rocket.throttle + .15f));
            button(c, "Throttle -", .75f, .90f, () -> rocket.throttle = Math.max(0f, rocket.throttle - .15f));
            button(c, "Reset", .5f, .79f, () -> { prepareLaunch(); scene = Scene.PAD; });
        }
    }

    private void drawBuilding(Canvas c) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(38,42,54)); c.drawRect(getWidth()*.14f, getHeight()*.13f, getWidth()*.86f, getHeight()*.78f, paint);
        paint.setColor(Color.rgb(58,64,78)); for (int i=0;i<=6;i++) c.drawRect(getWidth()*.18f+i*70, getHeight()*.18f, getWidth()*.21f+i*70, getHeight()*.72f, paint);
        paint.setColor(Color.rgb(98,110,126)); c.drawRect(getWidth()*.34f, getHeight()*.28f, getWidth()*.66f, getHeight()*.78f, paint);
    }

    private void drawGround(Canvas c) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(62,70,56)); c.drawRect(0, getHeight()-140, getWidth(), getHeight(), paint);
        paint.setColor(Color.rgb(100,98,86)); c.drawRect(getWidth()*.28f, getHeight()-170, getWidth()*.72f, getHeight()-130, paint);
    }

    private void drawLaunchComplex(Canvas c) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(82,88,96));
        c.drawRect(getWidth()*.16f, getHeight()*.42f, getWidth()*.24f, getHeight()-160, paint);
        c.drawRect(getWidth()*.12f, getHeight()*.42f, getWidth()*.28f, getHeight()*.46f, paint);
        paint.setStrokeWidth(6f); paint.setStyle(Paint.Style.STROKE); paint.setColor(Color.rgb(120,130,140));
        for (int i=0;i<=5;i++) c.drawLine(getWidth()*.16f, getHeight()*.46f+i*75, getWidth()*.24f, getHeight()*.50f+i*75, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawRocket(Canvas c, float cx, float baseY) {
        if (parts.isEmpty()) return;
        float y = baseY;
        for (int i = parts.size()-1; i >= 0; i--) {
            PartDef d = parts.get(i).def;
            if (shadows) { paint.setColor(Color.argb(90,0,0,0)); c.drawRoundRect(cx-d.w/2+9, y-d.h+9, cx+d.w/2+9, y+9, 10,10,paint); }
            paint.setColor(d.color); c.drawRoundRect(cx-d.w/2, y-d.h, cx+d.w/2, y, 10,10,paint);
            paint.setColor(Color.argb(60,255,255,255)); c.drawRect(cx-d.w/2+8, y-d.h+8, cx-d.w/2+18, y-8, paint);
            y -= d.h;
        }
    }

    private void drawFlame(Canvas c, float cx, float baseY) {
        paint.setColor(Color.rgb(255,180,40));
        Path p = new Path();
        p.moveTo(cx-28, baseY); p.lineTo(cx+28, baseY); p.lineTo(cx, baseY+90+(float)Math.sin(System.nanoTime()/100000000.0)*20); p.close();
        c.drawPath(p, paint);
        paint.setColor(Color.rgb(255,245,160)); c.drawCircle(cx, baseY+28, 20, paint);
    }

    private void title(Canvas c, String a, String b) { c.drawText(a, 40, getHeight()*.18f, titlePaint); c.drawText(b, 42, getHeight()*.23f, smallPaint); }

    private void button(Canvas c, String label, float xPct, float yPct, Click click) {
        float w = getWidth() * .56f, h = 72f;
        RectF r = new RectF(getWidth()*xPct-w/2, getHeight()*yPct-h/2, getWidth()*xPct+w/2, getHeight()*yPct+h/2);
        paint.setStyle(Paint.Style.FILL); paint.setColor(Color.rgb(34,54,78)); c.drawRoundRect(r,22,22,paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3f); paint.setColor(Color.rgb(120,180,230)); c.drawRoundRect(r,22,22,paint);
        paint.setStyle(Paint.Style.FILL); smallPaint.setTextAlign(Paint.Align.CENTER); c.drawText(label, r.centerX(), r.centerY()+10, smallPaint); smallPaint.setTextAlign(Paint.Align.LEFT);
        buttons.add(new GameButton(label, r, click));
    }

    private float rocketMass() { float v=0; for (Part p: parts) v += p.def.mass; return v; }
    private float rocketThrust() { float v=0; for (Part p: parts) v += p.def.thrust; return v; }
    private float rocketFuel() { float v=0; for (Part p: parts) v += p.def.fuel; return v; }
    private String one(float v) { return String.format(java.util.Locale.US, "%.1f", v); }
    private void prepareLaunch() {
        if (parts.isEmpty()) { parts.add(new Part(catalog[0])); parts.add(new Part(catalog[1])); parts.add(new Part(catalog[2])); }
        rocket.x = getWidth()/2f; rocket.y = getHeight()-170f; rocket.vx = 0; rocket.vy = 0; rocket.angle = 0; rocket.fuel = rocketFuel(); rocket.throttle = 0; rocket.launched = false;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            for (int i = buttons.size()-1; i >= 0; i--) {
                GameButton b = buttons.get(i);
                if (b.rect.contains(e.getX(), e.getY())) { b.click.run(); return true; }
            }
        }
        return true;
    }
}
