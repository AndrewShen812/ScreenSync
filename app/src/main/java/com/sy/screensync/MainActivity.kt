package com.sy.screensync

import android.annotation.SuppressLint
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import io.reactivex.Single
import io.reactivex.functions.Consumer
import kotlinx.android.synthetic.main.activity_main.*
import org.easydarwin.push.MediaStream

/**
 *  测试demo，借助EasyPusher库实时录制手机屏幕，发送到测试用的rtsp流服务器
 * @author ShenYong
 * @date 2020/3/16
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_MEDIA_PROJECTION = 1
    }

    private var mediaStream: MediaStream? = null

    private fun getMediaStream(): Single<MediaStream> {
        val single = RxHelper.single(MediaStream.getBindedMediaStream(this, this), mediaStream)
        return if (mediaStream == null) {
            single.doOnSuccess { ms -> mediaStream = ms }
        } else {
            single
        }
    }

    @SuppressLint("CheckResult")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val intent = Intent(this, MediaStream::class.java)
        startService(intent)

        getMediaStream().subscribe(
            { ms ->
                ms.observePushingState(
                    this@MainActivity,
                    Observer<MediaStream.PushingState> { pushingState ->
                        if (pushingState.screenPushing) {
                            tv_push_state.setText("屏幕推送")

                            // 更改屏幕推送按钮状态.

                            if (ms.isScreenPushing) {
                                btn_start.text = "stop"
                            } else {
                                btn_start.text = "start"
                            }
                            btn_start.isEnabled = true
                        }
                    })
            },
            {
                Toast.makeText(this@MainActivity, "创建服务出错!", Toast.LENGTH_SHORT).show()
            })
    }

    @SuppressLint("CheckResult")
    fun onPush(view: View) {
        getMediaStream().subscribe(Consumer { mediaStream ->
            if (mediaStream.isScreenPushing) {
                // 取消推送。
                mediaStream.stopPushScreen()
            } else {
                val url = et_addr.text.toString().trim()
                if (!url.matches(Regex("^rtsp://.*:\\d{1,5}/.*$"))) {
                    tv_push_state.text = "地址格式错误"
                    Toast.makeText(this, "地址格式错误", Toast.LENGTH_SHORT).show()
                    return@Consumer
                }
                PushConfig.parseUrl(url)
                val mMpMngr = applicationContext.getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                // 申请授权，启动推送。
                startActivityForResult( mMpMngr.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION)
                // 防止点多次.
                btn_start.isEnabled = false
            }
        })
    }

    @SuppressLint("CheckResult")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_MEDIA_PROJECTION && resultCode == RESULT_OK && data != null) {
            ScreenPushService.permResultCode = REQUEST_MEDIA_PROJECTION
            ScreenPushService.permResultData = data

            getMediaStream().subscribe { mediaStream ->
                mediaStream.pushScreen(
                    resultCode,
                    data,
                    PushConfig.serverIp,
                    PushConfig.serverPort,
                    PushConfig.streamName
                )
            }
        }
    }
}
