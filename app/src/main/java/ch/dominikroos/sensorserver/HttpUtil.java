package ch.dominikroos.sensorserver;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.os.Vibrator;
import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

class HttpUtil implements SensorEventListener {

    private static final String TAG = "HttpUtil";
    private  boolean badRequest = false;
    private SensorManager sensorMgr;
    private SensorEvent event;

    /**
     * Handle a http request
     *
     * @param input The BufferedReader providing the input
     * @param output The DataOutputStream to write the output to
     * @param context The context
     * @param vib The vibrator
     * @param mediaPlayer The media player
     */
    public void httpHandler(BufferedReader input, DataOutputStream output, final Server context, Vibrator vib, MediaPlayer mediaPlayer) {

        int requestType = 0;
        String path;
        String out;
        try {
            String http = input.readLine();
            String tmp;
            tmp = http.toUpperCase();
            if (tmp.startsWith("GET")) {
                requestType = 1;
            }

            if (requestType == 0) {
                try {
                    output.writeBytes(constructHttpHeader(501, false));
                    output.close();
                    return;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }


            Log.i(TAG, "client requested: " + http);


            int start = http.indexOf(' ');
            int end = http.indexOf(' ', start + 1);
            path = http.substring(start + 2, end);

        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        Log.i(TAG, "client requested: " + path);


        if (path.matches("vibration(\\?pattern=((\\d+(%2C)?)+).*)?")) {
            int i = path.indexOf('=');
            if (i == -1) {
                out = buildVibratorPage(context);
            } else {

                String request = path.substring(i + 1);
                request = request.replaceAll("\\%2C", ",");
                if (request.matches("((\\d+,?)+)")) {
                    String[] pattern = request.split(",");
                    vib.vibrate(parsePattern(pattern), -1);
                    out = buildVibratorPage(context, request);
                    Log.e(TAG, request + " matched");
                } else {
                    Log.e(TAG, request + " did not matched");
                    badRequest = true;
                    out = buildVibratorPage(context);
                }
            }

        } else if (path.matches("sound(\\?loop=(false||true||trues))?")) {
            int i = path.indexOf('=');
            if (i != -1) {

                String request = path.substring(i + 1);
                if (request.matches("trues")) {
                    mediaPlayer.release();
                } else {
                    boolean reqBool = Boolean.parseBoolean(request);
                    mediaPlayer.release();
                    mediaPlayer = MediaPlayer.create(context, reqBool ? R.raw.loop : R.raw.sound);
                    mediaPlayer.setVolume(1.0f, 1.0f);
                    mediaPlayer.setLooping(reqBool);

                    mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer mediaPlayer) {
                            mediaPlayer.release();
                        }
                    });
                    context.mediaPlayer = mediaPlayer;
                    context.mlistener.setMediaPlayer(mediaPlayer);

                    mediaPlayer.start();
                }
            }
            out = buildSoundPage(context);

        } else if (path.matches("(sensors(-websocket)?)?(/\\d+)?")) {
            if (sensorMgr == null) {
                sensorMgr = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
            }

            if(path.startsWith("sensors-websocket/")){
                String end = path.split("/")[1];
                int i = Integer.parseInt(end.substring(0, end.length()));
                out = buildSensorPageWebsocket(context, i, sensorMgr.getSensorList(Sensor.TYPE_ALL).get(i).getName());
            } else if (path.startsWith("sensors/")) {
                String end = path.split("/")[1];
                int i = Integer.parseInt(end.substring(0, end.length()));
                out = buildSensorPage(context, i);
            } else {
                out = buildMainPage(context,context.useWebSockets);
            }
        } else {
            badRequest = true;
            out = buildBadRequestPage(context);
        }

        try {
            if (badRequest) {
                output.writeBytes(constructHttpHeader(400, true));
                output.writeBytes(out);
            } else {
                output.writeBytes(constructHttpHeader(200, true));
                output.writeBytes(out);
            }
            output.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        badRequest = false;
    }

    /**
     * Create a html page, which shows a bad request page
     *
     * @param context The context
     * @return a String representing a html page
     */
    private String buildBadRequestPage(Server context) {

        return context.getString(R.string.badRequestPage);
    }

    /**
     * Parse a String array to a Long array
     *
     * @param pattern A String array
     * @return a parsed Long array
     */
    private long[] parsePattern(String[] pattern) {
        long[] ret = new long[pattern.length];
        for (int i = 0; i < ret.length; i++) {
            ret[i] = Long.parseLong(pattern[i]);
        }
        return ret;
    }

    /**
     * Create a http header
     *
     * @param return_code The return code
     * @param hasBody A boolean representing if the response will have a body
     * @return a String representing a http header
     */
    private String constructHttpHeader(int return_code, boolean hasBody) {
        StringBuilder s = new StringBuilder("HTTP/1.0 ");

        switch (return_code) {
            case 200:
                s.append("200 OK");
                break;
            case 400:
                s.append("400 Bad Request");
                break;
            case 403:
                s.append("403 Forbidden");
                break;
            case 404:
                s.append("404 Not Found");
                break;
            case 500:
                s.append("500 Internal Server Error");
                break;
            default:
                s.append("501 Not Implemented");
                break;
        }

        s.append("\r\n").append("Connection: close\r\n").append("Server: SimpleHTTPtutorial v0\r\n");

        if (hasBody) {
            s.append("Content-Type: text/html\r\n");
        }
        s.append("\r\n");

        return s.toString();
    }

