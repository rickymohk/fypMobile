package com.cse.fyp.fypmobile;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
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

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

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
    private TextView debugText;
    private Switch complex;
    private EditText rtmpText;
    private EditText tcpText;
    private Button rtmpBtn;
    private Button tcpBtn;
    private Button searchBtn;
    private InetAddress ip;
    private Thread tcpThread, udpThread;
    private Socket socket;
    private OutputStream os;
    private String state;
    private String debugmsg;
    private int port = 7689;


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
        System.setProperty("java.net.preferIPv4Stack", "true");
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
        debugText = (TextView)findViewById(R.id.debugText);
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
                tcpThread = new Thread(Connection);
                tcpThread.start();
            }
        });
        /*
        if(!rtmpText.getText().toString().isEmpty())
        {
            rtmpBtn.performClick();
        }
        if(!tcpText.getText().toString().isEmpty())
        {
            tcpBtn.performClick();
        }
*/
        searchBtn = (Button)findViewById(R.id.searchBtn);
        searchBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                udpThread = new Thread(BroadcastSearch);
                udpThread.start();
            }
        });

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
                    cmd = "m" + posX + "," + posY + "\n";
                }
                else
                {
                    if(posX==0 && posY==0)
                    {
                        cmd = "";
                    }
                    else if(Math.abs(posX)>Math.abs(posY))
                    {
                        if(posX>0)
                        {
                            cmd = "d";
                        }
                        else if(posX<0)
                        {
                            cmd = "a";
                        }
                    }
                    else
                    {
                        if(posY>0)
                        {
                            cmd = "s";
                        }
                        else if(posY<0)
                        {
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
                    catch (IOException e)
                    {
                        state = "disconnected";
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

    private Runnable BroadcastSearch = new Runnable() {
        @Override
        public void run() {
            try
            {
                InetAddress BroadcastAddress = InetAddress.getLocalHost();
                Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

                while (interfaces.hasMoreElements())
                {
                    NetworkInterface networkInterface = interfaces.nextElement();

                    if (networkInterface.isLoopback())
                        continue; // Don't want to broadcast to the loopback interface

                    for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses())
                    {
                        BroadcastAddress = interfaceAddress.getBroadcast();

                        if ( BroadcastAddress == null)
                            continue;

                    }
                }

                debugmsg = BroadcastAddress.getHostAddress();
                runOnUiThread(showDebugMsg);
                DatagramSocket udpsock =  new DatagramSocket();
                udpsock.setBroadcast(true);
                byte[] msg = "FYP_KHW1602_PI_REQUEST".getBytes();
                DatagramPacket packet = new DatagramPacket(msg,msg.length,BroadcastAddress,port);
                udpsock.send(packet);

                byte[] recvBuf = new byte[1500];
                DatagramPacket recvPacket =  new DatagramPacket(recvBuf, recvBuf.length);
                udpsock.receive(recvPacket);
                String recvMsg = new String(recvPacket.getData()).trim();
                if(recvMsg.equals("FYP_KHW1602_PI_RESPONSE"))
                {
                    ip = recvPacket.getAddress();
                    runOnUiThread(UpdateUI);
                }
                udpsock.close();
            } catch (UnknownHostException e) {
                debugmsg = e.getMessage();
                runOnUiThread(showDebugMsg);
            } catch (SocketException e) {
                debugmsg = e.getMessage();
                runOnUiThread(showDebugMsg);
            } catch (IOException e) {
                debugmsg = e.getMessage();
                runOnUiThread(showDebugMsg);
            }
        }
    };

    private Runnable showDebugMsg = new Runnable() {
        @Override
        public void run() {
            debugText.setText(debugmsg);
        }
    };

    private Runnable UpdateUI = new Runnable() {
        @Override
        public void run() {
            rtmpText.setText(ip.getHostAddress());
            tcpText.setText(ip.getHostAddress());
            rtmpBtn.performClick();
            tcpBtn.performClick();
        }
    };

    private Runnable Connection=new Runnable() {
        @Override
        public void run() {
            if(state.equals("init") || state.equals("disconnected"))
            {
                try
                {
                    ip = InetAddress.getByName(tcpText.getText().toString());
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
        }
    };
}
