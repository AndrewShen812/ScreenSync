# ScreenSync
A demo to show phone's real time screen on PC via rtsp.  
一个将手机屏幕实时同步到PC播放的demo。

Android端使用rtsp推流实现，局域网本地运行了[EasyDarwin开源流媒体服务器][2]，PC端用VLC客户端输入rtsp推流地址即可观看Android客户端屏幕画面。  
Android端使用的rtsp推流库为： [EasyPusher-Android][1]

VLC观看演示：  
![VLC观看演示](https://github.com/ChinaStyle812/ScreenSync/blob/master/gif/demo.gif)

[1]:https://github.com/EasyDarwin/EasyPusher-Android
[2]:https://github.com/EasyDarwin/EasyDarwin
