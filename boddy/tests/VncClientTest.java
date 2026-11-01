import com.boddy.os.vnc.RfbClient;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.net.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Runs the Boddy app's own VNC client (com.boddy.os.vnc.RfbClient, from the app's dex turned into a jar) on a
 * PC against Xvnc, through a proxy that counts the bytes Xvnc sends.
 *   dump PORT OUT.rgb   connect, wait until the screen stops changing, write the client's framebuffer as raw
 *                       RGB (as `convert x:root rgb:` would) and print its size
 *   measure PORT SECS   connect, take updates for SECS seconds, print bytes received, updates and the client
 *                       thread's CPU time
 * Classpath: the app jar, kotlin-stdlib, this class.
 */
public class VncClientTest {
    static final AtomicLong bytes = new AtomicLong(), updates = new AtomicLong(), lastUpdate = new AtomicLong();
    static volatile Throwable failure;

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        int port = Integer.parseInt(args[1]);
        int local = proxy(port);
        RfbClient client = new RfbClient(new RfbClient.Listener() {
            public void onResize(int w, int h) { lastUpdate.set(System.nanoTime()); }
            public void onUpdate(int x, int y, int w, int h) { updates.incrementAndGet(); lastUpdate.set(System.nanoTime()); }
            public void onClipboard(String text) {}
            public void onDisconnected(Throwable error) { if (error != null) failure = error; }
        });
        client.connect("127.0.0.1", local, 5000);
        long[] cpu = new long[1];
        Thread reader = new Thread(() -> {
            client.run();
            cpu[0] = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();
        });
        reader.setDaemon(true);
        reader.start();
        ThreadMXBean mx = ManagementFactory.getThreadMXBean();
        if (mode.equals("dump")) {
            long deadline = System.nanoTime() + 30_000_000_000L;
            Thread.sleep(500);
            while (System.nanoTime() - lastUpdate.get() < 1_500_000_000L && System.nanoTime() < deadline) Thread.sleep(100);
            check();
            int w, h;
            int[] copy;
            synchronized (client.getLock()) {
                w = client.getWidth(); h = client.getHeight(); copy = client.getPixels().clone();
            }
            try (OutputStream out = new BufferedOutputStream(new FileOutputStream(args[2]))) {
                for (int p : copy) { out.write(p >> 16); out.write(p >> 8); out.write(p); }
            }
            System.out.println("size=" + w + "x" + h + " updates=" + updates.get() + " bytes=" + bytes.get());
        } else {
            long secs = Long.parseLong(args[2]);
            Thread.sleep(1000);  // the first full screen
            long b0 = bytes.get(), u0 = updates.get(), c0 = mx.getThreadCpuTime(reader.getId());
            Thread.sleep(secs * 1000);
            check();
            long cpuMs = (mx.getThreadCpuTime(reader.getId()) - c0) / 1_000_000;
            System.out.println("bytes=" + (bytes.get() - b0) + " updates=" + (updates.get() - u0) + " client_cpu_ms=" + cpuMs);
        }
        client.close();
        System.exit(0);
    }

    static void check() {
        if (failure != null) { failure.printStackTrace(); System.exit(2); }
    }

    /** A local port forwarding to Xvnc, counting what Xvnc sends. */
    static int proxy(int port) throws IOException {
        ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        Thread t = new Thread(() -> {
            try (Socket in = server.accept(); Socket out = new Socket("127.0.0.1", port)) {
                in.setTcpNoDelay(true); out.setTcpNoDelay(true);
                Thread up = pump(in.getInputStream(), out.getOutputStream(), null);
                pump(out.getInputStream(), in.getOutputStream(), bytes).join();
                up.join();
            } catch (Exception e) { /* connection closed */ }
        });
        t.setDaemon(true);
        t.start();
        return server.getLocalPort();
    }

    static Thread pump(InputStream from, OutputStream to, AtomicLong count) {
        Thread t = new Thread(() -> {
            byte[] buf = new byte[65536];
            try {
                for (int n; (n = from.read(buf)) > 0; ) {
                    to.write(buf, 0, n);
                    to.flush();
                    if (count != null) count.addAndGet(n);
                }
            } catch (IOException e) { /* closed */ }
        });
        t.setDaemon(true);
        t.start();
        return t;
    }
}
