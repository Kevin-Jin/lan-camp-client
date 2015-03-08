package in.kevinj.lancamp.nativeimpl;

import in.kevinj.lancamp.model.Model;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.SwingUtilities;

import jnacontrib.x11.api.X;
import jnacontrib.x11.api.X.X11Exception;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.platform.unix.X11;

public class X11ProcFsWindowInfo extends ActiveWindowInfo {
	private interface Handle extends Library {
		Handle module = (Handle) Native.loadLibrary("c", Handle.class);

		int readlink(String path, byte[] buffer, int size);
	}

	private static String readlink(String path) throws java.io.FileNotFoundException {
		byte[] buffer = new byte[300];
		int size = Handle.module.readlink(path, buffer, 300);
		if (size > 0)
			return new String(buffer, 0, size);
		else
			throw new java.io.FileNotFoundException(path);
	}

	private X.Display display;

	public String getActiveWindowProcess() {
		try {
			return readlink("/proc/" + display.getActiveWindow().getPID() + "/exe");
		} catch (FileNotFoundException | X11Exception e) {
			throw new RuntimeException(e);
		}
	}

	public String getActiveWindowCommand() {
		try {
			return new String(Files.readAllBytes(Paths.get("/proc/" + display.getActiveWindow().getPID() + "/cmdline"))).replaceAll("\0", " ");
		} catch (IOException | X11Exception e) {
			throw new RuntimeException(e);
		}
	}

	public String getActiveWindowTitle() {
		try {
			return display.getActiveWindow().getTitle();
		} catch (X11Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public Runnable startListening(final Model m) {
		final AtomicBoolean stop = new AtomicBoolean(false);
		new Thread(new Runnable() {
			@Override
			public void run() {
				display = new X.Display();
				m.setProcess(getActiveWindowApplication());

				X11.XEvent event = new X11.XEvent();
				display.getRootWindow().selectInput(X11.PropertyChangeMask);
				//TODO: http://www.linuxquestions.org/questions/showthread.php?p=2431345#post2431345
				try {
					int currentProcess = 0;
					while (!stop.get()) {
						display.getRootWindow().nextEvent(event);
						//handle the union type
						event.setType(X11.XPropertyEvent.class);
						event.read();
						switch (event.type) {
							case X11.PropertyNotify:
								if (X11.INSTANCE.XGetAtomName(display.getX11Display(), event.xproperty.atom).equals("_NET_ACTIVE_WINDOW") && display.getActiveWindow().getID() != 0) {
									int nowProcess = display.getActiveWindow().getPID().intValue();
									if (nowProcess != currentProcess) {
										currentProcess = nowProcess;
										final String title = getActiveWindowTitle();
										final String process = getActiveWindowApplication();
										SwingUtilities.invokeLater(new Runnable() {
											@Override
											public void run() {
												System.out.println("SWITCHED TO " + title);
												m.setProcess(process);
											}
										});
									}
								}
								break;
						}
					}
				} catch (X11Exception e) {
					throw new RuntimeException(e);
				}
			}
		}).start();
		return new Runnable() {
			@Override
			public void run() {
				stop.set(true);
			}
		};
	}
}
