package Dont.Irradiate.Me.Bro;

import android.util.Log;
import orbotix.robot.base.Robot;
import orbotix.robot.base.RobotControl;

public class Game {
	
	/**
	* The Sphero Robot
	*/
	private Robot mRobot;
	private RobotControl robot_control;
	
	private static final String TAG = "Game";
	
	Game(Robot robot, RobotControl robotControl){
		mRobot = robot;
		robot_control = robotControl;
		
		Log.d(TAG, "Game loaded with robot connected: " + robot.isConnected());
	}

}
