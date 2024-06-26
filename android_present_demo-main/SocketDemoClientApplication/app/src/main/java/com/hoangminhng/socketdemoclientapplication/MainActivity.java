package com.hoangminhng.socketdemoclientapplication;

import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static int SERVER_PORT = 0;
    private static String SERVER_IP = "";

    private TextView tvMessages;
    private EditText etMessage, etIP, etPort;
    private Button btnSend, btnConnect;

    private Socket socket;
    private PrintWriter output;
    private BufferedReader input;
    private Thread socketThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etIP = findViewById(R.id.etIP);
        etPort = findViewById(R.id.etPort);
        tvMessages = findViewById(R.id.tvMessages);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        btnConnect = findViewById(R.id.btnConnect);

        try {
            SERVER_IP = getLocalIpAddress();
            SERVER_PORT = findAvailablePort();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        etIP.setText(SERVER_IP);
        etPort.setText(String.valueOf(SERVER_PORT));

        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String ip = etIP.getText().toString().trim();
                String portStr = etPort.getText().toString().trim();

                if (!ip.isEmpty() && !portStr.isEmpty()) {
                    int port = Integer.parseInt(portStr);
                    socketThread = new Thread(new SocketRunnable(ip, port));
                    socketThread.start();
                } else {
                    tvMessages.append("Please enter valid IP and Port\n");
                }
            }
        });

        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message = etMessage.getText().toString().trim();
                if (!message.isEmpty()) {
                    new Thread(new SendRunnable(message)).start();
                }
            }
        });
    }

    private String getLocalIpAddress() throws UnknownHostException {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        assert wifiManager != null;
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        int ipInt = wifiInfo.getIpAddress();
        return InetAddress.getByAddress(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(ipInt).array()).getHostAddress();
    }

    private int findAvailablePort() {
        try {
            ServerSocket serverSocket = new ServerSocket(0);
            int port = serverSocket.getLocalPort();
            serverSocket.close();
            return port;
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
    }

    private class SocketRunnable implements Runnable {
        private String ip;
        private int port;

        SocketRunnable(String ip, int port) {
            this.ip = ip;
            this.port = port;
        }

        @Override
        public void run() {
            try {
                socket = new Socket();
                socket.connect(new InetSocketAddress(ip, port), 10000); // 10000 ms timeout
                output = new PrintWriter(socket.getOutputStream(), true);
                input = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvMessages.append("Connected\n");
                    }
                });

                new Thread(new ReceiveRunnable()).start();
            } catch (IOException e) {
                e.printStackTrace();
                final String errorMessage = e.getMessage() != null ? e.getMessage() : "Unknown error";
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvMessages.append("Connection failed: " + errorMessage + "\n");
                    }
                });
                if (socket != null && !socket.isClosed()) {
                    try {
                        socket.close();
                    } catch (IOException ioException) {
                        ioException.printStackTrace();
                    }
                }
            }
        }
    }

    private class ReceiveRunnable implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    final String message = input.readLine();
                    if (message != null) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                tvMessages.append("server: " + message + "\n");
                            }
                        });
                    } else {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                tvMessages.append("Server disconnected\n");
                            }
                        });
                        socketThread = new Thread(new SocketRunnable(etIP.getText().toString().trim(), Integer.parseInt(etPort.getText().toString().trim())));
                        socketThread.start();
                        return;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvMessages.append("Connection lost\n");
                        }
                    });
                    return;
                }
            }
        }
    }

    private class SendRunnable implements Runnable {
        private String message;

        SendRunnable(String message) {
            this.message = message;
        }

        @Override
        public void run() {
            if (output != null) {
                output.println(message);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvMessages.append("client: " + message + "\n");
                        etMessage.setText("");
                    }
                });
            }
        }
    }
}
