package nl.andrewl.distribugit.cli;

import nl.andrewl.distribugit.DistribuGit;
import nl.andrewl.distribugit.GitCredentials;
import nl.andrewl.distribugit.RepositoryAction;
import nl.andrewl.distribugit.StatusListener;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "distribugit", description = "DistribuGit command-line tool for performing distributed git operations.", mixinStandardHelpOptions = true)
public class DistribuGitCommand implements Callable<Integer> {
	@CommandLine.Option(names = {"-d", "--dir"}, description = "The working directory for DistribuGit", defaultValue = "./.distribugit_tmp")
	public Path workingDir;

	@CommandLine.Option(
			names = {"-s", "--selector"},
			description = """
					The repository selector to use.
					The following selector types are permitted:
					 - "org-repo-prefix:{orgName}/{repoPrefix}": Selects repositories from a GitHub organization whose name begins with the given prefix.
					 - "stdin": Selects repository URIs that have been provided to the program via stdin, with one URI per line.
					 - "file:{filePath}": Selects repository URIs written in a file, with one URI per line.
					""",
			required = true
	)
	public String selectorExpression;

	@CommandLine.Option(names = {"-a", "--action"}, description = "The command to run on each repository.", required = true)
	public String actionCommand;

	@CommandLine.Option(names = {"-fa", "--finalization-action"}, description = "A command to run on each repository after all normal actions.")
	public String finalizationActionCommand;

	@CommandLine.Option(names = {"-t", "--access-token"}, description = "The access token to use to perform operations.")
	public String accessToken;

	@CommandLine.Option(names = {"-sf", "--strict-fail"}, description = "Whether to preemptively fail if any error occurs.", defaultValue = "true")
	public boolean strictFail;

	@CommandLine.Option(names = {"-cl", "--cleanup"}, description = "Whether to remove all repository files when done.", defaultValue = "false")
	public boolean cleanup;

	@Override
	public Integer call() throws Exception {
		var builder = new DistribuGit.Builder()
				.workingDir(workingDir)
				.strictFail(strictFail)
				.cleanup(cleanup)
				.selector(SelectorExpressionParser.parse(selectorExpression, accessToken))
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

	public static void main(String[] args) {
		new CommandLine(new DistribuGitCommand()).execute(args);
	}
}
