package valera;

import libcore.valera.ValeraConfig;
import libcore.valera.ValeraUtil;
import android.os.Handler;
import android.os.Process;
import android.os.Message;
import android.util.AndroidRuntimeException;
import android.util.Log;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;

public class ValeraTrace {
	private static final String TAG = "VALERA";

	public static void printPostMsg(Message msg) {
		if (ValeraConfig.isValeraEnabled()) {
		//if (ValeraGlobal.getValeraMode() == ValeraGlobal.MODE_RECORD) {
			Handler target = msg.getTarget();

			if (target == null)
				throw new AndroidRuntimeException(
						"ValeraError: Message does not have a target.");
			
			// Ignore ValeraPollingThread message.
			if (target.getClass().getName().equals("valera.ValeraHandler"))
					//&& msg.what == ValeraHandler.WAKEUP_AND_CHECK)
				return;

			int hashQ = target.getLooper().getQueue().hashCode();
			String dispatcher = target.toString();
			String callback = msg.getCallback() == null ? "null" : 
				msg.getCallback().getClass().getName() + '@' + Integer.toHexString(msg.getCallback().hashCode());
			String obj = msg.obj == null ? "null" : msg.obj.getClass().getName();
			//String callStack = ValeraUtil.getCallingStack("#");
			
			//ValeraUtil.valeraDebugPrint(String.format("POST_MSG %d %s", msg.msgId, callStack));
			
			String info = String.format("%d %d %d %s %s %d %d %d %s %s",
					ValeraGlobal.getRelativeTime() ,hashQ, msg.msgId, dispatcher, callback, 
					msg.what, msg.arg1, msg.arg2, obj, null);

			Thread.currentThread().valeraPostMessage(info);
			/*
			Log.i(TAG,
					String.format(
							"[PostMsg]: [tid:%d] => [tid:%d] [msgId:%d] [type:%d] [dispatcher:%s] [callback:%s] %s [stack:%s]\n",
							curTid, dstTid, msg.msgId, type, dispatcher,
							callback, info, getCallingStack()));
			*/
		}
	}

	public static void printActionBegin(Message msg, int hCnt) {
		if (ValeraConfig.isValeraEnabled()) {
			// Ignore ValeraPollingThread message.
			if (msg.getTarget().getClass().getName().equals("valera.ValeraHandler"))
					//&& msg.what == ValeraHandler.WAKEUP_AND_CHECK)
				return;
			String info = String.format("%d %d %d %d", msg.msgId, 
					ValeraGlobal.getRelativeTime(), hCnt, msg.isAsynchronous() ? 1 : 0);
			Thread.currentThread().valeraActionBegin(info);
		}
	}

	public static void printActionEnd(Message msg, int hCnt) {
		if (ValeraConfig.isValeraEnabled()) {
			// Ignore ValeraPollingThread message.
			if (msg.getTarget().getClass().getName().equals("valera.ValeraHandler"))
					//&& msg.what == ValeraHandler.WAKEUP_AND_CHECK)
				return;
			String info = String.format("%d %d %d %d", msg.msgId, 
					ValeraGlobal.getRelativeTime(), hCnt, msg.isAsynchronous() ? 1 : 0);
			Thread.currentThread().valeraActionEnd(info);
		}
	}

	public static void printAttachQ(int hashQ) {
		if (ValeraConfig.isValeraEnabled()) {
		//if (ValeraGlobal.getValeraMode() == ValeraGlobal.MODE_RECORD) {
			Thread.currentThread().valeraAttachQ(hashQ);
			/*
			long tid = Thread.currentThread().getId();
			Log.i(TAG,
					String.format("[AttachQ]: [tid:%d] [queue:%d]", tid, hashQ));
			*/
		}
	}
	
	public static void printInputEventBegin(int msgId, InputEvent event) {
		if (ValeraConfig.isValeraEnabled()) {
		//if (ValeraGlobal.getValeraMode() == ValeraGlobal.MODE_RECORD) {
			// TODO: fill out correct info later.
			String info = null;
			if (event instanceof KeyEvent) {
				info = String.format("KeyEvent %s", 
						KeyEvent.actionToString(((KeyEvent) event).getAction()));
			} else if (event instanceof MotionEvent) {
				info = String.format("MotionEvent %s", 
						MotionEvent.actionToString(((MotionEvent) event).getAction()));
			}
			info = ValeraGlobal.getRelativeTime() + " " + info;
			Thread.currentThread().valeraInputEventBegin(msgId, info);
		}
	}
	
	public static void printInputEventEnd(int msgId, InputEvent event) {
		if (ValeraConfig.isValeraEnabled()) {
		//if (ValeraGlobal.getValeraMode() == ValeraGlobal.MODE_RECORD) {
			// TODO: fill out correct info later.
			String info = null;
			if (event instanceof KeyEvent) {
				info = String.format("KeyEvent %s", 
						KeyEvent.actionToString(((KeyEvent) event).getAction()));
			} else if (event instanceof MotionEvent) {
				info = String.format("MotionEvent %s", 
						MotionEvent.actionToString(((MotionEvent) event).getAction()));
			}
			info = ValeraGlobal.getRelativeTime() + " " + info;
			Thread.currentThread().valeraInputEventEnd(msgId, info);
		}
	}
}
