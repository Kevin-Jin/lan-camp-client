package in.kevinj.lancamp.nativeimpl;

import in.kevinj.lancamp.model.Model;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.spi.AbstractInterruptibleChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.SwingUtilities;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.BaseTSD.SIZE_T;
import com.sun.jna.platform.win32.BaseTSD.ULONG_PTR;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinDef.DWORD;
import com.sun.jna.platform.win32.WinDef.HMODULE;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.UINT;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.platform.win32.WinUser.MSG;
import com.sun.jna.ptr.ByReference;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary.StdCallCallback;

public class Win32WindowInfo extends ActiveWindowInfo {
	public static class StringByReference extends ByReference {
	    public StringByReference() {
	        this(0);
	    }

	    public StringByReference(int size) {
	        super(size < Pointer.SIZE ? Pointer.SIZE : size);
	        getPointer().clear(size < Pointer.SIZE ? Pointer.SIZE : size);
	    }

	    public StringByReference(String str) {
	        super(str.length() < Pointer.SIZE ? Pointer.SIZE : str.length() + 1);
	        setValue(str);
	    }

	    private void setValue(String str) {
	        getPointer().setString(0, str);
	    }

	    public String getValue() {
	        return getPointer().getPointer(0).getString(0);
	    }
	}

	private static class PsapiDLL {
		static {
			Native.register("psapi");
		}

		public static native int GetModuleFileNameExW(Pointer hProcess, Pointer hmodule, char[] lpBaseName, int size);
	}

	private static class Kernel32DLL {
		static {
			Native.register("kernel32");
		}

		public static int PROCESS_QUERY_INFORMATION = 0x0400;
		public static int PROCESS_VM_READ = 0x0010;

		public static native int GetLastError();

		public static native Pointer OpenProcess(int dwDesiredAccess, boolean bInheritHandle, Pointer pointer);

		public static native int GetProcAddress(HMODULE hModule, String lpProcName);

		public static native HANDLE CreateRemoteThread(Pointer hProcess, Pointer lpThreadAttributes, ULONG_PTR dwStackSize, int lpStartAddress, Pointer lpParameter, int dwCreationFlags, PointerByReference lpThreadId);

		public static native boolean GetExitCodeThread(HANDLE hThread, IntByReference lpExitCode);

		public static native boolean GetExitCodeThread(HANDLE hThread, StringByReference lpExitCode);

		public static native Pointer HeapAlloc(HANDLE hHeap, DWORD dwFlags, SIZE_T dwBytes);

		public static native HANDLE GetProcessHeap();

		public static native boolean ReadProcessMemory(Pointer hProcess, Pointer lpBaseAddress, Pointer lpBuffer, SIZE_T nSize, LongByReference lpNumberOfBytesRead);
	}

	private static class User32DLL {
		public static interface WinEventProc extends StdCallCallback {
			void callback(HANDLE hWinEventHook, UINT eventType, HWND hWnd, int idObject, int idChild, UINT dwEventThread, UINT dwmsEventTime);
		}

		static {
			Native.register("user32");
		}

		public static final int EVENT_SYSTEM_FOREGROUND_INT = 0x03;
		public static final int EVENT_SYSTEM_MINIMIZESTART_INT = 0x16;
		public static final int EVENT_SYSTEM_MINIMIZEEND_INT = 0x17;

	    public static final UINT WINEVENT_OUTOFCONTEXT = new UINT(0x00);
	    public static final UINT EVENT_SYSTEM_FOREGROUND = new UINT(EVENT_SYSTEM_FOREGROUND_INT);
	    public static final UINT EVENT_SYSTEM_MINIMIZESTART = new UINT(EVENT_SYSTEM_MINIMIZESTART_INT);
	    public static final UINT EVENT_SYSTEM_MINIMIZEEND = new UINT(EVENT_SYSTEM_MINIMIZEEND_INT);

		public static native int GetWindowThreadProcessId(HWND hWnd, PointerByReference pref);

		public static native HWND GetForegroundWindow();

		public static native int GetWindowTextW(HWND hWnd, char[] lpString, int nMaxCount);

		public static native HANDLE SetWinEventHook(UINT eventMin, UINT eventMax, HWND hmodWinEventProc, WinEventProc lpfnWinEventProc, int idProcess, int idThread, UINT dwFlags);

		public static native boolean UnhookWinEvent(HANDLE hWinEventHook);
	}

	public static interface AccessRights {
		public static final int MAXIMUM_ALLOWED = 0x02000000;
	}

