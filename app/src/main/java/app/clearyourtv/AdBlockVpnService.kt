package app.clearyourtv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * «Щит»: локальный DNS-фильтр. Через туннель идут ТОЛЬКО DNS-запросы (маршрут на один адрес),
 * весь остальной трафик ТВ идёт мимо VPN и не замедляется. Запрос к домену из чёрного списка
 * получает ответ «такого домена нет», остальные пересылаются на выбранный DNS-сервер.
 * Ничего не логируется и никуда не отправляется.
 */
class AdBlockVpnService : VpnService() {

    companion object {
        private const val FAKE_DNS = "10.77.0.1"
        private const val TUN_ADDR = "10.77.0.2"
        val UPSTREAMS = listOf("1.1.1.1", "8.8.8.8", "9.9.9.9", "94.140.14.14")
        val UPSTREAM_NAMES = listOf("Cloudflare 1.1.1.1", "Google 8.8.8.8", "Quad9 9.9.9.9", "AdGuard 94.140.14.14")

        @Volatile var running = false
        val blocked = AtomicLong(0)
        val flushed = AtomicLong(0)

        fun pending(): Long = blocked.get() - flushed.get()

        fun start(ctx: Context) {
            try { ctx.startService(Intent(ctx, AdBlockVpnService::class.java)) } catch (e: Exception) { }
        }

        fun stop(ctx: Context) {
            try { ctx.stopService(Intent(ctx, AdBlockVpnService::class.java)) } catch (e: Exception) { }
        }
    }

