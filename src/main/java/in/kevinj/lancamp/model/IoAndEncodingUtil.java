package in.kevinj.lancamp.model;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.DatatypeConverter;

public class IoAndEncodingUtil {
	public static final Charset utf8 = Charset.forName("utf-8");

	public static String encodeXWwwFormUrlencoded(String... keyAndValues) {
		StringBuilder sb = new StringBuilder();
		if (keyAndValues.length % 2 != 0)
			throw new IllegalArgumentException("Keys without values");

		try {
			for (int i = 0; i < keyAndValues.length; i += 2) {
				if (keyAndValues[i] == null || keyAndValues[i].isEmpty())
					continue;

				sb.append('&').append(URLEncoder.encode(keyAndValues[i], utf8.name()));
				if (keyAndValues[i + 1] != null)
					sb.append('=').append(URLEncoder.encode(keyAndValues[i + 1], utf8.name()));
			}
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}

		return sb.length() > 0 ? sb.substring(1) : "";
	}

	private static String encodePercentEncodedOrBase64(String str, char base64Chr) throws UnsupportedEncodingException {
		if (str.length() == 0)
			return "";

		String pe = URLEncoder.encode(str, utf8.name());
		if (base64Chr == '\0')
			return pe;

		String b64 = base64Chr + toBase64Url(str);
		return b64.length() < pe.length() ? b64 : pe;
	}

	public static String encodeExtendedXWwwFormUrlencoded(Map<String, Map<Object, String>> keyAndValues, char pairDelim, char valDelim, char grpStartChr, char grpEndChr, char base64StartChr) {
		StringBuilder sb = new StringBuilder();

		try {
			for (Map.Entry<String, Map<Object, String>> entry : keyAndValues.entrySet()) {
				if (entry.getKey() == null || entry.getKey().isEmpty())
					continue;

				String key = encodePercentEncodedOrBase64(entry.getKey(), base64StartChr);
				if (entry.getValue() != null) {
					StringBuilder group = new StringBuilder();
					for (Map.Entry<Object, String> valueEntry : entry.getValue().entrySet()) {
						if (valueEntry.getKey() instanceof Integer || grpStartChr == '\0' || grpEndChr == '\0') {
							sb.append(pairDelim).append(key);
							if (valueEntry.getValue() != null)
								sb.append(valDelim).append(encodePercentEncodedOrBase64(valueEntry.getValue(), base64StartChr));
						} else if (valueEntry.getKey() instanceof String) {
							group.append(pairDelim).append(encodePercentEncodedOrBase64((String) valueEntry.getKey(), base64StartChr));
							if (valueEntry.getValue() != null)
								group.append(valDelim).append(encodePercentEncodedOrBase64(valueEntry.getValue(), base64StartChr));
						}
					}
					if (group.length() != 0)
						sb.append(pairDelim).append(key).append(valDelim).append(grpStartChr).append(group.substring(1)).append(grpEndChr);
				} else {
					sb.append(pairDelim).append(key);
				}
			}
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
		return sb.length() > 0 ? sb.substring(1) : "";
	}

	public static String encodeXWwwFormUrlencoded(Map<String, Map<Object, String>> keyAndValues) {
		return encodeExtendedXWwwFormUrlencoded(keyAndValues, '&', '=', '\0', '\0', '\0');
	}

	private static Map<Object, String> flatten(Map<String, Map<Object, String>> map) {
		Map<Object, String> flattened = new HashMap<>();
		for (Map.Entry<String, Map<Object, String>> entry : map.entrySet())
			for (String value : entry.getValue().values())
				flattened.put(entry.getKey(), value);
		return flattened;
	}

	private static String decodePercentEncodedOrBase64(String str, char base64StartChr) throws UnsupportedEncodingException {
		if (str.length() == 0)
			return "";
		else if (base64StartChr != '\0' && str.charAt(0) == base64StartChr)
			return fromBase64Url(str.substring(1));
		else
			return URLDecoder.decode(str, utf8.name());
	}

	public static Map<String, Map<Object, String>> decodeExtendedXWwwFormUrlencoded(String str, char pairDelim, char valDelim, char grpStartChr, char grpEndChr, char base64StartChr) {
		Map<String, Map<Object, String>> map = new HashMap<>();
		try {
			int i = 0, next;
			do {
				next = str.indexOf(pairDelim, i + 1);
				if (next == -1)
					next = str.length();

				int valSep = str.indexOf(valDelim, i + 1);
				String key;
				Map<Object, String> val;
				Map<Object, String> sameKey;
				if (valSep != -1 && valSep < next) {
					key = decodePercentEncodedOrBase64(str.substring(i, valSep), base64StartChr);
					sameKey = map.get(key);
					int grpStart, grpEnd;
					if (grpStartChr != '\0' && grpEndChr != '\0' && str.charAt(grpStart = valSep + 1) == grpStartChr && (grpEnd = str.indexOf(grpEndChr, valSep + 2)) != -1 && (grpEnd == str.length() - 1 || str.charAt(grpEnd + 1) == pairDelim)) {
						next = grpEnd + 1;
						val = flatten(decodeExtendedXWwwFormUrlencoded(str.substring(grpStart + 1, grpEnd), pairDelim, valDelim, '\0', '\0', base64StartChr));
					} else {
						val = Collections.singletonMap((Object) Integer.valueOf(sameKey == null ? 0 : sameKey.size()), decodePercentEncodedOrBase64(str.substring(valSep + 1, next), base64StartChr));
					}
				} else {
					key = decodePercentEncodedOrBase64(str.substring(i, next), base64StartChr);
					sameKey = map.get(key);
					val = null;
				}
				if (!key.isEmpty()) {
					if (sameKey == null) {
						sameKey = new HashMap<>();
						map.put(key, sameKey);
					}
					sameKey.putAll(val);
				}
			} while ((i = next + 1) < str.length());
			return map;
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}

	public static Map<String, Map<Object, String>> decodeXWwwFormUrlencoded(String str) {
		return decodeExtendedXWwwFormUrlencoded(str, '&', '=', '\0', '\0', '\0');
	}

	public static String readTextFile(Path file) throws IOException {
		StringBuilder sb = new StringBuilder();
		try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(file), utf8)) {
			char[] buf = new char[1024];
			int len;
			while ((len = reader.read(buf)) != -1)
				sb.append(buf, 0, len);
		}
		return sb.toString();
	}

	public static void overwriteTextFile(Path file, String contents) throws IOException {
		try (OutputStreamWriter writer = new OutputStreamWriter(Files.newOutputStream(file, StandardOpenOption.CREATE), utf8)) {
			writer.write(contents);
		}
	}

	public static String toBase64Url(byte[] bytes) {
		return DatatypeConverter.printBase64Binary(bytes).replaceAll("\\+", "-").replaceAll("\\/", "_").replaceAll("=", "");
	}

	public static String toBase64Url(String contents) {
		return toBase64Url(contents.getBytes(utf8));
	}

	private static String fill(char ch, int len) {
		char[] chars = new char[len];
		Arrays.fill(chars, ch);
		return String.valueOf(chars);
	}

	public static String fromBase64Url(String base64) {
		final int ALIGN = 4;
		int length = base64.length();
		int padding = (ALIGN - 1 - (length - 1) % ALIGN);
		if (padding != 0)
			base64 += fill('=', padding);
		return new String(DatatypeConverter.parseBase64Binary(base64.replaceAll("-", "+").replaceAll("_", "/")), utf8);
	}
}