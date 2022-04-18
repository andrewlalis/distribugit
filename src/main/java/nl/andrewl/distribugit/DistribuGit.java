package nl.andrewl.distribugit;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DistribuGit {
	private final RepositorySelector selector;
	private final RepositoryAction action;
	private final GitCredentials credentials;
	private final StatusListener statusListener;
	private final Path workingDir;
	private final boolean strictFail;
	private final boolean cleanup;

	private int stepsComplete;
	private int stepsTotal;

	public DistribuGit(
		RepositorySelector selector,
		RepositoryAction action,
		GitCredentials credentials,
		StatusListener statusListener,
		Path workingDir,
		boolean strictFail,
		boolean cleanup
	) {
		this.selector = selector;
		this.action = action;
		this.credentials = credentials;
		this.statusListener = statusListener;
		this.workingDir = workingDir;
		this.strictFail = strictFail;
		this.cleanup = cleanup;
	}

	public void doActions() throws IOException {
		stepsComplete = 0;
		Utils.delete(workingDir); // Delete the directory if it already exists.
		Files.createDirectory(workingDir);
		statusListener.messageReceived("Prepared temporary directory for repositories.");
		try {
			List<String> repositoryURIs = selector.getURIs();
			stepsTotal = 2 * repositoryURIs.size();
			Map<String, Path> repoDirs = downloadRepositories(repositoryURIs);
			applyActionToRepositories(repoDirs);
		} catch (Exception e) {
			throw new IOException(e);
		} finally {
			if (cleanup) {
				statusListener.messageReceived("Removing all repositories.");
				Utils.delete(workingDir);
			}
		}
	}

	private void completeStep() {
		stepsComplete++;
		statusListener.progressUpdated(stepsComplete / (float) stepsTotal);
	}

	private Map<String, Path> downloadRepositories(List<String> uris) throws IOException {
		Map<String, Path> repositoryDirs = new HashMap<>();
		int dirIdx = 1;
		for (String repositoryURI : uris) {
			Path repoDir = workingDir.resolve(Integer.toString(dirIdx++));
			try {
				statusListener.messageReceived("Cloning repository " + repositoryURI + " to " + repoDir);
				CloneCommand clone = Git.cloneRepository();
				credentials.addCredentials(clone);
				clone.setDirectory(repoDir.toFile());
				clone.setURI(repositoryURI);
				try (var ignored = clone.call()) {
					repositoryDirs.put(repositoryURI, repoDir);
				} catch (Exception e) {
					if (strictFail) {
						throw new IOException(e);
					} else {
						repositoryDirs.put(repositoryURI, null);
						e.printStackTrace();
					}
				}
			} catch (Exception e) {
				if (strictFail) {
					throw new IOException(e);
				}
				e.printStackTrace();
			}
			completeStep();
		}
		return repositoryDirs;
	}

	private void applyActionToRepositories(Map<String, Path> repoDirs) throws IOException {
		for (var entry : repoDirs.entrySet()) {
			if (entry.getValue() != null) {
				try (Git git = Git.open(entry.getValue().toFile())) {
					statusListener.messageReceived("Applying action to repository " + entry.getKey());
					action.doAction(git);
				} catch (Exception e) {
					if (strictFail) {
						throw new IOException(e);
					}
					e.printStackTrace();
				}
			} else {
				statusListener.messageReceived("Skipping action on repository " + entry.getKey() + " because it could not be downloaded.");
			}
			completeStep();
		}
	}

	public static class Builder {
		private RepositorySelector selector;
		private RepositoryAction action;
		private GitCredentials credentials = cmd -> {};
		private StatusListener statusListener;
		private Path workingDir = Path.of(".", ".distribugit_tmp");
		private boolean strictFail = true;
		private boolean cleanup = false;

		public Builder selector(RepositorySelector selector) {
			this.selector = selector;
			return this;
		}

		public Builder action(RepositoryAction action) {
			this.action = action;
			return this;
		}

		public Builder credentials(GitCredentials credentials) {
			this.credentials = credentials;
			return this;
		}

		public Builder statusListener(StatusListener listener) {
			this.statusListener = listener;
			return this;
		}

		public Builder workingDir(Path dir) {
			this.workingDir = dir;
			return this;
		}

		public Builder strictFail(boolean strictFail) {
			this.strictFail = strictFail;
			return this;
		}

		public Builder cleanup(boolean cleanup) {
			this.cleanup = cleanup;
			return this;
		}

		public DistribuGit build() {
			return new DistribuGit(selector, action, credentials, statusListener, workingDir, strictFail, cleanup);
		}
	}

	public static void main(String[] args) throws IOException {
		new DistribuGit.Builder()
				.selector(RepositorySelector.from(
						"https://github.com/andrewlalis/RandomHotbar.git",
						"https://github.com/andrewlalis/CoyoteCredit.git",
						"https://github.com/andrewlalis/SignalsAndSystems2021.git"
				))
				.credentials(GitCredentials.ofUsernamePassword("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", ""))
				.action(RepositoryAction.ofCommand("/bin/bash", "../../test.sh"))
				.statusListener(new StatusListener() {
					@Override
					public void progressUpdated(float percentage) {
						System.out.printf("Progress: %.1f%%%n", percentage * 100);
					}

					@Override
					public void messageReceived(String message) {
						System.out.println("Message: " + message);
					}
				})
				.strictFail(false)
				.cleanup(false)
				.build().doActions();
	}
}
