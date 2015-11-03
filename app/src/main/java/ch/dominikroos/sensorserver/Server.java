package ch.dominikroos.sensorserver;

import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.IBinder;
import android.os.Vibrator;
import android.util.Log;
import android.widget.CompoundButton;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

public class Server extends Service implements CompoundButton.OnCheckedChangeListener{

    private static final String TAG = "Server";
    public static final int DEFAULT_PORT = 8081;


    public static ServerSocket socket;
    public static boolean useWebSockets;
    public Vibrator vibrator;
    public MediaPlayer mediaPlayer;
    public BoundServiceListener mlistener;

    private volatile boolean shouldContinue = true;
    private int port = DEFAULT_PORT;
    private final Server context = this;
    private final IBinder mBinder = new ServerBinder();


    SensorServer sensorServer;


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        Log.i(TAG, "onStartCommand");
        return START_STICKY;

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        closeSocket();
        Log.i(TAG, "onDestroy");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }


    private void waitForConnections() {
        final Thread t = new Thread() {
            @Override
            public void run() {
                while (shouldContinue && socket != null) {

                    Log.i(TAG, "waitForConnection: running");
                    try {
                        final Socket connection = socket.accept();
                        final Thread worker = new Thread() {
                            @Override
                            public void run() {
                                super.run();
                                try {
                                    BufferedReader input = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                                    DataOutputStream output = new DataOutputStream(connection.getOutputStream());
                                    HttpUtil httpUtil = new HttpUtil();
                                    httpUtil.httpHandler(input, output, context, vibrator, mediaPlayer);
                                    connection.close();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        };
                        worker.run();

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        };
        t.start();

        sensorServer = new SensorServer(port + 1, context);
        sensorServer.start();

    }

    public void closeSocket() {
        shouldContinue = false;
        try {
            socket.close();
            socket = null;
            sensorServer.stop();
            mediaPlayer.stop();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void openSocket(int port, boolean useWebSockets) {

        if (socket != null)
            closeSocket();

        this.port = port;
        shouldContinue = true;
        this.useWebSockets = useWebSockets;
        try {
            socket = new ServerSocket(port);
            Log.i(TAG, "socket created on port " + port);
        } catch (IOException e) {
            e.printStackTrace();
        }

        waitForConnections();

    }

    public void setActuators(MediaPlayer mediaPlayer, Vibrator vibrator) {
        this.mediaPlayer = mediaPlayer;
        this.vibrator = vibrator;

        Log.i(TAG, "" + (mediaPlayer == null));
        Log.i(TAG, "" + (vibrator == null));
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        useWebSockets = isChecked;
        Log.i(TAG,""+isChecked);
    }

    public class ServerBinder extends Binder {
        Server getService() {
            return Server.this;
        }

        public void setListener(BoundServiceListener listener) {
            mlistener = listener;
        }
    }

    public boolean isServerRunning() {
        return socket != null;
    }


    public int getPort() {
        return port;
    }
}
