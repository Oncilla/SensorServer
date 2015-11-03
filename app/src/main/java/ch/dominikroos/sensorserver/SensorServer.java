package ch.dominikroos.sensorserver;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


class SensorServer extends WebSocketServer {

    private static final String TAG = "SensorServer";
    final private Server mContext;
    final private SensorManager mSensorManager;
    final private List<Sensor> mSensors;
    final private List<SensorHolder> mSensorHolders;
    final private Map<WebSocket, Integer> mMap;

    public SensorServer(int port, Server mContext) {
        super(new InetSocketAddress(port));

        mMap = new HashMap<>();
        this.mContext = mContext;
        this.mSensorManager = (SensorManager) mContext.getSystemService(Server.SENSOR_SERVICE);
        this.mSensors = mSensorManager.getSensorList(Sensor.TYPE_ALL);
        mSensorHolders = new ArrayList<>();
        for (int i = 0; i < mSensors.size(); i++) {
            mSensorHolders.add(new SensorHolder(mSensors.get(i)));
        }


    }

    @Override
    public void stop() throws IOException, InterruptedException {

        for (SensorHolder holder : mSensorHolders) {
            holder.unregister();
            for (WebSocket socket : holder.sockets) {
                socket.send("dis");
            }
        }
        super.stop();
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        int i = mMap.get(conn);
        mSensorHolders.get(i).removeSocket(conn);
        Log.i(TAG, conn + " closed");
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        Log.i(TAG, conn + ": " + message);
        mSensorHolders.get(Integer.parseInt(message)).addSocket(conn);
        mMap.put(conn, Integer.parseInt(message));
    }

    @Override
    public void onError(WebSocket webSocket, Exception e) {

    }


    public class SensorHolder implements SensorEventListener {
        public final List<WebSocket> sockets;
        public final Sensor sensor;
        public boolean running = false;

        public SensorHolder(Sensor sensor) {
            this.sockets = new ArrayList<>();
            this.sensor = sensor;
        }

        public void addSocket(WebSocket socket) {
            sockets.add(socket);
            if (!running) {
                mSensorManager.registerListener(this, sensor, sensor.getMinDelay());
                running = true;
            }
        }

        public void removeSocket(WebSocket socket) {
            sockets.remove(socket);
            if (sockets.size() <= 0) {
                unregister();
            }
        }

        public void unregister() {
            mSensorManager.unregisterListener(this);
            running = false;
        }

        public void sendToSubscribers(String text) {
            for (WebSocket socket : sockets) {
                socket.send(text);
            }
        }

        @Override
        public void onSensorChanged(SensorEvent sensorEvent) {


            String ret;
            if (sensorEvent.values.length == 0) {
                ret = "No values to show";
            } else {
                ret = "";
                for (int i = 0; i < sensorEvent.values.length; i++) {
                    ret += sensorEvent.values[i] + "\n";
                    Log.v(TAG, sensorEvent.values[i] + "");
                }
            }
            sendToSubscribers(ret);

        }


        @Override
        public void onAccuracyChanged(Sensor sensor, int i) {
            // unused
        }

    }
}

