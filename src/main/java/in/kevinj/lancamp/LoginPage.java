package in.kevinj.lancamp;

import in.kevinj.lancamp.model.Dispatchers;
import in.kevinj.lancamp.model.Model;
import in.kevinj.lancamp.model.WebRequester;

import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.io.IOException;
import java.net.CookieHandler;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

import org.json.JSONException;
import org.json.JSONObject;

public final class LoginPage {
	public static final LoginPage instance = new LoginPage();

	private LoginPage() {
		
	}

	private static String login(String userName, String password) throws Throwable {
		JSONObject json = new JSONObject();
		json.put("username", userName);
		json.put("password", password);
		final BlockingQueue<Object> queue = new ArrayBlockingQueue<Object>(1);
//TODO: implement login
if (Boolean.TRUE) return "kevin";
		WebRequester.ThroughHttpURLConnection.instance.loadPage("http://" + Model.domain + Model.path + "/api/login.php", "POST", json.toString(), new WebRequester.HttpResponse() {
			@Override
			public void failed(Throwable error) {
				queue.offer(error);
			}

			@Override
			public void success(String type, String content) {
				if (!type.equals("application/json")) {
					queue.offer(new Throwable("Invalid response"));
					return;
				}

				try {
					JSONObject json = new JSONObject(content);
					if (json.has("error")) {
						queue.offer(new Throwable(json.getString("error")));
						return;
					}
					queue.offer(json.getString("user"));
				} catch (JSONException e) {
					queue.offer(e);
					return;
				}
			}
		});
		Object result;
		try {
			result = queue.poll(2, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			result = e;
		}
		if (result instanceof Throwable)
			throw (Throwable) result;
		else
			return (String) result;
	}

	private static boolean logoff(String host, String token) {
		return false;
	}

	public Component constructLoginPage(RootPaneContainer frame, final Window w, final Model m, final Runnable displayMainPage) {
		JPanel panel = new JPanel(new GridBagLayout());
		panel.setBackground(new Color(0xff, 0xbb, 0x00));
		GridBagConstraints c = new GridBagConstraints();

		c.gridwidth = 3;
		c.insets.bottom = 10;
		c.gridx = 0;
		c.gridy = 0;
		final JLabel prompt;// = new JLabel("Log in");
		try {
			prompt = new JLabel(new ImageIcon(ImageIO.read(Thread.currentThread().getContextClassLoader().getResourceAsStream("in/kevinj/lancamp/resources/10KLIFEtopicon.png"))));
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			return null;
		}
		panel.add(prompt, c);

		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridwidth = 1;
		c.insets.bottom = 0;
		c.insets.right = 10;
		c.gridx = 0;
		c.gridy = 1;
		panel.add(new JLabel("Login:"), c);
		c.gridy = 2;
		panel.add(new JLabel("Password:"), c);

		final JTextField username = new JTextField(m.getDefaultUsername(), 20);
		final JPasswordField password = new JPasswordField();
		FocusListener highlightOnFocus = new FocusAdapter() {
			@Override
			public void focusGained(FocusEvent e) {
				((JTextComponent) e.getComponent()).setCaretPosition(0);
				((JTextComponent) e.getComponent()).selectAll();
			}
		};
		username.addFocusListener(highlightOnFocus);
		password.addFocusListener(highlightOnFocus);
		c.gridwidth = 2;
		c.gridx = 1;
		c.gridy = 1;
		c.insets.right = 0;
		panel.add(username, c);
		c.gridy = 2;
		panel.add(password, c);

		final JButton go;
		try {
			//Thread.currentThread().getContextClassLoader().getResourceAsStream("in/kevinj/lancamp/resources/refresh.png")
			//fails because Java .policy believes applet is accessing the
			//root directory of the file system.
			//getResourceAsStream() does not suffer from this problem but now
			//we have to resort to the synchronous ImageIO.read() call rather
			//than the asynchronous Tookit.getImage() call.
			go = new JButton(new ImageIcon(ImageIO.read(Thread.currentThread().getContextClassLoader().getResourceAsStream("in/kevinj/lancamp/resources/v3_LOG_IN.png")).getScaledInstance(500, 53, Image.SCALE_SMOOTH)));
			go.setOpaque(false);
			go.setContentAreaFilled(false);
			go.setBorderPainted(false);
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException("Unable to load refresh icon", e);
		}

		//final JButton go = new JButton("Login");
		go.addActionListener(new ActionListener() {
			private void loggedIn() {
				m.saveUsername();
				go.setEnabled(true);
				displayMainPage.run();
			}

			@Override
			public void actionPerformed(ActionEvent e) {
				go.setEnabled(false);
				if (m.getLoggedInSessionToken() == null) {
					doLogin(m, username.getText(), String.valueOf(password.getPassword()), new Runnable() {
						@Override
						public void run() {
							if (m.getLoggedInSessionToken() != null) {
								loggedIn();
							} else {
								go.setEnabled(true);
								prompt.setText("Login failed. Try again");
							}
						}
					});
				} else {
					loggedIn();
				}
			}
		});
		c.fill = GridBagConstraints.NONE;
		c.anchor = GridBagConstraints.LINE_END;
		c.gridwidth = 3;
		c.gridx = 0;
//c.gridwidth = 1;
//c.gridx = 2;
		c.gridy = 3;
		panel.add(go, c);
		frame.getRootPane().setDefaultButton(go);

		m.setLoginPageComponents(prompt, username, password);
		return panel;
	}

	public void doLogin(final Model m, final String username, final String password, final Runnable onComplete) {
		final String schemeAndAuthority = "https://" + Model.domain;
		final boolean secureCookie = true;

		Dispatchers.instance.getNetworkIoDispatcher().submit(new Runnable() {
			@Override
			public void run() {
				try {
					String sessionToken = login(username, password);
					if (sessionToken != null) {
						CookieHandler.getDefault().put(new URI(schemeAndAuthority), Collections.singletonMap("Set-Cookie", Arrays.asList(
							Model.makeCookie("user", sessionToken, "/", Model.domain, secureCookie)
						)));
						m.setUser(username);
					}
				} catch (Throwable e) {
					m.onError(e, "Login failed");
				}
				if (onComplete != null)
					SwingUtilities.invokeLater(onComplete);
			}
		});
	}

	public void doLogOff(final Window w, final Model m) {
		final String schemeAndAuthority = "https://" + Model.domain;
		final String token = m.getLoggedInSessionToken();

		Dispatchers.instance.getNetworkIoDispatcher().submit(new Runnable() {
			@Override
			public void run() {
				try {
					logoff(schemeAndAuthority, token);
					m.clearCookies();
				} catch (Throwable e) {
					m.onError(e, "Logoff failed");
				}
			}
		});
	}
}
