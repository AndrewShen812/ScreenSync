package com.sy.screensync

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_MEDIA_PROJECTION = 1
    }

    private var isPushing = false
    private val handler = Handler(Handler.Callback {
        when (it.what) {
            RecordService.MSG_START -> {
                isPushing = true
                tv_rtsp_addr.text = "正在推送屏幕"
                btn_start.text = "stop"
            }
            RecordService.MSG_END -> {
                isPushing = false
                tv_rtsp_addr.text = "已停止"
                btn_start.text = "start"
            }
        }
        return@Callback false
    })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    fun onPush(view: View) {
        if (isPushing) {
            stopService(Intent(this, RecordService::class.java))
            return
        }
        val url = et_addr.text.toString().trim()
        if (!url.matches(Regex("^rtsp://.*:\\d{1,5}/.*$"))) {
            tv_rtsp_addr.text = "地址格式错误"
            Toast.makeText(this, "地址格式错误", Toast.LENGTH_SHORT).show()
            return
        }
        PushConfig.parseUrl(url)
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_MEDIA_PROJECTION && resultCode == RESULT_OK && data != null) {
            RecordService.permResultCode = REQUEST_MEDIA_PROJECTION
            RecordService.permResultData = data
            startService(Intent(this, RecordService::class.java))
        }
    }
}
