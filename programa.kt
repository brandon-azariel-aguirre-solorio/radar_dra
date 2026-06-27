import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import kotlin.math.pow

class RadarManager {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val bleScanner = bluetoothAdapter?.bluetoothLeScanner

    // Estructura idéntica para los pings del radar
    data class Dispositivo(
        val mac: String,
        val nombre: String,
        val tipo: String,
        var rssi: Int,
        var distancia: Double,
        val angulo: Double = Math.random() * 2 * Math.PI // Ángulo fijo por dispositivo
    )

    val listaDispositivos = mutableMapOf<String, Dispositivo>()

    fun iniciarEscaneo() {
        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val rssi = result.rssi
                val mac = device.address
                
                // 1. Calcular distancia usando la misma fórmula matemática
                val medidoRssi = -60
                val factorN = 2.6
                val distancia = 10.0.pow((medidoRssi - rssi) / (10.0 * factorN))

                // Solo nos interesan dispositivos dentro del rango de 3 metros
                if (distancia <= 3.0) {
                    val nombre = result.scanRecord?.deviceName ?: "Oculto"
                    
                    // 2. Extraer el Tipo de dispositivo analizando el Payload binario (Appearance)
                    val tipo = descubrirTipo(result)

                    // 3. Memoria y Filtro de estabilidad (Evitar saltos si el cambio es menor a 15cm)
                    val dispositivoExistente = listaDispositivos[mac]
                    if (dispositivoExistente == null || Math.abs(dispositivoExistente.distancia - distancia) > 0.15) {
                        val nuevoDispositivo = Dispositivo(
                            mac = mac,
                            nombre = nombre,
                            tipo = tipo,
                            rssi = rssi,
                            distancia = distancia,
                            angulo = dispositivoExistente?.angulo ?: (Math.random() * 2 * Math.PI)
                        )
                        listaDispositivos[mac] = nuevoDispositivo
                        
                        // Aquí disparas la actualización de tu interfaz gráfica del reloj
                    }
                }
            }
        }
        bleScanner?.startScan(scanCallback)
    }

    // Analiza los bytes puros comerciales de Bluetooth (idéntico a la función C++ del ESP32)
    private fun descubrirTipo(result: ScanResult): String {
        val scanRecord = result.scanRecord
        val name = scanRecord?.deviceName?.lowercase() ?: ""

        // Intentar leer por Appearance Data en las propiedades BLE estándar
        // En Android, muchas veces el sistema ya procesa parte del payload en device.bluetoothClass
        val deviceClass = result.device.bluetoothClass?.majorDeviceClass
        when (deviceClass) {
            android.bluetooth.BluetoothClass.Device.Major.PHONE -> return "📱 Celular"
            android.bluetooth.BluetoothClass.Device.Major.COMPUTER -> return "💻 Computadora"
            android.bluetooth.BluetoothClass.Device.Major.AUDIO_VIDEO -> return "🎧 Audio"
            android.bluetooth.BluetoothClass.Device.Major.WEARABLE -> return "⌚ Wearable"
        }

        // Filtro por palabras clave en el nombre si el chip oculta su clase
        return when {
            name.contains("audio") || name.contains("airpods") || name.contains("jbl") || name.contains("earbud") -> "🎧 Audio"
            name.contains("laptop") || name.contains("pc") || name.contains("lenovo") || name.contains("hp") -> "💻 Computadora"
            name.contains("phone") || name.contains("iphone") || name.contains("galaxy") -> "📱 Celular"
            name.contains("watch") || name.contains("band") -> "⌚ Wearable"
            else -> "📡 Desconocido"
        }
    }
}
