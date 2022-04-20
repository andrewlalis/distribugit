package nl.andrewl.distribugit;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

/**
 * A single DistribuGit instance is used to execute a single sequence of
 * operations on a set of git repositories, as configured according to its
 * various components.
 * <p>
 *     A DistribuGit object, when invoking {@link DistribuGit#doActions()} or
 *     {@link DistribuGit#doActionsAsync()}, will perform the following series
 *     of operations:
 * </p>
 * <ol>
 *     <li>Call {@link RepositorySelector#getURIs()} to collect the list of
 *     repositories that it will operate on.</li>
 *     <li>Download each repository to the working directory.</li>
 *     <li>Applies the configured {@link RepositoryAction} to all of the
 *     repositories.</li>
 *     <li>If provided, applies the configured finalization action to all of
 *     the repositories.</li>
 *     <li>If needed, all repositories are deleted.</li>
 * </ol>
 * <p>
 *     Note that repositories are not guaranteed to be processed in any
 *     particular order.
 * </p>
 */
public class DistribuGit {
	private final RepositorySelector selector;
	private final RepositoryAction action;
	private final RepositoryAction finalizationAction;
	private final GitCredentials credentials;
	private final StatusListener statusListener;
	private final Path workingDir;
	private final boolean strictFail;
	private final boolean cleanup;

	private int stepsComplete;
	private int stepsTotal;

	/**
	 * Constructs a DistribuGit instance.
	 * @param selector A selector that provides a list of repository URIs.
	 * @param action An action to do for each repository.
	 * @param finalizationAction A final action to do for each repository,
	 *                           after all normal actions are done.
	 * @param credentials The credentials to use to operate on repositories.
	 * @param statusListener A listener that can be used to get information
	 *                       about the progress of the operations, and any
	 *                       messages that are emitted.
	 * @param workingDir The directory in which to do all git operations.
	 * @param strictFail Whether to fail instantly if any error occurs. If set
	 *                   to false, the program will continue even if actions
	 *                   fail for some repositories.
	 * @param cleanup Whether to perform cleanup after everything is done. This
	 *                will remove the working directory once we're done.
	 */
	public DistribuGit(
		RepositorySelector selector,
		RepositoryAction action,
		RepositoryAction finalizationAction,
		GitCredentials credentials,
		StatusListener statusListener,
		Path workingDir,
		boolean strictFail,
		boolean cleanup
	) {
		this.selector = selector;
		this.action = action;
		this.finalizationAction = finalizationAction;
		this.credentials = credentials;
		this.statusListener = statusListener;
		this.workingDir = workingDir;
		this.strictFail = strictFail;
		this.cleanup = cleanup;
	}

	/**
	 * Performs the configured actions on the selected git repositories.
	 * @throws IOException If an error occurs that requires us to quit early.
	 * This is only thrown if {@link DistribuGit#strictFail} is true.
	 */
	public synchronized void doActions() throws IOException {
		stepsComplete = 0;
		if (Files.exists(workingDir)) {
			try (var s = Files.list(workingDir)) {
				if (s.findAny().isPresent()) throw new IOException("Working directory is not empty!");
			}
		}
		Utils.delete(workingDir); // Delete the directory if it already exists.
		Files.createDirectory(workingDir);
		statusListener.messageReceived("Prepared temporary directory for repositories.");
		List<String> repositoryURIs;
		try {
			statusListener.messageReceived("Fetching repository URIs.");
			repositoryURIs = selector.getURIs();
		} catch (Exception e) {
			throw new IOException("Could not fetch repository URIs.", e);
		}
		if (repositoryURIs.isEmpty()) {
			statusListener.messageReceived("No repositories were found.");
			statusListener.progressUpdated(100f);
			return;
		}
		try {
			stepsTotal = (finalizationAction == null ? 2 : 3) * repositoryURIs.size();
			Map<String, Git> repos = downloadRepositories(repositoryURIs);
			applyActionToRepositories(repos, action);
			if (finalizationAction != null) {
				applyActionToRepositories(repos, finalizationAction);
			}
			repos.values().forEach(Git::close);
		} catch (Exception e) {
			throw new IOException(e);
		} finally {
			if (cleanup) {
				statusListener.messageReceived("Removing all repositories.");
				Utils.delete(workingDir);
			}
		}
	}

