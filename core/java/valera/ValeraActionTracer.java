package valera;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Scanner;

import android.os.Handler;
import android.os.Message;

import libcore.valera.ValeraConfig;
import libcore.valera.ValeraUtil;

class ValeraAction {
	int type; // 1 - callback msg; 2 - handle msg by 'what'
	String handler;
	String callback;
	int what;
	
	ValeraAction(int type, String handler, String callback, int what) {
		this.type = type;
		this.handler = handler;
		this.callback = callback;
		this.what = what;
	}
	
	public String toString() {
		return String.format("ValeraTrace type=%d handler=%s callback=%s what=%d", 
				type, handler, callback, what);
	}
}

public class ValeraActionTracer {

	private static ArrayList<ValeraAction> sTracingActions = new ArrayList<ValeraAction>();

	public static void loadTraceActionFile(String filename) {
		Scanner scanner = null;
		try {
			FileInputStream fis = new FileInputStream(new File(filename));
			ValeraUtil.valeraDebugPrint("Find trace action file " + filename);
			scanner = new Scanner(fis);
			while (scanner.hasNextLine()) {
				String line = scanner.nextLine();
				
				// Line starting with '#' is considered as comments.
				if (line.startsWith("#"))
					continue;
				
				String[] ss = line.split(" +");
				ValeraUtil.valeraAssert(ss.length == 3, "Invalid format for action trace. line = " + line);
				int type = Integer.parseInt(ss[0].trim());
				String handler = ss[1].trim();
				ValeraAction va = null;
				if (type == 1) {
					String callback = ss[2].trim();
					va = new ValeraAction(1, handler, callback, 0);
				} else if (type == 2) {
					int what = Integer.parseInt(ss[2].trim());
					va = new ValeraAction(2, handler, null, what);
				} else {
					ValeraUtil.valeraAssert(false, "Invalid format for action trace. Type can only be 1 or 2. This type is " + type);
				}
				sTracingActions.add(va);
				ValeraUtil.valeraDebugPrint(va.toString());
			}
			
			scanner.close();
			fis.close();
		} catch (FileNotFoundException fnfe) {
			ValeraUtil.valeraDebugPrint("Could not find trace action file " + filename);
		} catch (Exception e) {
			ValeraUtil.valeraAbort(e);
		} finally {
			if (scanner != null)
				scanner.close();
		}
	}

	public static boolean traceThisAction(Message msg) {
		if (ValeraConfig.isValeraEnabled() == false)
			return false;
		
		Handler handler = msg.getTarget();
		String handlerName = handler.getClass().getName();
		Runnable callback = msg.getCallback();
		int what = msg.what;
		
		for (ValeraAction action : sTracingActions) {
			if (!handlerName.equals(action.handler))
				continue;
			
			if (callback != null) {
				//System.out.println("yhu009:tracethisaction " + handlerName + " " + callback);
				// type 1;
				if (action.type == 1 && callback.getClass().getName().equals(action.callback))
					return true;
			} else {
				//System.out.println("yhu009:tracethisaction " + handlerName + " " + what);
				// type 2;
				if (action.type == 2 && what == action.what)
					return true;
			}
			
			/*
			boolean cond1 = handlerName.equals(action.handler);
			boolean cond2 = callback == null && action.callback.equals("null");
			boolean cond3 = callback != null && callback.getClass().getName().equals(action.callback);
			
			if (cond1 && (cond2 || cond3))
				return true;
			*/
		}
		return false;
	}
}
