package com.bluetooth;

import com.bluetooth.Bluetooth.ServerOrCilent;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

public class chatActivity extends Activity implements OnItemClickListener ,OnClickListener{
	/** Called when the activity is first created. */

	private Button disconnectButton;
	Context mContext;

	/* 一些常量，代表服务器的名称 */
	public static final String PROTOCOL_SCHEME_L2CAP = "btl2cap";
	public static final String PROTOCOL_SCHEME_RFCOMM = "btspp";
	public static final String PROTOCOL_SCHEME_BT_OBEX = "btgoep";
	public static final String PROTOCOL_SCHEME_TCP_OBEX = "tcpobex";

	private BluetoothServerSocket mserverSocket = null;
	private ServerThread startServerThread = null;
	private clientThread clientConnectThread = null;
	private BluetoothSocket socket = null;
	private BluetoothDevice device = null;
	private readThread mreadThread = null;
	private BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

	private SensorManager mSensorManager;
	private Sensor mSensor;
	private TextView mTextView;
	private FrameLayout mLayout;
	private Button op;
	private Button ed;
	private Button check;
	private float []realValues = new float[2];
	private float []standardValues = new float[2];
	private float []dealValues = new float[2];
	private boolean isInit;

	private boolean con;
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(com.bluetooth.R.layout.chat);
		mContext = this;
		init();

