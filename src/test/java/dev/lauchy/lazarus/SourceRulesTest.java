package dev.lauchy.lazarus;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Rules about the mod's own source that a reviewer shouldn't have to spot by eye (CODING_STANDARDS.md lists them). */
class SourceRulesTest {
	/** Writing to the player's chat, or making something in it clickable or hoverable. */
	private static final Pattern CHAT = Pattern.compile("sendSystemMessage\\(|displayClientMessage\\(|getChat\\(\\)\\.addMessage\\(|\\b(ClickEvent|HoverEvent)\\b");
	/** The one file allowed to: every message goes through it, so prefixes and colours stay the same everywhere. */
	private static final Path MESSAGE = Path.of("src/main/java/dev/lauchy/lazarus/core/Message.java");
	/** Unicode's private-use area, where the badge font's glyphs live: invisible in an editor, so source writes them as \\uE000. */
	private static final Pattern PRIVATE_USE = Pattern.compile("[\\uE000-\\uF8FF]");

	@Test
	void chatGoesThroughMessage() throws IOException {
		assertEquals(List.of(), matches(Path.of("src/main/java"), CHAT, MESSAGE),
				"Chat is written only through core/Message (Message.lazarus()/plus() ... .send())");
	}

	@Test
	void privateUseCharactersAreEscaped() throws IOException {
		assertEquals(List.of(), matches(Path.of("src"), PRIVATE_USE, null),
				"Write private-use characters as unicode escapes (\\uE000), which an editor shows");
	}

	/** Every "file:line" under {@code root}'s Java files, except {@code skip}, where {@code pattern} turns up. */
	private static List<String> matches(Path root, Pattern pattern, Path skip) throws IOException {
		List<String> found = new ArrayList<>();
		try (Stream<Path> files = Files.walk(root)) {
			for (Path file : files.filter(f -> f.toString().endsWith(".java") && !f.equals(skip)).sorted().toList()) {
				List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
				for (int i = 0; i < lines.size(); i++) {
					if (pattern.matcher(lines.get(i)).find()) found.add(file.toString().replace('\\', '/') + ":" + (i + 1));
				}
			}
		}
		return found;
	}
}
