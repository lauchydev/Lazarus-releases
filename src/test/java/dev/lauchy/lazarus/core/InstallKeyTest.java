package dev.lauchy.lazarus.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstallKeyTest {
	@Test
	void aKeyIsMadeOnceAndKeptForTheNextStart(@TempDir Path dir) {
		Path file = dir.resolve("lazarus").resolve("install-key");
		KeyPair first = InstallKey.load(file);
		assertTrue(Files.exists(file));
		assertEquals(InstallKey.publicKey(first), InstallKey.publicKey(InstallKey.load(file)));
	}

	@Test
	void aSignatureChecksOutAgainstThePublicKeyTheServerIsSent(@TempDir Path dir) throws Exception {
		KeyPair pair = InstallKey.load(dir.resolve("install-key"));
		String challenge = "lazarus|/hello|1700000000000";
		PublicKey key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(InstallKey.publicKey(pair))));
		Signature check = Signature.getInstance("Ed25519");
		check.initVerify(key);
		check.update(challenge.getBytes(StandardCharsets.UTF_8));
		assertTrue(check.verify(Base64.getDecoder().decode(InstallKey.sign(pair, challenge))));
	}

	@Test
	void aDamagedFileIsReplacedWithANewKey(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("install-key");
		Files.writeString(file, "not a key");
		KeyPair made = InstallKey.load(file);
		assertNotEquals("not a key", Files.readString(file));
		assertEquals(InstallKey.publicKey(made), InstallKey.publicKey(InstallKey.load(file)));
	}
}
