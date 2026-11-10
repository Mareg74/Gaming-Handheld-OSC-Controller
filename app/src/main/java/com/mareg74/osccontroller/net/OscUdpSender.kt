package com.mareg74.osccontroller.net

import com.mareg74.osccontroller.config.AppConfig
import com.mareg74.osccontroller.osc.OscCodec
import com.mareg74.osccontroller.osc.OscMessage
import com.mareg74.osccontroller.ui.MonitorLog
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors

class OscUdpSender(private val log: MonitorLog) {
    private val sendExecutor = Executors.newSingleThreadExecutor()

    fun send(config: AppConfig, msg: OscMessage): Boolean {
        if (!NetValidation.isValidIpv4(config.targetIp) || !NetValidation.isValidPort(config.targetPort)) {
            log.appendError("invalid target ${config.targetIp}:${config.targetPort}")
            return false
        }
        sendExecutor.execute {
            try {
                val data = OscCodec.encode(msg)
                DatagramSocket().use { socket ->
                    val addr = InetAddress.getByName(config.targetIp)
                    socket.send(DatagramPacket(data, data.size, addr, config.targetPort))
                }
                log.appendOut(msg.address, msg.args)
            } catch (e: Exception) {
                log.appendError("send ${e.message}")
            }
        }
        return true
    }
}
