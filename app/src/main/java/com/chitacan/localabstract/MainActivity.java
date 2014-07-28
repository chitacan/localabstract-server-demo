package com.chitacan.localabstract;

import android.app.Activity;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import com.chitacan.localabstract.R;


public class MainActivity extends Activity
        implements View.OnClickListener, View.OnKeyListener{

    private static final int MSG_STATUS = 0;

    private LocalSocketServer mServer  = null;
    private Handler           mHandler = new UIHandler();

    private Button   mBtnStart = null;
    private Button   mBtnStop  = null;
    private Button   mBtnSend  = null;
    private TextView mStatus   = null;
    private EditText mMessage  = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mBtnStart = (Button)   findViewById(R.id.btn_start);
        mBtnStop  = (Button)   findViewById(R.id.btn_stop);
        mBtnSend  = (Button)   findViewById(R.id.btn_send);
        mStatus   = (TextView) findViewById(R.id.status);
        mMessage  = (EditText) findViewById(R.id.message);

        mBtnStart.setOnClickListener(this);
        mBtnStop .setOnClickListener(this);
        mBtnSend .setOnClickListener(this);
        mMessage .setOnKeyListener(this);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        switch (id) {
            case R.id.btn_start:
                startServer();
                break;
            case R.id.btn_stop:
                stopServer();
                break;
            case R.id.btn_send:
                sendMessage();
                break;
        }
    }

    @Override
    public boolean onKey(View view, int keyCode, KeyEvent keyEvent) {
        if (keyEvent.getAction() == keyEvent.ACTION_DOWN &&
            keyCode == keyEvent.KEYCODE_ENTER)
            sendMessage();
        return false;
    }

    private void startServer() {
        if (mServer != null && mServer.isAlive())
            return;

        mServer = new LocalSocketServer(mHandler);
        mServer.start();
    }

    private void stopServer() {
        if (mServer != null) {
            mServer.interrupt();
            mServer = null;
        }
    }

    private void sendMessage() {
        if (mMessage.getText().length() == 0) {
            Toast.makeText(this, "No Text !!", Toast.LENGTH_LONG).show();
            return;
        }

        mServer.setMessage(mMessage.getText().toString());
        mMessage.setText("");
    }

    class UIHandler extends Handler {

        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
                case MSG_STATUS:
                    String status = (String) msg.obj;
                    mStatus.setText(status);
                    break;
                default:
                    break;
            }
        }
    }

    class LocalSocketServer extends Thread {

        private static final String ADDR = "chitacan_remote";

        private LocalSocket       mSocket  = null;
        private Handler           mHandler = null;
        private LocalServerSocket mServer  = null;

        private InputStreamReader  mIn  = null;
        private OutputStreamWriter mOut = null;

        private String mMessage = null;

        public LocalSocketServer(Handler handler) {
            mHandler = handler;
            setName("local_socket_server");
        }

        @Override
        public void run() {
            super.run();
            try{
                updateStatus("server started");

                mServer = new LocalServerSocket(ADDR);
                mSocket = mServer.accept();

                if (isInterrupted())
                    return;

                mIn  = new InputStreamReader(mSocket.getInputStream());
                mOut = new OutputStreamWriter(mSocket.getOutputStream());

                updateStatus("connected");

                mOut.write(createResponse("connected"));
                mOut.flush();

                int len;
                char[] buffer = new char[2048];
                while (true) {
                    if (isInterrupted())
                        break;

                    if (mIn.ready()) {
                        len = mIn.read(buffer);
                        String req = new String(buffer, 0, len).trim();
                        String msg = req.substring(4, req.length());
                        updateStatus("received : " + msg);
                    }

                    sendResponse();
                }

                updateStatus("Loop End");
            } catch (IOException e) {
                e.printStackTrace();
                updateStatus("Exception : " + e.getMessage());
            } finally {
                closeServer();
            }
        }

        public synchronized  void setMessage(String msg) {
            mMessage = msg;
        }

        private String createResponse(String response) {
            String len = Integer.toHexString(response.length());
            int remainder = 4 - len.length();

            StringBuilder sb = new StringBuilder();
            sb.append("OKAY");

            for(int i = 0; i < remainder; i++){
                sb.append("0");
            }
            sb.append(len);
            sb.append(response);

            return sb.toString();
        }

        private void updateStatus(String status) {
            Log.d("chitacan", status);
            Message msg = mHandler.obtainMessage();
            msg.what = MSG_STATUS;
            msg.obj  = status;
            mHandler.sendMessage(msg);
        }

        private void sendResponse() throws IOException {
            if (mMessage == null) return;

            String res = createResponse(mMessage);
            mOut.write(res, 0, res.length());
            mOut.flush();
            setMessage(null);
        }

        private void closeServer() {
            try {
                if (mServer != null)
                    mServer.close();

                if (mSocket != null)
                    mSocket.close();

                mServer = null;
                mSocket = null;

                updateStatus("server close");
            } catch (IOException e) {
                e.printStackTrace();
                updateStatus("Exception on close");
            }
        }
    }
}
