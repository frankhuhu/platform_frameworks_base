package valera;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;

import libcore.valera.ValeraUtil;

import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;

class OrderEntry {
	int logicOrder;
	String handler;
	int handlerId;
	String callback;
	int what, arg1, arg2;
	
	Message msg;
	long msgAttachTime;
	
	long schedLoadTime;
	
	OrderEntry(int logicOrder, String handler, int handlerId, String callback, int what, 
			int arg1, int arg2) {
		this.logicOrder = logicOrder;
		this.handler = handler;
		this.handlerId = handlerId;
		this.callback = callback;
		this.what = what;
		this.arg1 = arg1;
		this.arg2 = arg2;
		
		this.msg = null;
		
		this.schedLoadTime = SystemClock.uptimeMillis();
	}

	boolean match(Message msg) {
		boolean result = false;
		Handler handler = msg.getTarget();
		int handlerId = handler.getHandlerId();
		Runnable callback = msg.getCallback();
		int what = msg.what;
		int arg1 = msg.arg1;
		int arg2 = msg.arg2;
		if (handler.getClass().getName().equals(this.handler)) {
			if ((callback == null && this.callback.equals("null") && this.what == what)
			 || (callback != null && callback.getClass().getName().equals(this.callback)))
				result = true;
		}
		
		//ValeraUtil.valeraDebugPrint(String.format("match %b msg[%d] vs sched[%d]: %s<>%s %d<>%d %s<>%s", 
		//		result, msg.msgId, logicOrder, 
		//		handler.getClass().getName(), this.handler, 
		//		handlerId, this.handlerId, 
		//		callback == null ? "null" : callback.getClass().getName(), this.callback));
		
		return result;
	}
	
	void attachMessage(Message msg) {
		this.msg = msg;
		this.msgAttachTime = SystemClock.uptimeMillis();
	}
	
	public String toString() {
		return String.format("OrderEntry[%d]: %s %d %s %d %d %d", 
				logicOrder, handler, handlerId, callback, what, arg1, arg2);
	}
}

public class ValeraEventScheduler {
	// TODO: currently we just consider the main looper. 
	// Multi-looper sync need to be finished later.
	
	private static int sArbitorClock = 1;
	private static LinkedList<OrderEntry> sSchedList = new LinkedList<OrderEntry>();
	
	private final static int SCHED_BUF_SIZE = 100;
	private final static int TIMEOUT_MILLSEC = 10000;
	private static int sSeq = 0;
	private static BufferedReader sSchedReader = null;

	private static OrderEntry readSchedFileLine(String line) {
		OrderEntry entry = null;
		Scanner scanner = new Scanner(line);
		while (scanner.hasNext()) {
			String handler = scanner.next().trim();
			int handlerId = scanner.nextInt();
			String callback = scanner.next().trim();
			int what = scanner.nextInt();
			int arg1 = scanner.nextInt();
			int arg2 = scanner.nextInt();
			
			entry = new OrderEntry(++sSeq, handler, handlerId, callback, 
					what, arg1, arg2);
			ValeraUtil.valeraDebugPrint("load sched: " + entry.toString());
		}
		scanner.close();
		
		return entry;
	}
	
	private static void loadSchedule() {
		String line = null;
		try {
			while (sSchedList.size() < SCHED_BUF_SIZE && (line = sSchedReader.readLine()) != null) {
				OrderEntry entry = readSchedFileLine(line);
				// FIXME: sync if multi-looper replay is enabled.
				//synchronized(sSchedList) {
					sSchedList.addLast(entry);
				//}
			}
		} catch (IOException e) {
			ValeraUtil.valeraDebugPrint("IOE while reading schedule file ");
			e.printStackTrace();
		}
	}
	
	public static void loadSchedulerFile(String filename) {
		try {
			if (sSchedReader == null)
				sSchedReader = new BufferedReader(new FileReader(filename));
			
			loadSchedule();
			
		} catch (FileNotFoundException fnfe) {
			ValeraUtil.valeraDebugPrint("Could not find schedule file " + filename);
		} finally {
		}
	}
	
