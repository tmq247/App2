package dev.yourapp.blem3
import java.util.*

object GattIds {
    val SVC_HID: UUID = UUID.fromString("00001812-0000-1000-8000-00805f9b34fb")
    val CHR_REPORT: UUID = UUID.fromString("00002a4d-0000-1000-8000-00805f9b34fb")
    val CHR_PROTOCOL_MODE: UUID = UUID.fromString("00002a4e-0000-1000-8000-00805f9b34fb")
    // nếu cần thêm: MAP(2A4B/2A4A/2A4C)…
}
