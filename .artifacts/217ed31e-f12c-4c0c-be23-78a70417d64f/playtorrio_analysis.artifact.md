# Deep Dive Analysis: PlayTorrioV3 Torrent Streaming

This document analyzes the torrent streaming implementation in `PlayTorrioV3-1.0.9` and compares it with our current `TorrentManager.kt` and `TorrentStreamServer.kt`.

## 1. Torrent Engine Configuration
PlayTorrioV3 uses **TorrServer** (via the `torrserver_flutter` plugin) as its core engine. TorrServer is a Go-based high-performance torrent-to-HTTP proxy.

- **Piece Priorities**: Handled internally by TorrServer's Go engine (`libtorrent-go`). It implements a sophisticated sequential downloading strategy that prioritizes pieces based on HTTP range requests.
- **Deadlines**: TorrServer dynamically sets piece deadlines based on the player's read speed and current buffer position.
- **Sequential Download**: Always enabled for streaming. TorrServer ensures the "rarest-first" algorithm is tempered by sequential needs to minimize buffering.

## 2. Streaming Server Strategy
Unlike our custom `NanoHTTPD` implementation, PlayTorrio uses TorrServer as a **local HTTP proxy**.
- **Lifecycle**: The engine starts a local server (usually on a random free port).
- **Endpoint**: Once a torrent is added, it provides a URL like `http://127.0.0.1:PORT/stream/HASH?index=FILE_ID`.
- **Robustness**: This offloads all complex networking (uTP, DHT, PEX, LPD) and piece management to a specialized, battle-tested process.

## 3. Buffering & Playback Logic
PlayTorrio follows a "Stremio-like" approach:
- **Metadata First**: It strictly waits for metadata (`_waitForMetadata`) with a 45s timeout.
- **Immediate Playback**: Once metadata is received and the file is selected, it returns the stream URL immediately. It does **not** wait for a fixed 10MB buffer like Vidiio.
- **Range Requests**: When the player (MediaKit/VideoPlayer) seeks, it sends an HTTP `Range` request. TorrServer intercepts this, cancels old piece deadlines, and immediately shifts the swarm's focus to the new byte range.

## 4. UI Feedback
- **Real-time Stats**: Polled every second from TorrServer's `/torrent/get` endpoint.
- **Metrics**:
    - `speedMbps`: Direct download speed.
    - `activePeers` / `totalPeers`: Swarm health.
    - `cachePercent`: How much of the file is currently in TorrServer's RAM/disk cache.
    - `isConnected`: Simple boolean for UI indicators.

## 5. Visual Comparison with Vidiio

| Feature | Vidiio (Current) | PlayTorrioV3 (Proposed Improvements) |
| :--- | :--- | :--- |
| **Engine** | `jlibtorrent` (Native Wrapper) | Robust HTTP Proxy (TorrServer style) |
| **Buffering** | Fixed 10MB Pre-buffer | Dynamic Range-based Buffering |
| **Seeking** | Manual `waitForRange` (Polling) | Reactive piece-priority shifting |
| **Stability** | Standard Wi-Fi usage | Low-latency Wi-Fi + CPU WakeLocks |
| **Downloads** | Stream only | Stream-to-File (using same proxy) |

## Key Findings & Recommendations

### A. Implement "Low-Latency" Networking
PlayTorrio's `MainActivity.kt` explicitly acquires a `WIFI_MODE_FULL_LOW_LATENCY` lock. This is critical for preventing the Android OS from throttling the Wi-Fi chip during high-throughput P2P sessions.

### B. Optimize Seeking (The "Stremio" Way)
Our `TorrentStreamServer` should be more "proactive". Instead of just waiting for the range, it should explicitly communicate to `TorrentManager` to drop all previous deadlines and focus exclusively on the next 5-10MB of the requested range.

### C. Intelligent File Selection
PlayTorrio's `_selectFile` is more robust, handling Season/Episode matching and falling back to the largest media file. We should adopt this logic to handle complex "Pack" torrents better.

### D. Unified Stream/Download
By making `TorrentStreamServer` the source of truth, we can implement "Download" by simply performing a standard HTTP download from our own local server. This ensures the engine handles the torrent swarm identically for both use cases.