	/**
	 * Runs the configured git actions on all selected repositories in an
	 * asynchronous manner.
	 * @return A future that completes when all actions are complete, or if an
	 * error occurs and the operation quits early.
	 */
	public CompletableFuture<Void> doActionsAsync() {
		final CompletableFuture<Void> cf = new CompletableFuture<>();
		ForkJoinPool.commonPool().submit(() -> {
			try {
				doActions();
				cf.complete(null);
			} catch (IOException e) {
				cf.completeExceptionally(e);
			}
		});
		return cf;
	}

	private void completeStep() {
		stepsComplete++;
		statusListener.progressUpdated(stepsComplete / (float) stepsTotal * 100f);
	}

	/**
	 * Downloads a set of repositories to working directories.
	 * @param uris The repositories to download.
	 * @return A map which maps each repository URI to its {@link Git} instance.
	 * @throws IOException If {@link DistribuGit#strictFail} is set to true,
	 * this will be thrown if an error occurs.
	 */
	private Map<String, Git> downloadRepositories(List<String> uris) throws IOException {
		Map<String, Git> repositoryDirs = new HashMap<>();
		int dirIdx = 1;
		for (String repositoryURI : uris) {
			Path repoDir = workingDir.resolve(Integer.toString(dirIdx++));
			statusListener.messageReceived("Cloning repository " + repositoryURI + " to " + repoDir);
			CloneCommand clone = Git.cloneRepository();
			try {
				credentials.addCredentials(clone);
			} catch (Exception e) {
				if (strictFail) {
					throw new IOException(e);
				}
				statusListener.messageReceived("Could not add credentials to repository: " + e.getMessage());
				e.printStackTrace();
				// Skip the rest of the logic since this failed. Just go to the next repository.
				completeStep();
				continue;
			}
			clone.setDirectory(repoDir.toFile());
			clone.setURI(repositoryURI);
			try (var git = clone.call()) {
				repositoryDirs.put(repositoryURI, git);
			} catch (Exception e) {
				if (strictFail) {
					throw new IOException(e);
				} else {
					statusListener.messageReceived("Could not clone repository: " + e.getMessage());
					repositoryDirs.put(repositoryURI, null);
					e.printStackTrace();
				}
			}
			completeStep();
		}
		return repositoryDirs;
	}

	/**
	 * Applies an action to all git repositories in the given map.
	 * @param repositories A map which maps URIs to {@link Git} instances.
	 * @param action The action to apply to each repository.
	 * @throws IOException If {@link DistribuGit#strictFail} is set to true,
	 * this will be thrown if an error occurs.
	 */
	private void applyActionToRepositories(Map<String, Git> repositories, RepositoryAction action) throws IOException {
		for (var entry : repositories.entrySet()) {
			if (entry.getValue() != null) {
				try {
					Git git = entry.getValue();
					statusListener.messageReceived("Applying action to repository " + entry.getKey());
					action.doAction(git);
				} catch (Exception e) {
					if (strictFail) {
						throw new IOException(e);
					}
					statusListener.messageReceived("Action could not be applied to repository: " + e.getMessage());
					e.printStackTrace();
				}
			} else {
				statusListener.messageReceived("Skipping action on repository " + entry.getKey() + " because it could not be downloaded.");
			}
			completeStep();
		}
	}

	/**
	 * A builder class to help with constructing {@link DistribuGit} instances
	 * with a fluent method interface.
	 */
	public static class Builder {
		private RepositorySelector selector;
		private RepositoryAction action;
		private RepositoryAction finalizationAction;
		private GitCredentials credentials = cmd -> {};
		private StatusListener statusListener = new StatusListener() {
			@Override
			public void progressUpdated(float percentage) {
				System.out.printf("Progress: %.1f%%%n", percentage);
			}

			@Override
			public void messageReceived(String message) {
				System.out.println("Message: " + message);
			}
		};
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

		public Builder finalizationAction(RepositoryAction finalizationAction) {
			this.finalizationAction = finalizationAction;
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
			if (selector == null || action == null) {
				throw new IllegalStateException("Cannot build an instance of DistribuGit without a selector or action.");
			}
			return new DistribuGit(selector, action, finalizationAction, credentials, statusListener, workingDir, strictFail, cleanup);
		}
	}
}
