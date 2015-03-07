package in.kevinj.lancamp.model;

import in.kevinj.lancamp.nativeimpl.ActiveWindowInfo;

import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.TrayIcon;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.jnativehook.GlobalScreen;
import org.jnativehook.NativeHookException;
import org.jnativehook.keyboard.NativeKeyEvent;
import org.jnativehook.keyboard.NativeKeyListener;
import org.jnativehook.mouse.NativeMouseEvent;
import org.jnativehook.mouse.NativeMouseListener;
import org.json.JSONObject;

public class Model {
	public static final String domain = "localhost";
	public static final String path = "/10k";

	public static volatile boolean popupsDisabled = false;

	private static final ThreadLocal<SimpleDateFormat> rfc822 = new ThreadLocal<SimpleDateFormat>() {
		@Override
		protected SimpleDateFormat initialValue() {
			return new SimpleDateFormat("EEE, dd-MMM-yyyy HH:mm:ss zzz", Locale.US);
		}
	};

	private final CookieManager manager;
	private final SettingsPersister settings;
	private TrayIcon trayIcon;
	private JPanel root;
	//private JLabel loginPrompt;
	private JTextField loginUsername;
	private JPasswordField loginPassword;
	private final Map<String, Integer> keyCount, clickCount;
	private final Map<String, Long> timeCount;
	private int runningClicks, runningKeystrokes;
	private long lastUpdateTime, lastSwitchTime;
	private int lastUpdateClicks, lastUpdateKeystrokes;
	private JLabel keyCounter, clickCounter;
	private Runnable watchStopper;
	private String process;
	private boolean updateLock;

	private Model(SettingsPersister settingsImpl, CookieManager managerImpl) {
		settings = settingsImpl;
		manager = managerImpl;
		keyCount = new HashMap<String, Integer>();
		clickCount = new HashMap<String, Integer>();
		timeCount = new HashMap<String, Long>();
	}

	public Model(String settingsFile) {
		this(SettingsPersister.JsonFilePersister.create(settingsFile), new CookieManager());
		assert !SwingUtilities.isEventDispatchThread();
		manager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
		CookieHandler.setDefault(manager);
	}

	public void setTrayIcon(TrayIcon trayIcon) {
		this.trayIcon = trayIcon;
	}

	public void setLoginPageComponents(JLabel loginPrompt, JTextField loginUsername, JPasswordField loginPassword) {
		assert SwingUtilities.isEventDispatchThread();

		//this.loginPrompt = loginPrompt;
		this.loginUsername = loginUsername;
		this.loginPassword = loginPassword;
	}

	public void setMiddlePanelComponents(JLabel keyCounter, JLabel clickCounter) {
		assert SwingUtilities.isEventDispatchThread();

		this.keyCounter = keyCounter;
		this.clickCounter = clickCounter;
	}

	public void setRootComponent(JPanel root) {
		assert SwingUtilities.isEventDispatchThread();

		this.root = root;
	}

	public String getDefaultUsername() {
		String value = settings.get("lastusername");
		if (value == null)
			return "";
		return value;
	}

	public void saveUsername() {
		String username = loginUsername.getText();
		if (username != null && !username.trim().isEmpty())
			settings.put("lastusername", username);
	}

	private void update() {
		assert SwingUtilities.isEventDispatchThread();
		if (updateLock) return;

		JSONObject json = new JSONObject();
		for (Map.Entry<String, Integer> clicks : clickCount.entrySet()) {
			JSONObject t = json.optJSONObject(clicks.getKey());
			if (t == null) {
				t = new JSONObject();
				json.put(clicks.getKey(), t);
			}
			t.put("clicks", clicks.getValue().intValue());
		}
		for (Map.Entry<String, Integer> keys : keyCount.entrySet()) {
			JSONObject t = json.optJSONObject(keys.getKey());
			if (t == null) {
				t = new JSONObject();
				json.put(keys.getKey(), t);
			}
			t.put("keys", keys.getValue().intValue());
		}
		for (Map.Entry<String, Long> times : timeCount.entrySet()) {
			JSONObject t = json.optJSONObject(times.getKey());
			if (t == null) {
				t = new JSONObject();
				json.put(times.getKey(), t);
			}
			t.put("times", times.getValue().longValue());
		}
		json.put("user", username);
System.out.println(json);
		WebRequester.ThroughHttpURLConnection.instance.loadPage("http://" + domain + path + "/api/update.php", "POST", json.toString(), new WebRequester.HttpResponse() {
			@Override
			public void failed(final Throwable error) {
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						updateLock = false;
						onError(error, "Could not update stats");
					}
				});
			}