		tv_init();
	}

	private void init() {

		disconnectButton= (Button)findViewById(com.bluetooth.R.id.btn_disconnect);
		disconnectButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				// TODO Auto-generated method stub
				if (Bluetooth.serviceOrCilent == ServerOrCilent.CILENT)
				{
					shutdownClient();
				}
				else if (Bluetooth.serviceOrCilent == ServerOrCilent.SERVICE)
				{
					shutdownServer();
				}
				Bluetooth.isOpen = false;
				Bluetooth.serviceOrCilent=ServerOrCilent.NONE;
				Toast.makeText(mContext, "已断开连接！", Toast.LENGTH_SHORT).show();
			}
		});
	}

	private void tv_init(){

		mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
		mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		mLayout = (FrameLayout)findViewById(R.id.FL);
		op = (Button)findViewById(R.id.op);
		ed = (Button)findViewById(R.id.ed);
		check = (Button)findViewById(R.id.check);
		con = false;

		//mSensorManager.registerListener(myListener, mSensor, SensorManager.SENSOR_DELAY_NORMAL);
		isInit = false;

		op.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				switch (event.getAction()) {
					case MotionEvent.ACTION_DOWN:
						con = true;
						mSensorManager.registerListener(myListener, mSensor, SensorManager.SENSOR_DELAY_NORMAL);
						break;
				}
				return false;
			}
		});
		ed.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				switch (event.getAction()) {
					case MotionEvent.ACTION_DOWN:
						con = false;
						mSensorManager.unregisterListener(myListener);
						break;
				}
				return false;
			}
		});


		check.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				switch (event.getAction()) {
					case MotionEvent.ACTION_DOWN:
						AlertDialog dialog = new AlertDialog.Builder(mContext).setTitle("检查").setMessage("请将手机置于水平位置，便于校准").setPositiveButton("取消",
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog, int whichButton) {}
								}).setNegativeButton("确定", new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int whichButton) {
								Toast.makeText(mContext, "下次刷新将重新校准！", Toast.LENGTH_SHORT).show();
								isInit=false;
							}
						}).create();    //创建对话框
						dialog.show();          //显示对话框
						break;
				}
				return false;
			}
		});
		mTextView = new TextView(this);
		mTextView.setText("");
		mTextView.setTextSize(20);
		mTextView.setTextColor(Color.RED);
		mTextView.setGravity(Gravity.CENTER);
		mLayout.addView(mTextView);
	}

	@Override
	public synchronized void onPause() {
		if(con) {
			con = false;
			mSensorManager.unregisterListener(myListener);
		}
		super.onPause();
	}

	//方向传感器改变方向时
	final SensorEventListener myListener = new SensorEventListener() {
		public void onSensorChanged(SensorEvent event) {
			// TODO Auto-generated method stub
			realValues[0] = event.values[0];
			realValues[1] = event.values[1];
			if(!isInit){
				standardValues[0] = event.values[0];
				standardValues[1] = event.values[1];
				isInit = true;
			}
			dealValues[0] = realValues[0] - standardValues[0];
			dealValues[1] = realValues[1] - standardValues[1];
			String view =
					"standardValues: " + standardValues[0] + " , y: " + standardValues[1] + "\n"
							+
							"realValues: " + realValues[0] + " , y: " + realValues[1] + "\n"
							+
							"dealValues: " + dealValues[0] + " , y: " + dealValues[1] + "\n";
			view += "Control: ";
			if(dealValues[1]<-2.0) {
				view += "左";
				if(dealValues[0]>=-2&&dealValues[0]<=2)
					sendMessageHandle("l");
				else if(dealValues[0]<-2)
					sendMessageHandle("u");
				else if(dealValues[0]>2)
					sendMessageHandle("d");
			}
			else if(dealValues[1]>2) {
				view += "右";
				if(dealValues[0]>=-2&&dealValues[0]<=2)
					sendMessageHandle("r");
				else if(dealValues[0]<-2)
					sendMessageHandle("u");
				else if(dealValues[0]>2)
					sendMessageHandle("d");
			}
			if(dealValues[0]<-2) {
				view += "前";
				if(dealValues[1]>=-2&&dealValues[1]<=2)
					sendMessageHandle("u");
			}
			else if(dealValues[0]>2) {
				view += "后";
				if(dealValues[1]>=-2&&dealValues[1]<=2)
					sendMessageHandle("d");
			}
			if(dealValues[0]>=-2&&dealValues[0]<=2&&dealValues[1]>=-2&&dealValues[1]<=2){
				view += "停";
				sendMessageHandle("s");
			}
			mTextView.setText(view);
		}
		public void onAccuracyChanged(Sensor sensor, int accuracy) {}
	};

	@Override
	public synchronized void onResume() {
		super.onResume();
		if(Bluetooth.isOpen)
		{
			Toast.makeText(mContext, "连接已经打开，可以通信。如果要再建立连接，请先断开！", Toast.LENGTH_SHORT).show();
			return;
		}
		if(Bluetooth.serviceOrCilent==ServerOrCilent.CILENT)
		{
			String address = Bluetooth.BlueToothAddress;
			if(!address.equals("null"))
			{
				device = mBluetoothAdapter.getRemoteDevice(address);
				clientConnectThread = new clientThread();
				clientConnectThread.start();
				Bluetooth.isOpen = true;
			}
			else
			{
				Toast.makeText(mContext, "address is null !", Toast.LENGTH_SHORT).show();
			}
		}
		else if(Bluetooth.serviceOrCilent==ServerOrCilent.SERVICE)
		{
			startServerThread = new ServerThread();
			startServerThread.start();
			Bluetooth.isOpen = true;
		}
	}
	//开启客户端
	private class clientThread extends Thread {
		public void run() {
			try {
				//创建一个Socket连接：只需要服务器在注册时的UUID号
				socket = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
				//连接
				Message msg2 = new Message();
				msg2.obj = "请稍候，正在连接服务器:"+Bluetooth.BlueToothAddress;
				msg2.what = 0;

				socket.connect();

				Message msg = new Message();
				msg.obj = "已经连接上服务端！可以发送信息。";
				msg.what = 0;

				//启动接受数据
				mreadThread = new readThread();
				mreadThread.start();
			}
			catch (IOException e)
			{
				Log.e("connect", "", e);
				Message msg = new Message();
				msg.obj = "连接服务端异常！断开连接重新试一试。";
				msg.what = 0;
			}
		}
	};

	//开启服务器
	private class ServerThread extends Thread {
		public void run() {

			try {
				/* 创建一个蓝牙服务器 
				 * 参数分别：服务器名称、UUID	 */
				mserverSocket = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(PROTOCOL_SCHEME_RFCOMM,
						UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));

				Log.d("server", "wait cilent connect...");

				Message msg = new Message();
				msg.obj = "请稍候，正在等待客户端的连接...";
				msg.what = 0;
				
				/* 接受客户端的连接请求 */
				socket = mserverSocket.accept();
				Log.d("server", "accept success !");

				Message msg2 = new Message();
				String info = "客户端已经连接上！可以发送信息。";
				msg2.obj = info;
				msg.what = 0;
				//启动接受数据
				mreadThread = new readThread();
				mreadThread.start();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	};
	/* 停止服务器 */
	private void shutdownServer() {
		new Thread() {
			public void run() {
				if(startServerThread != null)
				{
					startServerThread.interrupt();
					startServerThread = null;
				}
				if(mreadThread != null)
				{
					mreadThread.interrupt();
					mreadThread = null;
				}
				try {
					if(socket != null)
					{
						socket.close();
						socket = null;
					}
					if (mserverSocket != null)
					{
						mserverSocket.close();/* 关闭服务器 */
						mserverSocket = null;
					}
				} catch (IOException e) {
					Log.e("server", "mserverSocket.close()", e);
				}
			};
		}.start();
	}
	/* 停止客户端连接 */
	private void shutdownClient() {
		new Thread() {
			public void run() {
				if(clientConnectThread!=null)
				{
					clientConnectThread.interrupt();
					clientConnectThread= null;
				}
				if(mreadThread != null)
				{
					mreadThread.interrupt();
					mreadThread = null;
				}
				if (socket != null) {
					try {
						socket.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					socket = null;
				}
			};
		}.start();
	}
	//发送数据
	private void sendMessageHandle(String msg)
	{
		if (socket == null)
		{
			Toast.makeText(mContext, "没有连接", Toast.LENGTH_SHORT).show();
			return;
		}
		try {
			OutputStream os = socket.getOutputStream();
			os.write(msg.getBytes());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	//读取数据
	private class readThread extends Thread {
		public void run() {

			byte[] buffer = new byte[1024];
			int bytes;
			InputStream mmInStream = null;

			try {
				mmInStream = socket.getInputStream();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			while (true) {
				try {
					// Read from the InputStream
					if( (bytes = mmInStream.read(buffer)) > 0 )
					{
						byte[] buf_data = new byte[bytes];
						for(int i=0; i<bytes; i++)
						{
							buf_data[i] = buffer[i];
						}
						String s = new String(buf_data);
						Message msg = new Message();
						msg.obj = s;
						msg.what = 1;
						//LinkDetectedHandler.sendMessage(msg);
					}
				} catch (IOException e) {
					try {
						mmInStream.close();
					} catch (IOException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
					break;
				}
			}
		}
	}
	@Override
	protected void onDestroy() {
		super.onDestroy();

		if (Bluetooth.serviceOrCilent == ServerOrCilent.CILENT)
		{
			shutdownClient();
		}
		else if (Bluetooth.serviceOrCilent == ServerOrCilent.SERVICE)
		{
			shutdownServer();
		}
		Bluetooth.isOpen = false;
		Bluetooth.serviceOrCilent = ServerOrCilent.NONE;
	}
	public class SiriListItem {
		String message;
		boolean isSiri;

		public SiriListItem(String msg, boolean siri) {
			message = msg;
			isSiri = siri;
		}
	}
	@Override
	public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
		// TODO Auto-generated method stub
	}
	@Override
	public void onClick(View arg0) {
		// TODO Auto-generated method stub
	}
	public class deviceListItem {
		String message;
		boolean isSiri;

		public deviceListItem(String msg, boolean siri) {
			message = msg;
			isSiri = siri;
		}
	}
}