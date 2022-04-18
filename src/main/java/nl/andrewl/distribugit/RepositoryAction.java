package nl.andrewl.distribugit;

import org.eclipse.jgit.api.Git;

public interface RepositoryAction {
	void doAction(Git git) throws Exception;


}
