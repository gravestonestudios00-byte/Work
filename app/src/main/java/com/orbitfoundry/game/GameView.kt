package com.orbitfoundry.game

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.*

enum class Scene { MENU, SETTINGS, VAB, PAD, FLIGHT }

data class Settings(var quality: Int = 2, var lighting: Boolean = true, var shadows: Boolean = true)
data class Button(val label: String, val rect: RectF, val action: () -> Unit)
data class PartDef(val name: String, val mass: Float, val thrust: Float, val fuel: Float, val color: Int, val w: Float, val h: Float)
data class Part(val def: PartDef, var x: Float, var y: Float)
data class RocketState(var x: Float, var y: Float, var vx: Float = 0f, var vy: Float = 0f, var angle: Float = 0f, var fuel: Float = 0f, var throttle: Float = 0f, var launched: Boolean = false)

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {
    private var running = false
    private var thread: Thread? = null
    private var scene = Scene.MENU
    private val settings = Settings()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 46f; typeface = Typeface.DEFAULT_BOLD }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 28f }
    private val buttons = mutableListOf<Button>()
    private val parts = mutableListOf<Part>()
    private val partCatalog = listOf(
        PartDef("Command Pod", 1.5f, 0f, 0f, Color.rgb(210,220,230), 70f, 60f),
        PartDef("Fuel Tank", 2.0f, 0f, 80f, Color.rgb(240,180,70), 58f, 90f),
        PartDef("Engine", 1.2f, 45f, 0f, Color.rgb(170,170,180), 66f, 50f),
        PartDef("Booster", 2.4f, 70f, 50f, Color.rgb(220,90,70), 44f, 120f)
    )
    private var selectedPart = 0
    private var rocket = RocketState(0f, 0f)
    private var lastTime = System.nanoTime()
    private var touchX = 0f
    private var touchY = 0f
    private var dragging = false

    init { holder.addCallback(this); isFocusable = true }

    override fun surfaceCreated(holder: SurfaceHolder) { running = true; thread = Thread(this); thread?.start() }
    override fun surfaceDestroyed(holder: SurfaceHolder) { running = false; thread?.join(800) }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun run() {
        while (running) {
            val now = System.nanoTime()
            val dt = ((now - lastTime) / 1_000_000_000f).coerceAtMost(0.033f)
            lastTime = now
            update(dt)
            drawFrame()
        }
    }

    private fun update(dt: Float) {
        if (scene != Scene.FLIGHT) return
        val mass = rocketMass().coerceAtLeast(1f)
        val thrust = rocketThrust() * rocket.throttle
        if (rocket.fuel <= 0f) rocket.throttle = 0f
        if (rocket.launched) {
            val ax = sin(rocket.angle) * thrust / mass
            val ay = -cos(rocket.angle) * thrust / mass + 9.81f
            rocket.vx += ax * dt * 34f
            rocket.vy += ay * dt * 34f
            rocket.x += rocket.vx * dt
            rocket.y += rocket.vy * dt
            rocket.fuel = (rocket.fuel - rocket.throttle * dt * 12f).coerceAtLeast(0f)
            if (rocket.y > height - 170f) { rocket.y = height - 170f; rocket.vy = 0f }
        }
    }

    private fun drawFrame() {
        val canvas = holder.lockCanvas() ?: return
        canvas.drawColor(Color.rgb(8, 12, 28))
        drawSky(canvas)
        buttons.clear()
        when (scene) {
            Scene.MENU -> drawMenu(canvas)
            Scene.SETTINGS -> drawSettings(canvas)
            Scene.VAB -> drawVab(canvas)
            Scene.PAD -> drawPad(canvas, false)
            Scene.FLIGHT -> drawPad(canvas, true)
        }
        holder.unlockCanvasAndPost(canvas)
    }

    private fun drawSky(c: Canvas) {
        val g = LinearGradient(0f, 0f, 0f, height.toFloat(), Color.rgb(10,16,38), Color.rgb(20,42,66), Shader.TileMode.CLAMP)
        paint.shader = g; c.drawRect(0f,0f,width.toFloat(),height.toFloat(),paint); paint.shader = null
        paint.color = Color.WHITE
        for (i in 0 until 45) c.drawCircle(((i * 83) % max(1,width)).toFloat(), ((i * 191) % max(1,height/2)).toFloat(), (i % 3 + 1).toFloat(), paint)
        if (settings.lighting) {
            paint.shader = RadialGradient(width*0.75f, height*0.16f, 260f, Color.argb(100,255,230,150), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            c.drawCircle(width*0.75f, height*0.16f, 260f, paint); paint.shader = null
        }
    }

    private fun drawMenu(c: Canvas) {
        title(c, "ORBIT FOUNDRY", "Native mobile rocket sandbox")
        button(c, "Vehicle Assembly", 0.5f, 0.42f) { scene = Scene.VAB }
        button(c, "Launch Pad", 0.5f, 0.55f) { prepareLaunch(); scene = Scene.PAD }
        button(c, "Settings", 0.5f, 0.68f) { scene = Scene.SETTINGS }
    }

    private fun drawSettings(c: Canvas) {
        title(c, "SETTINGS", "Simple engine options")
        button(c, "Quality: ${settings.quality}", 0.5f, 0.38f) { settings.quality = settings.quality % 3 + 1 }
        button(c, "Lighting: ${if(settings.lighting) "On" else "Off"}", 0.5f, 0.51f) { settings.lighting = !settings.lighting }
        button(c, "Shadows: ${if(settings.shadows) "On" else "Off"}", 0.5f, 0.64f) { settings.shadows = !settings.shadows }
        button(c, "Back", 0.5f, 0.80f) { scene = Scene.MENU }
    }

    private fun drawVab(c: Canvas) {
        drawBuilding(c)
        c.drawText("VAB - tap part buttons, drag rocket pieces", 24f, 52f, small)
        button(c, "Add ${partCatalog[selectedPart].name}", 0.5f, 0.82f) { addPart() }
        button(c, "Next Part", 0.25f, 0.91f) { selectedPart = (selectedPart + 1) % partCatalog.size }
        button(c, "Launch", 0.75f, 0.91f) { prepareLaunch(); scene = Scene.PAD }
        button(c, "Menu", 0.13f, 0.06f) { scene = Scene.MENU }
        drawRocketParts(c, width/2f, height*0.55f)
        c.drawText("Mass ${"%.1f".format(rocketMass())}t  Thrust ${rocketThrust().toInt()}  Fuel ${rocketFuel().toInt()}", 26f, height-170f, small)
    }

    private fun drawPad(c: Canvas, flight: Boolean) {
        drawGround(c)
        drawLaunchComplex(c)
        val baseX = if (flight) rocket.x else width/2f
        val baseY = if (flight) rocket.y else height-170f
        drawRocketParts(c, baseX, baseY)
        if (flight && rocket.throttle > 0f && rocket.fuel > 0f) drawFlame(c, baseX, baseY)
        if (!flight) {
            c.drawText("Launch Pad", 30f, 52f, text)
            button(c, "Ignite / Launch", 0.5f, 0.88f) { scene = Scene.FLIGHT; rocket.launched = true; rocket.throttle = 1f }
            button(c, "Back to VAB", 0.22f, 0.77f) { scene = Scene.VAB }
        } else {
            c.drawText("Altitude ${max(0f, height-170f-rocket.y).toInt()}m   Fuel ${rocket.fuel.toInt()}", 24f, 52f, small)
            button(c, "Throttle +", 0.25f, 0.90f) { rocket.throttle = (rocket.throttle + .15f).coerceAtMost(1f) }
            button(c, "Throttle -", 0.75f, 0.90f) { rocket.throttle = (rocket.throttle - .15f).coerceAtLeast(0f) }
            button(c, "Reset", 0.5f, 0.79f) { prepareLaunch(); scene = Scene.PAD }
        }
    }

    private fun drawBuilding(c: Canvas) {
        paint.color = Color.rgb(38,42,54); c.drawRect(width*.14f, height*.13f, width*.86f, height*.78f, paint)
        paint.color = Color.rgb(58,64,78); for (i in 0..6) c.drawRect(width*.18f+i*70, height*.18f, width*.21f+i*70, height*.72f, paint)
        paint.color = Color.rgb(98,110,126); c.drawRect(width*.34f, height*.28f, width*.66f, height*.78f, paint)
    }

    private fun drawGround(c: Canvas) {
        paint.color = Color.rgb(62,70,56); c.drawRect(0f, height-140f, width.toFloat(), height.toFloat(), paint)
        paint.color = Color.rgb(100,98,86); c.drawRect(width*.28f, height-170f, width*.72f, height-130f, paint)
    }

    private fun drawLaunchComplex(c: Canvas) {
        paint.color = Color.rgb(82,88,96)
        c.drawRect(width*.16f, height*.42f, width*.24f, height-160f, paint)
        c.drawRect(width*.12f, height*.42f, width*.28f, height*.46f, paint)
        paint.strokeWidth = 6f; paint.style = Paint.Style.STROKE; paint.color = Color.rgb(120,130,140)
        for (i in 0..5) c.drawLine(width*.16f, height*.46f+i*75, width*.24f, height*.50f+i*75, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawRocketParts(c: Canvas, cx: Float, baseY: Float) {
        var y = baseY
        parts.asReversed().forEach { p ->
            val d = p.def
            paint.color = if (settings.shadows) Color.argb(90,0,0,0) else Color.TRANSPARENT
            c.drawRoundRect(cx-d.w/2+9, y-d.h+9, cx+d.w/2+9, y+9, 10f,10f,paint)
            paint.color = d.color
            c.drawRoundRect(cx-d.w/2, y-d.h, cx+d.w/2, y, 10f, 10f, paint)
            paint.color = Color.argb(60,255,255,255); c.drawRect(cx-d.w/2+8, y-d.h+8, cx-d.w/2+18, y-8, paint)
            y -= d.h
        }
    }

    private fun drawFlame(c: Canvas, cx: Float, baseY: Float) {
        paint.color = Color.rgb(255,180,40)
        val path = Path(); path.moveTo(cx-28, baseY); path.lineTo(cx+28, baseY); path.lineTo(cx, baseY+90+sin(System.nanoTime()/1e8).toFloat()*20); path.close(); c.drawPath(path, paint)
        paint.color = Color.rgb(255,245,160); c.drawCircle(cx, baseY+28, 20f, paint)
    }

    private fun title(c: Canvas, a: String, b: String) { c.drawText(a, 40f, height*.18f, text); c.drawText(b, 42f, height*.23f, small) }
    private fun button(c: Canvas, label: String, xPct: Float, yPct: Float, action: () -> Unit) {
        val w = width * .56f; val h = 72f; val r = RectF(width*xPct-w/2, height*yPct-h/2, width*xPct+w/2, height*yPct+h/2)
        paint.color = Color.rgb(34,54,78); c.drawRoundRect(r, 22f,22f, paint)
        paint.color = Color.rgb(120,180,230); paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f; c.drawRoundRect(r,22f,22f,paint); paint.style = Paint.Style.FILL
        small.textAlign = Paint.Align.CENTER; c.drawText(label, r.centerX(), r.centerY()+10, small); small.textAlign = Paint.Align.LEFT
        buttons.add(Button(label, r, action))
    }

    private fun addPart() { val y = parts.sumOf { it.def.h.toDouble() }.toFloat(); parts.add(Part(partCatalog[selectedPart], 0f, y)) }
    private fun rocketMass() = parts.sumOf { it.def.mass.toDouble() }.toFloat()
    private fun rocketThrust() = parts.sumOf { it.def.thrust.toDouble() }.toFloat()
    private fun rocketFuel() = parts.sumOf { it.def.fuel.toDouble() }.toFloat()
    private fun prepareLaunch() { if (parts.isEmpty()) { parts.add(Part(partCatalog[0],0f,0f)); parts.add(Part(partCatalog[1],0f,0f)); parts.add(Part(partCatalog[2],0f,0f)) }; rocket = RocketState(width/2f, height-170f, fuel=rocketFuel()) }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        touchX = e.x; touchY = e.y
        if (e.action == MotionEvent.ACTION_DOWN) {
            buttons.lastOrNull { it.rect.contains(touchX, touchY) }?.let { it.action(); return true }
            dragging = scene == Scene.VAB
        }
        if (e.action == MotionEvent.ACTION_UP) dragging = false
        return true
    }
}
