# DistribuGit
Tool for performing operations on many git repositories at once. It works by cloning a set of repositories, and applying an action to each repository.

The easiest way to use this toolset for automating git operations is to include it as a dependency in your Java project. You can get this project from [jitpack.io](https://jitpack.io/#andrewlalis/distribugit).

Here's an example of how one might use DistribuGit to apply a bash script to a set of repositories:

```java
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
				System.out.printf("Progress: %.1f%%%n", percentage);
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
```

In short, we need to specify the following things in order to use the tool:

- The `RepositorySelector` provides a list of URIs that we can use to clone the repositories. Make sure that the URI format matches the type of credentials you plan to use, i.e. HTTPS should begin with `https://`, and SSH should begin with `git@`. This component is **mandatory**.
- The `RepositoryAction` is the action you want to perform on each repository. The `ofCommand` method can be used as a convenience to execute a command for each repository. Note that the script is executed within the repository's directory, which itself is in DistribuGit's working directory. Hence the `../../` in our example to point to a script in the directory from which our main method was started. This component is **mandatory**.
- The `GitCredentials` are used to provide credentials in case you're trying to perform actions that only an authenticated user can. If you're using a GitHub personal access token, you can provide it like in the example above: by providing it as a username, with no password. By default, no credentials are provided.
- The `StatusListener` is a component that's used to send log output and occasional progress updates as DistribuGit runs. By default, this just outputs information to `System.out`.
- `workingDir` denotes the directory in which DistribuGit will run. This directory may be completely deleted. By default, it is set to `.distribugit_tmp` in the current directory.
- `strictFail` determines if we should quit as soon as any error occurs. If this is false, then we will continue the operations even if some repositories encounter errors. By default, this is set to **true**.
- `cleanup` determines if we should remove all repository files after DistribuGit is finished. By default, this is set to **false**.
