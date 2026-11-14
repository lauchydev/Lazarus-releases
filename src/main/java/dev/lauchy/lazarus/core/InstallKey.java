package dev.lauchy.lazarus.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * This install's own key: an Ed25519 pair the mod makes on first launch and keeps in {@code config/lazarus/install-key}.
 * Every call to the Lazarus server is signed with it, and the server ties the account to the first key it hears from,
 * so nobody else's install can speak for this account. It has nothing to do with Mojang: nothing from the Minecraft
 * session is read or sent. A new computer or a lost file needs an admin to reset the account's key.
 */
public final class InstallKey {
	/** The mod's log, by name: Lazarus.LOG would start the whole mod, which a unit test can't. */
	private static final Logger LOG = LoggerFactory.getLogger("Lazarus");
	private static KeyPair pair;

	private InstallKey() {
	}

	/** The public half as the server stores it: X.509 (SPKI), base64. */
	public static synchronized String publicKey() {
		return publicKey(pair());
	}

	/** {@code text} signed with this install's key, base64. */
	public static synchronized String sign(String text) throws Exception {
		return sign(pair(), text);
	}

	private static KeyPair pair() {
		if (pair == null) pair = load(Config.dir().resolve("install-key"));
		return pair;
	}

	static String publicKey(KeyPair pair) {
		return Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
	}

	static String sign(KeyPair pair, String text) throws Exception {
		Signature s = Signature.getInstance("Ed25519");
		s.initSign(pair.getPrivate());
		s.update(text.getBytes(StandardCharsets.UTF_8));
		return Base64.getEncoder().encodeToString(s.sign());
	}

	/**
	 * The key saved in {@code file}, or a new one saved to it. One that can't be saved still works for this game start;
	 * a damaged file is replaced, and the server then refuses the new key for an account it knows until an admin resets it.
	 */
	static KeyPair load(Path file) {
		KeyPair saved = read(file);
		if (saved != null) return saved;
		KeyPair made;
		try {
			made = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
		} catch (Exception e) {
			throw new IllegalStateException("this Java can't make an Ed25519 key", e);
		}
		Base64.Encoder b64 = Base64.getEncoder();
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, b64.encodeToString(made.getPrivate().getEncoded()) + "\n" + b64.encodeToString(made.getPublic().getEncoded()) + "\n");
		} catch (Exception e) {
			LOG.warn("Could not save this install's Lazarus key: {}", e.toString());
		}
		return made;
	}

	/** The saved pair: the private half (PKCS#8) on the first line, the public half (X.509) on the second; null when there's none. */
	private static KeyPair read(Path file) {
		try {
			if (!Files.exists(file)) return null;
			String[] lines = Files.readString(file).trim().split("\\s+");
			if (lines.length < 2) return null;
			Base64.Decoder b64 = Base64.getDecoder();
			KeyFactory f = KeyFactory.getInstance("Ed25519");
			PrivateKey priv = f.generatePrivate(new PKCS8EncodedKeySpec(b64.decode(lines[0])));
			PublicKey pub = f.generatePublic(new X509EncodedKeySpec(b64.decode(lines[1])));
			return new KeyPair(pub, priv);
		} catch (Exception e) {
			LOG.warn("This install's Lazarus key couldn't be read: {}", e.toString());
			return null;
		}
	}
}
