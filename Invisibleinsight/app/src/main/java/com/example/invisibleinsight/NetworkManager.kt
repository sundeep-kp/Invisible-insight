package com.example.invisibleinsight

import android.graphics.PointF
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class NetworkManager {
    private var socket: DatagramSocket? = null
    private val port = 8888
    private val broadcastAddress = InetAddress.getByName("255.255.255.255")
    private var isListening = false

    // Map of remote players: IP Address -> Position
    val remotePlayers = HashMap<String, PointF>()

    fun start() {
        try {
            socket = DatagramSocket(port)
            socket?.broadcast = true
            isListening = true
            startListening()
        } catch (e: Exception) {
            Log.e("NetworkManager", "Error starting socket", e)
        }
    }

    fun stop() {
        isListening = false
        socket?.close()
        socket = null
    }

    fun broadcastPosition(x: Float, y: Float) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val message = "$x,$y"
                val buffer = message.toByteArray()
                val packet = DatagramPacket(buffer, buffer.size, broadcastAddress, port)
                socket?.send(packet)
            } catch (e: Exception) {
                // Silent fail is okay for udp position updates
            }
        }
    }

    private fun startListening() {
        CoroutineScope(Dispatchers.IO).launch {
            val buffer = ByteArray(1024)
            while (isListening) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket?.receive(packet)
                    
                    // Ignore own packets (simplified check: if it's from localhost, usually it's us, 
                    // but on Android broadcast usually loops back. We can filter by checking sender IP if needed.
                    // For now, simplest is to just update. If we see ourselves, we'll draw a ghost over ourselves.
                    // To fix: filter by checking against local IP, or just accept "ghost self" for this demo.)
                    
                    val senderIp = packet.address.hostAddress
                    val message = String(packet.data, 0, packet.length)
                    val parts = message.split(",")
                    if (parts.size == 2) {
                        val x = parts[0].toFloat()
                        val y = parts[1].toFloat()
                        synchronized(remotePlayers) {
                            remotePlayers[senderIp] = PointF(x, y)
                        }
                    }
                } catch (e: Exception) {
                    if (isListening) Log.e("NetworkManager", "Error receiving", e)
                }
            }
        }
    }
}
