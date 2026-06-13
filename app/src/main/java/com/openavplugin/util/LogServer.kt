package com.openavplugin.util

import android.content.Context
import android.net.wifi.WifiManager
import java.net.Inet6Address
import java.net.NetworkInterface
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

object LogServer {

    data class LogEntry(
        val timestamp: String,
        val level: String,
        val tag: String,
        val message: String
    )

    private const val PORT = 8088
    private const val MAX_ENTRIES = 1000

    private val entries = CopyOnWriteArrayList<LogEntry>()
    private val running = AtomicBoolean(false)
    private var serverThread: Thread? = null

    fun append(level: String, tag: String, message: String) {
        if (!running.get()) return
        val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        entries.add(LogEntry(ts, level, tag, message))
        if (entries.size > MAX_ENTRIES) {
            entries.removeAt(0)
        }
    }

    fun start(context: Context) {
        if (running.getAndSet(true)) {
            if (serverThread == null || !serverThread!!.isAlive) {
                running.set(false)
            } else {
                return
            }
        }
        val ip = getWifiIp(context) ?: "127.0.0.1"

        serverThread = Thread({
            try {
                val server = ServerSocket()
                server.reuseAddress = true
                server.bind(InetSocketAddress(PORT))
                android.util.Log.i("LogServer", "Started on http://$ip:$PORT")

                while (running.get()) {
                    try {
                        val client = server.accept()
                        client.tcpNoDelay = true
                        Thread({ handleClient(client) }).start()
                    } catch (_: Exception) {
                        if (!running.get()) break
                    }
                }
                try { server.close() } catch (_: Exception) { }
            } catch (e: Exception) {
                android.util.Log.e("LogServer", "Bind failed: ${e.message}")
                running.set(false)
            }
        }, "LogServer").apply {
            isDaemon = true
            start()
        }

    }

    fun stop() {
        running.set(false)
        val t = serverThread
        serverThread = null
        if (t != null) {
            t.interrupt()
            Thread({ try { t.join(1000) } catch (_: Exception) { } }, "LogServer-cleanup").start()
        }
    }

    fun isRunning(): Boolean = running.get()

    fun getAddress(context: Context): String {
        val ip = getWifiIp(context) ?: "127.0.0.1"
        return "http://$ip:$PORT"
    }