			@Override
			public void success(String type, final String content) {
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						lastUpdateTime = System.currentTimeMillis();
						lastUpdateClicks = runningClicks;
						lastUpdateKeystrokes = runningKeystrokes;

						keyCount.clear();
						timeCount.clear();
						clickCount.clear();

						updateLock = false;
					}
				});
			}
		});
	}

	private void updateIfNeeded() {
		if (System.currentTimeMillis() - lastUpdateTime < 10000 || runningClicks - lastUpdateClicks + runningKeystrokes - lastUpdateKeystrokes < 20)
			return;

		update();
	}

	public void setKeyCounter(int count) {
		assert SwingUtilities.isEventDispatchThread();
		if (process == null) return;

		Integer previousVal = keyCount.put(process, Integer.valueOf(count));
		runningKeystrokes += count - (previousVal != null ? previousVal.intValue() : 0);
		String text = "<html>Keystrokes<br>";
		for (Map.Entry<String, Integer> entry : keyCount.entrySet())
			text += entry.getKey() + ": " + entry.getValue() + "<br>";
		text += "</html>";
		keyCounter.setText(text);

		updateIfNeeded();
	}

	public void setClickCounter(int count) {
		assert SwingUtilities.isEventDispatchThread();
		if (process == null) return;

		Integer previousVal = clickCount.put(process, Integer.valueOf(count));
		runningClicks += count - (previousVal != null ? previousVal.intValue() : 0);
		String text = "<html>Clicks<br>";
		for (Map.Entry<String, Integer> entry : clickCount.entrySet())
			text += entry.getKey() + ": " + entry.getValue() + "<br>";
		text += "</html>";
		clickCounter.setText(text);

		updateIfNeeded();
	}

	private void beginWatching() {
		assert SwingUtilities.isEventDispatchThread();

		try {
			Logger.getLogger(GlobalScreen.class.getPackage().getName()).setLevel(Level.OFF);
			GlobalScreen.registerNativeHook();
			watchStopper = ActiveWindowInfo.INSTANCE.startListening(this);
		} catch (NativeHookException e) {
			onError(e, "Could not listen for changes");
			return;
		}
		setProcess(ActiveWindowInfo.INSTANCE.getActiveWindowApplication());
		GlobalScreen.getInstance().addNativeKeyListener(new NativeKeyListener() {
			@Override
			public void nativeKeyPressed(NativeKeyEvent ke) {
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						if (process == null) return;

						Integer i = keyCount.get(process);
						setKeyCounter((i != null ? i.intValue() : 0) + 1);
					}
				});
			}

			@Override
			public void nativeKeyReleased(NativeKeyEvent ke) { }

			@Override
			public void nativeKeyTyped(NativeKeyEvent ke) { }
		});
		GlobalScreen.getInstance().addNativeMouseListener(new NativeMouseListener() {
			@Override
			public void nativeMouseClicked(NativeMouseEvent me) {
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						if (process == null) return;

						Integer i = clickCount.get(process);
						setClickCounter((i != null ? i.intValue() : 0) + 1);
					}
				});
			}

			@Override
			public void nativeMousePressed(NativeMouseEvent me) { }

			@Override
			public void nativeMouseReleased(NativeMouseEvent me) { }
		});
	}

	public void endWatching(boolean cleanup) {
		if (watchStopper != null) {
			watchStopper.run();
			
		}
	}

	public void flipToMainPage() {
		assert SwingUtilities.isEventDispatchThread();

		beginWatching();
		((CardLayout) root.getLayout()).show(root, "main");
	}

	public void flipToLoginPage() {
		assert SwingUtilities.isEventDispatchThread();

		endWatching(false);
		((CardLayout) root.getLayout()).show(root, "login");
	}

	public void retrieveExistingData() {
		assert SwingUtilities.isEventDispatchThread();
		// TODO Auto-generated method stub
		
	}

	public static void onWarning(Throwable error, String title) {
		System.err.println(title);
		error.printStackTrace();
	}

	public static void onError(final Component owner, Throwable error, final String title) {
		System.err.println(title);
		error.printStackTrace();
		//cause is usually more informative when we don't give the full callstack in the popup
		while (error.getCause() != null)
			error = error.getCause();
		final Throwable rootCause = error;
		//wait 100ms in case this error was made due to navigating away from page. by then, popupsDisabled will be set
		Dispatchers.instance.getTimerDispatcher().schedule(new Runnable() {
			@Override
			public void run() {
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						if (popupsDisabled)
							return;

						Object msg;
						JTextArea jta = new JTextArea(rootCause.getClass().getName() + (rootCause.getLocalizedMessage() != null ? "\n" + rootCause.getLocalizedMessage() : ""));
						jta.setEditable(false);
						JScrollPane jsp = new JScrollPane(jta);
						jsp.setPreferredSize(new Dimension(480, 200));
						msg = jsp;
						JOptionPane.showMessageDialog(owner, msg, title, JOptionPane.ERROR_MESSAGE);
					}
				});
			}
		}, 100, TimeUnit.MILLISECONDS);
	}

	public void onError(Throwable error, String title) {
		onError(root, error, title);
	}

	public void clearCookies() {
		manager.getCookieStore().removeAll();
	}

	public static String cookieToString(HttpCookie cookie) {
		StringBuilder sb = new StringBuilder();
		sb.append(cookie.getName()).append("=").append(cookie.getValue());
		if (cookie.getMaxAge() != -1L)
			sb.append(";Expires=").append(rfc822.get().format(new Date(System.currentTimeMillis() + cookie.getMaxAge()))).append(";Max-Age=").append(cookie.getMaxAge());
		//sb.append(";Version=").append(cookie.getVersion());
		if (cookie.getDomain() != null && !cookie.getDomain().trim().isEmpty())
			sb.append(";Domain=").append(cookie.getDomain());
		if (cookie.getPath() != null && !cookie.getPath().trim().isEmpty())
			sb.append(";Path=").append(cookie.getPath());
		if (cookie.getSecure())
			sb.append(";Secure");
		if (cookie.isHttpOnly())
			sb.append(";HttpOnly");
		if (cookie.getComment() != null && !cookie.getComment().trim().isEmpty())
			sb.append(";Comment=").append(cookie.getComment());
		if (cookie.getCommentURL() != null && !cookie.getCommentURL().trim().isEmpty())
			sb.append(";CommentURL=").append(cookie.getCommentURL());
		if (cookie.getDiscard())
			sb.append(";Discard");
		return sb.toString();
	}

	public static String makeCookie(String name, String value, String path, String domain, boolean secure) {
		HttpCookie cookie = new HttpCookie(name, value);
		cookie.setPath(path);
		cookie.setDomain(domain);
		cookie.setSecure(secure);
		return Model.cookieToString(cookie);
	}

	public String getLoggedInSessionToken() {
		for (HttpCookie cookie : manager.getCookieStore().getCookies())
			if (cookie.getName().contains("user") && !cookie.hasExpired())
				return cookie.getValue();

		return null;
	}

	public boolean isLoginUsernameFieldFilled() {
		return loginUsername.getText() != null && !loginUsername.getText().isEmpty();
	}

	public void focusOnLoginField() {
		assert SwingUtilities.isEventDispatchThread();

		if (loginUsername != null)
			loginUsername.requestFocusInWindow();
	}

	public void focusOnPasswordField() {
		assert SwingUtilities.isEventDispatchThread();

		if (loginPassword != null)
			loginPassword.requestFocusInWindow();
	}

	public void setProcess(String process) {
		assert SwingUtilities.isEventDispatchThread();

		this.process = process;
		long now = System.currentTimeMillis();
		Long runningTime = timeCount.get(process);
		if (process != null)
			timeCount.put(process, Long.valueOf((runningTime != null ? runningTime.longValue() : 0) + now - lastSwitchTime));
		this.lastSwitchTime = now;
		if (trayIcon != null)
			if (process != null)
				trayIcon.setToolTip("Using " + process);
			else
				trayIcon.setToolTip(null);
	}
private String username;
	public void setUser(String username) {
		this.username = username;
	}
}
