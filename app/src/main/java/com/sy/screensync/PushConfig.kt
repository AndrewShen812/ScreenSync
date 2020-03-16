package com.sy.screensync

import android.util.Log

/**
 *
 * @author ShenYong
 * @date 2020/3/16
 */
object PushConfig {
    var serverIp = ""
    var serverPort = ""
    var streamName = ""

    fun parseUrl(url: String) {
        serverIp = getIp(url)
        serverPort = getPort(url)
        streamName = getName(url)
        Log.d("PushConfig", "serverIp:$serverIp")
        Log.d("PushConfig", "serverPort:$serverPort")
        Log.d("PushConfig", "streamName:$streamName")
    }

    fun getIp(url: String): String {
        try {
            val path = url.replace("rtsp://", "")
            val arr = path.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

            val ip = arr[0]

            val arr1 = arr[1].split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val port = arr1[0]
            val id = arr1[1]

            return ip
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return ""
    }

    fun getPort(url: String): String {
        try {
            val path = url.replace("rtsp://", "")
            val arr = path.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

            val ip = arr[0]

            val arr1 = arr[1].split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val port = arr1[0]
            val id = arr1[1]

            return port
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return ""
    }

    fun getName(url: String): String {
        try {
            val path = url.replace("rtsp://", "")
            val arr = path.split(":".toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()

            val ip = arr[0]

            val arr1 = arr[1].split("/".toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()
            val port = arr1[0]

            return arr1[1]
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return ""
    }
}