package in.kevinj.lancamp.model;

import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;

public abstract class WebRequester {
	public interface HttpResponse {
		public void failed(Throwable error);
		public void success(String type, String content);
	}

	public static abstract class ProgressAdapter {
		public static final ProgressAdapter EMPTY_PROGRESS_ADAPTER = new ProgressAdapter() { };

		public void beginUpload(int total) {
			
		}

		public void uploaded(int added) {
			
		}

		public void beginDownload(int total) {
			
		}

		public void downloaded(int added) {
			
		}
	}

	public static class ThroughHttpURLConnection extends WebRequester {
		private static final int BUFFER_SIZE = 1024;

		public static final ThroughHttpURLConnection instance = new ThroughHttpURLConnection();

		private ThroughHttpURLConnection() {
			
		}

		@Override
		public void loadPage(final String url, final String method, final String body, final ProgressAdapter listener, final HttpResponse responseHandler) {
			Dispatchers.instance.getNetworkIoDispatcher().submit(new Runnable() {
				private int utf8Length(CharSequence sequence) {
					int count = 0;
					for (int i = 0, len = sequence.length(); i < len; i++) {
						char ch = sequence.charAt(i);
						if (ch <= 0x7F) {
							count++;
						} else if (ch <= 0x7FF) {
							count += 2;
						} else if (Character.isHighSurrogate(ch)) {
							count += 4;
							++i;
						} else {
							count += 3;
						}
					}
					return count;
				}

				@Override
				public void run() {
					try {
						HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
						conn.setRequestMethod(method);
						conn.setDoInput(true);
						if (body != null && body.length() != 0) {
							conn.setDoOutput(true);
							conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
							conn.setFixedLengthStreamingMode(utf8Length(body));
							listener.beginUpload(body.length());
							listener.uploaded(0);
							try (OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream())) {
								int fullBlocks = body.length() - BUFFER_SIZE;
								int writtenBytes;
								for (writtenBytes = 0; writtenBytes < fullBlocks; writtenBytes += BUFFER_SIZE) {
									writer.write(body, writtenBytes, BUFFER_SIZE);
									listener.uploaded(BUFFER_SIZE);
								}
								writer.write(body, writtenBytes, body.length() - writtenBytes);
								listener.uploaded(body.length() - writtenBytes);
							}
							listener.uploaded(-1);
						} else {
							listener.beginUpload(0);
							listener.uploaded(0);
							listener.uploaded(-1);
						}
						conn.connect();

						boolean isError = (conn.getResponseCode() / 100 != 2);
						int length = conn.getContentLength();
						boolean unknownLength = length == -1;
						StringBuilder sb = !unknownLength ? new StringBuilder(length) : new StringBuilder();
						if (length != 0) {
							Charset cs = Charset.defaultCharset();
							int charsetIndex = conn.getContentType().indexOf("; charset=");
							if (charsetIndex != -1) {
								try {
									cs = Charset.forName(conn.getContentType().substring(charsetIndex + "; charset=".length()));
								} catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
									responseHandler.failed(e);
									throw e;
								}
							}
							listener.beginDownload(length);
							listener.downloaded(0);
							try (InputStreamReader reader = new InputStreamReader(!isError ? conn.getInputStream() : conn.getErrorStream(), cs)) {
								char[] buf = new char[BUFFER_SIZE];
								int len;
								while ((len = reader.read(buf)) != -1) {
									listener.downloaded(len);
									sb.append(buf, 0, len);
								}
								listener.downloaded(-1);
							}
						} else {
							listener.beginDownload(0);
							listener.downloaded(0);
							listener.downloaded(-1);
						}
						if (!isError)
							responseHandler.success(conn.getContentType(), sb.toString());
						else
							responseHandler.failed(new RuntimeException(conn.getResponseCode() + " " + conn.getResponseMessage() + ": " + sb.toString()));
					} catch (Throwable e) {
						responseHandler.failed(e);
					}
				}
			});
		}
	}

	/*public static class ThroughJavaScript extends WebRequester {
		private final JApplet applet;

		public ThroughJavaScript(JApplet applet) {
			this.applet = applet;
		}

		@Override
		public void loadPage(final String url, final String method, final String body, final ProgressAdapter listener, final HttpResponse r) {
			Dispatchers.instance.getJsDispatcher().submit(new Runnable() {
				@Override
				public void run() {
					try {
						JSObject window = (JSObject) JSObject.getWindow(applet);
						window.call("loadPage", new Object[] { url, method, body, new ProgressAdapter() {
							@Override
							public void beginUpload(final int total) {
								AccessController.doPrivileged(new PrivilegedAction<Object>() {
									@Override
									public Object run() {
										try {
											listener.beginUpload(total);
										} catch (Throwable t) {
											r.failed(t);
										}
										return null;
									}
								});
							}

							public void uploaded(final int added) {
								AccessController.doPrivileged(new PrivilegedAction<Object>() {
									@Override
									public Object run() {
										try {
											listener.uploaded(added);
										} catch (Throwable t) {
											r.failed(t);
										}
										return null;
									}
								});
							}

							public void beginDownload(final int total) {
								AccessController.doPrivileged(new PrivilegedAction<Object>() {
									@Override
									public Object run() {
										try {
											listener.beginDownload(total);
										} catch (Throwable t) {
											r.failed(t);
										}
										return null;
									}
								});
							}

							@Override
							public void downloaded(final int added) {
								AccessController.doPrivileged(new PrivilegedAction<Object>() {
									@Override
									public Object run() {
										try {
											listener.downloaded(added);
										} catch (Throwable t) {
											r.failed(t);
										}
										return null;
									}
								});
							}
						}, new HttpResponse() {
							@Override
							public void failed(final Throwable error) {
								AccessController.doPrivileged(new PrivilegedAction<Object>() {
									@Override
									public Object run() {
										try {
											r.failed(error);
										} catch (Throwable t) {
											Model.onError(null, t, "Uncaught exception in HttpResponse.failed");
										}
										return null;
									}
								});
							}

							@Override
							public void success(final String type, final String content) {
								AccessController.doPrivileged(new PrivilegedAction<Object>() {
									@Override
									public Object run() {
										try {
											r.success(type, content);
										} catch (Throwable t) {
											r.failed(t);
										}
										return null;
									}
								});
							}
						} });
					} catch (Throwable e) {
						r.failed(e);
					}
				}
			});
		}
	}*/

	public abstract void loadPage(String url, String method, String body, ProgressAdapter listener, HttpResponse r);

	public void loadPage(String url, String method, String body, HttpResponse r) {
		loadPage(url, method, body, ProgressAdapter.EMPTY_PROGRESS_ADAPTER, r);
	}
}
