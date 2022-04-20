package nl.andrewl.distribugit;

/**
 * Listens for updates during {@link DistribuGit#doActions()}.
 */
public interface StatusListener {
	/**
	 * Called when the operation's progress is updated.
	 * @param percentage The percentage (0 - 100) complete.
	 */
	void progressUpdated(float percentage);

	/**
	 * Called when the DistribuGit operation emits a message.
	 * @param message The message that was emitted.
	 */
	void messageReceived(String message);
}
