package com.sy.screensync

import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import org.easydarwin.push.EasyPusher
import org.easydarwin.push.Pusher
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor


/**
 *
 * @author ShenYong
 * @date 2020/3/16
 */
class RecordService : Service() {
    companion object{
        private const val TAG = "RecordService"
        private const val SCREEN_SCALE = 0.5f
        const val MSG_START = 1001
        const val MSG_END = 1002
        var permResultCode: Int = Activity.RESULT_CANCELED
        var permResultData: Intent? = null
    }
    // 录屏相关
    private lateinit var mpManager: MediaProjectionManager
    private var mediaProj: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null

    private var mediaCodeC: MediaCodec? = null
    private var surface: Surface? = null
    // 屏幕相关
    private lateinit var windowManager: WindowManager
    private var screenW = 0
    private var screenH = 0
    private var displayMetrics = DisplayMetrics()
    // 推送任务相关
    private var executor = Executors.newFixedThreadPool(1) as ThreadPoolExecutor
    private var isPushing = false
    private lateinit var easyPusher: Pusher
    private val handler = Handler()

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        mpManager = applicationContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        Log.d(TAG, "initSize")
        initSize()
        Log.d(TAG, "initMediaCoder")
        initMediaCoder()
        Log.d(TAG, "startRecord")
        startRecord()
        Log.d(TAG, "startPush")
        startPush()
    }

    private fun initSize() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val size = Point()
        windowManager.defaultDisplay.getSize(size)
        screenW = size.x
        screenH = size.y
        windowManager.defaultDisplay.getMetrics(displayMetrics)

        screenW = (screenW * SCREEN_SCALE).toInt()
        screenH = (screenH * SCREEN_SCALE).toInt()
    }

    private fun initMediaCoder() {
        val mediaFormat = MediaFormat.createVideoFormat("video/avc", screenW, screenH)
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 1200000)
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 25)
        mediaFormat.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
        )
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        mediaFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1)
        mediaFormat.setInteger(MediaFormat.KEY_CAPTURE_RATE, 25)
        mediaFormat.setInteger(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 1000000 / 25)
        try {
            mediaCodeC = MediaCodec.createEncoderByType("video/avc")
        } catch (e: IOException) {
            e.printStackTrace()
        }

        mediaCodeC?.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        surface = mediaCodeC?.createInputSurface()
        mediaCodeC?.start()
    }

    private fun startRecord() {
        if (mediaProj == null) {
            mediaProj = mpManager.getMediaProjection(permResultCode, permResultData)
        }
        if (mediaProj == null) {
            return
        }
        virtualDisplay = mediaProj!!.createVirtualDisplay(
            "screenCopy",
            screenW,
            screenH,
            displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR or DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION,
            // surface由MediaCodec创建，以此为媒介将录屏数据送入视频编码器
            surface,
            null,
            null)
    }

    private fun startPush() {
        isPushing = true
        executor.execute(pushTask)
    }

    private val pushTask = Runnable {
        if (mediaCodeC == null) {
            return@Runnable
        }
        val bufferInfo = MediaCodec.BufferInfo()
        val lastKeyFrameUS: Long = 0
        var lastRequestKeyFrameUS: Long = 0
        var pushBuffer = ByteArray(screenW * screenH)
        var mediaBuffer = ByteArray(bufferInfo.size)
        easyPusher = EasyPusher()
        easyPusher.initPush(applicationContext, null)
        easyPusher.setMediaInfo(
            Pusher.Codec.EASY_SDK_VIDEO_CODEC_H264,
            25,
            Pusher.Codec.EASY_SDK_AUDIO_CODEC_AAC,
            1,
            8000,
            16
        )
        easyPusher.start(PushConfig.serverIp, PushConfig.serverPort, PushConfig.streamName, Pusher.TransType.EASY_RTP_OVER_TCP)
        Log.d(TAG, "easyPusher.start")
        while (isPushing) {
            try {
                if (lastKeyFrameUS > 0 && SystemClock.elapsedRealtimeNanos() / 1000 - lastKeyFrameUS >= 3000000) {  // 3s no key frame.
                    if (SystemClock.elapsedRealtimeNanos() / 1000 - lastRequestKeyFrameUS >= 3000000) {
                        val p = Bundle()
                        p.putInt(PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
                        mediaCodeC!!.setParameters(p)
                        lastRequestKeyFrameUS = SystemClock.elapsedRealtimeNanos() / 1000
                    }
                }

                //得到成功编码后输出的out buffer Id
                val outputBufferId = mediaCodeC!!.dequeueOutputBuffer(bufferInfo, 0)
                if (outputBufferId >= 0) {
                    val outputBuffer = mediaCodeC!!.getOutputBuffer(outputBufferId)
                    val out = ByteArray(bufferInfo.size)
                    outputBuffer!!.get(out)
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                    var sync = false

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {// sps
                        sync = bufferInfo.flags and MediaCodec.BUFFER_FLAG_SYNC_FRAME != 0

                        if (!sync) {
                            val temp = ByteArray(bufferInfo.size)
                            outputBuffer.get(temp)
                            mediaBuffer = temp
                            continue
                        } else {
                            mediaBuffer = ByteArray(0)
                        }
                    }

                    sync = sync or (bufferInfo.flags and MediaCodec.BUFFER_FLAG_SYNC_FRAME != 0)
                    val len = mediaBuffer.size + bufferInfo.size

                    if (len > pushBuffer.size) {
                        pushBuffer = ByteArray(len)
                    }

                    if (sync) {
                        System.arraycopy(mediaBuffer, 0, pushBuffer, 0, mediaBuffer.size)
                        outputBuffer.get(pushBuffer, mediaBuffer.size, bufferInfo.size)
                        easyPusher.push(
                            pushBuffer,
                            0,
                            mediaBuffer.size + bufferInfo.size,
                            bufferInfo.presentationTimeUs / 1000,
                            2
                        )
                    } else {
                        outputBuffer.get(pushBuffer, 0, bufferInfo.size)
                        easyPusher.push(
                            pushBuffer,
                            0,
                            bufferInfo.size,
                            bufferInfo.presentationTimeUs / 1000,
                            1
                        )
                    }

                    //释放output buffer
                    mediaCodeC!!.releaseOutputBuffer(outputBufferId, false)
                }
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
        easyPusher.stop()
    }

    private fun stopPush() {
        isPushing = false
        executor.shutdown()
    }

    private fun release() {
        mediaCodeC?.stop()
        mediaCodeC?.release()
        mediaCodeC = null

        surface?.release()

        virtualDisplay?.release()
        virtualDisplay = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPush()
        release()
        mediaProj?.stop()
    }
}