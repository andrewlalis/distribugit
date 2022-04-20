package nl.andrewl.distribugit;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DistribuGitTest {
	@Test
	public void testBasicActionOperation() throws IOException {
		AtomicInteger count = new AtomicInteger(0);
		RepositoryAction countAction = git -> count.incrementAndGet();
		new DistribuGit.Builder()
			.selector(RepositorySelector.from(
					"https://github.com/andrewlalis/record-net.git",
					"https://github.com/andrewlalis/distribugit.git",
					"https://github.com/andrewlalis/RandomHotbar.git"
			))
			.action(countAction)
			.statusListener(noOpListener())
			.strictFail(true)
			.cleanup(true)
			.workingDir(Path.of(".", ".distribugit_test_tmp"))
			.build().doActions();
		assertEquals(3, count.get());
	}

	@Test
	public void testStrictFailFalse() throws IOException {
		AtomicInteger count = new AtomicInteger(0);
		RepositoryAction countAction = git -> count.incrementAndGet();
		new DistribuGit.Builder()
			.selector(RepositorySelector.from(
					"https://github.com/andrewlalis/record-net.git",
					"https://github.com/andrewlalis/distribugit.git",
					"https://github.com/andrewlalis/somerandomgitrepositorythatdoesntexist.git"
			))
			.action(countAction)
			.statusListener(noOpListener())
			.strictFail(false)
			.cleanup(true)
			.workingDir(Path.of(".", ".distribugit_test_tmp"))
			.build().doActions();
		assertEquals(2, count.get());
	}

	@Test
	public void testStrictFailTrue() {
		AtomicInteger count = new AtomicInteger(0);
		RepositoryAction countAction = git -> count.incrementAndGet();
		DistribuGit d = new DistribuGit.Builder()
			.selector(RepositorySelector.from(
					"https://github.com/andrewlalis/record-net.git",
					"https://github.com/andrewlalis/distribugit.git",
					"https://github.com/andrewlalis/somerandomgitrepositorythatdoesntexist.git"
			))
			.action(countAction)
			.statusListener(noOpListener())
			.strictFail(true)
			.cleanup(true)
			.workingDir(Path.of(".", ".distribugit_test_tmp"))
			.build();
		assertThrows(IOException.class, d::doActions);
	}

	private static StatusListener noOpListener() {
		return new StatusListener() {
			@Override
			public void progressUpdated(float percentage) {}

			@Override
			public void messageReceived(String message) {}
		};
	}
}
