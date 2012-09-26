package Dont.Irradiate.Me.Bro;

import java.util.List;
import orbotix.robot.app.StartupActivity;
import orbotix.robot.base.DeviceAsyncData;
import orbotix.robot.base.DeviceMessenger;
import orbotix.robot.base.DeviceSensorsAsyncData;
import orbotix.robot.base.DriveAlgorithm;
import orbotix.robot.base.FrontLEDOutputCommand;
import orbotix.robot.base.Robot;
import orbotix.robot.base.RobotControl;
import orbotix.robot.base.RobotProvider;
import orbotix.robot.base.RollCommand;
import orbotix.robot.base.SetDataStreamingCommand;
import orbotix.robot.base.TiltDriveAlgorithm;
import orbotix.robot.sensor.DeviceSensorsData;
import orbotix.robot.sensor.LocatorData;
import orbotix.robot.widgets.ControllerActivity;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.util.FloatMath;
import android.util.Log;
import android.view.View;

/**
* Connects to an available Sphero robot, and then flashes its LED.
*/
public class MainActivity extends ControllerActivity
{
	protected PowerManager.WakeLock mWakeLock;
	private final static int TOTAL_PACKET_COUNT = 200;
	private final static int PACKET_COUNT_THRESHOLD = 50;
	private int mPacketCounter;
	private final static int BOUNDARY_DISTANCE_FROM_CENTER_CM = 200;
	private final static int BACK_IN_BOUNDS_FROM_CENTER_CM = 30;
	private float rRadius;
	private double rAngle;
	private boolean roll_back = false;
	
	private static final String TAG = "Movement"; //tag of log
	
	/**
	* ID for launching the StartupActivity for result to connect to the robot
	*/
	private final static int STARTUP_ACTIVITY = 0;
	
	/**
	* The Sphero Robot
	*/
	private Robot mRobot;
	private String robot_id;
	private RobotControl robot_control;
	private DriveAlgorithm drive_algorithm;
	
	private SensorManager sensor_manager;
	private Sensor accelerometer;
	
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		this.sensor_manager = (SensorManager)getSystemService(SENSOR_SERVICE);
		this.accelerometer = this.sensor_manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
	        
