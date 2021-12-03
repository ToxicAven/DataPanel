import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.ShutdownEvent
import com.lambda.client.module.Category
import com.lambda.client.module.ModuleManager
import com.lambda.client.module.modules.combat.AutoLog
import com.lambda.client.module.modules.movement.AutoWalk
import com.lambda.client.plugin.api.PluginModule
import com.lambda.client.process.PauseProcess
import com.lambda.client.util.BaritoneUtils
import com.lambda.client.util.CircularArray
import com.lambda.client.util.InfoCalculator
import com.lambda.client.util.InfoCalculator.speed
import com.lambda.client.util.TpsCalculator
import com.lambda.client.util.math.Direction
import com.lambda.client.util.math.RotationUtils
import com.lambda.client.util.threads.safeListener
import com.lambda.commons.utils.MathUtils
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiMainMenu
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.awt.Color
import java.awt.Font
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.Graphics
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.text.SimpleDateFormat
import java.util.Date
import javax.swing.JFrame
import javax.swing.JPanel
import kotlin.collections.ArrayList
import java.util.ArrayDeque


internal object DataPanel: PluginModule(
    name = "DataPanel",
    category = Category.CLIENT,
    description = "Adds a second window to the screen containing at-a-glance data.",
    pluginMain = DataPanelPlugin
) {
    var window: SecondScreenFrame? = null
    private val speedList = ArrayDeque<Double>()

    private val username by setting("Username", true)
    private val autoLogWarn by setting("AutoLog", true)
    private val coords by setting("Coords", true)
    private val inlineCoords by setting("Inline Coords", true)
    private val fps by setting("FPS", true)
    private val tps by setting("TPS", true)
    private val ping by setting("Ping", true)
    private val pitchYaw by setting("Pitch / Yaw", true)
    private val speedRender by setting("Speed", true)
    private val speedUnit by setting("Speed Unit", SpeedUnit.MPS)
    private val clock by setting("Clock", true)
    private val dimension by setting("Dimension", true)
    private val biome by setting("Biome", true)
    private val direction by setting("Direction", true)
    private val modules by setting("Modules", true)
    private val baritone by setting("Baritone Info", true)


    init {
        onEnable {
            if (window == null) {
                window = SecondScreenFrame
            }
            window!!.isVisible = true
        }

        onDisable {
            if (this.window != null) {
                window!!.isVisible = false
            }
            this.window = null
        }

        safeListener<ShutdownEvent> {
            disable()
        }

        safeListener<TickEvent.ClientTickEvent> {
            if (mc.currentScreen is GuiMainMenu || mc.player == null || mc.world == null) {
                disable()
                window!!.isVisible = false
                window = null
                return@safeListener
            }

            if (window != null) {
                val drawInfo = ArrayList<String>()

                if (username) drawInfo.add("Username: ${mc.player.name}")

                if (autoLogWarn) {
                    if (AutoLog.isDisabled) {
                        drawInfo.add("")
                        drawInfo.add("AUTOLOG DISABLED")
                        drawInfo.add("")
                    }
                }

                if (coords) {
                    val inHell: Boolean = mc.player?.dimension == -1
                    val nether = if (!inHell) 0.125f else 8.0f
                    if (!inlineCoords) {
                        val coordinates = "XYZ: " + "${"%.2f".format(mc.player.posX)}, ${"%.2f".format(mc.player.posY)}, ${"%.2f".format(mc.player.posZ)} [${"%.2f".format((mc.player.posX * nether.toDouble()))}, ${"%.2f".format((mc.player.posZ * nether.toDouble()))}]"
                        drawInfo.add(coordinates)
                    } else {
                        val x = "X: ${"%.2f".format(mc.player.posX)} [${"%.2f".format((mc.player.posX * nether.toDouble()))}]"
                        val y = "Y: ${"%.2f".format(mc.player.posY)}"
                        val z = "Z: ${"%.2f".format(mc.player.posZ)} [${"%.2f".format((mc.player.posZ * nether.toDouble()))}]"
                        for (i in arrayOf(x, y, z)) {
                            drawInfo.add(i)
                        }
                    }
                }

                if (fps) drawInfo.add("Fps: ${Minecraft.getDebugFPS()}")

                if (tps) {
                    val tpsBuffer = CircularArray(120, 20.0f)
                    tpsBuffer.add(TpsCalculator.tickRate)
                    drawInfo.add("TPS: " + "%.2f".format(tpsBuffer.average()))
                }

                if (ping) drawInfo.add("Ping: ${InfoCalculator.ping()} ms")

                if (pitchYaw) {
                    val yaw = MathUtils.round(RotationUtils.normalizeAngle(mc.player?.rotationYaw ?: 0.0f), 1)
                    val pitch = MathUtils.round(mc.player?.rotationPitch ?: 0.0f, 1)
                    drawInfo.add("Yaw: $yaw / Pitch: $pitch" )
                }

                if (speedRender) {
                    updateSpeedList()
                    var averageSpeed = if (speedList.isEmpty()) 0.0 else speedList.sum() / speedList.size
                    averageSpeed *= speedUnit.multiplier
                    averageSpeed = MathUtils.round(averageSpeed, 2)
                    drawInfo.add("${"%.2f".format(averageSpeed)} ${speedUnit.displayName}")
                }

                if (clock) drawInfo.add("Time: " + SimpleDateFormat("h:mm a").format(Date()))

                if (dimension) drawInfo.add("Dimension: ${InfoCalculator.dimension()}")

                if (biome) {
                    val biome = mc.world.getBiome(mc.player.position).biomeName
                    if (biome != "Hell") {
                        drawInfo.add("Biome: $biome")
                    }
                }

                if (direction) {
                    val entity = mc.renderViewEntity ?: player
                    val direction = Direction.fromEntity(entity)
                    drawInfo.add("Direction: ${direction.displayName} [${direction.displayNameXY}]")
                }

                if (modules) {
                    val array : MutableList<String> = mutableListOf()
                    ModuleManager.modules.forEach {
                        if (it.isEnabled && it.isVisible) {
                            array.add(it.name)
                        }
                    }
                    drawInfo.add("${array.size} Modules enabled")
                }

                if (baritone) {
                    val process = BaritoneUtils.primary?.pathingControlManager?.mostRecentInControl()?.orElse(null)

                    if (process != null) {
                        when {
                            process == PauseProcess -> {
                                drawInfo.add(process.displayName0())
                            }
                            AutoWalk.baritoneWalk -> {
                                drawInfo.add("AutoWalk (${AutoWalk.direction.displayName})")
                            }
                            process.displayName0().lowercase().contains("trombone") -> {
                                drawInfo.add("HighwayTools Active!")
                            }
                            else -> {
                                drawInfo.add("Process: ${process.displayName()}")
                            }
                        }
                    }
                }

                window!!.setToDraw(drawInfo)
            } else {
                disable()
            }
        }
    }
    private fun SafeClientEvent.updateSpeedList() {
        val speed = speed()

        if (speed > 0.0 || mc.player.ticksExisted % 4 == 0) {
            speedList.add(speed) // Only adding it every 4 ticks if speed is 0
        } else {
            speedList.pollFirst()
        }

        while (speedList.size > 10) speedList.pollFirst()
    }

    @Suppress("UNUSED")
    private enum class SpeedUnit(val displayName: String, val multiplier: Double) {
        MPS("m/s", 1.0),
        KMH("km/h", 3.6),
        MPH("mph", 2.237) // Monkey Americans
    }
}




