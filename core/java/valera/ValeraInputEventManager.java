package valera;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.HashMap;

import libcore.valera.ValeraConfig;
import libcore.valera.ValeraConstant;
import libcore.valera.ValeraLogReader;
import libcore.valera.ValeraLogWriter;
import libcore.valera.ValeraUtil;

import com.android.internal.view.IInputConnectionWrapper;

import android.location.Location;
import android.os.Bundle;
import android.os.Message;
import android.os.SystemClock;
import android.text.SpannableString;
import android.util.Log;
import android.view.InputEvent;
import android.view.InputEventReceiver;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewRootImpl;
import android.view.WindowManagerGlobal;
import android.view.inputmethod.InputMethodManager;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.hardware.Camera;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.SystemSensorManager;
import android.hardware.SystemSensorManager.SensorEventQueue;

public class ValeraInputEventManager {
	private final static String TAG = "ValeraInputEventManager";
	private final static String LOG_FILE = "inputevent.bin";

	private final static int TYPE_MOTION_EVENT = 1;
	private final static int TYPE_KEY_EVENT    = 2;
	private final static int TYPE_IMM_METHOD = 3;
	private final static int TYPE_SENSOR_EVENT = 4;
	private final static int TYPE_LOCATION_EVENT = 5;
	private final static int TYPE_CAMERA_EVENT = 6;

	private final static int TYPE_WINDOW_INPUT_RECEIVER = 1;

	private static final int IM_DO_GET_TEXT_AFTER_CURSOR = 10;
	private static final int IM_DO_GET_TEXT_BEFORE_CURSOR = 20;
	private static final int IM_DO_GET_SELECTED_TEXT = 25;
	private static final int IM_DO_GET_CURSOR_CAPS_MODE = 30;
	private static final int IM_DO_GET_EXTRACTED_TEXT = 40;
	private static final int IM_DO_COMMIT_TEXT = 50;
	private static final int IM_DO_COMMIT_COMPLETION = 55;
	private static final int IM_DO_COMMIT_CORRECTION = 56;
	private static final int IM_DO_SET_SELECTION = 57;
	private static final int IM_DO_PERFORM_EDITOR_ACTION = 58;
	private static final int IM_DO_PERFORM_CONTEXT_MENU_ACTION = 59;
	private static final int IM_DO_SET_COMPOSING_TEXT = 60;
	private static final int IM_DO_SET_COMPOSING_REGION = 63;
	private static final int IM_DO_FINISH_COMPOSING_TEXT = 65;
	private static final int IM_DO_SEND_KEY_EVENT = 70;
	private static final int IM_DO_DELETE_SURROUNDING_TEXT = 80;
	private static final int IM_DO_BEGIN_BATCH_EDIT = 90;
	private static final int IM_DO_END_BATCH_EDIT = 95;
	private static final int IM_DO_REPORT_FULLSCREEN_MODE = 100;
	private static final int IM_DO_PERFORM_PRIVATE_COMMAND = 120;
	private static final int IM_DO_CLEAR_META_KEY_STATES = 130;
	
	private static final int LOC_LOCATION_CHANGED = 1;
    private static final int LOC_STATUS_CHANGED = 2;
    private static final int LOC_PROVIDER_ENABLED = 3;
    private static final int LOC_PROVIDER_DISABLED = 4;

	// TODO: multi-thread issue? Currently only replay the main thread input
	// event.
	private static ValeraLogReader sReader;
	private static ValeraLogWriter sWriter;
	private static ReplayEvent sCurReplayEvent;
	private static ValeraInputEventManager sDefaultManager;
	private static Object sLock;
	
	public final static int REPLAY_STATUS_CLEAR = 101;
	public final static int REPLAY_STATUS_IN_PROGRESS = 102;
	public final static int REPLAY_STATUS_PENDING = 103;
	private static int sReplayStatus = REPLAY_STATUS_CLEAR;
	private static int sVsyncCounter = 0;

	public static class SensorEvent {
		int handle;
		float[] values;
		int inAccuracy;
		long timestamp;
		String strListener;
		String strActivity;
		
