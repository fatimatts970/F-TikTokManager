package com.ftiktokmanager.app;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Local relay on 127.0.0.1: accepts HTTP proxy requests (CONNECT / plain HTTP) from the WebView
 * and forwards them through an upstream SOCKS5 proxy (with optional username/password).
 * WebView can only talk to HTTP proxies, this is what makes SOCKS5 usable.
 */
final class SocksBridge {
    private final String host;
    private final int port;
    private final String user;
    private final String pass;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private volatile boolean running;
    private ServerSocket server;

    SocksBridge(String host, int port, String user, String pass) {
        this.host = host;
        this.port = port;
        this.user = user == null ? "" : user;
        this.pass = pass == null ? "" : pass;
    }

    /** Starts listening on a random local port and returns it. */
    int start() throws IOException {
        server = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
        running = true;
        pool.execute(this::acceptLoop);
        return server.getLocalPort();
    }

    void stop() {
        running = false;
        try {
            if (server != null) server.close();
        } catch (IOException ignored) {
        }
        pool.shutdownNow();
    }

    private void acceptLoop() {
        while (running) {
            try {
                final Socket c = server.accept();
                pool.execute(() -> handle(c));
            } catch (IOException e) {
                if (!running) return;
            }
        }
    }

    // ------------------------------------------------------------------ client side

