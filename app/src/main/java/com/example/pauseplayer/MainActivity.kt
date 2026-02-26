package com.example.pauseplayer

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.MotionEvent
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlin.math.abs
import kotlin.math.sign

class MainActivity : AppCompatActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private var startX = 0f
    private var startY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. 初始化界面逻辑
        playerView = findViewById(R.id.playerView)
        applyFullScreen()
        initPlayer()

        // 2. 处理启动意图
        handleIntent(intent)
    }

    private fun initPlayer() {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                128000, // minBufferMs: 至少缓冲多少
                256000, // maxBufferMs: 最多缓冲多少
                1500,  // bufferForPlaybackMs: 起播缓冲
                2000,  // bufferForPlaybackAfterRebufferMs: 卡顿后重新起播缓冲
            )
            .setBackBuffer(128000, true) // 核心代码：保留过去 多少毫秒的数据在内存中，不立即丢弃
            .build()

// 使用这个 loadControl 初始化 player
        player = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .build()
//        player = ExoPlayer.Builder(this).build()
        playerView.player = player

        // 自动旋转逻辑：根据视频宽高比决定横竖屏
        player.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                requestedOrientation = if (videoSize.width > videoSize.height) {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                }
            }
        })

        setupTouchLogic()
    }

    private fun handleIntent(intent: Intent?) {
        val data: Uri? = intent?.data
        if (data != null && data.scheme == "pauseplayer") {
            val segments = data.pathSegments
            if (segments.size >= 2) {
                // Base64 解码 (对应你的方案 B)
                val url = String(Base64.decode(segments[0], Base64.DEFAULT))
                val ua = String(Base64.decode(segments[1], Base64.DEFAULT))
                startPlay(url, ua)
            }
        }
    }

    private fun startPlay(url: String, ua: String) {
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(ua)
            .setAllowCrossProtocolRedirects(true)

        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(url))

        player.setMediaSource(mediaSource)
        player.prepare()
        player.play()
    }
    @SuppressLint("ClickableViewAccessibility") // 加在方法或者类上方
    private fun setupTouchLogic() {
        playerView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
//                    playerView.performClick() // 等同于 _.performClick()
                    startX = event.x
                    startY = event.y
                    player.pause() // 【核心】触碰即暂停
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val density=resources.displayMetrics.density
                    val dx = (event.x - startX)/density
                    val dy = (event.y - startY)/density
                    val absDx = abs(dx)
                    val absDy = abs(dy)

                    if (absDx > 10 && absDx > absDy) {
                        // 横向滑动：快进/快退 (基于你的 JS 公式)
                        val moveMs = (dx * dx / 625).toLong() * sign(dx).toLong() * 1000
                        val seekSync=if (moveMs<0&&moveMs>-5000){SeekParameters.EXACT}else if (moveMs<0){SeekParameters.PREVIOUS_SYNC}else if (moveMs>0){SeekParameters.NEXT_SYNC}else{SeekParameters.CLOSEST_SYNC}
                        player.setSeekParameters(seekSync)
                        player.seekTo(player.currentPosition + moveMs)
                        player.play()
                    } else if (absDy > 10 && absDy > absDx) {
                        if (dy > 0) { // 向下滑：倍速还原 + 显示控制栏（保持暂停）
                            player.setPlaybackSpeed(1.0f)
                            playerView.useController = true
                            playerView.showController()
                        }
                        else if (dy < -80) { // 大幅向上滑：切换ZOOM/FIT
                            playerView.resizeMode = if (playerView.resizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM) {
                                AspectRatioFrameLayout.RESIZE_MODE_FIT
                            } else {
                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            }
//                            playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
                            player.play()
                            playerView.useController = false
                        }
                        else { // 向上滑：2倍速播放
                            player.setPlaybackSpeed(2.0f)
                            player.play()
                            playerView.useController = false
                        }
                    } else {
                        // 普通松手：继续播放
                        player.play()
                        playerView.useController = false
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun applyFullScreen() {
        // 针对 Android 16 的沉浸式处理，隐藏三大按键导航栏
        window.setDecorFitsSystemWindows(false)
        val controller = window.insetsController
        if (controller != null) {
            controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    // 释放资源
    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }
}