    private fun handleClient(client: java.net.Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            val path = if (parts.size >= 2) parts[1] else "/"
            while (reader.readLine()?.isNotEmpty() == true) { }
            val output = client.getOutputStream()

            when {
                path == "/" || path == "/index.html" -> serveHtml(output)
                path.startsWith("/logs") -> serveJson(output)
                path == "/clear" -> { entries.clear(); serveOk(output) }
                path == "/download" -> serveDownload(output)
                else -> serveNotFound(output)
            }
            output.flush()
            client.close()
        } catch (_: Exception) { }
    }

    private fun serveHtml(output: OutputStream) {
        val html = "<!DOCTYPE html>\n<html lang=\"zh\">\n<head>\n<meta charset=\"UTF-8\">\n<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n<title>OpenAVPlugin Logs</title>\n<style>\n*{margin:0;padding:0;box-sizing:border-box}\nbody{background:#0d1117;color:#c9d1d9;font-family:'SF Mono',Consolas,monospace;font-size:13px}\n#header{position:sticky;top:0;z-index:10;background:#161b22;border-bottom:1px solid #30363d;padding:10px 14px}\n#header h1{font-size:15px;color:#58a6ff;margin-bottom:8px}\n#toolbar{display:flex;flex-wrap:wrap;gap:6px;align-items:center}\n.btn{padding:3px 10px;border:1px solid #30363d;border-radius:4px;background:#21262d;color:#c9d1d9;cursor:pointer;font-size:12px;font-family:inherit}\n.btn:hover{background:#30363d}\n.btn.on{border-color:#58a6ff;color:#58a6ff;background:#1f2937}\n.sep{width:1px;height:20px;background:#30363d;margin:0 4px}\n#search{background:#0d1117;border:1px solid #30363d;border-radius:4px;color:#c9d1d9;padding:3px 8px;font-size:12px;font-family:inherit;width:140px}\n#search:focus{outline:none;border-color:#58a6ff}\n#status{font-size:11px;color:#8b949e;margin-left:auto}\n#status.ok{color:#3fb950}#status.err{color:#f85149}\n.entry{padding:2px 14px;border-bottom:1px solid #161b22;line-height:1.5}\n.entry:hover{background:#161b22}\n.ts{color:#484f58;margin-right:8px}\n.level{display:inline-block;width:40px;text-align:center;font-weight:bold;font-size:11px}\n.l-D{color:#8b949e}.l-I{color:#3fb950}.l-W{color:#d29922}.l-E{color:#f85149}\n.tag{color:#58a6ff;font-weight:bold;margin:0 6px}\n.msg{color:#c9d1d9;word-break:break-all}\n.hidden{display:none}\n#log{max-height:calc(100vh - 78px);overflow-y:auto;padding-bottom:20px}\n::-webkit-scrollbar{width:6px}::-webkit-scrollbar-track{background:#0d1117}::-webkit-scrollbar-thumb{background:#30363d;border-radius:3px}\n</style>\n</head>\n<body>\n<div id=\"header\">\n<h1>OpenAVPlugin Logs</h1>\n<div id=\"toolbar\">\n<button class=\"btn on\" onclick=\"toggleFilter('D',this)\" style=\"color:#8b949e\">DBG</button>\n<button class=\"btn on\" onclick=\"toggleFilter('I',this)\" style=\"color:#3fb950\">INF</button>\n<button class=\"btn on\" onclick=\"toggleFilter('W',this)\" style=\"color:#d29922\">WRN</button>\n<button class=\"btn on\" onclick=\"toggleFilter('E',this)\" style=\"color:#f85149\">ERR</button>\n<span class=\"sep\"></span>\n<input id=\"search\" placeholder=\"Filter tag...\" oninput=\"applyFilters()\">\n<span class=\"sep\"></span>\n<button class=\"btn\" onclick=\"clearLogs()\">Clear</button>\n<button class=\"btn\" onclick=\"downloadLogs()\">Export</button>\n<span id=\"status\" class=\"ok\">Connected</span>\n</div>\n</div>\n<div id=\"log\"></div>\n<script>\nvar filters={D:true,I:true,W:true,E:true};\nvar known=[],maxEntries=500;\nfunction toggleFilter(l,b){filters[l]=!filters[l];b.classList.toggle('on',filters[l]);applyFilters()}\nfunction applyFilters(){var s=document.getElementById('search').value.toLowerCase();document.querySelectorAll('.entry').forEach(function(e){e.classList.toggle('hidden',!filters[e.dataset.level]||(s&&e.dataset.tag.toLowerCase().indexOf(s)===-1))})}\nfunction clearLogs(){fetch('/clear');document.getElementById('log').innerHTML='';known=[]}\nfunction downloadLogs(){fetch('/download').then(function(r){return r.text()}).then(function(t){var a=document.createElement('a');a.href='data:text/plain;charset=utf-8,'+encodeURIComponent(t);a.download='openavplugin_logs.txt';a.click()})}\nfunction addEntry(e){var k=e.timestamp+e.level+e.tag+e.message;if(known.indexOf(k)>=0)return;known.push(k);if(known.length>2000)known.shift();var d=document.createElement('div');d.className='entry';d.dataset.level=e.level.charAt(0);d.dataset.tag=e.tag;d.innerHTML='<span class=\"ts\">'+e.timestamp+'</span><span class=\"level l-'+e.level.charAt(0)+'\">'+e.level+'</span><span class=\"tag\">'+e.tag+'</span><span class=\"msg\">'+e.message+'</span>';var log=document.getElementById('log');log.appendChild(d);while(log.children.length>maxEntries)log.removeChild(log.firstElementChild);var l=e.level.charAt(0);var t=e.tag.toLowerCase();var s=document.getElementById('search').value.toLowerCase();d.classList.toggle('hidden',!filters[l]||(s&&t.indexOf(s)===-1));if(log.scrollHeight-log.scrollTop-log.clientHeight<40)log.scrollTop=log.scrollHeight}\nfunction poll(){var s=document.getElementById('status');fetch('/logs').then(function(r){return r.json()}).then(function(d){if(d.entries)d.entries.forEach(addEntry);s.textContent='Connected';s.className='ok'}).catch(function(){s.textContent='Disconnected';s.className='err'});setTimeout(poll,500)}\npoll();\n</script>\n</body>\n</html>"
        val response = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nCache-Control: no-cache\r\nConnection: close\r\n\r\n$html"
        output.write(response.toByteArray())
    }

    private fun serveJson(output: OutputStream) {
        val list = entries.toList()
        val sb = StringBuilder("{\"entries\":[")
        for (i in list.indices) {
            val e = list[i]
            if (i > 0) sb.append(",")
            sb.append("{\"timestamp\":\"${e.timestamp}\",\"level\":\"${e.level}\",\"tag\":\"${jsonEscape(e.tag)}\",\"message\":\"${jsonEscape(e.message)}\"}")
        }
        sb.append("]}")
        val response = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nAccess-Control-Allow-Origin: *\r\nCache-Control: no-cache\r\nConnection: close\r\n\r\n${sb}"
        output.write(response.toByteArray())
    }

    private fun serveDownload(output: OutputStream) {
        val sb = StringBuilder()
        val list = entries.toList()
        for (e in list) {
            sb.append("${e.timestamp} ${e.level} [${e.tag}] ${e.message}\n")
        }
        val response = "HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Disposition: attachment; filename=openavplugin_logs.txt\r\nConnection: close\r\n\r\n${sb}"
        output.write(response.toByteArray())
    }

    private fun serveNotFound(output: OutputStream) {
        val response = "HTTP/1.1 404 Not Found\r\nContent-Type: text/plain\r\nConnection: close\r\n\r\nNot Found"
        output.write(response.toByteArray())
    }

    private fun serveOk(output: OutputStream) {
        val response = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nConnection: close\r\n\r\nOK"
        output.write(response.toByteArray())
    }

    private fun jsonEscape(s: String): String {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
    }

    private fun getWifiIp(context: Context): String? {
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wm.connectionInfo
            val ip = info.ipAddress
            if (ip != 0) {
                return "${ip and 0xFF}.${(ip shr 8) and 0xFF}.${(ip shr 16) and 0xFF}.${(ip shr 24) and 0xFF}"
            }
        } catch (_: Exception) { }
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr.isLoopbackAddress || addr is Inet6Address) continue
                    val host = addr.hostAddress ?: continue
                    if (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172.")) {
                        return host
                    }
                }
            }
        } catch (_: Exception) { }
        return null
    }
}
