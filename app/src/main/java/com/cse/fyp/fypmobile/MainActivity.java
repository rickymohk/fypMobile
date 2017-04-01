package com.cse.fyp.fypmobile;

import android.app.Activity;
import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewDebug;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;

import io.vov.vitamio.LibsChecker;
import io.vov.vitamio.MediaPlayer;
import io.vov.vitamio.widget.MediaController;
import io.vov.vitamio.widget.VideoView;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private String path;
    //private HashMap<String, String> options;
    private VideoView mVideoView;
    private MediaController mc;
    private Button joyStick;
    private float dx,dy;
    private TextView posText;
    private Switch complex;
    private EditText rtmpText;
    private EditText tcpText;
    private Button rtmpBtn;
    private Button tcpBtn;
    private InetAddress ip;
    private Thread thread;
    private Socket socket;
    private OutputStream os;
    private String state;

    private SharedPreferences sp;
    private static final String data = "DATA";
    private static final String rtmpField = "RTMP";
    private static final String tcpField = "TCP";

    public void readData()
    {
        sp = getSharedPreferences(data,0);
        rtmpText.setText(sp.getString(rtmpField,""));
        tcpText.setText(sp.getString(tcpField,""));
    }

    public void saveData()
    {
        sp = getSharedPreferences(data,0);
        sp.edit().putString(rtmpField,rtmpText.getText().toString()).putString(tcpField,tcpText.getText().toString()).commit();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!LibsChecker.checkVitamioLibs(this))
            return;
        setContentView(R.layout.activity_main);
        mVideoView = (VideoView) findViewById(R.id.vitamio_videoView);
       // path = "rtmp://192.168.1.107:1935/rtmp/live";
        /*options = new HashMap<>();
        options.put("rtmp_playpath", "");
        options.put("rtmp_swfurl", "");
        options.put("rtmp_live", "1");
        options.put("rtmp_pageurl", "");*/
        //mVideoView.setVideoPath(path);
        //mVideoView.setVideoURI(Uri.parse(path), options);
        //mVideoView.requestFocus();
        state = "init";
        mVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mediaPlayer) {
                mediaPlayer.setPlaybackSpeed(1.0f);
                mVideoView.start();
                mediaPlayer.start();
            }

        });

        mVideoView.setOnBufferingUpdateListener(new MediaPlayer.OnBufferingUpdateListener() {
            @Override
            public void onBufferingUpdate(MediaPlayer mp, int percent) {
                mp.start();
                mVideoView.start();
            }
        });
        mVideoView.start();

        posText = (TextView)findViewById(R.id.posText);
        complex = (Switch) findViewById(R.id.complexSwitch);
        rtmpText = (EditText)findViewById(R.id.rtmpText);
        tcpText = (EditText)findViewById(R.id.tcpText);
        rtmpBtn = (Button)findViewById(R.id.rtmpBtn);
        tcpBtn = (Button)findViewById(R.id.tcpBtn);
        tcpText.setOnFocusChangeListener(onBlur);
        rtmpText.setOnFocusChangeListener(onBlur);
        readData();
        rtmpBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                path = "rtmp://"+rtmpText.getText()+":1935/rtmp/live";
                mVideoView.setVideoPath(path);
                mVideoView.requestFocus();

            }
        });

        tcpBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                thread = new Thread(Connection);
                thread.start();
            }
        });
        if(!rtmpText.getText().toString().isEmpty())
        {
            rtmpBtn.performClick();
        }
        if(!tcpText.getText().toString().isEmpty())
        {
            tcpBtn.performClick();
        }

        joyStick = (Button)findViewById(R.id.joystick);
        joyStick.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                View parent = (View)v.getParent();
                float width = parent.getWidth();
                float height = parent.getHeight();
                float centerX = (width-v.getWidth())/2;
                float centerY = (height-v.getHeight())/2;

                switch (event.getAction()) {

                    case MotionEvent.ACTION_DOWN:

                        dx = v.getX() - event.getRawX();
                        dy = v.getY() - event.getRawY();
                        break;

                    case MotionEvent.ACTION_MOVE:
                        double x = (event.getRawX() + dx)-centerX;
                        double y = (event.getRawY() + dy)-centerY;
                        double z = Math.sqrt(x*x+y*y);
                        double arg = Math.atan2(y,x);
                        if(z>v.getWidth()/2)z=v.getWidth()/2;
                        v.animate().x((float)(z*Math.cos(arg)+centerX)).y((float)(z*Math.sin(arg)+centerY)).setDuration(0).start();
                        break;
                    case MotionEvent.ACTION_UP:
                        v.animate().x(centerX).y(centerY).setDuration(0).start();
                        break;
                    default:
                        return false;
                }
                int posX =  (int)Math.floor(v.getX()-centerX);
                int posY = (int)Math.floor(v.getY()-centerY);
                String cmd = "";
                if(complex.isChecked())
                {
                    posText.setText(posX+","+ posY);
                    cmd = "m" + posX + "," + posY + "\n";
                }
                else
                {
                    if(posX==0 && posY==0)
                    {
                        posText.setText("0");
                        cmd = "";
                    }
                    else if(Math.abs(posX)>Math.abs(posY))
                    {
                        if(posX>0)
                        {
                            posText.setText("→");
                            cmd = "d";
                        }
                        else if(posX<0)
                        {
                            posText.setText("←");
                            cmd = "a";
                        }
                    }
                    else
                    {
                        if(posY>0)
                        {
                            posText.setText("↓");
                            cmd = "s";
                        }
                        else if(posY<0)
                        {
                            posText.setText("↑");
                            cmd = "w";
                        }
                    }

                }
                if(!cmd.isEmpty() && state.equals("connected"))
                {
                    try
                    {
                        os.write(cmd.getBytes());
                    }
                    catch (Exception e)
                    {

                    }
                }

                return true;
            }
        });
    }

    private View.OnFocusChangeListener onBlur = new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            if(!hasFocus)
            {
                InputMethodManager inputMethodManager =(InputMethodManager)getSystemService(Activity.INPUT_METHOD_SERVICE);
                inputMethodManager.hideSoftInputFromWindow(v.getWindowToken(), 0);
                saveData();
            }
        }
    };

    private Runnable Connection=new Runnable() {
        @Override
        public void run() {
            try
            {

                ip = InetAddress.getByName(tcpText.getText().toString());
                int port = 7689;
                try
                {
                    socket = new Socket(ip,port);
                    if(socket.isConnected()) {
                        state = "connected";
                        os = socket.getOutputStream();
                    }
                }
                catch(Exception e)
                {
                    state = e.getMessage().toString();
                }
            }
            catch(Exception e)
            {
                state = e.getMessage().toString();
            }
        }
    };
}