	private static String getProcessForWindow(HWND hwnd) {
		final int MAX_TITLE_LENGTH = 1024;
		char[] buffer = new char[MAX_TITLE_LENGTH * 2];
		PointerByReference pointer = new PointerByReference();
		User32DLL.GetWindowThreadProcessId(hwnd, pointer);
		Pointer process = Kernel32DLL.OpenProcess(Kernel32DLL.PROCESS_QUERY_INFORMATION | Kernel32DLL.PROCESS_VM_READ, false, pointer.getValue());
		PsapiDLL.GetModuleFileNameExW(process, null, buffer, MAX_TITLE_LENGTH);
		return Native.toString(buffer);
	}

	/**
	 * Get the launching path
	 */
	@Override
	public String getActiveWindowApplication() {
		HWND hwnd = User32DLL.GetForegroundWindow();
		final int MAX_TITLE_LENGTH = 1024;
		char[] buffer = new char[MAX_TITLE_LENGTH * 2];
		PointerByReference pointer = new PointerByReference();
		User32DLL.GetWindowThreadProcessId(hwnd, pointer);
		Pointer process = Kernel32DLL.OpenProcess(Kernel32DLL.PROCESS_QUERY_INFORMATION | Kernel32DLL.PROCESS_VM_READ, false, pointer.getValue());
		PsapiDLL.GetModuleFileNameExW(process, null, buffer, MAX_TITLE_LENGTH);

		String processPath = getActiveWindowProcess();
		File f = new File(processPath);
		try {
			InputStream fis = new FileInputStream(f);

			byte[] fileBuffer = new byte[1024];
			MessageDigest complete = MessageDigest.getInstance("MD5");
			int numRead;

			do {
				numRead = fis.read(fileBuffer);
				if (numRead > 0) {
					complete.update(fileBuffer, 0, numRead);
				}
			} while (numRead != -1);

			fis.close();
		} catch (IOException e) {
			Logger.getLogger(Win32WindowInfo.class.getName()).log(Level.INFO, "", e);
		} catch (NoSuchAlgorithmException e) {
			Logger.getLogger(Win32WindowInfo.class.getName()).log(Level.INFO, "", e);
		}

		processPath = processPath.substring(processPath.lastIndexOf(File.separator) + 1);
		int fileExtension = processPath.lastIndexOf('.');
		if (fileExtension != -1)
			processPath = processPath.substring(0, fileExtension);
		if (processPath.equalsIgnoreCase("javaw") || processPath.equalsIgnoreCase("java") || processPath.equalsIgnoreCase("python")) {
			//TODO: extract info from command line arguments rather than blacklist
			//https://groups.google.com/forum/embed/?place=msg%2Fmicrosoft.public.win32.programmer.kernel%2FFoe4xnAxQ7I%2FBnXfGQxdSJsJ#!msg/microsoft.public.win32.programmer.kernel/Foe4xnAxQ7I/BnXfGQxdSJsJ
			pointer = new PointerByReference();
			User32DLL.GetWindowThreadProcessId(hwnd, pointer);
			process = Kernel32DLL.OpenProcess(AccessRights.MAXIMUM_ALLOWED, false, pointer.getValue());

			int handle = Kernel32DLL.GetProcAddress(Kernel32.INSTANCE.GetModuleHandle("KERNEL32.DLL"), "GetCommandLineA");
			if (handle == 0)
				return null;
			pointer = new PointerByReference();
			HANDLE thread = Kernel32DLL.CreateRemoteThread(process, Pointer.NULL, new ULONG_PTR(0), handle, Pointer.NULL, 0, pointer);
			if (thread == null || Pointer.nativeValue(thread.getPointer()) == 0)
				return null;
			if (Kernel32.INSTANCE.WaitForSingleObject(thread, WinBase.INFINITE) != 0)
				return null;
			StringByReference cmdLineStr = new StringByReference();
			if (!Kernel32DLL.GetExitCodeThread(thread, cmdLineStr))
				return null;
			if (!Kernel32.INSTANCE.CloseHandle(thread))
				return null;

			handle = Kernel32DLL.GetProcAddress(Kernel32.INSTANCE.GetModuleHandle("KERNEL32.DLL"), "lstrlenA");
			if (handle == 0)
				return null;
			thread = Kernel32DLL.CreateRemoteThread(process, Pointer.NULL, new ULONG_PTR(0), handle, cmdLineStr.getPointer().getPointer(0), 0, pointer);
			if (thread == null || Pointer.nativeValue(thread.getPointer()) == 0)
				return null;
			if (Kernel32.INSTANCE.WaitForSingleObject(thread, WinBase.INFINITE) != 0)
				return null;
			IntByReference cmdLineStrLen = new IntByReference();
			if (!Kernel32DLL.GetExitCodeThread(thread, cmdLineStrLen))
				return null;
			if (!Kernel32.INSTANCE.CloseHandle(thread))
				return null;

			Pointer heap = Kernel32DLL.HeapAlloc(Kernel32DLL.GetProcessHeap(), new DWORD(0x00000008), new SIZE_T(cmdLineStrLen.getValue()));
			if (Pointer.nativeValue(heap) == 0)
				return null;
			LongByReference bytesRead = new LongByReference();
			if (!Kernel32DLL.ReadProcessMemory(process, cmdLineStr.getPointer().getPointer(0), heap, new SIZE_T(cmdLineStrLen.getValue()), bytesRead))
				return null;

			String launchingCommand = heap.getString(0);
			if (launchingCommand.contains("org.eclipse.equinox.launcher"))
				return "eclipse";

			return null;
		}
		return processPath;
	}