		public SensorEvent(int handle, float[] values, int inAccuracy, long timestamp,
				String strListener, String strActivity) {
			this.handle = handle;
			this.values = values;
			this.inAccuracy = inAccuracy;
			this.timestamp = timestamp;
			this.strListener = strListener;
			this.strActivity = strActivity;
		}
	}
	
	public static class InputMethodEvent {
		CharSequence text;
		int newCursorPosition;
		long timestamp;
		
		public InputMethodEvent(CharSequence text, int newCursorPosition, long timestamp) {
			this.text = text;
			this.newCursorPosition = newCursorPosition;
			this.timestamp = timestamp;
		}
	}
	
	public static class ReplayEvent {
		public int event_type;
		public InputEvent inputEvent;
		public int receiver_type;
		public int window_index;
		public SensorEvent sensorEvent;
		public InputMethodEvent imEvent;
		public long timestamp;
		public long cntVsync;

		public ReplayEvent() {
			event_type = -1;
			inputEvent = null;
			receiver_type = -1;
			window_index = -1;
			sensorEvent = null;
			imEvent = null;
			timestamp = Long.MAX_VALUE;
			cntVsync = 0;
		}
	}

	private ValeraInputEventManager() {
		try {
			sLock = new Object();
			switch (valera.ValeraGlobal.getValeraMode()) {
			case ValeraConstant.MODE_RECORD:
				sWriter = new ValeraLogWriter(new FileOutputStream(
						"/data/data/" + ValeraConfig.getPackageName() + "/valera/"
								+ LOG_FILE, false));
				break;
			case ValeraConstant.MODE_REPLAY:
				sReader = new ValeraLogReader(new FileInputStream("/data/data/"
						+ ValeraConfig.getPackageName() + "/valera/" + LOG_FILE));
				break;
			}
			sCurReplayEvent = null;
		} catch (Exception e) {
			Log.e(TAG, e.toString());
			e.printStackTrace();
			System.exit(-1);
		}
	}

	public static ValeraInputEventManager getInstance() {
		synchronized (ValeraInputEventManager.class) {
			if (sDefaultManager == null) {
				sDefaultManager = new ValeraInputEventManager();
			}
			return sDefaultManager;
		}
	}

