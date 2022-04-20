package nl.andrewl.distribugit.cli;

import nl.andrewl.distribugit.RepositorySelector;
import nl.andrewl.distribugit.selectors.GitHubSelectorBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SelectorExpressionParser {
	private static final Pattern SELECTOR_EXPRESSION_PATTERN = Pattern.compile("([\\w-]+):(.+)");
	private static final Pattern ORG_REPO_PREFIX_PATTERN = Pattern.compile("(.+)/(.+)");

	public static RepositorySelector parse(String expr, String accessToken) throws IOException {
		Matcher m = SELECTOR_EXPRESSION_PATTERN.matcher(expr);
		if (!m.find()) throw new IllegalArgumentException("Invalid selector expression. Should be \"selector-type[:expression]\".");
		String slug = m.group(1);
		String content = m.groupCount() > 1 ? m.group(2) : null;
		return switch (slug) {
			case "org-repo-prefix" -> parseOrgRepoPrefix(content, accessToken);
			case "stdin" -> stdinSelector();
			case "file" -> fileSelector(content);
			default -> throw new IllegalArgumentException("Unsupported selector type: " + slug);
		};
	}

	private static RepositorySelector parseOrgRepoPrefix(String content, String accessToken) throws IOException {
		if (content == null) throw new IllegalArgumentException("Missing required selector expression.");
		if (accessToken == null) throw new IllegalArgumentException("Missing required access-token for GitHub org-repo-prefix selector.");
		Matcher m = ORG_REPO_PREFIX_PATTERN.matcher(content);
		if (!m.find()) throw new IllegalArgumentException("Invalid content for org-repo-prefix select. Should be \"orgName/prefix\"");
		return GitHubSelectorBuilder.fromPersonalAccessToken(accessToken).orgAndPrefix(m.group(1), m.group(2));
	}

	private static RepositorySelector stdinSelector() {
		return () -> {
			List<String> uris = new ArrayList<>();
			String line;
			try (var reader = new BufferedReader(new InputStreamReader(System.in))) {
				while ((line = reader.readLine()) != null) {
					if (!line.isBlank()) {
						uris.add(line.trim());
					}
				}
			}
			return uris;
		};
	}

	private static RepositorySelector fileSelector(String content) {
		if (content == null) throw new IllegalArgumentException("No file paths were given.");
		String[] filePaths = content.split(";");
		if (filePaths.length < 1) throw new IllegalArgumentException("No file paths were given.");
		List<Path> paths = Arrays.stream(filePaths).map(Path::of).toList();
		for (var path : paths) {
			if (Files.notExists(path)) throw new IllegalArgumentException("File " + path + " does not exist.");
			if (!Files.isRegularFile(path)) throw new IllegalArgumentException("File " + path + " is not a regular file.");
			if (!Files.isReadable(path)) throw new IllegalArgumentException("File " + path + " is not readable.");
		}
		return () -> {
			List<String> uris = new ArrayList<>();
			for (var path : paths) {
				try (var s = Files.lines(path)) {
					uris.addAll(s.filter(str -> !str.isBlank()).toList());
				}
			}
			return uris;
		};
	}
}
