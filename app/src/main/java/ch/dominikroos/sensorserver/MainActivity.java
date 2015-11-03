package ch.dominikroos.sensorserver;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Vibrator;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity implements BoundServiceListener, View.OnClickListener {

    public final String TAG = "MainActivity";

    private final BoundServiceListener mListener = this;
    private Server mService;
    private EditText mEditTextPort;
    private TextView mTextViewIp;
    private Vibrator mVibrator;
    private MediaPlayer mMediaPlayer;
    private boolean mServerRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mEditTextPort = (EditText) findViewById(R.id.et_port);
        mTextViewIp = (TextView) findViewById(R.id.tv_ip);
        mMediaPlayer = new MediaPlayer();

        mVibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(this);

    }

    @Override
    protected void onPause() {
        super.onPause();
        unbindService(mConnection);
    }

    @Override
    protected void onResume() {
        super.onResume();
        service();
        setViews();
    }

    /**
     * Start the service
     */
    private void service() {
        Intent intent = new Intent(this, Server.class);
        startService(intent);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }


    private final ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Server.ServerBinder binder = (Server.ServerBinder) service;
            mService = binder.getService();
            ((Switch)findViewById(R.id.switch2)).setOnCheckedChangeListener(mService);

            setViews();
            binder.setListener(mListener);
        }


        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mService = null;
        }
    };

    /**
     * Make views editable given the current state
     */
    private void setViews() {
        boolean running = (mService != null) && mService.isServerRunning();
        String ip = HttpUtil.getIPAddress(true);
        if (running) {
            mMediaPlayer = mService.mediaPlayer;
            mEditTextPort.setText(mService.getPort() + "");
            mEditTextPort.setEnabled(false);
            mTextViewIp.setText(Html.fromHtml("<a href=\"http://" + ip + ":" + mService.getPort() + "\">" + ip + "</a>"));
            mTextViewIp.setMovementMethod(LinkMovementMethod.getInstance());
            mServerRunning = true;
            ((FloatingActionButton)findViewById(R.id.fab)).setImageDrawable(ContextCompat.getDrawable(this, android.R.drawable.ic_menu_close_clear_cancel));
        } else {
            mTextViewIp.setText(ip);
            mTextViewIp.setMovementMethod(null);
            mEditTextPort.setClickable(false);
            mEditTextPort.setEnabled(true);
            mServerRunning = false;
            ((FloatingActionButton)findViewById(R.id.fab)).setImageDrawable(ContextCompat.getDrawable(this, android.R.drawable.ic_media_play));
        }
    }

    @Override
    public void onClick(View view) {

        int port = Integer.parseInt((mEditTextPort).getText().toString());

        if (port <= 1024 || port > 9998) {
            Toast.makeText(this, "Port: " + port + " not in range", Toast.LENGTH_SHORT).show();
            port = 8081;
            (mEditTextPort).setText(Server.DEFAULT_PORT + "");
        }
        Log.i(TAG, port + "");
        Intent intent = new Intent(this, Server.class);

        if(mService == null)
            return;

        if (!mServerRunning) {
            service();
            mEditTextPort.setEnabled(false);
            mService.setActuators(mMediaPlayer, mVibrator);
            mService.openSocket(port, ((Switch) findViewById(R.id.switch2)).isChecked());
            mServerRunning = true;
            ((FloatingActionButton)view).setImageDrawable(ContextCompat.getDrawable(this, android.R.drawable.ic_menu_close_clear_cancel));
            Snackbar.make(view, "Server is now running", Snackbar.LENGTH_SHORT).show();
        } else {
            mService.closeSocket();
            mEditTextPort.setEnabled(true);
            stopService(intent);
            mServerRunning = false;
            ((FloatingActionButton)view).setImageDrawable(ContextCompat.getDrawable(this, android.R.drawable.ic_media_play));
            Snackbar.make(view, "Server has stopped", Snackbar.LENGTH_SHORT).show();
        }

        setViews();

    }

    public void setMediaPlayer(MediaPlayer mediaPlayer) {
        this.mMediaPlayer = mediaPlayer;
    }

    public void stopVibration(View view) {
        mVibrator.cancel();
    }

    public void stopSound(View view) {
        mMediaPlayer.stop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_about) {
            Intent intent = new Intent(this, About.class);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

}