object SecondScreen : JPanel() {
    private var toDraw: ArrayList<String>
    fun setToDraw(list: ArrayList<String>) {
        toDraw = list
        this.repaint()
    }

    init {
        this.font = Font("Verdana", 0, 20)
        toDraw = ArrayList()
        initBoard()
    }

    private fun initBoard() {
        background = Color.BLACK
        this.isFocusable = true
        this.preferredSize = Dimension(600, 400)
    }

    public override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        drawScreen(g)
    }

    private fun drawScreen(g: Graphics) {
        val small: Font = this.font
        val metrics = getFontMetrics(small)
        g.color = Color.white
        g.font = small
        var y = 40
        for (msg in toDraw) {
            g.drawString(msg, (width - metrics.stringWidth(msg)) / 2, y)
            y += 20
        }
        Toolkit.getDefaultToolkit().sync()
    }
}



object SecondScreenFrame : JFrame() {
    private var panel: SecondScreen? = null

    init {
        initUI()
    }

    private fun initUI() {
        panel = SecondScreen
        this.add(panel)
        this.isResizable = true
        pack()
        title = "Lambda DataWindow"
        setLocationRelativeTo(null)
        defaultCloseOperation = HIDE_ON_CLOSE
        this.addWindowListener(object : WindowAdapter() {
            override fun windowClosing(windowEvent: WindowEvent) {
                DataPanel.disable()
            }
        })
    }

    fun setToDraw(list: ArrayList<String>) {
        panel!!.setToDraw(list)
    }
}