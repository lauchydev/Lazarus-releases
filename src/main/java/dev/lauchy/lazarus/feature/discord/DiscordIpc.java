package dev.lauchy.lazarus.feature.discord;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A connection to the Discord app running on this computer, the way games set a Rich Presence:
 * a named pipe on Windows, a socket file on macOS and Linux. Only the status is ever sent; no
 * Discord account details are read and no token is needed. Each request waits for Discord's
 * answer before the next goes out, so one connection never reads and writes at once.
 */
final class DiscordIpc implements Closeable {
	/** Opens the connection with the application id. */
	static final int HANDSHAKE = 0;
	/** A command, such as setting the status, and Discord's answers to it. */
	static final int FRAME = 1;
	/** Discord closing the connection, with the reason. */
	static final int CLOSE = 2;
	/** The most Discord sends in one answer; anything bigger is a broken connection, not a status. */
	private static final int MAX_FRAME = 64 * 1024;

	private final Pipe pipe;

	private DiscordIpc(Pipe pipe) {
		this.pipe = pipe;
	}

	/** Finds the running Discord app and says hello as this application, or fails when Discord isn't open. */
	static DiscordIpc connect(String appId) throws IOException {
		Pipe pipe = open();
		DiscordIpc ipc = new DiscordIpc(pipe);
		try {
			JsonObject hello = new JsonObject();
			hello.addProperty("v", 1);
			hello.addProperty("client_id", appId);
			JsonObject ready = ipc.send(HANDSHAKE, hello);
			if (!"READY".equals(string(ready, "evt"))) throw new IOException("Discord said " + ready);
			return ipc;
		} catch (IOException | RuntimeException e) {
			ipc.close();
			throw e;
		}
	}

	/** Shows this activity on the player's Discord profile, or clears it when {@code activity} is null. */
	void activity(JsonObject activity) throws IOException {
		JsonObject args = new JsonObject();
		args.addProperty("pid", ProcessHandle.current().pid());
		if (activity != null) args.add("activity", activity);
		JsonObject cmd = new JsonObject();
		cmd.addProperty("cmd", "SET_ACTIVITY");
		cmd.add("args", args);
		cmd.addProperty("nonce", UUID.randomUUID().toString());
		JsonObject answer = send(FRAME, cmd);
		if ("ERROR".equals(string(answer, "evt"))) throw new IOException("Discord turned down the status: " + answer.get("data"));
	}

	/** Sends one message and reads Discord's answer to it. */
	private JsonObject send(int op, JsonObject body) throws IOException {
		pipe.write(encode(op, body.toString()));
		byte[] header = new byte[8];
		pipe.readFully(header);
		ByteBuffer h = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
		int answerOp = h.getInt();
		int length = h.getInt();
		if (length < 0 || length > MAX_FRAME) throw new IOException("Discord sent a frame of " + length + " bytes");
		byte[] payload = new byte[length];
		pipe.readFully(payload);
		JsonObject answer = JsonParser.parseString(new String(payload, StandardCharsets.UTF_8)).getAsJsonObject();
		if (answerOp == CLOSE) throw new IOException("Discord closed the connection: " + answer);
		return answer;
	}

	/** One message as Discord reads it: its kind and length as little-endian ints, then the JSON. */
	static byte[] encode(int op, String json) {
		byte[] body = json.getBytes(StandardCharsets.UTF_8);
		return ByteBuffer.allocate(8 + body.length).order(ByteOrder.LITTLE_ENDIAN).putInt(op).putInt(body.length).put(body).array();
	}

	private static String string(JsonObject o, String key) {
		return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
	}

	@Override
	public void close() {
		try {
			pipe.close();
		} catch (IOException ignored) {
			// Already gone; Discord clears the status when the connection drops.
		}
	}

	/** Tries each of the ten places Discord listens, first one that answers wins. */
	private static Pipe open() throws IOException {
		boolean windows = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
		for (int i = 0; i < 10; i++) {
			if (windows) {
				try {
					return new WindowsPipe(new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + i, "rw"));
				} catch (IOException ignored) {
					// Not this one; Discord takes the first free number.
				}
				continue;
			}
			for (Path dir : socketDirs()) {
				Path socket = dir.resolve("discord-ipc-" + i);
				if (!Files.exists(socket)) continue;
				try {
					SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX);
					try {
						channel.connect(UnixDomainSocketAddress.of(socket));
						return new SocketPipe(channel);
					} catch (IOException e) {
						channel.close();
					}
				} catch (IOException ignored) {
					// A stale socket file left by a Discord that has quit.
				}
			}
		}
		throw new IOException("Discord isn't running");
	}

	/** Where Discord puts its socket on macOS and Linux, including the Snap and Flatpak builds. */
	private static List<Path> socketDirs() {
		List<Path> roots = new ArrayList<>();
		for (String var : new String[]{"XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP"}) {
			String v = System.getenv(var);
			if (v != null && !v.isBlank()) roots.add(Path.of(v));
		}
		roots.add(Path.of("/tmp"));
		List<Path> dirs = new ArrayList<>();
		for (Path root : roots) {
			dirs.add(root);
			dirs.add(root.resolve("app/com.discordapp.Discord"));
			dirs.add(root.resolve("snap.discord"));
		}
		return dirs;
	}

	/** The byte stream to Discord, whichever way this computer reaches it. */
	private interface Pipe extends Closeable {
		void write(byte[] bytes) throws IOException;

		void readFully(byte[] into) throws IOException;
	}

	/** Windows' named pipe, opened like a file. */
	private record WindowsPipe(RandomAccessFile file) implements Pipe {
		public void write(byte[] bytes) throws IOException {
			file.write(bytes);
		}

		public void readFully(byte[] into) throws IOException {
			file.readFully(into);
		}

		public void close() throws IOException {
			file.close();
		}
	}

	/** A Unix socket file, on macOS and Linux. */
	private record SocketPipe(SocketChannel channel) implements Pipe {
		public void write(byte[] bytes) throws IOException {
			ByteBuffer buf = ByteBuffer.wrap(bytes);
			while (buf.hasRemaining()) channel.write(buf);
		}

		public void readFully(byte[] into) throws IOException {
			ByteBuffer buf = ByteBuffer.wrap(into);
			while (buf.hasRemaining()) {
				if (channel.read(buf) < 0) throw new IOException("Discord closed the connection");
			}
		}

		public void close() throws IOException {
			channel.close();
		}
	}
}
