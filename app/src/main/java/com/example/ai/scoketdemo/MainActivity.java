package com.example.ai.scoketdemo;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    // 本机IP
    private final String host = "192.168.0.61";
    // 端口
    private final int port = 8086;
    // 超时时间
    private final int timeOut = 15000;

    private ExecutorService inputExecutor, outputExecutor;

    enum SocketState {
        Connecting,
        UnConnected,
        Connected,
        UnKnow
    }

    /*请求对立*/
    private LinkedBlockingQueue<byte[]> outputMeg;

    private SocketState socketState = SocketState.UnConnected;
    /*Socket实例*/
    private Socket socket;
    /*Socket的写入/发送数据流，通过这个可以向服务器发送请求*/
    private InputStream inputStream;
    /*Socket的读取/接收数据流，通过监听这个可以拿到服务器发送的请求*/
    private OutputStream outputStream;

    private EditText editText;

    private TextView log;
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        outputMeg = new LinkedBlockingQueue<>();
        log = findViewById(R.id.log);
        editText = findViewById(R.id.editText);
        log.setMovementMethod(ScrollingMovementMethod.getInstance());
        findViewById(R.id.connect).setOnClickListener(this);
        findViewById(R.id.disconnect).setOnClickListener(this);
        findViewById(R.id.sendHeart).setOnClickListener(this);
        findViewById(R.id.send).setOnClickListener(this);
        findViewById(R.id.sendText).setOnClickListener(this);

    }


    private void addLog(CharSequence msg) {
        if (null != log) {
            StringBuffer stringBuffer = new StringBuffer(log.getText());
            stringBuffer.append("\n");
            stringBuffer.append(msg);
            log.setText(stringBuffer.toString());
        }
    }

    private void addToLog(CharSequence msg) {
        if (null == handler) {
            handler = new Handler(getMainLooper()) {
                @Override
                public void dispatchMessage(Message msg) {
                    if (null != msg) {
                        addLog((CharSequence) msg.obj);
                    }
                }
            };
        }
        Message message = Message.obtain();
        message.what = 0;
        message.obj = msg;
        handler.sendMessage(message);
    }


    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.connect:
                connect();
                break;
            case R.id.disconnect:
                disConnect();
                break;
            case R.id.sendHeart:
                //心跳，无数据返回
                byte[] heart = {0, 0, 0, 18, 0, 0, 0, 1, 0, 0, 0, 113, 0, 0, 0, 0, 10, 10};
                sendMessage(heart);
                break;
            case R.id.send:
                /*发送数据*/
                // 登陆，有数据返回
                byte[] cmd = {0, 0, 0, -85, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 0};
                String data =
                        "chenwf aipu 5 ui+ghXgIWmwy2aYCiE+CDw== 2 0 5.0.36 11\n" +
                                "ibundleid:\n" +
                                "optype:1\n" +
                                "idevpush:\n" +
                                "smscode:\n" +
                                "userdevice:unknown\n" +
                                "opdata:unknown\n" +
                                "ipushid:18071adc032d706cf56\n" +
                                "\n";
                byte[] login = new byte[cmd.length + data.getBytes().length];
                System.arraycopy(data.getBytes(), 0, login, cmd.length, data.getBytes().length);
                System.arraycopy(cmd, 0, login, 0, cmd.length);

                sendMessage(login);
                break;
            case R.id.sendText:
                sendMessage(getEditText().getBytes());
                break;
        }
    }

    private void sendMessage(byte[] bytes) {
        outputMeg.add(bytes);
        checkAndStartSocket();
    }

    // 拿到输入的内容
    private String getEditText() {
        if (null != editText && !TextUtils.isEmpty(editText.getText())) {
            return editText.getText().toString();
        }

        return "没有数据";
    }

    /*检查Socket的状态，关闭的时候自动打开*/
    private void checkAndStartSocket() {
        boolean state = null != socket && socket.isConnected();
        if (!state) {
            disConnect();
            connect();
        }
    }

    /*断开连接*/
    private void disConnect() {
        if (null != inputExecutor) {
            inputExecutor.shutdownNow();
            inputExecutor = null;
        }
        if (null != outputExecutor) {
            outputExecutor.shutdownNow();
            outputExecutor = null;
        }
        stopSocket();
    }

    private void stopSocket() {
        if (null != socket && socket.isConnected()) {
            try {
                socket.close();
                socket = null;
                inputStream = null;
                outputStream = null;
                addLog("Socket连接已断开");
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            addLog("Socket没有连接");
        }
    }

    /*连接Socket*/
    private void connect() {
        if (null != socket && socket.isConnected()) {
            addLog("Socket已连接");
            return;
        }
        new AsyncTask<String, Void, SocketState>() {

            @Override
            protected SocketState doInBackground(String... strings) {
                return connectSocket(host, port, timeOut);
            }

            @Override
            protected void onPostExecute(SocketState socketState) {
                addLog("Socket连接" + host + ":" + port + "结果为: " + (SocketState.Connected == socketState ? "连接成功" : "连接失败"));
                intReadWriteExecutor();
            }
        }.execute();
    }

    private void intReadWriteExecutor() {
        if (SocketState.Connected == socketState && null != socket && socket.isConnected()) {
            if (null == inputExecutor) {
                inputExecutor = Executors.newSingleThreadExecutor();
                inputExecutor.execute(new InputRunnable());
            }
            if (null == outputExecutor) {
                outputExecutor = Executors.newSingleThreadExecutor();
                outputExecutor.execute(new OutputRunnable());
            }
        }
    }

    private SocketState connectSocket(String host, int port, int timeOut) {
        if (SocketState.Connecting == socketState) {
            //正在链接...
            return socketState;
        }

        if (SocketState.Connected == socketState && null != socket && socket.isConnected()) {
            // 已经链接成功
            return socketState;
        }

        try {
            socketState = SocketState.Connecting;
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), timeOut);
            inputStream = socket.getInputStream();
            outputStream = socket.getOutputStream();
            socketState = socket.isConnected() ? SocketState.Connected : SocketState.UnConnected;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {

        }
        return socketState;
    }

    private class InputRunnable implements Runnable {
        @Override
        public void run() {
            byte[] tmp = new byte[1024 * 8];
            byte[] buf;
            while (true) {
                if (null != socket && socket.isConnected() && !socket.isClosed() && null != inputStream) {
                    try {
                        int size = inputStream.read(tmp);
                        if (0 < size) {
                            buf = new byte[size];
                            System.arraycopy(buf, 0, tmp, 0, size);
                            String msg = "接收到的数据长度 " + size + "byte 内容：：\n" + new String(buf, Charset.forName("UTF-8"));
                            Log.e("Socket input: ", msg);
                        }
//                        addToLog(msg);
                        if (-1 == size) {
                            // 已经断开连接，需要重新连接、
                            addToLog("连接已断开，开始重新连接");
                            stopSocket();
                            connect();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private class OutputRunnable implements Runnable {
        @Override
        public void run() {
            while (true) {
                if (null != socket && socket.isConnected() && !socket.isClosed() && null != inputStream) {
                    if (outputMeg.size() <= 0)
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    if (outputMeg.size() > 0)
                        try {
                            byte[] msg = outputMeg.poll();
                            outputStream.write(msg);
                            outputStream.flush();
                            String log = "发送的数据：\n" + new String(msg, Charset.forName("UTF-8"));
//                            addToLog(log);
                            Log.e("Socket output", log);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                }
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

}
