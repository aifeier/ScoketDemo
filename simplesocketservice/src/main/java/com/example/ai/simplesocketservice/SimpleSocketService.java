package com.example.ai.simplesocketservice;

import java.io.Console;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 简单的Socket服务器，没有界面，只能接受和发送数据，接受到数据的同时加上时间戳把数据返回给客户端
 * Created by chenwanfeng on 2018/4/12.
 */

public class SimpleSocketService {


    static ExecutorService service = Executors.newFixedThreadPool(5);

    public static void main(String[] args) throws Exception {
        //UTP Socket
//        DatagramSocket udpSocket = new DatagramSocket(8887);
        try {
            // TCP Socket
            ServerSocket serverSocket = new ServerSocket(8086);
            System.out.println("start Socket server");
            while (true) {
                if (serverSocket.isClosed())
                    break;
                Socket socket = serverSocket.accept();
                service.execute(new SocketRunnable(socket));
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Socket server stopped");
    }

    private static class SocketRunnable implements Runnable {
        Socket socket;
        int port;
        InputStream inputStream;
        OutputStream outputStream;

        public SocketRunnable(Socket socket) {
            if (null == socket)
                return;
            this.socket = socket;
            this.port = socket.getPort();
            try {
                this.inputStream = socket.getInputStream();
                this.outputStream = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.println("成功连接一个客户端: HostAddress=" + socket.getInetAddress().getHostAddress() + "，Port=" + this.port);
        }

        @Override
        public void run() {

            byte[] temp = new byte[1024 * 8];
            byte[] buf;
            while (true) {
                if (null == socket || !socket.isConnected() || socket.isClosed() || null == inputStream || null == outputStream)
                    break;

                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                try {
                    int length = inputStream.read(temp);
                    if (-1 == length) {
                        // 客户端已经断开连接
                        break;
                    }
                    if (0 > length) {
                        // 没有数据
                        continue;
                    }
                    buf = new byte[length];
                    System.arraycopy(temp, 0, buf, 0, length);
                    String getMsg = new String(buf, Charset.forName("UTF-8"));
                    System.out.println("-----------inputStream--------------\n接收到来自客户端数据，内容为：" + getMsg + "\n-----------end--------------");
                    outputStream.write(("服务端成功接收到数据 " + System.currentTimeMillis() + "\n接收到的数据内容：" + getMsg).getBytes());
                    outputStream.flush();
                    System.out.println("-----------outputStream--------------\n成功返回数据给客户端 Port=" + this.port + "\n-----------end--------------");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (null != socket)
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            System.out.println("客户端: Port=" + this.port + "已经断开连接");
        }
    }
}