    /**
     * Create a html page, which shows the main page
     * This provides a list of all sensors
     *
     * @param context The context
     * @param useWebSockets
     * @return  a String representing a html page
     */
    private String buildMainPage(Context context, boolean useWebSockets) {
        StringBuilder ret = new StringBuilder(context.getString(R.string.mainPageHtmlStart));
        ret.append(context.getString(R.string.mainPageHtmlListStart));
        SensorManager sensorMgr = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        List<Sensor> sensors = sensorMgr.getSensorList(Sensor.TYPE_ALL);

        for (int i = 0; i < sensors.size(); i++) {
            if(useWebSockets){
                ret.append("<li>").append(createLink(context.getString(R.string.sensorPageWebSocket) + i, sensors.get(i).getName())).append("</li>");
            }else{
                ret.append("<li>").append(createLink(context.getString(R.string.sensorPage) + i, sensors.get(i).getName())).append("</li>");
            }
        }
        ret.append("</ul></div>").append(context.getString(R.string.mainPageHtmlEnd)).append(context.getString(R.string.mainPageHtmlListEnd));
        return ret.toString();
    }


    private String buildSensorPage(Server context, int i) {
        Sensor sensor = sensorMgr.getSensorList(Sensor.TYPE_ALL).get(i);
        sensorMgr.registerListener(this, sensor, sensor.getMinDelay());
        while (event == null){}
        SensorEvent event = this.event;
        StringBuilder s = new StringBuilder();

        for(int j = 0; j < event.values.length; j++){
            s.append(event.values[j]);
            s.append("\n");
        }

        sensorMgr.unregisterListener(this);
        event = null;

        return s.toString();

    }


    /**
     * Create a html page, which shows a sound interface
     *
     * @param context The context
     * @return a String representing a html page
     */
    private String buildSoundPage(Context context) {
        return (new StringBuilder(context.getString(R.string.mainPageHtmlStart)).append(context.getString(R.string.soundPageHtml)).append(context.getString(R.string.mainPageHtmlEnd)).toString());
    }

    /**
     * A wrapper to create a vibrator interface page.
     * The default pattern is {0,500}
     *
     * @param context The context
     * @return a String representing a html page
     */
    private String buildVibratorPage(Context context) {
        return buildVibratorPage(context, "0,500");
    }

    /**
     * Create a html page, which shows the vibrator interface
     *
     * @param context The context
     * @param pattern The vibration pattern
     * @return a String representing a html page
     */
    private String buildVibratorPage(Context context, String pattern) {
        return (new StringBuilder(context.getString(R.string.mainPageHtmlStart)).append(context.getString(R.string.vibratorPageHtmlStart)).append(pattern).append(context.getString(R.string.vibratorPageHtmlEnd)).append(context.getString(R.string.mainPageHtmlEnd)).toString());
    }

    /**
     * Create a html page, which shows a specific sensor interface
     *
     * @param context The context
     * @param i The integer representing a sensor
     * @param name The name of the sensor
     * @return a String representing a html page
     */
    private String buildSensorPageWebsocket(Server context, int i, String name) {

        return (new StringBuilder(readRawTextFile(context, R.raw.sensor_page_top))).append(String.format(readRawTextFile(context, R.raw.sensor_page), i, name, getIPAddress(true) + ":" + (context.getPort() + 1))).append(context.getString(R.string.mainPageHtmlEnd)).toString();
    }

    /**
     * Create a html link
     *
     * @param link The link address
     * @param name The name to be displayed
     * @return a String representing a html link
     */
    private String createLink(String link, String name) {
        return (new StringBuilder("<a href='/")).append(link).append("'>").append(name).append("</a>").toString();
    }


    /**
     * Get IP address from first non-localhost interface
     *
     * @param useIPv4 true=return ipv4, false=return ipv6
     * @return address or empty string
     */
    public static String getIPAddress(boolean useIPv4) {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress().toUpperCase();
                        boolean isIPv4 = sAddr.matches("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$");

                        if (useIPv4) {
                            if (isIPv4) {
                                return sAddr;
                            }
                        } else {
                            if (!isIPv4) {
                                int delim = sAddr.indexOf('%'); // drop ip6 port suffix
                                return delim < 0 ? sAddr : sAddr.substring(0, delim);
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
            Log.e(TAG, "No network interface supporting ipv4 found");
        }
        return "";
    }

    private static String readRawTextFile(Context ctx, int resId) {
        InputStream inputStream = ctx.getResources().openRawResource(resId);

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        int i;
        try {
            i = inputStream.read();
            while (i != -1) {
                byteArrayOutputStream.write(i);
                i = inputStream.read();
            }
            inputStream.close();
        } catch (IOException e) {
            return "";
        }
        return byteArrayOutputStream.toString();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        this.event = event;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
