package valera;

import libcore.valera.ValeraConstant;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.view.ViewRootImpl;
import android.view.WindowManagerGlobal;

public class ValeraHandler extends Handler {
	
	private final static int WAKEUP_INTERVAL 	= 16; // milli seconds.
	
	public final static int WAKEUP_AND_CHECK	= 100;
	public final static int REPLAY_INPUT_EVENT	= 101;
	public final static int REPLAY_IMM_EVENT	= 102;
	public final static int REPLAY_SENSOR_EVENT	= 103;
	
	
	public void postNextWakeup() {
		if (valera.ValeraGlobal.getValeraMode() == ValeraConstant.MODE_REPLAY) {
			// Wake up the looper thread, check and replay external events.
			Handler h = ValeraGlobal.getValeraHandler();
			Message msg = Message.obtain(h, ValeraHandler.WAKEUP_AND_CHECK);
			msg.setAsynchronous(true);
			h.sendMessageDelayed(msg, WAKEUP_INTERVAL);
		}
	}
	
	
	public void handleMessage(Message msg) {
		switch (msg.what) {
		case WAKEUP_AND_CHECK: {
			if (Looper.myLooper() == Looper.getMainLooper()) {
				long now = SystemClock.uptimeMillis();
				ValeraInputEventManager.getInstance().replayExternalEvent(now);
			}
			postNextWakeup();
		} break;
		
		case REPLAY_INPUT_EVENT: {
			ValeraInputEventManager.ReplayEvent re = (ValeraInputEventManager.ReplayEvent) msg.obj;
			
			int index = re.window_index;
			ViewRootImpl root = WindowManagerGlobal.getInstance().getRootViewImpl(index);
			if (root != null) {
				root.getInputEventReceiver().valeraReplayFakeInputEvent(re.inputEvent);
				ValeraInputEventManager.getInstance().replayExternalEventDone();
			} else {
				ValeraInputEventManager.getInstance().replayExternalEventPending();
			}
		} break;
		
		case REPLAY_IMM_EVENT: {
			ValeraInputEventManager.ReplayEvent re = (ValeraInputEventManager.ReplayEvent) msg.obj;
			if (ValeraInputEventManager.getInstance().replayIMCommitText(re))
				ValeraInputEventManager.getInstance().replayExternalEventDone();
			else
				ValeraInputEventManager.getInstance().replayExternalEventPending();
		} break;
		
		case REPLAY_SENSOR_EVENT: {
			ValeraInputEventManager.ReplayEvent re = (ValeraInputEventManager.ReplayEvent) msg.obj;
			if (ValeraInputEventManager.getInstance().replaySensorEvent(re))
				ValeraInputEventManager.getInstance().replayExternalEventDone();
			else
				ValeraInputEventManager.getInstance().replayExternalEventPending();
		} break;
		}
	}

}
