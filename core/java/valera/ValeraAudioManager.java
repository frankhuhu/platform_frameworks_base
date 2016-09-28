package valera;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;

import android.util.Log;
import libcore.valera.ValeraConfig;
import libcore.valera.ValeraConstant;
import libcore.valera.ValeraLogReader;
import libcore.valera.ValeraLogWriter;
import libcore.valera.ValeraUtil;

public class ValeraAudioManager {
	
	private final static String TAG = "ValeraAudioManager";
	private final static String LOG_FILE = "audio.bin";
	
	private final static int TYPE_AUDIO_EVENT = 1;
	
	private static ValeraAudioManager sDefaultManager;
	private static ValeraLogReader sReader;
	private static ValeraLogWriter sWriter;
	
	private Object sLock;
	
	
	private ValeraAudioManager() {
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
		} catch (Exception e) {
			Log.e(TAG, e.toString());
			e.printStackTrace();
			System.exit(-1);
		}
	}
	
	public static ValeraAudioManager getInstance() {
		synchronized (ValeraAudioManager.class) {
			if (sDefaultManager == null) {
				sDefaultManager = new ValeraAudioManager();
			}
			return sDefaultManager;
		}
	}
	
	public void recordAudioReadByteArray(byte[] audioData, int offsetInBytes, int sizeInBytes, 
			int nread, long millisec) {
		synchronized (sLock) {
			try {
				sWriter.writeInt(TYPE_AUDIO_EVENT);
				sWriter.writeLong(ValeraGlobal.getRelativeTime());
				sWriter.writeLong(millisec);
				if (audioData == null) {
					sWriter.writeInt(0);
				} else {
					sWriter.writeInt(1);
					sWriter.writeInt(nread);
					if (nread > 0) {
						sWriter.writeByteArray(audioData, offsetInBytes, nread);
					}
				}
				sWriter.flush();
				ValeraUtil.valeraDebugPrint(String.format(
						"yhu009: record audio byte[] off=%d size=%d nread=%d",
						offsetInBytes, sizeInBytes, nread));
			} catch (Exception e) {
				Log.e(TAG, e.toString());
				e.printStackTrace();
				System.exit(-1);
			}
		}
	}
	
	public void replayAudioReadByteArray() {
		// TODO: impl replay.
	}
	
	public void recordAudioReadShortArray(short[] audioData, int offsetInShorts, int sizeInShorts,
			int nread, long millisec) {
		synchronized (sLock) {
			try {
				sWriter.writeInt(TYPE_AUDIO_EVENT);
				sWriter.writeLong(ValeraGlobal.getRelativeTime());
				sWriter.writeLong(millisec);
				if (audioData == null) {
					sWriter.writeInt(0);
				} else {
					sWriter.writeInt(1);
					sWriter.writeInt(nread);
					if (nread > 0) {
						sWriter.writeShortArray(audioData, offsetInShorts, nread);
					}
				}
				sWriter.flush();
				ValeraUtil.valeraDebugPrint(String.format(
						"yhu009: record audio short[] off=%d size=%d nread=%d",
						offsetInShorts, sizeInShorts, nread));
			} catch (Exception e) {
				Log.e(TAG, e.toString());
				e.printStackTrace();
				System.exit(-1);
			}
		}
	}

	public void replayAudioReadShortArray() {
		// TODO: impl replay.
	}
	
	public void recordAudioReadByteBuffer(ByteBuffer audioBuffer, int sizeInBytes,
			int nread, long millisec) {
		synchronized (sLock) {
			try {
				sWriter.writeInt(TYPE_AUDIO_EVENT);
				sWriter.writeLong(ValeraGlobal.getRelativeTime());
				sWriter.writeLong(millisec);
				if (audioBuffer == null) {
					sWriter.writeInt(0);
				} else {
					sWriter.writeInt(1);
					sWriter.writeInt(nread);
					byte[] buf = audioBuffer.array();
					if (nread > 0) {
						sWriter.writeByteArray(buf, 0, nread);
					}
				}
				sWriter.flush();
				ValeraUtil.valeraDebugPrint(String.format(
						"yhu009: record audio ByteBuffer off=%d size=%d nread=%d",
						0, sizeInBytes, nread));
			} catch (Exception e) {
				Log.e(TAG, e.toString());
				e.printStackTrace();
				System.exit(-1);
			}
		}
	}
	
	public void replayAudioReadByteBuffer() {
		// TODO: impl replay.
	}
}
