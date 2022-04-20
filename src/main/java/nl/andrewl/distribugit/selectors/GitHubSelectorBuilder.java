package nl.andrewl.distribugit.selectors;

import nl.andrewl.distribugit.RepositorySelector;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A builder that can be used to construct {@link RepositorySelector} instances
 * that fetch repositories from GitHub via its API.
 */
public record GitHubSelectorBuilder(GitHub gh) {
	public static GitHubSelectorBuilder fromPersonalAccessToken(String token) throws IOException {
		return fromPersonalAccessToken(token, null);
	}

	public static GitHubSelectorBuilder fromPersonalAccessToken(String token, String userOrOrgName) throws IOException {
		return new GitHubSelectorBuilder(new GitHubBuilder().withOAuthToken(token, userOrOrgName).build());
	}

	/**
	 * Select repositories using an organization name, and a prefix to match
	 * against all repositories in that organization.
	 * @param orgName The name of the organization.
	 * @param prefix The prefix to use.
	 * @return A selector that selects matching repositories.
	 */
	public RepositorySelector orgAndPrefix(String orgName, String prefix) {
		return () -> {
			List<String> repoURIs = new ArrayList<>();
			GHOrganization org = gh.getOrganization(orgName);
			for (GHRepository repo : org.listRepositories()) {
				if (repo.getName().startsWith(prefix)) {
					repoURIs.add(repo.getHttpTransportUrl());
				}
			}
			return repoURIs;
		};
	}

	/**
	 * A custom selector that can be used to perform some operations using the
	 * GitHub API and return a list of repositories.
	 * @param selector The GitHub selector to use.
	 * @return A selector that selects GitHub repositories.
	 */
	public RepositorySelector custom(GitHubRepositorySelector selector) {
		return () -> selector.getRepos(gh).stream().map(GHRepository::getHttpTransportUrl).toList();
	}
}