	public void recordInputEvent(int msgId, InputEventReceiver receiver, InputEvent event) {
		ValeraUtil.valeraAssert(ValeraGlobal.getValeraMode() == ValeraConstant.MODE_RECORD, 
				"Not in velera record mode");

		synchronized(sLock) {
		int event_type = 0;
		if (event instanceof MotionEvent) {
			try {
				MotionEvent me = (MotionEvent) event;
				event_type = TYPE_MOTION_EVENT;
				sWriter.writeInt(event_type);
				sWriter.writeLong(ValeraGlobal.getRelativeTime());
				sWriter.writeInt(msgId);
				long downTime = me.getDownTime()
						- valera.ValeraGlobal.getStartTime();
				sWriter.writeLong(downTime);
				long eventTime = me.getEventTime()
						- valera.ValeraGlobal.getStartTime();
				sWriter.writeLong(eventTime);
				int action = me.getAction();
				sWriter.writeInt(action);
				int pointerCount = me.getPointerCount();
				sWriter.writeInt(pointerCount);
				for (int i = 0; i < pointerCount; i++) {
					int id = me.getPointerId(i);
					sWriter.writeInt(id);
					int toolType = me.getToolType(i);
					sWriter.writeInt(toolType);
					float orientation = me.getOrientation(i);
					sWriter.writeFloat(orientation);
					float pressure = me.getPressure(i);
					sWriter.writeFloat(pressure);
					float size = me.getSize(i);
					sWriter.writeFloat(size);
					float toolMajor = me.getToolMajor(i);
					sWriter.writeFloat(toolMajor);
					float toolMinor = me.getToolMinor(i);
					sWriter.writeFloat(toolMinor);
					float touchMajor = me.getTouchMajor(i);
					sWriter.writeFloat(touchMajor);
					float touchMinor = me.getTouchMinor(i);
					sWriter.writeFloat(touchMinor);
					float x = me.getX(i);
					sWriter.writeFloat(x);
					float y = me.getY(i);
					sWriter.writeFloat(y);
				}
				int metaState = me.getMetaState();
				sWriter.writeInt(metaState);
				int buttonState = me.getButtonState();
				sWriter.writeInt(buttonState);
				float xPrecision = me.getXPrecision();
				sWriter.writeFloat(xPrecision);
				float yPrecision = me.getYPrecision();
				sWriter.writeFloat(yPrecision);
				int deviceId = me.getDeviceId();
				sWriter.writeInt(deviceId);
				int edgeFlags = me.getEdgeFlags();
				sWriter.writeInt(edgeFlags);
				int source = me.getSource();
				sWriter.writeInt(source);
				int flags = me.getFlags();
				sWriter.writeInt(flags);

				// Writer InputEventReceiver Info
				int receiver_type = 0;
				int index = -1;
				if (receiver instanceof ViewRootImpl.WindowInputEventReceiver) {
					ViewRootImpl.WindowInputEventReceiver wier = (ViewRootImpl.WindowInputEventReceiver) receiver;
					receiver_type = TYPE_WINDOW_INPUT_RECEIVER;
					index = wier.getWindowIndex();
				}
				sWriter.writeInt(receiver_type);
				sWriter.writeInt(index);

				sWriter.flush();
			} catch (Exception e) {
				Log.e(TAG, e.toString());
				e.printStackTrace();
				System.exit(-1);
			}
		} else if (event instanceof KeyEvent) {
			try {
				KeyEvent ke = (KeyEvent) event;
				event_type = TYPE_KEY_EVENT;
				sWriter.writeInt(event_type);
				sWriter.writeLong(ValeraGlobal.getRelativeTime());
				sWriter.writeInt(msgId);
				long downTime = ke.getDownTime()
						- valera.ValeraGlobal.getStartTime();
				sWriter.writeLong(downTime);
				long eventTime = ke.getEventTime()
						- valera.ValeraGlobal.getStartTime();
				sWriter.writeLong(eventTime);
				int action = ke.getAction();
				sWriter.writeInt(action);
				int code = ke.getKeyCode();
				sWriter.writeInt(code);
				int repeat = ke.getRepeatCount();
				sWriter.writeInt(repeat);
				int metaState = ke.getMetaState();
				sWriter.writeInt(metaState);
				int deviceId = ke.getDeviceId();
				sWriter.writeInt(deviceId);
				int scanCode = ke.getScanCode();
				sWriter.writeInt(scanCode);
				int flags = ke.getFlags();
				sWriter.writeInt(flags);
				int source = ke.getSource();
				sWriter.writeInt(source);
				// Writer InputEventReceiver Info
				int receiver_type = 0;
				int index = -1;
				if (receiver instanceof ViewRootImpl.WindowInputEventReceiver) {
					ViewRootImpl.WindowInputEventReceiver wier = (ViewRootImpl.WindowInputEventReceiver) receiver;
					receiver_type = TYPE_WINDOW_INPUT_RECEIVER;
					index = wier.getWindowIndex();
				}
				sWriter.writeInt(receiver_type);
				sWriter.writeInt(index);
				sWriter.flush();
			} catch (Exception e) {
				Log.e(TAG, e.toString());
				e.printStackTrace();
				System.exit(-1);
			}
		} else {
			event_type = -1;
			Log.e(TAG, "Unsupported record event: "
					+ event.getClass().getName());
		}
		}
	}
	
