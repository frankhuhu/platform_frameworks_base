/* valera begin */

package valera;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Scanner;

import libcore.valera.ValeraConfig;
import libcore.valera.ValeraConstant;
import libcore.valera.ValeraUtil;

import android.app.Activity;
import android.app.ActivityThread;
import android.app.Application;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;



public class ValeraGlobal {
	
	private static final String TAG = "VALERA";

	private static int uniqueMsgId = 0;
	
	private static long startTime = 0;
	
	private static ActivityThread sActivityThread;
	
	private static ValeraHandler sVH;
	
	public static synchronized int generateMsgId() {
		return ++uniqueMsgId;
	}
	
	public static int getValeraMode() {
		return ValeraConfig.getValeraMode();
	}
	
	

	public static void startupCheck() {
		startTime = SystemClock.uptimeMillis();
		
		ValeraConfig.loadConfig();
		
		if (!ValeraConfig.isValeraEnabled())
			return;
		
		ValeraActionTracer.loadTraceActionFile(ValeraConfig.getActionTraceFilePath());
		ValeraEventScheduler.loadSchedulerFile(ValeraConfig.getSchedulerFilePath());
	}
	
	public static long getStartTime() {
		return startTime;
	}
	
	public static long getRelativeTime() {
		return SystemClock.uptimeMillis() - startTime;
	}
	
	public static ActivityThread getCurrentActivityThread() {
		if (sActivityThread != null)
			return sActivityThread;
		try {
		    final Class<?> activityThreadClass =
		            Class.forName("android.app.ActivityThread");
		    final Method method = activityThreadClass.getMethod("currentActivityThread");
		    sActivityThread = (ActivityThread) method.invoke(null, (Object[]) null);
		    // Note that it is possible for this method to return null, 
		    // e.g. when you call the method outside of the UI thread, 
		    // or the application is not bound to the thread.
		} catch (final ClassNotFoundException e) {
		    // handle exception
		} catch (final NoSuchMethodException e) {
		    // handle exception
		} catch (final IllegalArgumentException e) {
		    // handle exception
		} catch (final IllegalAccessException e) {
		    // handle exception
		} catch (final InvocationTargetException e) {
		    // handle exception
		}

		return sActivityThread;
	}
	
	public static Activity getActivity(String actName) {
		ActivityThread thread = getCurrentActivityThread();
		return thread.getActivityByName(actName);
	}
	
	public static Application getApplication() {
		ActivityThread thread = getCurrentActivityThread();
		return thread.getApplication();
	}
	
	public static void exitCleanUp() {
		Thread.currentThread().valeraExitCleanUp();
	}
	
	public static void setValeraHandler(ValeraHandler h) {
		sVH = h;
	}
	
	public static ValeraHandler getValeraHandler() {
		return sVH;
	}

}

/* valera end */
