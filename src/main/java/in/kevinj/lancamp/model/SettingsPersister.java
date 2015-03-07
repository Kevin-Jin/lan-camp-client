package in.kevinj.lancamp.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public abstract class SettingsPersister {
/*
	public static class CookiesPersister extends SettingsPersister {
		private static final String KEY_PREFIX = "LSEC_%s_config_";
		private static final String KEY_FORMAT = KEY_PREFIX + "%02d";
		private static final int MAX_COOKIE_LENGTH = 4093;
		private static final int MAX_COOKIE_COUNT = 3;
		private final JApplet applet;
		private final Lock lock;
		private volatile String currentStore;
		private volatile HttpCookie cookie;
		private volatile Map<String, Map<Object, String>> parsed;

		public CookiesPersister(JApplet applet) {
			this.applet = applet;
			this.currentStore = "";
			this.cookie = findCookies();
			this.parsed = IoAndEncodingUtil.decodeExtendedXWwwFormUrlencoded(cookie.getValue(), '&', ':', '[', ']', '#');
			this.lock = new ReentrantLock();
		}

		private HttpCookie findCookies() {
			SortedMap<String, HttpCookie> subCookies = new TreeMap<>();
			for (HttpCookie subCookie : HttpCookie.parse(Model.getCookieString(applet)))
				if (subCookie.getName().startsWith(String.format(KEY_PREFIX, currentStore)))
					subCookies.put(subCookie.getName(), subCookie);

			String concatedVal = "";
			//one month
			long greatestMaxAge = 60 * 60 * 24 * 30;
			for (HttpCookie subCookie : subCookies.values()) {
				if (subCookie.getMaxAge() > greatestMaxAge)
					greatestMaxAge = subCookie.getMaxAge();
				concatedVal += subCookie.getValue();
			}

			HttpCookie cookie = new HttpCookie(String.format(KEY_FORMAT, currentStore, 0), concatedVal);
			cookie.setMaxAge(greatestMaxAge);
			cookie.setDomain(applet.getDocumentBase().getHost());
			//if (currentStore != null) cookie.setPath('/' + currentStore + '/');

			return cookie;
		}

		private void commit() {
			//no equals signs in cookie values. also enable our custom groups extension to x-www-form-urlencoded
			String wholeValue = IoAndEncodingUtil.encodeExtendedXWwwFormUrlencoded(parsed, '&', ':', '[', ']', '#');

			cookie.setValue(wholeValue);
			int toSaveLen = wholeValue.length();
			int overhead = Model.cookieToString(cookie).length() - toSaveLen;
			int maxValLen = MAX_COOKIE_LENGTH - overhead;
			if (wholeValue.length() > MAX_COOKIE_COUNT * maxValLen) {
				Model.onError(null, new RuntimeException("Settings cookie too long"), "Failed to save updated setting");
				return;
			} else if (wholeValue.length() < maxValLen) {
				Model.setCookieString(applet, cookie);
				return;
			}

			long maxAge = cookie.getMaxAge();
			String domain = cookie.getDomain();
			//String path = cookie.getPath();
			for (int i = 0, j = 0; i < toSaveLen; i += maxValLen, j++) {
				String name = String.format(KEY_FORMAT, currentStore, j);
				String value = wholeValue.substring(i, Math.min(i + maxValLen, toSaveLen));
				HttpCookie subCookie = new HttpCookie(name, value);
				subCookie.setMaxAge(maxAge);
				subCookie.setDomain(domain);
				//subCookie.setPath(path);
				Model.setCookieString(applet, subCookie);
			}
		}

		@Override
		public void setStore(String store, Path workingFolder) {
			String nonNullStore = store == null ? "" : store;
			if (nonNullStore.equalsIgnoreCase(currentStore))
				return;
			super.setStore(store, workingFolder);

			lock.lock();
			try {
				currentStore = nonNullStore;
				cookie = findCookies();
				parsed = IoAndEncodingUtil.decodeExtendedXWwwFormUrlencoded(cookie.getValue(), '&', ':', '[', ']', '#');
			} finally {
				lock.unlock();
			}
		}

		@Override
		public String get(String key) {
			lock.lock();
			try {
				Map<Object, String> subMap = parsed.get(key);
				if (subMap == null)
					return null;

				return subMap.get(Integer.valueOf(0));
			} finally {
				lock.unlock();
			}
		}

		@Override
		protected String getSpecific(String key, String specific) {
			lock.lock();
			try {
				Map<Object, String> subMap = parsed.get(key);
				if (subMap == null)
					return null;

				return subMap.get(specific);
			} finally {
				lock.unlock();
			}
		}

		@Override
		public void put(String key, String value) {
			lock.lock();
			try {
				Map<Object, String> subMap = parsed.get(key);
				if (subMap == null) {
					subMap = new HashMap<>();
					parsed.put(key, subMap);
				}
				if (value != null)
					subMap.put(Integer.valueOf(0), value);
				else
					subMap.remove(Integer.valueOf(0));
				commit();
			} finally {
				lock.unlock();
			}
		}

		@Override
		protected void putSpecific(String key, String specific, String value) {
			lock.lock();
			try {
				Map<Object, String> subMap = parsed.get(key);
				if (subMap == null) {
					subMap = new HashMap<>();
					parsed.put(key, subMap);
				}
				if (value != null)
					subMap.put(specific, value);
				else
					subMap.remove(specific);
				commit();
			} finally {
				lock.unlock();
			}
		}
	}
*/
	public static class JsonFilePersister extends SettingsPersister {
		private final boolean prettyFile;
		private final JSONObject cache;
		private final Path p;
		private volatile String currentStore;

		private JsonFilePersister(JSONObject cache, Path p, boolean prettyFile) {
			this.prettyFile = prettyFile;
			this.cache = cache;
			this.p = p;
		}

		private void commit() {
			if (p == null)
				return;

			final String contents = prettyFile ? cache.toString(4) : cache.toString();
			Dispatchers.instance.getFileIoDispatcher().submit(new Runnable() {
				@Override
				public void run() {
					try {
						IoAndEncodingUtil.overwriteTextFile(p, contents);
					} catch (Throwable e) {
						Model.onError(null, e, "Failed to save updated setting");
					}
				}
			});
		}

		private JSONObject getContainer(String useStore) {
			if (useStore == null)
				return cache;

			JSONObject allStores = cache.optJSONObject("stores");
			if (allStores == null)
				return null;
			return allStores.optJSONObject(useStore);
		}

		private JSONObject makeContainer(String useStore) {
			assert useStore != null;
			JSONObject allStores = cache.optJSONObject("stores");
			if (allStores == null) {
				allStores = new JSONObject();
				cache.put("stores", allStores);
			}
			JSONObject container = new JSONObject();
			allStores.put(useStore, container);
			return container;
		}

		private void breakContainer(String useStore) {
			cache.remove(useStore);
		}

		@Override
		public void setStore(String store, Path workingFolder) {
			if ((store == null || store.equalsIgnoreCase(currentStore)) && (currentStore == null || currentStore.equalsIgnoreCase(store)))
				return;
			super.setStore(store, workingFolder);
			currentStore = store;
		}

		@Override
		public synchronized String get(String key) {
			String useStore = currentStore;
			JSONObject container = getContainer(useStore);
			if (container == null)
				return null;

			return container.optString(key, null);
		}

		@Override
		protected synchronized String getSpecific(String key, String specific) {
			String useStore = currentStore;
			JSONObject container = getContainer(useStore);
			if (container == null)
				return null;

			container = container.optJSONObject(key);
			if (container == null)
				return null;

			return container.optString(specific, null);
		}

		@Override
		public synchronized void put(String key, String value) {
			String useStore = currentStore;
			JSONObject container = getContainer(useStore);
			if (value != null) {
				if (container == null)
					container = makeContainer(useStore);
				container.put(key, value);
			} else if (container != null) {
				container.remove(key);
				if (useStore != null && container.length() == 0)
					breakContainer(useStore);
			}
			commit();
		}

		@Override
		protected synchronized void putSpecific(String key, String specific, String value) {
			String useStore = currentStore;
			JSONObject container = getContainer(useStore);
			if (value != null) {
				if (container == null)
					container = makeContainer(useStore);
				JSONObject values = container.optJSONObject(key);
				if (values == null) {
					values = new JSONObject();
					container.put(key, values);
				}
				values.put(specific, value);
			} else if (container != null) {
				JSONObject values = container.optJSONObject(key);
				if (values != null) {
					values.remove(specific);
					if (values.length() == 0)
						container.remove(key);
				}
				if (useStore != null && container.length() == 0)
					breakContainer(useStore);
			}
			commit();
		}

		public synchronized JSONArray getArray(String key, String specific) {
			String useStore = currentStore;
			JSONObject container = getContainer(useStore);
			if (container == null)
				return null;

			container = container.optJSONObject(key);
			if (container == null)
				return null;

			return container.optJSONArray(specific);
		}

		public synchronized void putArray(String key, String specific, JSONArray value) {
			String useStore = currentStore;
			JSONObject container = getContainer(useStore);
			if (value != null) {
				if (container == null)
					container = makeContainer(useStore);
				JSONObject values = container.optJSONObject(key);
				if (values == null) {
					values = new JSONObject();
					container.put(key, values);
				}
				values.put(specific, value);
			} else if (container != null) {
				JSONObject values = container.optJSONObject(key);
				if (values != null) {
					values.remove(specific);
					if (values.length() == 0)
						container.remove(key);
				}
				if (useStore != null && container.length() == 0)
					breakContainer(useStore);
			}
			commit();
		}

		private static JsonFilePersister create(String settingsFile, boolean prettyFile) {
			try {
				Path p = Paths.get(settingsFile).toAbsolutePath();
				Files.createDirectories(p.getParent());
				JSONObject json;
				if (Files.exists(p)) {
					try {
						json = new JSONObject(IoAndEncodingUtil.readTextFile(p));
					} catch (JSONException je) {
						json = new JSONObject();
					}
				} else {
					Files.createFile(p);
					json = new JSONObject();
				}
				if (!Files.isWritable(p)) {
					System.err.println("Non-writable settings file");
					return new JsonFilePersister(new JSONObject(), null, prettyFile);
				}
				return new JsonFilePersister(json, p, prettyFile);
			} catch (IOException e) {
				Model.onError(null, e, "Failed to load settings file");
				return new JsonFilePersister(new JSONObject(), null, prettyFile);
			}
		}

		public static JsonFilePersister create(String settingsFile) {
			return create(settingsFile, true);
		}
	}

	private JsonFilePersister metaDb;

	public void setStore(String store, Path workingFolder) {
		if (workingFolder == null)
			metaDb = null;
		else
			metaDb = JsonFilePersister.create(workingFolder.resolve("db.json").toString(), false);
	}

	public abstract String get(String key);

	protected abstract String getSpecific(String key, String specific);

	public abstract void put(String key, String value);

	protected abstract void putSpecific(String key, String specific, String value);

	public String getFromDb(String key, String specific) {
		return metaDb.getSpecific(key, specific);
	}

	public JSONArray getArrayFromDb(String key, String specific) {
		return metaDb.getArray(key, specific);
	}

	public void putInDb(String key, String specific, String value) {
		metaDb.putSpecific(key, specific, value);
	}

	public void putArrayInDb(String string, String typeName, JSONArray jsonArray) {
		metaDb.putArray(string, typeName, jsonArray);
	}
}
