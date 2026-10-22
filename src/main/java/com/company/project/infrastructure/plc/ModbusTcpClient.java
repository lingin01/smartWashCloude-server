package com.company.project.infrastructure.plc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** JDK NIO 客户端：一次交换共用连接／收发总截止时间，支持 TCP 分包及粘包。 */
public final class ModbusTcpClient implements AutoCloseable {
    private final String host;
    private final int port;
    private final Object exchangeLock = new Object();
    private volatile SocketChannel channel;
    private volatile Selector selector;

    public ModbusTcpClient(String host, int port) { this.host = host; this.port = port; }

    public byte[] exchange(byte[] request, Duration timeout) throws IOException {
        long deadline = System.nanoTime() + timeout.toNanos();
        // 只有单设备会话锁跨 I/O；不能同时持缓冲／业务锁。
        synchronized (exchangeLock) {
            try {
                connect(deadline);
                SocketChannel current = channel;
                Selector events = selector;
                transfer(current, events, ByteBuffer.wrap(request), false, deadline);
                ByteBuffer header = ByteBuffer.allocate(7);
                transfer(current, events, header, true, deadline);
                int length = ModbusTcpCodec.u16(header.array(), 4);
                if (length < 3 || length > 254) throw new IOException("MODBUS_FRAME_LENGTH_INVALID");
                ByteBuffer response = ByteBuffer.allocate(6 + length);
                response.put(header.array());
                transfer(current, events, response, true, deadline);
                byte[] result = response.array();
                ModbusTcpCodec.validateHeaders(request, result);
                return result;
            } catch (IOException | RuntimeException failure) {
                close();
                if (failure instanceof IOException io) throw io;
                throw new IOException("MODBUS_CHANNEL_FAILED", failure);
            }
        }
    }

    private void connect(long deadline) throws IOException {
        if (System.nanoTime() >= deadline || Thread.currentThread().isInterrupted())
            throw new java.net.SocketTimeoutException("MODBUS_EXCHANGE_TIMEOUT");
        if (channel != null && channel.isOpen() && channel.isConnected()) return;
        Selector events = Selector.open();
        SocketChannel current = SocketChannel.open();
        selector = events; channel = current;
        current.configureBlocking(false);
        current.socket().setTcpNoDelay(true);
        current.register(events, SelectionKey.OP_CONNECT);
        if (!current.connect(new InetSocketAddress(host, port))) {
            while (!current.finishConnect()) waitReady(current, events, SelectionKey.OP_CONNECT, deadline);
        }
    }

    private static void transfer(SocketChannel channel, Selector selector, ByteBuffer data,
                                 boolean reading, long deadline) throws IOException {
        while (data.hasRemaining()) {
            if (Thread.currentThread().isInterrupted() || System.nanoTime() >= deadline)
                throw new java.net.SocketTimeoutException("MODBUS_EXCHANGE_TIMEOUT");
            int count = reading ? channel.read(data) : channel.write(data);
            if (count < 0) throw new java.io.EOFException("MODBUS_PEER_CLOSED");
            if (count == 0) waitReady(channel, selector, reading ? SelectionKey.OP_READ : SelectionKey.OP_WRITE, deadline);
        }
    }

    private static void waitReady(SocketChannel channel, Selector selector, int interest, long deadline) throws IOException {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0 || Thread.currentThread().isInterrupted())
            throw new java.net.SocketTimeoutException("MODBUS_EXCHANGE_TIMEOUT");
        channel.keyFor(selector).interestOps(interest);
        selector.select(Math.max(1, TimeUnit.NANOSECONDS.toMillis(remaining)));
        selector.selectedKeys().clear();
    }

    /** 不等待会话锁，停机可直接关闭正在等待响应的连接。 */
    @Override public void close() {
        SocketChannel current = channel; channel = null;
        Selector events = selector; selector = null;
        if (current != null) try { current.close(); } catch (IOException ignored) { }
        if (events != null) try { events.close(); } catch (IOException ignored) { }
    }
}
