package nl.andrewl.distribugit.selectors;

import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import java.util.List;

public interface GitHubRepositorySelector {
	List<GHRepository> getRepos(GitHub gh) throws Exception;
}