        /* will make the screen be always on until this Activity gets destroyed. */
        final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        this.mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "My Tag");
        this.mWakeLock.acquire();
	}
	
	public void launchGame(View v){
		Log.d(TAG, "pressed go button");
		Intent i = new Intent(this, Game.class);
		i.putExtra("ROBOT_ID", robot_id);
		startActivity(i);
	}
	
	public void calibrateSphero(View v){
		
	}
	
	@Override
	protected void onStart() {
		super.onStart();
		//Launch the StartupActivity to connect to the robot
		Intent i = new Intent(this, StartupActivity.class);
		startActivityForResult(i, STARTUP_ACTIVITY);
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		Log.v("connection", "successful connection");
		if(requestCode == STARTUP_ACTIVITY && resultCode == RESULT_OK){
			//Get the connected Robot
			final String robot_id = data.getStringExtra(StartupActivity.EXTRA_ROBOT_ID);
			if(robot_id != null && !robot_id.equals("")){
				mRobot = RobotProvider.getDefaultProvider().findRobot(robot_id);
				robot_control = RobotProvider.getDefaultProvider().getRobotControl(mRobot);
				robot_control.setRGBColor(255, 0, 255);
				drive_algorithm = new TiltDriveAlgorithm();
				drive_algorithm.speedScale = .2;
				this.robot_control = RobotProvider.getDefaultProvider().getRobotControl(mRobot);
				this.robot_control.setDriveAlgorithm(drive_algorithm);
				robot_control.setRGBColor(0, 255, 255);
				sensor_manager.registerListener(this.accelerometer_listener, this.accelerometer, SensorManager.SENSOR_DELAY_GAME);
				
				//FrontLEDOutputCommand.sendCommand(mRobot, 255.0f);
				requestDataStreaming();
				
				//Set the AsyncDataListener that will process each response.
				DeviceMessenger.getInstance().addAsyncDataListener(mRobot, mDataListener);
			}
		}
	}
		
	@Override
	protected void onStop() {
		super.onStop();
		robot_control.stopMotors();
		FrontLEDOutputCommand.sendCommand(mRobot, 0);
		
		//Disconnect Robot
		RobotProvider.getDefaultProvider().removeAllControls();
        RobotProvider.getDefaultProvider().disconnectControlledRobots();
		mRobot = null;
	}
	
	private SensorEventListener accelerometer_listener = new SensorEventListener() {
	
		@Override
		public void onAccuracyChanged(Sensor arg0, int arg1) {
		
		}
	
		@Override
		public void onSensorChanged(SensorEvent event) {
			if(mRobot == null || event == null)
			return;
			if(event.sensor.getType() != Sensor.TYPE_ACCELEROMETER)
			return;
			System.out.print("event is from accelerometer, ");
			if (roll_back)
			return;
						
			double x = event.values[0];
			double y = event.values[1];
			double z = event.values[2];
			Log.d(TAG, "accelerometer data: " + x + " " + y + " " + z );
			robot_control.drive(x, y, z);
		}
	};
	
	/**
	* AsyncDataListener that will be assigned to the DeviceMessager, listen for streaming data, and then do the
	*/
	private DeviceMessenger.AsyncDataListener mDataListener = new DeviceMessenger.AsyncDataListener() {
		@Override
		public void onDataReceived(DeviceAsyncData data) {
		
			if(data instanceof DeviceSensorsAsyncData){
			
			// If we are getting close to packet limit, request more
			mPacketCounter++;
			if( mPacketCounter > (TOTAL_PACKET_COUNT - PACKET_COUNT_THRESHOLD) ) {
			requestDataStreaming();
			}
			
			//get the frames in the response
			List<DeviceSensorsData> data_list = ((DeviceSensorsAsyncData)data).getAsyncData();
			if(data_list != null){
				
				// Iterate over each frame, however we set data streaming as only one frame
				for(DeviceSensorsData datum : data_list){
					LocatorData locatorData = datum.getLocatorData();
					//calculate polar coordinates for robot
					rRadius = FloatMath.sqrt(	(locatorData.getPositionX()*locatorData.getPositionX()) +
											(locatorData.getPositionY()*locatorData.getPositionY()));
					rAngle = Math.atan2((double) locatorData.getPositionY(), (double) locatorData.getPositionX());
					
					if( locatorData != null ) {
						if (roll_back) {
							if (rRadius < BACK_IN_BOUNDS_FROM_CENTER_CM) {
								RollCommand.sendStop(mRobot);
								roll_back = false;
								robot_control.setRGBColor(0, 255, 255);
							}
						}
					
						else if (rRadius > BOUNDARY_DISTANCE_FROM_CENTER_CM) {
							Log.d(TAG, "Out of Bounds! Turn AROUND!" );
							RollCommand.sendStop(mRobot);
							if (!roll_back) {
								// Roll back into bounds
								roll_back = true;
								Log.d(TAG, "STOP! it's hammer time..." );
								robot_control.stopMotors();
								Handler handlerTimer = new Handler();
								handlerTimer.postDelayed(new Runnable(){
							        public void run() {
							        	// Find current angle
										double angle = rAngle;
										angle = 90 - Math.toDegrees(angle);
										
										// Invert
										if (angle < 180)
										angle += 180;
										else
										angle -= 180;
										Log.d(TAG, "ROLL BACK!" );
							        	robot_control.roll((float)angle, 0.2f);            
							      }}, 2000);
								
								robot_control.setRGBColor(255, 0, 128);
							}
					
						}
						else {
							FrontLEDOutputCommand.sendCommand(mRobot, 0);
							}
						}
					}
				}
			}
		}
	};
	
	private void requestDataStreaming(){	
		if(mRobot == null) return;
		
		// Set up a bitmask containing the sensor information we want to stream, in this case locator
		// with which only works with Firmware 1.20 or greater.
		final long mask = SetDataStreamingCommand.DATA_STREAMING_MASK_LOCATOR_ALL;
		
		//Specify a divisor. The frequency of responses that will be sent is 400hz divided by this divisor.
		final int divisor = 50;
		
		//Specify the number of frames that will be in each response. You can use a higher number to "save up" responses
		//and send them at once with a lower frequency, but more packets per response.
		final int packet_frames = 1;
		
		// Reset finite packet counter
		mPacketCounter = 0;
		
		// Count is the number of async data packets Sphero will send you before
		// it stops. You want to register for a finite count and then send the command
		// again once you approach the limit. Otherwise data streaming may be left
		// on when your app crashes, putting Sphero in a bad state
		final int response_count = TOTAL_PACKET_COUNT;
		
		
		// Send this command to Sphero to start streaming.
		// If your Sphero is on Firmware less than 1.20, Locator values will display as 0's
		SetDataStreamingCommand.sendCommand(mRobot, divisor, packet_frames, mask, response_count);
		}
	}