	private ReplayEvent readMotionEvent() {
		ReplayEvent event = null;
		try {
			long relativeTime = sReader.readLong();
			int msgId = sReader.readInt();
			long downTime = sReader.readLong();
			downTime += valera.ValeraGlobal.getStartTime();
			long eventTime = sReader.readLong();
			eventTime += valera.ValeraGlobal.getStartTime();
			int action = sReader.readInt();
			int pointerCount = sReader.readInt();

			MotionEvent.PointerProperties pointerProperties[] = new MotionEvent.PointerProperties[pointerCount];
			MotionEvent.PointerCoords pointerCoords[] = new MotionEvent.PointerCoords[pointerCount];

			for (int i = 0; i < pointerCount; i++) {
				pointerProperties[i] = new MotionEvent.PointerProperties();
				int id = sReader.readInt();
				pointerProperties[i].id = id;
				int toolType = sReader.readInt();
				pointerProperties[i].toolType = toolType;

				pointerCoords[i] = new MotionEvent.PointerCoords();
				float orientation = sReader.readFloat();
				pointerCoords[i].orientation = orientation;
				float pressure = sReader.readFloat();
				pointerCoords[i].pressure = pressure;
				float size = sReader.readFloat();
				pointerCoords[i].size = size;
				float toolMajor = sReader.readFloat();
				pointerCoords[i].toolMajor = toolMajor;
				float toolMinor = sReader.readFloat();
				pointerCoords[i].toolMinor = toolMinor;
				float touchMajor = sReader.readFloat();
				pointerCoords[i].touchMajor = touchMajor;
				float touchMinor = sReader.readFloat();
				pointerCoords[i].touchMinor = touchMinor;
				float x = sReader.readFloat();
				pointerCoords[i].x = x;
				float y = sReader.readFloat();
				pointerCoords[i].y = y;
			}

			int metaState = sReader.readInt();
			int buttonState = sReader.readInt();
			float xPrecision = sReader.readFloat();
			float yPrecision = sReader.readFloat();
			int deviceId = sReader.readInt();
			int edgeFlags = sReader.readInt();
			int source = sReader.readInt();
			int flags = sReader.readInt();

			MotionEvent me = MotionEvent.obtain(downTime, eventTime,
					action, pointerCount, pointerProperties, pointerCoords,
					metaState, buttonState, xPrecision, yPrecision,
					deviceId, edgeFlags, source, flags);

			int receiver_type = sReader.readInt();
			int index = sReader.readInt();

			event = new ReplayEvent();
			event.event_type = TYPE_MOTION_EVENT;
			event.timestamp = me.getEventTime();
			event.inputEvent = (InputEvent) me;
			event.receiver_type = receiver_type;
			event.window_index = index;
		} catch (Exception e) {
			Log.e(TAG, e.toString());
			e.printStackTrace();
			System.exit(-1);
		}
		
		return event;
	}
	
	private ReplayEvent readKeyEvent() {
		ReplayEvent event = null;
		try {
			long relativeTime = sReader.readLong();
			int msgId = sReader.readInt();
			long downTime = sReader.readLong();
			downTime += valera.ValeraGlobal.getStartTime();
			long eventTime = sReader.readLong();
			eventTime += valera.ValeraGlobal.getStartTime();

			int action = sReader.readInt();
			int code = sReader.readInt();
			int repeat = sReader.readInt();
			int metaState = sReader.readInt();
			int deviceId = sReader.readInt();
			int scancode = sReader.readInt();
			int flags = sReader.readInt();
			int source = sReader.readInt();

			KeyEvent ke = new KeyEvent(downTime, eventTime, action, code,
					repeat, metaState, deviceId, scancode, flags, source);

			int receiver_type = sReader.readInt();
			int index = sReader.readInt();

			event = new ReplayEvent();
			event.event_type = TYPE_KEY_EVENT;
			event.timestamp = ke.getEventTime();
			event.inputEvent = (InputEvent) ke;
			event.receiver_type = receiver_type;
			event.window_index = index;
		} catch (IOException e) {
			Log.e(TAG, e.toString());
			e.printStackTrace();
			System.exit(-1);
		}
		return event;
	}
	
