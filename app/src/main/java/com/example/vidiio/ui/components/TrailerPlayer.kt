package com.example.vidiio.ui.components

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Looping YouTube trailer rendered full-bleed (CSS "cover" crop) via the YouTube
 * IFrame API in a WebView, on the youtube-nocookie host (avoids the common
 * "embedding disabled" 152 error).
 *
 * @param playing  drives play/pause (e.g. pause when scrolled out of view).
 * @param muted    autoplay only works while muted, so callers start muted.
 * @param controls show native YouTube controls (used in the fullscreen view).
 * @param interactive let touches reach the player (fullscreen) vs. pass through (inline background).
 * @param onUnavailable fires when the video blocks embedding or never starts.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TrailerPlayer(
    videoId: String,
    playing: Boolean,
    muted: Boolean,
    modifier: Modifier = Modifier,
    controls: Boolean = false,
    interactive: Boolean = false,
    startMuted: Boolean = true,
    onUnavailable: () -> Unit = {}
) {
    val html = remember(videoId, controls, interactive, startMuted) {
        buildHtml(videoId, controls, interactive, startMuted)
    }
    val currentOnUnavailable by rememberUpdatedState(onUnavailable)

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(android.graphics.Color.BLACK)
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                    }
                    webViewClient = WebViewClient()
                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun onUnavailable() {
                            post { currentOnUnavailable() }
                        }
                    }, "AndroidTrailer")
                    loadDataWithBaseURL(
                        "https://www.youtube-nocookie.com",
                        html,
                        "text/html",
                        "utf-8",
                        null
                    )
                }
            },
            update = { wv ->
                wv.evaluateJavascript("window.setMuted && setMuted(${muted});", null)
                wv.evaluateJavascript("window.setPlaying && setPlaying(${playing});", null)
            },
            onRelease = { wv ->
                wv.loadUrl("about:blank")
                wv.stopLoading()
                wv.removeJavascriptInterface("AndroidTrailer")
                wv.destroy()
            }
        )
    }
}

private fun buildHtml(videoId: String, controls: Boolean, interactive: Boolean, startMuted: Boolean): String {
    val pe = if (interactive) "auto" else "none"
    val ctl = if (controls) 1 else 0
    val mute0 = if (startMuted) 1 else 0
    val wantMutedInit = if (startMuted) "true" else "false"
    return """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
<style>
  html,body{margin:0;padding:0;background:#000;overflow:hidden;height:100%}
  #wrap{position:fixed;inset:0;overflow:hidden}
  #wrap,#wrap *{pointer-events:$pe !important}
  #player{position:absolute;top:50%;left:50%;transform:translate(-50%,-50%);
          width:100vw;height:56.25vw;min-height:100vh;min-width:177.78vh}
</style>
</head>
<body>
<div id="wrap"><div id="player"></div></div>
<script src="https://www.youtube.com/iframe_api"></script>
<script>
  var player, ready=false, wantMuted=$wantMutedInit, wantPlaying=true, failed=false;
  function fail(){ if(failed)return; failed=true; try{ AndroidTrailer.onUnavailable(); }catch(e){} }
  function onYouTubeIframeAPIReady(){
    player=new YT.Player('player',{
      host:'https://www.youtube-nocookie.com',
      videoId:'$videoId',
      playerVars:{autoplay:1,controls:$ctl,mute:$mute0,loop:1,playlist:'$videoId',
                  playsinline:1,modestbranding:1,rel:0,fs:1,disablekb:1,iv_load_policy:3},
      events:{
        onReady:function(e){ ready=true; apply(); },
        onError:function(){ fail(); },
        onStateChange:function(e){ if(e.data===YT.PlayerState.ENDED){ e.target.seekTo(0); e.target.playVideo(); } }
      }
    });
  }
  function apply(){
    if(!ready)return;
    if(wantMuted){player.mute();}else{player.unMute();player.setVolume(100);}
    if(wantPlaying){player.playVideo();}else{player.pauseVideo();}
  }
  window.setMuted=function(m){ wantMuted=m; apply(); };
  window.setPlaying=function(p){ wantPlaying=p; apply(); };
  setTimeout(function(){ if(!ready) fail(); }, 6000);
</script>
</body>
</html>
""".trimIndent()
}