    private void handle(Socket client) {
        Socket upstream = null;
        try {
            client.setSoTimeout(30000);
            InputStream in = client.getInputStream();
            OutputStream out = client.getOutputStream();

            String head = readHeader(in);
            if (head == null) {
                client.close();
                return;
            }
            String[] lines = head.split("\r\n");
            String[] first = lines[0].split(" ");
            if (first.length < 3) {
                client.close();
                return;
            }
            String method = first[0];
            String target = first[1];

            if (method.equalsIgnoreCase("CONNECT")) {
                String h;
                int p = 443;
                int idx = target.lastIndexOf(':');
                if (idx > 0) {
                    h = target.substring(0, idx);
                    try {
                        p = Integer.parseInt(target.substring(idx + 1));
                    } catch (NumberFormatException ignored) {
                    }
                } else {
                    h = target;
                }
                if (h.startsWith("[") && h.endsWith("]")) h = h.substring(1, h.length() - 1);
                try {
                    upstream = openUpstream(host, port, user, pass, h, p);
                } catch (IOException e) {
                    out.write(("HTTP/1.1 502 Bad Gateway\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                    out.flush();
                    client.close();
                    return;
                }
                out.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                out.flush();
            } else {
                // plain http://host[:port]/path request
                String rest = target.replaceFirst("(?i)^http://", "");
                int slash = rest.indexOf('/');
                String hostPort = slash >= 0 ? rest.substring(0, slash) : rest;
                String path = slash >= 0 ? rest.substring(slash) : "/";
                String h = hostPort;
                int p = 80;
                int idx = hostPort.lastIndexOf(':');
                if (idx > 0) {
                    h = hostPort.substring(0, idx);
                    try {
                        p = Integer.parseInt(hostPort.substring(idx + 1));
                    } catch (NumberFormatException ignored) {
                    }
                }
                try {
                    upstream = openUpstream(host, port, user, pass, h, p);
                } catch (IOException e) {
                    out.write(("HTTP/1.1 502 Bad Gateway\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                    out.flush();
                    client.close();
                    return;
                }
                StringBuilder sb = new StringBuilder();
                sb.append(method).append(' ').append(path).append(' ').append(first[2]).append("\r\n");
                for (int i = 1; i < lines.length; i++) {
                    String l = lines[i];
                    if (l.isEmpty()) continue;
                    String low = l.toLowerCase();
                    if (low.startsWith("proxy-connection:") || low.startsWith("connection:")) continue;
                    sb.append(l).append("\r\n");
                }
                sb.append("Connection: close\r\n\r\n");
                upstream.getOutputStream().write(sb.toString().getBytes(StandardCharsets.ISO_8859_1));
                upstream.getOutputStream().flush();
            }

            client.setSoTimeout(0);
            upstream.setSoTimeout(0);
            relay(client, upstream);
        } catch (Exception e) {
            closeQuietly(client);
            closeQuietly(upstream);
        }
    }

    private void relay(final Socket a, final Socket b) throws IOException {
        final InputStream aIn = a.getInputStream();
        final OutputStream bOut = b.getOutputStream();
        final InputStream bIn = b.getInputStream();
        final OutputStream aOut = a.getOutputStream();
        pool.execute(() -> pipe(aIn, bOut, a, b));
        pipe(bIn, aOut, a, b);
    }

    private static void pipe(InputStream in, OutputStream out, Socket a, Socket b) {
        byte[] buf = new byte[16384];
        try {
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                out.flush();
            }
        } catch (IOException ignored) {
        } finally {
            closeQuietly(a);
            closeQuietly(b);
        }
    }

    private static String readHeader(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int state = 0;
        int c;
        while ((c = in.read()) != -1) {
            bos.write(c);
            if (c == '\r' && (state == 0 || state == 2)) state++;
            else if (c == '\n' && (state == 1 || state == 3)) state++;
            else state = 0;
            if (state == 4) return new String(bos.toByteArray(), StandardCharsets.ISO_8859_1);
            if (bos.size() > 32768) return null;
        }
        return null;
    }

    private static void closeQuietly(Socket s) {
        try {
            if (s != null) s.close();
        } catch (IOException ignored) {
        }
    }

    // ------------------------------------------------------------------ SOCKS5 client

    /** Connects to the SOCKS5 server and asks it to open destHost:destPort. Friendly (Roman Urdu) errors. */
    static Socket openUpstream(String proxyHost, int proxyPort, String user, String pass,
                               String destHost, int destPort) throws IOException {
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress(proxyHost, proxyPort), 15000);
            s.setSoTimeout(15000);
            InputStream in = s.getInputStream();
            OutputStream out = s.getOutputStream();
            boolean auth = user != null && !user.isEmpty();

            out.write(auth ? new byte[]{5, 2, 0, 2} : new byte[]{5, 1, 0});
            out.flush();
            byte[] r = new byte[2];
            readFully(in, r);
            if (r[0] != 5) throw new IOException("Ye SOCKS5 proxy nahi hai (port ya type check karo)");
            if ((r[1] & 0xFF) == 0xFF) throw new IOException("Proxy ne login method qabool nahi kiya");
            if (r[1] == 2) {
                byte[] u = user.getBytes(StandardCharsets.UTF_8);
                byte[] p = (pass == null ? "" : pass).getBytes(StandardCharsets.UTF_8);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                bos.write(1);
                bos.write(u.length);
                bos.write(u);
                bos.write(p.length);
                bos.write(p);
                out.write(bos.toByteArray());
                out.flush();
                byte[] ar = new byte[2];
                readFully(in, ar);
                if (ar[1] != 0) throw new IOException("Proxy username/password ghalat hai");
            } else if (r[1] != 0) {
                throw new IOException("Proxy ka login method support nahi hota");
            }

            byte[] d = destHost.getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream req = new ByteArrayOutputStream();
            req.write(5);
            req.write(1);
            req.write(0);
            req.write(3);
            req.write(d.length);
            req.write(d);
            req.write((destPort >> 8) & 0xFF);
            req.write(destPort & 0xFF);
            out.write(req.toByteArray());
            out.flush();

            byte[] h = new byte[4];
            readFully(in, h);
            if (h[1] != 0) throw new IOException("Proxy connect nahi kar saka (code " + (h[1] & 0xFF) + ")");
            int skip;
            switch (h[3]) {
                case 1: skip = 4; break;
                case 4: skip = 16; break;
                default: {
                    byte[] l = new byte[1];
                    readFully(in, l);
                    skip = l[0] & 0xFF;
                }
            }
            readFully(in, new byte[skip + 2]);
            return s;
        } catch (UnknownHostException e) {
            closeQuietly(s);
            throw new IOException("Host nahi mila - proxy ka address check karo");
        } catch (SocketTimeoutException e) {
            closeQuietly(s);
            throw new IOException("Time out - proxy jawab nahi de raha");
        } catch (ConnectException e) {
            closeQuietly(s);
            throw new IOException("Connect nahi hua - proxy off ya port ghalat");
        } catch (IOException e) {
            closeQuietly(s);
            throw e;
        }
    }

    private static void readFully(InputStream in, byte[] buf) throws IOException {
        int off = 0;
        while (off < buf.length) {
            int n = in.read(buf, off, buf.length - off);
            if (n < 0) throw new IOException("Proxy ne connection band kar diya");
            off += n;
        }
    }
}