	private ReplayEvent readInputMethod() {
		ReplayEvent event = null;
		try {
			int op = sReader.readInt();
			switch (op) {
			case IM_DO_GET_TEXT_AFTER_CURSOR:
			case IM_DO_GET_TEXT_BEFORE_CURSOR:
			case IM_DO_GET_SELECTED_TEXT:
			case IM_DO_GET_CURSOR_CAPS_MODE:
			case IM_DO_GET_EXTRACTED_TEXT:
				break;
			case IM_DO_COMMIT_TEXT:
				event = readIMCommitText();
				break;
			case IM_DO_COMMIT_COMPLETION:
			case IM_DO_COMMIT_CORRECTION:
			case IM_DO_SET_SELECTION:
			case IM_DO_PERFORM_EDITOR_ACTION:
			case IM_DO_PERFORM_CONTEXT_MENU_ACTION:
			case IM_DO_SET_COMPOSING_TEXT:
			case IM_DO_SET_COMPOSING_REGION:
			case IM_DO_FINISH_COMPOSING_TEXT:
			case IM_DO_SEND_KEY_EVENT:
			case IM_DO_DELETE_SURROUNDING_TEXT:
			case IM_DO_BEGIN_BATCH_EDIT:
			case IM_DO_END_BATCH_EDIT:
			case IM_DO_REPORT_FULLSCREEN_MODE:
			case IM_DO_PERFORM_PRIVATE_COMMAND:
			case IM_DO_CLEAR_META_KEY_STATES:
			}
		} catch (Exception e) {
			Log.e(TAG, e.toString());
			e.printStackTrace();
			System.exit(-1);
		}
		return event;
	}

	private void writeCharSequence(CharSequence text) throws Exception {
		// 'text' here is android.text.SpannableString which is not
		// serializable.
		// change it to String.
		String clazz = text.getClass().getName();
		sWriter.writeString(clazz);
		if (clazz.equals("android.text.SpannableString"))
			sWriter.writeString(text.toString());
		else if (clazz.equals("java.lang.String"))
			sWriter.writeString((String) text);
		else
			throw new Exception("What is this CharSequence? "
					+ text.getClass().toString());
	}
	
	private CharSequence readCharSequence() throws Exception {
		String clazz = sReader.readString();
		CharSequence text;
		if (clazz.equals("android.text.SpannableString"))
			text = new SpannableString(sReader.readString());
		else if (clazz.equals("java.lang.String"))
			text = sReader.readString();
		else
			throw new Exception("Illegal CharSequence Type.");
		return text;
	}

	public void recordIMCommitText(CharSequence text, int newCursorPosition) {
		synchronized (sLock) {
			try {
				sWriter.writeInt(TYPE_IMM_METHOD);
				sWriter.writeInt(IM_DO_COMMIT_TEXT);
				writeCharSequence(text);
				sWriter.writeInt(newCursorPosition);
				sWriter.writeLong(ValeraGlobal.getRelativeTime());
				sWriter.flush();
				System.out.println("yhu009: IMM_DEBUG record commit text " + text + " " + newCursorPosition);
			} catch (Exception e) {
				Log.e(TAG, e.toString());
				e.printStackTrace();
				System.exit(-1);
			}
		}
	}

	private ReplayEvent readIMCommitText() {
		ReplayEvent event = null;
		try {
			CharSequence text = readCharSequence();
			int newCursorPosition = sReader.readInt();
			long timestamp = sReader.readLong() + ValeraGlobal.getStartTime();
			
			InputMethodEvent ime = new InputMethodEvent(text, newCursorPosition, timestamp);
			event = new ReplayEvent();
			event.event_type = TYPE_IMM_METHOD;
			event.timestamp = timestamp;
			event.imEvent = ime;
		} catch (Exception e) {
			Log.e(TAG, e.toString());
			e.printStackTrace();
			System.exit(-1);
		}
		return event;
	}
	
	public boolean replayIMCommitText(ReplayEvent event) {
		try {
			ValeraUtil.valeraAssert(event.event_type == TYPE_IMM_METHOD, "event type mismatch");
			InputMethodEvent ime = event.imEvent;
			InputMethodManager imm = InputMethodManager.getInstance();
			imm.getInputContext().commitText(ime.text, ime.newCursorPosition);
			System.out.println("yhu009: IMM_DEBUG replay commit text " + ime.text + 
					" " + ime.newCursorPosition);
			return true;
		}  catch (Exception e) {
			Log.e(TAG, e.toString());
			e.printStackTrace();
			System.exit(-1);
		}
		return false;
	}
	