    private var tun: ParcelFileDescriptor? = null
    @Volatile private var alive = false
    private val pool = Executors.newFixedThreadPool(6)
    private var upstream: InetAddress? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        if (tun == null) startShield()
        return START_STICKY
    }

    override fun onRevoke() {
        // пользователь отозвал разрешение VPN в системе
        Prefs(this).adblock = false
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        alive = false
        running = false
        flush()
        try { tun?.close() } catch (e: Exception) { }
        tun = null
        pool.shutdownNow()
        super.onDestroy()
    }

    private fun goForeground() {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val b: Notification.Builder
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(NotificationChannel("shield", "Защита", NotificationManager.IMPORTANCE_MIN))
                b = Notification.Builder(this, "shield")
            } else {
                b = Notification.Builder(this)
            }
            val n = b.setContentTitle("ClearYourTV: защита от рекламы включена")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .build()
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(7, n)
            }
        } catch (e: Exception) {
            // без уведомления тоже работаем
        }
    }

    private fun startShield() {
        val p = Prefs(this)
        upstream = try {
            InetAddress.getByName(UPSTREAMS[p.dnsUpstream.coerceIn(0, UPSTREAMS.size - 1)])
        } catch (e: Exception) { null }

        val fd = try {
            Builder()
                .setSession("ClearYourTV")
                .addAddress(TUN_ADDR, 32)
                .addDnsServer(FAKE_DNS)
                .addRoute(FAKE_DNS, 32)
                .setBlocking(true)
                .establish()
        } catch (e: Exception) { null }

        if (fd == null) {
            p.adblock = false
            stopSelf()
            return
        }
        tun = fd
        alive = true
        running = true
        Thread { loop(fd) }.start()
    }

    private fun loop(fd: ParcelFileDescriptor) {
        try { Blocklist.load(this) } catch (e: Exception) { }
        val input = FileInputStream(fd.fileDescriptor)
        val output = FileOutputStream(fd.fileDescriptor)
        val buf = ByteArray(32767)
        try {
            while (alive) {
                val n = input.read(buf)
                if (n < 0) break
                if (n == 0) continue
                handle(buf.copyOf(n), output)
            }
        } catch (e: Exception) {
            // туннель закрыт
        }
    }

    private fun handle(p: ByteArray, out: FileOutputStream) {
        if (p.size < 28) return
        if (((p[0].toInt() shr 4) and 0xF) != 4) return
        val ihl = (p[0].toInt() and 0xF) * 4
        if ((p[9].toInt() and 0xFF) != 17 || p.size < ihl + 8) return
        if (u16(p, ihl + 2) != 53) return
        val srcPort = u16(p, ihl)
        val end = minOf(p.size, ihl + u16(p, ihl + 4))
        if (end <= ihl + 8) return
        val dns = p.copyOfRange(ihl + 8, end)

        val name = qname(dns)
        if (name != null && Blocklist.isBlocked(name)) {
            if (blocked.incrementAndGet() % 50L == 0L) flush()
            write(out, reply(p, srcPort, nxdomain(dns)))
            return
        }
        try {
            pool.execute { forward(p, srcPort, dns, out) }
        } catch (e: Exception) { }
    }

    private fun forward(p: ByteArray, srcPort: Int, dns: ByteArray, out: FileOutputStream) {
        val up = upstream ?: return
        var sock: DatagramSocket? = null
        try {
            sock = DatagramSocket()
            protect(sock)
            sock.soTimeout = 4000
            sock.connect(up, 53)
            sock.send(DatagramPacket(dns, dns.size))
            val rb = ByteArray(4096)
            val rp = DatagramPacket(rb, rb.size)
            sock.receive(rp)
            write(out, reply(p, srcPort, rb.copyOf(rp.length)))
        } catch (e: Exception) {
            // таймаут или сеть недоступна — приложение само повторит запрос
        } finally {
            try { sock?.close() } catch (e: Exception) { }
        }
    }

    private fun write(out: FileOutputStream, bytes: ByteArray) {
        try {
            synchronized(out) { out.write(bytes) }
        } catch (e: Exception) { }
    }

    private fun flush() {
        val d = blocked.get() - flushed.get()
        if (d > 0) {
            val pr = Prefs(this)
            pr.blockedTotal = pr.blockedTotal + d
            flushed.addAndGet(d)
        }
    }

    // ───────────────────────── разбор и сборка пакетов ─────────────────────────

    private fun u16(a: ByteArray, o: Int): Int = ((a[o].toInt() and 0xFF) shl 8) or (a[o + 1].toInt() and 0xFF)

    private fun put16(a: ByteArray, o: Int, v: Int) {
        a[o] = (v shr 8).toByte()
        a[o + 1] = v.toByte()
    }

    /** Имя из вопроса DNS-запроса (в нижнем регистре) или null. */
    private fun qname(d: ByteArray): String? {
        if (d.size < 13) return null
        val sb = StringBuilder()
        var i = 12
        while (i < d.size) {
            val len = d[i].toInt() and 0xFF
            if (len == 0) break
            if ((len and 0xC0) != 0) return null
            if (i + 1 + len > d.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            for (k in 1..len) sb.append((d[i + k].toInt() and 0xFF).toChar().lowercaseChar())
            i += len + 1
        }
        return if (sb.isEmpty()) null else sb.toString()
    }

    /** Ответ «домена не существует» (NXDOMAIN) на запрос q. */
    private fun nxdomain(q: ByteArray): ByteArray {
        var i = 12
        while (i < q.size && q[i].toInt() != 0) i += (q[i].toInt() and 0xFF) + 1
        val qEnd = minOf(q.size, i + 5)
        val r = q.copyOfRange(0, qEnd)
        r[2] = 0x81.toByte() // ответ, рекурсия желательна
        r[3] = 0x83.toByte() // рекурсия доступна, код 3 = NXDOMAIN
        for (k in 6..11) r[k] = 0 // ответов и дополнительных записей нет
        return r
    }

    /** Заворачивает DNS-ответ в IPv4/UDP-пакет обратно к приложению. */
    private fun reply(req: ByteArray, srcPort: Int, dns: ByteArray): ByteArray {
        val udpLen = 8 + dns.size
        val out = ByteArray(20 + udpLen)
        out[0] = 0x45.toByte()
        put16(out, 2, out.size)
        put16(out, 6, 0x4000)
        out[8] = 64
        out[9] = 17
        System.arraycopy(req, 16, out, 12, 4) // отправитель = бывший получатель
        System.arraycopy(req, 12, out, 16, 4) // получатель = бывший отправитель
        put16(out, 10, checksum(out, 20))
        put16(out, 20, 53)
        put16(out, 22, srcPort)
        put16(out, 24, udpLen)
        System.arraycopy(dns, 0, out, 28, dns.size)
        return out
    }

    private fun checksum(a: ByteArray, len: Int): Int {
        var sum = 0
        var i = 0
        while (i < len) {
            sum += ((a[i].toInt() and 0xFF) shl 8) or (a[i + 1].toInt() and 0xFF)
            i += 2
        }
        while ((sum shr 16) != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv() and 0xFFFF
    }
}
