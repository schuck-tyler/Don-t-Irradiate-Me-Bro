package Dont.Irradiate.Me.Bro;

import java.util.List;
import orbotix.robot.app.StartupActivity;
import orbotix.robot.base.DeviceAsyncData;
import orbotix.robot.base.DeviceMessenger;
import orbotix.robot.base.DeviceSensorsAsyncData;
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
import android.os.PowerManager;
import android.util.Log;

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
	private final static int BACK_IN_BOUNDS_FROM_CENTER_CM = 160;
	private boolean roll_back = false;
	
	/**
	* ID for launching the StartupActivity for result to connect to the robot
	*/
	private final static int STARTUP_ACTIVITY = 0;
	
	/**
	* The Sphero Robot
	*/
	private Robot mRobot;
	private RobotControl robot_control;
	
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
				this.robot_control = RobotProvider.getDefaultProvider().getRobotControl(mRobot);
				this.robot_control.setDriveAlgorithm(new TiltDriveAlgorithm());
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
					if( locatorData != null ) {
						if (roll_back) {
							if (locatorData.getPositionX() < BACK_IN_BOUNDS_FROM_CENTER_CM
							&& locatorData.getPositionX() > -BACK_IN_BOUNDS_FROM_CENTER_CM
							&& locatorData.getPositionY() < BACK_IN_BOUNDS_FROM_CENTER_CM
							&& locatorData.getPositionY() > -BACK_IN_BOUNDS_FROM_CENTER_CM) {
								RollCommand.sendStop(mRobot);
								roll_back = false;
							}
						}
					
						else if (locatorData.getPositionX() > BOUNDARY_DISTANCE_FROM_CENTER_CM
						|| locatorData.getPositionX() < -BOUNDARY_DISTANCE_FROM_CENTER_CM
						|| locatorData.getPositionY() > BOUNDARY_DISTANCE_FROM_CENTER_CM
						|| locatorData.getPositionY() < -BOUNDARY_DISTANCE_FROM_CENTER_CM) {
							//FrontLEDOutputCommand.sendCommand(mRobot, 255);
							RollCommand.sendStop(mRobot);
							if (!roll_back) {
								// Roll back into bounds
								// Find current angle
								double angle = Math.atan2((double) locatorData.getPositionY(), (double) locatorData.getPositionX());
								angle = 90 - Math.toDegrees(angle);
								
								// Invert
								if (angle < 180)
								angle += 180;
								else
								angle -= 180;
							
								roll_back = true;
								//RollCommand.sendCommand(mRobot, (int) angle, 0.6f);
								robot_control.roll((float)angle, 0.6f);
								System.out.println("OUT OF BOUNDS!");
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