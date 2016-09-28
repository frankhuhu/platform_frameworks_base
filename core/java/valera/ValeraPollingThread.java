package valera;

import libcore.valera.ValeraConstant;
import android.os.Handler;
import android.os.Message;

public class ValeraPollingThread extends Thread {
	private Handler mHandler;
	
	public static final int EMPTY_MSG = -99;

	public ValeraPollingThread(Handler handler) {
		super("ValeraPollingThread");
		mHandler = handler;
	}
	
	public void run() {
		if (valera.ValeraGlobal.getValeraMode() == ValeraConstant.MODE_REPLAY) {
			while (true) {
				try {
					Thread.sleep(10);
					
					// Wake up the looper thread, check and replay external events.
					Message msg = Message.obtain(ValeraGlobal.getValeraHandler(), 
							ValeraHandler.WAKEUP_AND_CHECK);
					msg.setAsynchronous(true);
					msg.sendToTarget();
					
					// Just send an empty message to wake up the looper.
					//mHandler.sendEmptyMessage(EMPTY_MSG);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}
}