	public static Message checkPendingAction() {
		if (ValeraGlobal.getValeraMode() != libcore.valera.ValeraConstant.MODE_REPLAY
				|| sSchedList.isEmpty())
			return null;
		OrderEntry first = sSchedList.getFirst();
		
		long now = SystemClock.uptimeMillis();
		
		if (first.msg == null && checkInputEvent(first)) {
			if (now - first.schedLoadTime >= TIMEOUT_MILLSEC) {
				sSchedList.removeFirst();
				loadSchedule();
				sArbitorClock++;
				ValeraUtil.valeraDebugPrint("execute timeout " + first.toString());
			}
			return null;
		}
		
		// TODO: Do we need timeout??
		// The first element in the pending queue is executed if the logic order match 
		// or timeout threshold reached!
		//boolean isTimeout = (now - first.msgAttachTime) >= TIMEOUT_MILLSEC;
		//ValeraUtil.valeraDebugPrint("execute timeout ? " + (now - first.msgAttachTime) + " " + first.logicOrder);
		//if (isTimeout) {
		//	ValeraUtil.valeraDebugPrint("execute timeout: " + first.toString());
		//}
		if (sArbitorClock == first.logicOrder && checkInputEvent(first)) {
			// FIXME: sync if multi-looper replay is enabled.
			//synchronized(sSchedList) {
				sSchedList.removeFirst();
				loadSchedule();
				sArbitorClock++;
			//}
			ValeraUtil.valeraDebugPrint("execute pending " + first.toString());
			return first.msg;
		}
		return null;
	}
	
	private static boolean checkInputEvent(OrderEntry entry) {
		// If not input event, do normal process
		if (!entry.handler.equals("valera.ValeraHandler") ||
			entry.what != ValeraHandler.REPLAY_INPUT_EVENT)
			return true;
		
		boolean result = false;
		int cntVsync = ValeraInputEventManager.getInstance().getVsyncCounter();
		
		// Replay input event require certain number of vsync.
		if (cntVsync >= entry.arg1) {
			// Yes, we can run this input event now.
			// Clear the vsync count.
			ValeraInputEventManager.getInstance().resetVsyncCounter();
			result = true;
		}
		ValeraUtil.valeraDebugPrint("canRunInputEvent: " + cntVsync + " " + entry.arg1);
		return result;
	}
	
	public static boolean canExecute(Message msg) {
		if (ValeraGlobal.getValeraMode() != libcore.valera.ValeraConstant.MODE_REPLAY)
			return true;
		
		// increase vsync count.
		//if (msg.getTarget().getClass().getName().equals("android.view.Choreographer$FrameHandler")) {
		//	ValeraInputEventManager.getInstance().incVsyncCounter();
		//	return true;
		//}	
		
		// Async Event (e.g. VSYNC), let it execute
		if (msg.isAsynchronous()) {
			ValeraInputEventManager.getInstance().incVsyncCounter();
			return true;
		}
		
		for (OrderEntry entry : sSchedList) {
			if (entry.msg == null && entry.match(msg)) {
				entry.attachMessage(msg);
				if (sArbitorClock == entry.logicOrder && checkInputEvent(entry)) {
					// FIXME: sync if multi-looper replay is enabled.
					//synchronized(sSchedList) {
						sSchedList.removeFirst();
						loadSchedule();
						sArbitorClock++;
					//}
					ValeraUtil.valeraDebugPrint("can execute: " + entry.toString());
					return true;
				} else {
					ValeraUtil.valeraDebugPrint("add pending: " + entry.toString());
					return false;
				}
			}
		}
		
		// If the action is not in controlled schedule, let it (NOT??) execute.
		ValeraUtil.valeraDebugPrint("NOT IN SCHEDULE MSG: " + String.format(" %s %s %d %d %d %s", 
				msg.getTarget().getClass().getName(), msg.getCallback(), msg.what, msg.arg1, 
				msg.arg2, msg.obj));
		return true;
	}
	
	// TODO: this is debug code
	private static void printSchedList() {
		System.out.print("Schedule list: ");
		for (OrderEntry entry : sSchedList) {
			System.out.print(entry.logicOrder + " ");
		}
		System.out.println();
	}
}
