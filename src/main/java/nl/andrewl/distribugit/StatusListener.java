package nl.andrewl.distribugit;

public interface StatusListener {
	void progressUpdated(float percentage);
	void messageReceived(String message);
}
