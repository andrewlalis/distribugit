package nl.andrewl.distribugit;

import org.eclipse.jgit.api.Git;

/**
 * An action that can be applied to a git repository.
 */
public interface RepositoryAction {
	/**
	 * Performs the action on the given git repository.
	 * @param git A reference to the git repository.
	 * @throws Exception If an error occurs during the action.
	 */
	void doAction(Git git) throws Exception;

	/**
	 * An action which executes a system command, as handled by
	 * {@link ProcessBuilder}. Note that the working directory of the command
	 * is set to the directory of the repository.
	 * @param command The command to run.
	 * @return The command action.
	 */
	static RepositoryAction ofCommand(String... command) {
		return git -> {
			ProcessBuilder pb = new ProcessBuilder(command);
			pb.directory(git.getRepository().getWorkTree());
			pb.inheritIO();
			Process p = pb.start();
			int result = p.waitFor();
			if (result != 0) throw new IllegalStateException("Non-zero exit code from script.");
		};
	}
}
