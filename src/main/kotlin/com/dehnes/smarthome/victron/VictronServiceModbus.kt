package com.dehnes.smarthome.victron

import com.dehnes.smarthome.victron.VictronModbusRegisterType.*
import com.ghgande.j2mod.modbus.facade.ModbusTCPMaster
import com.ghgande.j2mod.modbus.procimg.Register
import com.ghgande.j2mod.modbus.procimg.SimpleRegister
import java.nio.ByteBuffer

class VictronServiceModbus {

    val modbusTCPMaster = ModbusTCPMaster("192.168.1.18")

    init {
        modbusTCPMaster.connect()
    }

    fun writeRegister(reg: VictronModbusRegisters, value: Double) {

        val registers: Array<Register> = when (reg.type) {
            int16 -> {
                val b = ByteBuffer.allocate(2)
                b.putShort(0, (value / reg.scale).toInt().toShort())
                val a = b.array()
                arrayOf(SimpleRegister(a[0], a[1]))
            }

            uint16 -> {
                val elements = if (value < 0) {
                    SimpleRegister(-1)
                } else {
                    SimpleRegister((value / reg.scale).toInt())
                }
                arrayOf(elements)
            }

            else -> error("Unsupported ${reg.type}")
        }


        modbusTCPMaster.writeMultipleRegisters(
            100,
            reg.addr,
            registers
        )
    }

    fun readRegister(reg: VictronModbusRegisters): Any {
        val readInputRegisters = modbusTCPMaster.readInputRegisters(
            100,
            reg.addr,
            reg.type.bytes / 2
        )

        val value = when (reg.type) {
            string12 -> readInputRegisters.fold("") { acc, inputRegister ->
                acc + Charsets.UTF_8.decode(ByteBuffer.wrap(inputRegister.toBytes())).toString()
            }

            uint16 -> readInputRegisters[0].toUnsignedShort().toDouble()
            int16 -> ByteBuffer.wrap(readInputRegisters[0].toBytes()).getShort(0).toDouble()
            int32 -> {
                val byteArray = ByteArray(4)
                var toBytes = readInputRegisters[0].toBytes()
                byteArray[0] = toBytes[0]
                byteArray[1] = toBytes[1]
                toBytes = readInputRegisters[1].toBytes()
                byteArray[2] = toBytes[0]
                byteArray[3] = toBytes[1]
                ByteBuffer.wrap(byteArray).getInt(0).toDouble()
            }

            else -> error("Unsupported ${reg.type}")
        }

        return if (value is String) value else {
            (value as Double) * reg.scale
        }
    }

}