	public void recordSensorEvent(int handle, float[] values, int inAccuracy, 
			long timestamp, SensorEventListener listener, SystemSensorManager ssm) {
		/*
		synchronized (sLock) {
			try {
				sWriter.writeInt(TYPE_SENSOR_EVENT);
				sWriter.writeLong(ValeraGlobal.getRelativeTime());
				sWriter.writeInt(handle);
				sWriter.writeInt(values.length);
				for (int i = 0; i < values.length; i++)
					sWriter.writeFloat(values[i]);
				sWriter.writeInt(inAccuracy);
                        // timestamp is in nanosecond
				sWriter.writeLong(timestamp - 1000000 * valera.ValeraGlobal.getStartTime());
			
				Context ctx = ssm.getContext();
				//ValeraUtil.valeraAssert(ctx instanceof Activity,
				//		"Currently we only support getting SystemSensorManager from Activity");
				//Activity activity = (Activity) ctx;
				//sWriter.writeString(activity.getClass().getName());
			
				String strListener = listener.getClass().getName();
				sWriter.writeString(strListener);
				sWriter.flush();
				// System.out.println("yhu009: SensorEvent record " + handle + " " + strListener);
			} catch (Exception e) {
				Log.e(TAG, e.toString());
				e.printStackTrace();
				System.exit(-1);
			}
		}
		*/
	}
	
	private ReplayEvent readSensorEvent() {
		ReplayEvent event = null;
		try {
			long relativeTime = sReader.readLong();
			int handle = sReader.readInt();
			int length = sReader.readInt();
			float[] values = new float[length];
			for (int i = 0; i < length; i++)
				values[i] = sReader.readFloat();
			int inAccuracy = sReader.readInt();
                        // timestamp is in nanosecond
			long timestamp = sReader.readLong() + 1000000 * valera.ValeraGlobal.getStartTime();
			String strActivity = sReader.readString();
			String strListener = sReader.readString();
			SensorEvent se = new SensorEvent(handle, values, inAccuracy, timestamp, 
					strListener, strActivity);
			event = new ReplayEvent();
			event.event_type = TYPE_SENSOR_EVENT;
			event.timestamp = timestamp / 1000000;
			event.sensorEvent = se;
		} catch (Exception e) {
			Log.e(TAG, e.toString());
			e.printStackTrace();
			System.exit(-1);
		}
		return event;
	}
	
	public void recordOnLocationChanged(Location loc) {
		synchronized (sLock) {
			try {
				sWriter.writeInt(TYPE_LOCATION_EVENT);
				sWriter.writeInt(LOC_LOCATION_CHANGED);
				sWriter.writeLong(ValeraGlobal.getRelativeTime());
				if (loc == null) {
					sWriter.writeInt(0);
				} else {
					sWriter.writeInt(1);
					sWriter.writeFloat(loc.getAccuracy());
					sWriter.writeDouble(loc.getAltitude());
					sWriter.writeFloat(loc.getBearing());
					sWriter.writeDouble(loc.getLatitude());
					sWriter.writeDouble(loc.getLongitude());
					sWriter.writeString(loc.getProvider());
					sWriter.writeFloat(loc.getSpeed());
					sWriter.writeLong(loc.getTime());
				}
				sWriter.flush();
			} catch (Exception e) {
				Log.e(TAG, e.toString());
				e.printStackTrace();
				System.exit(-1);
			}
		}
	}
	
	private Location readOnLocationChanged() {
		// TODO: impl read location from log.
		return null;
	}
	
	public void recordOnStatusChanged(String provider, int status, Bundle extras) {
		synchronized (sLock) {
			try {
				sWriter.writeInt(TYPE_LOCATION_EVENT);
				sWriter.writeInt(LOC_STATUS_CHANGED);
				sWriter.writeLong(ValeraGlobal.getRelativeTime());
				sWriter.writeString(provider);
				sWriter.writeInt(status);
				// ignore Bundle.
				sWriter.flush();
			} catch (Exception e) {
				Log.e(TAG, e.toString());
				e.printStackTrace();
				System.exit(-1);
			}
		}
	}
	
