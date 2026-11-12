package dev.lauchy.lazarus.core;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The x-lazarus-auth header the worker checks (tools/lazarus-server/worker.js, signedIn), built as the mod sends it. */
class SignedRequestTest {
	private static final String UUID = "00000000-0000-4000-8000-000000000002";

	private static JsonObject auth(KeyPair pair, String path, String body, long now) throws Exception {
		return LazarusServer.auth(path, body, UUID, InstallKey.publicKey(pair), text -> InstallKey.sign(pair, text), now);
	}

	@Test
	void theChallengeNamesThePathTheTimeANonceAndTheBodysHash(@TempDir Path dir) throws Exception {
		KeyPair pair = InstallKey.load(dir.resolve("install-key"));
		String body = "{\"name\":\"Lauchy\",\"action\":\"status\"}";
		JsonObject auth = auth(pair, "/plus/admin", body, 1_700_000_000_000L);

		String[] parts = auth.get("challenge").getAsString().split("\\|");
		assertEquals(5, parts.length);
		assertEquals("lazarus", parts[0]);
		assertEquals("/plus/admin", parts[1]);
		assertEquals("1700000000000", parts[2]);
		assertTrue(parts[3].matches("[A-Za-z0-9_-]{8,64}"), "the nonce is base64url, as the worker expects: " + parts[3]);
		String hash = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
		assertEquals(hash, parts[4], "the body's SHA-256, base64");
		assertEquals(UUID, auth.get("uuid").getAsString());
		assertEquals(InstallKey.publicKey(pair), auth.get("key").getAsString());
	}

	@Test
	void theSignatureIsTheInstallKeysOverTheChallenge(@TempDir Path dir) throws Exception {
		KeyPair pair = InstallKey.load(dir.resolve("install-key"));
		JsonObject auth = auth(pair, "/hello", "{\"name\":\"Lauchy\"}", 1_700_000_000_000L);

		Signature check = Signature.getInstance("Ed25519");
		check.initVerify(KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(auth.get("key").getAsString()))));
		check.update(auth.get("challenge").getAsString().getBytes(StandardCharsets.UTF_8));
		assertTrue(check.verify(Base64.getDecoder().decode(auth.get("signature").getAsString())));
	}

	@Test
	void twoRequestsAlikeStillSignDifferently(@TempDir Path dir) throws Exception {
		KeyPair pair = InstallKey.load(dir.resolve("install-key"));
		JsonObject a = auth(pair, "/hello", "{}", 1_700_000_000_000L), b = auth(pair, "/hello", "{}", 1_700_000_000_000L);
		// The worker takes each signature once: the nonce keeps two honest requests in the same millisecond apart.
		assertNotEquals(a.get("signature").getAsString(), b.get("signature").getAsString());
	}
}