enum class VictronModbusRegisters(
    val addr: Int,
    val type: VictronModbusRegisterType,
    val scale: Double = 1.0
) {
    Serial(800, string12),
    BatterySoC(843, uint16),
    BatteryCurrent(841, int16, 0.1),
    BatteryPower(842, int16), // /Dc/Battery/Power
    BatteryVoltage(840, uint16, 0.1), // /Dc/Battery/Voltage

    GridCurrentLimit(22, int16, 0.1),

    GridL1Voltage(3, uint16, 0.1),
    GridL2Voltage(4, uint16, 0.1),
    GridL3Voltage(5, uint16, 0.1),
    GridL1Current(6, int16, 0.1),
    GridL2Current(7, int16, 0.1),
    GridL3Current(8, int16, 0.1),
    GridL1Freq(9, int16, 0.01),
    GridL2Freq(10, int16, 0.01),
    GridL3Freq(11, int16, 0.01),
    GridL1Power(12, int16, 10.0),
    GridL2Power(13, int16, 10.0),
    GridL3Power(14, int16, 10.0),

    OutputL1Voltage(15, uint16, 0.1),
    OutputL2Voltage(16, uint16, 0.1),
    OutputL3Voltage(17, uint16, 0.1),
    OutputL1Current(18, int16, 0.1),
    OutputL2Current(19, int16, 0.1),
    OutputL3Current(20, int16, 0.1),
    OutputL1Freq(21, int16, 0.01),
    //OutputL2Freq(?, int16, 0.01), // TODO
    //OutputL3Freq(?, int16, 0.01), // TODO
    OutputL1Power(23, int16, 10.0),
    OutputL2Power(24, int16, 10.0),
    OutputL3Power(25, int16, 10.0),

    ESSMode(2902, uint16), // 1=ESS with Phase Compensation;2=ESS without phase compensation;3=Disabled/External Control

    ESSMode2AcPowerSetPoint(2700, int16), // ESS Mode 2 - Setpoint for the ESS control-loop in the CCGX. The control-loop will increase/decrease the Multi charge/discharge power to get the grid reading to this setpoint
    ESSMode2MaxChargePercentage(2701, uint16), // ESS Mode 2 - Max charge current for ESS control-loop. The control-loop will use this value to limit the multi power setpoint. For DVCC, use 2705 instead.
    ESSMode2AcPowerSetPointLargeScale(2703, int16, 100.0), // ESS Mode 2 – Same as 2700, but with a different scale factor. Meant for values larger than +-32kW.
    ESSMode2MaxDischargePower(2704, uint16, 10.0), // ESS Mode 2 – similar to 2702, but as an absolute value instead of a percentage.
    ESSMode2MaxChargeCurrent(2705, int16), // ESS Mode 2 with DVCC – Maximum system charge current. -1 Disables.
    ESSMode2MaxFeedInPower(2706, int16, 100.0), // -1: No limit, >=0: limited system feed-in. Applies to DC-coupled and AC-coupled feed-in.
    ESSMode2OvervoltageFeedIn(2707, int16), // 0=Don’t feed excess DC-tied PV into grid; 1=Feed excess DC-tied PV into the grid, Also known as Overvoltage Feed-in
    ESSMode2PreventFeedback(2708, int16), // 0=Feed excess AC-tied PV into grid; 1=Don’t feed excess AC-tied PV into the grid. Formerly  called Fronius Zero-Feedin
    ESSMode2PvPowerLimiterActive(2709, int16), // 0=Feed-in limiting is inactive; 1=Feed-in limiting is active. Applies to both AC-coupled and DC-coupled limiting

    ESSMode3AcPowerSetpointL1(37, int16), // ESS Mode 3 - Instructs the multi to charge/discharge with giving power. Negative = discharge. Used by the control loop in grid-parallel systems.
    ESSMode3AcPowerSetpointL2(40, int16),
    ESSMode3AcPowerSetpointL3(41, int16),
    ESSMode3DisableCharge(38, uint16), // ESS Mode 3 - Enables/Disables charge (0=enabled, 1=disabled). Note that power setpoint will yield to this setting
    ESSMode3DisableFeedIn(39, uint16), // ESS Mode 3 - Enables/Disables feedback (0=enabled, 1=disabled). Note that power setpoint will yield to this setting
    ESSMode3DoNotFeedInOvervoltage(65, uint16), // 0=Feed in overvoltage;1=Do not feed in overvoltage
    ESSMode3MaxFeedInPowerL1(66, uint16, 100.0), //
    ESSMode3MaxFeedInPowerL2(67, uint16, 100.0), //
    ESSMode3MaxFeedInPowerL3(68, uint16, 100.0), //
    ESSMode3TargetPowerIsMaxFeedIn(71, uint16), // 0=AcPowerSetpoint interpreted normally; 1=AcPowerSetpoint is OvervoltageFeedIn limit. When set to 1, the Multi behaves as if DoNotFeedInOvervoltage is disabled and AcPowerSetpoint is the maximum allowed feed-in
    ESSMode3FixSolarOffsetTo100mV(72, uint16), // 0=OvervoltageFeedIn uses 1V offset; 1=OvervoltageFeedIn uses 0.1V offset. When feeding overvoltage into the grid, the solar chargers are set to a higher voltage. This flag determines the size of the offset (per 12V increment).


    StateA(4530, uint16),
    StateB(844, uint16), // 0=idle;1=charging;2=discharging
    StateC(31, uint16),
    StateD(1282, uint16),
    StateE(2318, uint16),
    StateF(2900, uint16),
    StateG(3128, uint16),

}

enum class VictronModbusRegisterType(
    val bytes: Int,
) {
    int16(2),
    int32(4),

    uint16(2),
    uint32(4),

    string12(12),
}

fun main() {
    val victronServiceModbus = VictronServiceModbus()

    victronServiceModbus.writeRegister(VictronModbusRegisters.ESSMode2AcPowerSetPoint, 8000.0)
    victronServiceModbus.writeRegister(VictronModbusRegisters.ESSMode2MaxChargeCurrent, -1.0)
    victronServiceModbus.writeRegister(VictronModbusRegisters.ESSMode2MaxChargePercentage, 100.0)
    victronServiceModbus.writeRegister(VictronModbusRegisters.ESSMode2MaxDischargePower, -1.0)

    while (true) {
        println()
        println("ESSMode2AcPowerSetPoint: " + victronServiceModbus.readRegister(VictronModbusRegisters.ESSMode2AcPowerSetPoint))
        println("ESSMode2MaxChargeCurrent: " + victronServiceModbus.readRegister(VictronModbusRegisters.ESSMode2MaxChargeCurrent))
        println("ESSMode2MaxChargePercentage: " + victronServiceModbus.readRegister(VictronModbusRegisters.ESSMode2MaxChargePercentage))
        println("ESSMode2MaxDischargePower: " + victronServiceModbus.readRegister(VictronModbusRegisters.ESSMode2MaxDischargePower))
        Thread.sleep(5000)
    }



    victronServiceModbus.modbusTCPMaster.disconnect()
}