	private void readOnStatusChanged() {
		// TODO: impl read location status changed.
		return ;
	}
	
	public void recordOnProviderEnabled(String provider) {
		synchronized (sLock) {
			try {
				sWriter.writeInt(TYPE_LOCATION_EVENT);
				sWriter.writeInt(LOC_PROVIDER_ENABLED);
				sWriter.writeLong(ValeraGlobal.getRelativeTime());
				sWriter.writeString(provider);
				sWriter.flush();
			} catch (Exception e) {
				Log.e(TAG, e.toString());
				e.printStackTrace();
				System.exit(-1);
			}
		}
	}
	
	private void readOnProviderEnabled(String provider) {
		// TODO: impl read onProviderEnabled.
		return ;
	}
	
	public void recordOnProviderDisabled(String provider) {
		synchronized (sLock) {
			try {
				sWriter.writeInt(TYPE_LOCATION_EVENT);
				sWriter.writeInt(LOC_PROVIDER_DISABLED);
				sWriter.writeLong(ValeraGlobal.getRelativeTime());
				sWriter.writeString(provider);
				sWriter.flush();
			} catch (Exception e) {
				Log.e(TAG, e.toString());
				e.printStackTrace();
				System.exit(-1);
			}
		}
	}
	
	private void readOnProviderDisabled(String provider) {
		// TODO: impl read onProviderDisabled.
		return ;
	}
	
	public void recordCameraEvent(Camera camera, int what, int arg1, int arg2, Object obj) {
		synchronized(sLock) {
			try {
				sWriter.writeInt(TYPE_CAMERA_EVENT);
				sWriter.writeLong(ValeraGlobal.getRelativeTime());
				// TODO: Dump camera.parameters
				// TODO: Add cameraId ???
				sWriter.writeInt(what);
				sWriter.writeInt(arg1);
				sWriter.writeInt(arg2);
				// TODO: CAMERA_MSG_PREVIEW_METADATA obj is Face[]. Not impl yet!
				ValeraUtil.valeraAssert(what != 1024, "CAMERA_MSG_PREVIEW_METADATA case not impl yet!");
				byte[] bytes = (byte[]) obj;
				int len = bytes == null ? 0 : bytes.length;
				sWriter.writeByteArray(bytes, 0, len);
				sWriter.flush();
			} catch (Exception e) {
				Log.e(TAG, e.toString());
				e.printStackTrace();
				System.exit(-1);
			}
		}
	}
	
	private void readCameraEvent() {
		// TODO: impl read camera.
	}
	
	public boolean replaySensorEvent(ReplayEvent event) {
		ValeraUtil.valeraAssert(event.event_type == TYPE_SENSOR_EVENT, "event type mismatch");
		SensorEvent se = event.sensorEvent;
		
		Activity activity = ValeraGlobal.getActivity(se.strActivity);
		libcore.valera.ValeraUtil.valeraAssert(activity != null, "Can not find Activity " + se.strActivity);
		SensorManager sm = (SensorManager) activity.getSystemService(Context.SENSOR_SERVICE);
		libcore.valera.ValeraUtil.valeraAssert(sm != null, "SensorManager is null.");
		System.out.println("yhu009: replaySensorEvent " 
				+ activity.toString() + " " + se.strListener);
		SystemSensorManager ssm = (SystemSensorManager) sm;
		SensorEventQueue queue = ssm.getSensorEventQueueByListener(se.strListener);
		ValeraUtil.valeraAssert(queue != null, "Can not find queue " + se.strListener);
		queue.valeraReplayFakeSensorEvent(se.handle, se.values, se.inAccuracy, se.timestamp);
		
		return true;
	}
	

