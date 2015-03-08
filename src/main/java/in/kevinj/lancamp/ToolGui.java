package in.kevinj.lancamp;

import in.kevinj.lancamp.model.Dispatchers;
import in.kevinj.lancamp.model.Model;

import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.swing.JApplet;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.UnsupportedLookAndFeelException;

@SuppressWarnings("serial")
public class ToolGui extends JApplet {
	private static JPanel constructMainPage(final Component frame, final Window w, final Model m) {
		return new JPanel() {
			{
				this.setLayout(new BorderLayout());
				this.add(constructTopPanel(), BorderLayout.PAGE_START);
				this.add(constructMiddlePanel(), BorderLayout.CENTER);
				this.add(constructBottomPanel(), BorderLayout.PAGE_END);
			}

			private JPanel constructTopPanel() {
				JPanel top = new JPanel(new GridBagLayout());
				return top;
			}

			private JPanel constructMiddlePanel() {
				JPanel middle = new JPanel();
				middle.setBackground(new Color(0xff, 0xbb, 0x00));
				JLabel keyCounter = new JLabel("<html>Keystrokes<br></html>");
				JLabel clickCounter = new JLabel("<html>Clicks<br></html>");

				middle.add(keyCounter);
				middle.add(clickCounter);

				m.setMiddlePanelComponents(keyCounter, clickCounter);
				return middle;
			}

			private JPanel constructBottomPanel() {
				JPanel bottom = new JPanel(new GridBagLayout());
				return bottom;
			}
		};
	}

	private static Component constructLoginPage(RootPaneContainer frame, final Window w, final Model m) {
		return LoginPage.instance.constructLoginPage(frame, w, m, new Runnable() {
			@Override
			public void run() {
				m.retrieveExistingData();
				displayMainPage(w, m);
			}
		});
	}

	private static void setupInterface(Model m, Component frame, Window w, boolean enableLoginPage) {
		LookAndFeelInfo preferredLaf = null;
		for (LookAndFeelInfo lafInfo : UIManager.getInstalledLookAndFeels()) {
			if (lafInfo.getName().equals("Nimbus")) {
				if (preferredLaf == null || preferredLaf.getClassName().equals(UIManager.getSystemLookAndFeelClassName()))
					preferredLaf = lafInfo;
			} else if (lafInfo.getClassName().equals(UIManager.getSystemLookAndFeelClassName())) {
				if (preferredLaf == null)
					preferredLaf = lafInfo;
			}
		}
		if (preferredLaf != null) {
			try {
				UIManager.setLookAndFeel(preferredLaf.getClassName());
				SwingUtilities.updateComponentTreeUI(frame);
			} catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
				m.onError(e, "Failed to set preferred look and feel");
			}
		}
		frame.setMinimumSize(new Dimension(640, 320));
		frame.setPreferredSize(frame.getMinimumSize());
		JPanel root = new JPanel(new CardLayout());
		root.add(constructMainPage(frame, w, m), "main");
		if (enableLoginPage)
			root.add(constructLoginPage((RootPaneContainer) frame, w, m), "login");
		((RootPaneContainer) frame).getContentPane().add(root);
		m.setRootComponent(root);
	}

	private static void displayMainPage(Window w, Model m) {
		m.flipToMainPage();
		//if (w instanceof JFrame)
			//((JFrame) w).setResizable(true);
	}

	private static void displayLoginPage(Window w, final Model m) {
		if (m.getLoggedInSessionToken() != null)
			LoginPage.instance.doLogOff(w, m);

		m.flipToLoginPage();
		if (m.isLoginUsernameFieldFilled())
			m.focusOnPasswordField();
		else
			m.focusOnLoginField();
		//if (w instanceof JFrame)
			//((JFrame) w).setResizable(false);
	}

	private static boolean validPath(String s) {
		Path file = Paths.get(s);
		if (Files.isDirectory(file))
			return false;
		if (Files.exists(file) && (!Files.isWritable(file) || !Files.isReadable(file)))
			return false;
		return true;
	}

	private static void setTrayAndTitleBarIcon(Model m, final JFrame frame) {
		Image favicon = Toolkit.getDefaultToolkit().getImage(Thread.currentThread().getContextClassLoader().getResource("in/kevinj/lancamp/resources/favicon.png"));

		if (SystemTray.isSupported()) {
			SystemTray tray = SystemTray.getSystemTray();
			TrayIcon trayIcon = new TrayIcon(favicon.getScaledInstance(tray.getTrayIconSize().width, tray.getTrayIconSize().height, Image.SCALE_SMOOTH));
			//trayIcon.setImageAutoSize(true);
			PopupMenu popup = new PopupMenu();
			trayIcon.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					if (frame.isVisible()) {
						frame.setVisible(false);
					} else {
						frame.setVisible(true);
						frame.toFront();
						frame.repaint();
					}
				}
			});

			MenuItem exitLink = new MenuItem("Exit");
			exitLink.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
				}
			});
			MenuItem openLink = new MenuItem("Open");
			openLink.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					frame.setVisible(true);
					frame.toFront();
					frame.repaint();
				}
			});
			popup.add(openLink);
			popup.add(exitLink);

			trayIcon.setPopupMenu(popup);
			try {
				tray.add(trayIcon);

				frame.addWindowListener(new WindowAdapter() {
					@Override
					public void windowIconified(WindowEvent event) {
						frame.setVisible(false);
						frame.setState(JFrame.NORMAL);
					}
				});

				m.setTrayIcon(trayIcon);
			} catch (AWTException e) {
				m.onError(e, "Failed to set tray icon");
			}
		} else {
			System.err.println("Could not set tray icon: not supported");
		}

		frame.setIconImage(favicon);
	}

	public static void main(String[] args) {
		final Model m = new Model(args.length > 0 && validPath(args[0]) ? args[1] : "10kLife.json");
/*try {
	Pcap.main(args);
} catch (PcapNativeException | NotOpenException e) {
	// TODO Auto-generated catch block
	e.printStackTrace();
}*/
//ActiveWindowInfo.INSTANCE.startListening(null);
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				JFrame frame = new JFrame("10kLife");
				frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
				setupInterface(m, frame, frame, true);
				frame.pack();
				setTrayAndTitleBarIcon(m, frame);
				frame.setVisible(true);

				displayLoginPage(frame, m);
			}
		});
	}

	public RuntimeException makeRtException(String message) {
		return new RuntimeException(message);
	}

	@Override
	public void stop() {
		Model.popupsDisabled = true;
		//m.endWatching(false);
	}

	@Override
	public void destroy() {
		Dispatchers.instance.shutdownNow();
		//start() doesn't seem to be called when page is restored after call to stop() and destroy()
		//so just restart the thread pools now
	}
}
