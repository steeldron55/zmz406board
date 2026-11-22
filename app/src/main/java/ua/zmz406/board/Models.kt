package ua.zmz406.board

data class Telemetry(
    val rpm: Int,
    val coolant: Int,
    val voltage: Float,
    val fuelLph: Float,
    val dtc: List<String>
)

interface EcuSource {
    fun start(onData: (Telemetry) -> Unit, onStatus: (String) -> Unit, onConnected: () -> Unit)
    fun stop()
}
