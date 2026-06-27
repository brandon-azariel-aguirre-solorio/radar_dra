package com.example.radar_bt.presentation
import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

// Estructura de datos para las esferas detectadas
data class DispositivoBle(
    val mac: String,
    val nombre: String,
    val tipo: String,
    val distancia: Double,
    val angulo: Double
)

class MainActivity : ComponentActivity() {

    private var bluetoothLeScanner: BluetoothLeScanner? = null

    // Estado dinámico en Kotlin para avisarle a la interfaz gráfica cuando cambien los pings
    private val listaDispositivos = mutableStateMapOf<String, DispositivoBle>()

    // Lanzador automático para pedir los permisos al usuario de forma segura
    private val pedirPermisosLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permisos ->
        val todosConcedidos = permisos.values.all { it }
        if (todosConcedidos) {
            iniciarEscaneoBle()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar adaptadores nativos del reloj
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothLeScanner = bluetoothManager.adapter?.bluetoothLeScanner

        setContent {
            // Dibujar la interfaz en la pantalla redonda
            PantallaRadarDragon(dispositivos = listaDispositivos.values.toList())
        }

        verificarYPedirPermisos()
    }

    private fun verificarYPedirPermisos() {
        val permisosNecesarios = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }

        val faltanPermisos = permisosNecesarios.any {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (faltanPermisos) {
            pedirPermisosLauncher.launch(permisosNecesarios)
        } else {
            iniciarEscaneoBle()
        }
    }

    private fun iniciarEscaneoBle() {
        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val mac = result.device.address
                val rssi = result.rssi

                // --- MATEMÁTICAS RADAR DRAGÓN (Escala 3 metros) ---
                val medidoRssi = -60
                val factorN = 2.6
                val distanciaNueva = 10.0.pow((medidoRssi - rssi) / (10.0 * factorN))

                if (distanciaNueva <= 3.0) {
                    val nombre = result.scanRecord?.deviceName ?: "Oculto"
                    val tipo = descubrirTipoDispositivo(result)

                    val dispositivoExistente = listaDispositivos[mac]

                    // FILTRO DE ESTABILIDAD: Si cambia menos de 15cm, dejamos el punto congelado
                    if (dispositivoExistente == null || Math.abs(dispositivoExistente.distancia - distanciaNueva) > 0.15) {

                        // Si es nuevo asignamos un ángulo al azar, si ya existía mantenemos su ángulo fijo
                        val anguloFijo = dispositivoExistente?.angulo ?: (Math.random() * 2 * Math.PI)

                        listaDispositivos[mac] = DispositivoBle(
                            mac = mac,
                            nombre = nombre,
                            tipo = tipo,
                            distancia = distanciaNueva,
                            angulo = anguloFijo
                        )
                    }
                }
            }
        }

        // Verifica si tiene permisos antes de arrancar la antena (evita crashes)
        try {
            bluetoothLeScanner?.startScan(scanCallback)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
    private fun descubrirTipoDispositivo(result: ScanResult): String {
        val name = result.scanRecord?.deviceName?.lowercase() ?: ""

        // Envolvemos la lectura de la clase del dispositivo en un try-catch de seguridad
        val deviceClass = try {
            result.device.bluetoothClass?.majorDeviceClass
        } catch (e: SecurityException) {
            null // Si no hay permiso, devolvemos nulo de forma segura para que use el filtro de nombres
        }

        when (deviceClass) {
            android.bluetooth.BluetoothClass.Device.Major.PHONE -> return "📱"
            android.bluetooth.BluetoothClass.Device.Major.COMPUTER -> return "💻"
            android.bluetooth.BluetoothClass.Device.Major.AUDIO_VIDEO -> return "🎧"
        }

        return when {
            name.contains("audio") || name.contains("airpods") || name.contains("buds") || name.contains("jbl") -> "🎧"
            name.contains("pc") || name.contains("laptop") || name.contains("lenovo") || name.contains("hp") -> "💻"
            name.contains("phone") || name.contains("iphone") || name.contains("galaxy") -> "📱"
            name.contains("watch") || name.contains("band") -> "⌚"
            else -> "📡"
        }
    }
}

// --- LOGICA DE DISEÑO GRÁFICO (JETPACK COMPOSE) ---
@Composable
fun PantallaRadarDragon(dispositivos: List<DispositivoBle>) {
    // Animación infinita para la línea verde del radar (gira 360 grados cada 2.5s)
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val anguloLinea by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "giro"
    )

    val textMeasurer = rememberTextMeasurer()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A)),
        contentAlignment = Alignment.Center
    ) {
        // Lienzo para dibujar de forma nativa con el procesador gráfico del reloj
        Canvas(
            modifier = Modifier
                .size(180.dp) // Tamaño ideal para smartwatches circulares
                .clip(CircleShape)
                .background(Color(0xFF009432))
        ) {
            val centroX = size.width / 2
            val centroY = size.height / 2
            val radioMaximo = size.width / 2

            // 1. Dibujar la cuadrícula del radar (Líneas divisorias)
            drawCircle(color = Color(0x33000000), radius = radioMaximo * 0.66f, style = Stroke(width = 1f))
            drawCircle(color = Color(0x33000000), radius = radioMaximo * 0.33f, style = Stroke(width = 1f))
            drawLine(color = Color(0x33000000), start = Offset(0f, centroY), end = Offset(size.width, centroY))
            drawLine(color = Color(0x33000000), start = Offset(centroX, 0f), end = Offset(centroX, size.height))

            // 2. Dibujar la línea de escaneo verde giratoria
            val radianesLinea = Math.toRadians(anguloLinea.toDouble())
            val destinoX = centroX + radioMaximo * cos(radianesLinea).toFloat()
            val destinoY = centroY + radioMaximo * sin(radianesLinea).toFloat()
            drawLine(
                color = Color(0xFF00FF00),
                start = Offset(centroX, centroY),
                end = Offset(destinoX, destinoY),
                strokeWidth = 3f
            )

            // 3. Dibujar los dispositivos (Esferas) activos en rango
            dispositivos.forEach { dispositivo ->
                // Mapeo matemático: Proporción física según la distancia (Límite 3 metros)
                val r = (dispositivo.distancia / 3.0) * radioMaximo
                val x = centroX + r * cos(dispositivo.angulo)
                val y = centroY + r * sin(dispositivo.angulo)

                // Si está a menos de 30 cm se dibuja en Rojo parpadeante, de lo contrario Amarillo
                val esCerca = dispositivo.distancia <= 0.30
                val colorEsfera = if (esCerca) Color.Red else Color(0xFFFFEB3B)

                // Graficar el punto de la esfera
                drawCircle(
                    color = colorEsfera,
                    radius = 7f,
                    center = Offset(x.toFloat(), y.toFloat())
                )

                // Renderizar la etiqueta de texto (Emoji + Distancia) al lado de la esfera
                val textoEtiqueta = if (esCerca) {
                    "${dispositivo.tipo} ${Math.round(dispositivo.distancia * 100)} cm"
                } else {
                    "${dispositivo.tipo} ${String.format("%.2fm", dispositivo.distancia)}"
                }

                drawText(
                    textMeasurer = textMeasurer,
                    text = textoEtiqueta,
                    style = TextStyle(color = Color.White, fontSize = 9.sp),
                    topLeft = Offset(x.toFloat() + 10f, y.toFloat() - 15f)
                )
            }

            // 4. Nodo central fijo (La flecha roja de tu propia ubicación)
            drawCircle(color = Color.Red, radius = 5f, center = Offset(centroX, centroY))
        }
    }
}