	public void replayExternalEvent(long now) {
		ValeraUtil.valeraAssert(sReader != null, "sReader is null");
		
		if (sCurReplayEvent == null) {
			int event_type = -1;
			try {
				event_type = sReader.readInt();
			} catch (EOFException eofe) {
				//Log.e(TAG, eofe.toString());
				return;
			} catch (IOException ioe) {
				Log.e(TAG, ioe.toString());
				ioe.printStackTrace();
				System.exit(-1);
			}
			
			switch (event_type) {
			case TYPE_MOTION_EVENT:
				sCurReplayEvent = readMotionEvent();
				break;
			case TYPE_KEY_EVENT:
				sCurReplayEvent = readKeyEvent();
				break;
			case TYPE_IMM_METHOD:
				sCurReplayEvent = readInputMethod();
				break;
			case TYPE_SENSOR_EVENT:
				sCurReplayEvent = readSensorEvent();
				break;
			default:
				Log.w(TAG, "Unsupported replay event: " + event_type);
				break;
			}
		}
		
		if (sCurReplayEvent != null && sReplayStatus == REPLAY_STATUS_PENDING) {
			// Replay pending input events that does not success last time.
			ReplayEvent re = sCurReplayEvent;
			switch (sCurReplayEvent.event_type) {
			case TYPE_MOTION_EVENT:
			case TYPE_KEY_EVENT:
			{
				int index = re.window_index;
				ViewRootImpl root = WindowManagerGlobal.getInstance().getRootViewImpl(index);
				if (root != null) {
					root.getInputEventReceiver().valeraReplayFakeInputEvent(re.inputEvent);
					ValeraInputEventManager.getInstance().replayExternalEventDone();
				}
			}
				break;
			case TYPE_IMM_METHOD:
				if (ValeraInputEventManager.getInstance().replayIMCommitText(re))
					ValeraInputEventManager.getInstance().replayExternalEventDone();
				break;
			case TYPE_SENSOR_EVENT:
				if (ValeraInputEventManager.getInstance().replaySensorEvent(re))
					ValeraInputEventManager.getInstance().replayExternalEventDone();
				break;
			}
		} else if (sCurReplayEvent != null && sReplayStatus == REPLAY_STATUS_CLEAR
				// TODO: replace the timestamp by differ of two adjant input event.
				&& sCurReplayEvent.timestamp <= now) {
			switch (sCurReplayEvent.event_type) {
			case TYPE_MOTION_EVENT:
			case TYPE_KEY_EVENT:
			{
				Message msg = Message.obtain(ValeraGlobal.getValeraHandler(), 
						ValeraHandler.REPLAY_INPUT_EVENT, sCurReplayEvent);
				msg.sendToTarget();
				sReplayStatus = REPLAY_STATUS_IN_PROGRESS;
				
				/*
				int index = sCurReplayEvent.window_index;
				ViewRootImpl root = WindowManagerGlobal.getInstance().getRootViewImpl(index);
				if (root != null) {
					root.getInputEventReceiver().valeraReplayFakeInputEvent(sCurReplayEvent.inputEvent);
					sCurReplayEvent = null;
				}
				*/
			}
				break;
			case TYPE_IMM_METHOD:
			{
				Message msg = Message.obtain(ValeraGlobal.getValeraHandler(), 
						ValeraHandler.REPLAY_IMM_EVENT, sCurReplayEvent);
				msg.sendToTarget();
				sReplayStatus = REPLAY_STATUS_IN_PROGRESS;
			}
				/*
				replayIMCommitText(sCurReplayEvent);
				sCurReplayEvent = null;
				*/
				break;
			case TYPE_SENSOR_EVENT:
				/*
				replaySensorEvent(sCurReplayEvent);
				sCurReplayEvent = null;
				*/
				break;
			}
		}
	}
	
	public void replayExternalEventDone() {
		sCurReplayEvent = null;
		sReplayStatus = REPLAY_STATUS_CLEAR;
	}
	
	public void replayExternalEventPending() {
		sReplayStatus = REPLAY_STATUS_PENDING;
	}
	
	// Must be invoked by the UI thread.
	public void incVsyncCounter() {
		sVsyncCounter++;
	}
	
	public int getVsyncCounter() {
		return sVsyncCounter;
	}
	
	public void resetVsyncCounter() {
		sVsyncCounter = 0;
	}
}
