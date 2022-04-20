package nl.andrewl.distribugit.cli;

import nl.andrewl.distribugit.*;
import nl.andrewl.distribugit.selectors.GitHubSelectorBuilder;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@CommandLine.Command(name = "distribugit")
public class DistribuGitCommand implements Callable<Integer> {
	private static final Pattern SELECTOR_EXPRESSION_PATTERN = Pattern.compile("([\\w-]+):(.+)");
	private static final Pattern ORG_REPO_PREFIX_PATTERN = Pattern.compile("(.+)/(.+)");

	@CommandLine.Option(names = {"-d", "--dir"}, description = "The working directory for DistribuGit", defaultValue = "./.distribugit_tmp")
	private Path workingDir;

	@CommandLine.Option(names = {"-s", "--selector"}, description = "The repository selector to use. Format: \"slug:content\"", required = true)
	private String selectorExpression;

	@CommandLine.Option(names = {"-a", "--action"}, description = "The command to run on each repository.", required = true)
	private String actionCommand;

	@CommandLine.Option(names = {"-fa", "--finalization-action"}, description = "A command to run on each repository after all normal actions.")
	private String finalizationActionCommand;

	@CommandLine.Option(names = {"-t", "--access-token"}, description = "The access token to use to perform operations.")
	private String accessToken;

	@CommandLine.Option(names = {"-sf", "--strict-fail"}, description = "Whether to preemptively fail if any error occurs.", defaultValue = "true")
	private boolean strictFail;

	@CommandLine.Option(names = {"-cl", "--cleanup"}, description = "Whether to remove all repository files when done.", defaultValue = "false")
	private boolean cleanup;

	@Override
	public Integer call() throws Exception {
		var builder = new DistribuGit.Builder()
				.workingDir(workingDir)
				.strictFail(strictFail)
				.cleanup(cleanup)
				.selector(parseSelectorExpression(selectorExpression))
				.action(RepositoryAction.ofCommand(actionCommand.split("\\s+")));
		if (finalizationActionCommand != null) {
			builder.finalizationAction(RepositoryAction.ofCommand(finalizationActionCommand.split("\\s+")));
		}
		if (accessToken != null) {
			builder.credentials(GitCredentials.ofUsernamePassword(accessToken, ""));
		}
		builder.statusListener(new StatusListener() {
			@Override
			public void progressUpdated(float percentage) {
				System.out.printf("Progress: %.1f%%%n", percentage);
			}

			@Override
			public void messageReceived(String message) {
				System.out.println(message);
			}
		});
		builder.build().doActions();
		return 0;
	}

	private RepositorySelector parseSelectorExpression(String expr) throws IOException {
		Matcher m = SELECTOR_EXPRESSION_PATTERN.matcher(expr);
		if (!m.find()) throw new IllegalArgumentException("Invalid selector expression. Should be \"selector-type:expression\".");
		String slug = m.group(1);
		String content = m.group(2);
		if (slug.equalsIgnoreCase("org-repo-prefix")) {
			Matcher m1 = ORG_REPO_PREFIX_PATTERN.matcher(content);
			if (!m1.find()) throw new IllegalArgumentException("Invalid content for org-repo-prefix select. Should be \"orgName/prefix\"");
			return GitHubSelectorBuilder.fromPersonalAccessToken(accessToken).orgAndPrefix(m1.group(1), m1.group(2));
		} else {
			throw new IllegalArgumentException("Unsupported selector type: \"" + slug + "\".");
		}
	}

	public static void main(String[] args) {
		new CommandLine(new DistribuGitCommand()).execute(args);
	}
}
