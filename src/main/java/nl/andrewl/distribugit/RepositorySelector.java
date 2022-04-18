package nl.andrewl.distribugit;


import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Component which produces a list of repositories to operate on.
 */
public interface RepositorySelector {
	List<String> getURIs() throws Exception;

	static RepositorySelector fromCollection(Collection<String> uris) {
		return () -> new ArrayList<>(uris);
	}

	static RepositorySelector from(String... uris) {
		return () -> List.of(uris);
	}
}