	@Override
	public String getActiveWindowProcess() {
		return getProcessForWindow(User32DLL.GetForegroundWindow());
	}

	@Override
	public String getActiveWindowTitle() {
		HWND hwnd = User32DLL.GetForegroundWindow();
		final int MAX_TITLE_LENGTH = 1024;
		char[] buffer = new char[MAX_TITLE_LENGTH * 2];
		User32DLL.GetWindowTextW(hwnd, buffer, MAX_TITLE_LENGTH);
		return Native.toString(buffer);
	}

	private static int interruptibleGetMessage(MSG msg, User32 lib, final AtomicBoolean quit, final long id) {
		return new AbstractInterruptibleChannel() {
			public int run(MSG msg, User32 lib) {
				boolean completed = false;
				begin();
				try {
					int returnValue = lib.GetMessage(msg, null, 0, 0);
					completed = true;
					return returnValue;
				} finally {
					try {
						end(completed);
					} catch (AsynchronousCloseException e) {
						Logger.getLogger(Win32WindowInfo.class.getName()).log(Level.INFO, "", e);
					}
				}
			}

			@Override
			protected void implCloseChannel() throws IOException {
				quit.set(true);
				User32DLL.UnhookWinEvent(new HANDLE(new Pointer(id)));
			}
		}.run(msg, lib);
	}

	/**
	 * 
	 * @return A task that can be used to stop listening.
	 */
	@Override
	public Runnable startListening(final Model m) {
		//FIXME: this dies after a few seconds. thread safety issues?
		final AtomicBoolean quit = new AtomicBoolean(false);
		final BlockingQueue<HANDLE> queue = new ArrayBlockingQueue<HANDLE>(1);
		final Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				HANDLE handle = User32DLL.SetWinEventHook(User32DLL.EVENT_SYSTEM_FOREGROUND, User32DLL.EVENT_SYSTEM_MINIMIZEEND, new HWND(Pointer.NULL), new User32DLL.WinEventProc() {
					private long currentProcess;

					@Override
					public void callback(HANDLE hWinEventHook, UINT eventType, final HWND hWnd, int idObject, int idChild, UINT dwEventThread, UINT dwmsEventTime) {
						if (quit.get())
							return;

						switch (eventType.intValue()) {
							case User32DLL.EVENT_SYSTEM_FOREGROUND_INT:
							case User32DLL.EVENT_SYSTEM_MINIMIZEEND_INT:
								if (hWnd == null)
									return;

								long nowProcess = Pointer.nativeValue(hWnd.getPointer());
								if (nowProcess != currentProcess) {
									currentProcess = nowProcess;
									SwingUtilities.invokeLater(new Runnable() {
										@Override
										public void run() {
											System.out.println("SWITCHED TO " + getActiveWindowTitle());
											m.setProcess(getActiveWindowApplication());
										}
									});
								}
								break;
						}
					}
				}, 0, 0, User32DLL.WINEVENT_OUTOFCONTEXT);
				if (handle == null)
					handle = new HANDLE(Pointer.NULL);
				queue.offer(handle);

				int result;
				MSG msg = new MSG();
				final User32 lib = User32.INSTANCE;

				long id = Pointer.nativeValue(handle.getPointer()); 
				while (!quit.get() && (result = interruptibleGetMessage(msg, lib, quit, id)) != 0) {
					if (result == -1) {
						System.err.println("error in get message");
						break;
					} else {
						System.err.println("got message");
						lib.TranslateMessage(msg);
						lib.DispatchMessage(msg);
					}
				}
			}
		});
		t.start();
		HANDLE handle;
		try {
			handle = queue.poll(2, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			handle = null;
		}
		if (handle == null)
			handle = new HANDLE(Pointer.NULL);

		return new Runnable() {
			@Override
			public void run() {
				//t.interrupt();
			}
		};
	}
}
