package nl.andrewl.distribugit;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.eclipse.jgit.api.TransportCommand;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.transport.ssh.jsch.JschConfigSessionFactory;
import org.eclipse.jgit.transport.ssh.jsch.OpenSshConfig;
import org.eclipse.jgit.util.FS;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public interface GitCredentials {
	void addCredentials(TransportCommand<?, ?> gitCommand) throws Exception;

	static GitCredentials ofUsernamePassword(String username, String password) {
		return cmd -> cmd.setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, password));
	}

	static GitCredentials ofSshKey() {
		return ofSshKey(Path.of(System.getProperty("user.home"), ".ssh", "id_rsa"));
	}

	static GitCredentials ofSshKey(Path privateKeyFile) {
		return ofSshKey(
				privateKeyFile,
				privateKeyFile.getParent().resolve(privateKeyFile.getFileName().toString() + ".pub"),
				null
		);
	}

	static GitCredentials ofSshKey(Path privateKeyFile, Path publicKeyFile, String passphrase) {
		System.out.println("Using private key at " + privateKeyFile.toAbsolutePath());
		SshSessionFactory sshSessionFactory = new JschConfigSessionFactory() {
			@Override
			protected void configure(OpenSshConfig.Host hc, Session session) {
				session.setConfig("StrictHostKeyChecking", "no"); // Don't require the host to be in the system's known hosts list.
			}

			@Override
			protected JSch createDefaultJSch(FS fs) throws JSchException {
				var jsch = super.createDefaultJSch(fs);
				jsch.removeAllIdentity();
				jsch.addIdentity(
						privateKeyFile.toAbsolutePath().toString(),
						publicKeyFile.toAbsolutePath().toString(),
						passphrase == null ? null : passphrase.getBytes(StandardCharsets.UTF_8)
				);
				return jsch;
			}
		};
		return cmd -> cmd.setTransportConfigCallback(transport -> {
			if (transport instanceof SshTransport sshTransport) {
				sshTransport.setSshSessionFactory(sshSessionFactory);
			} else {
				throw new IllegalStateException("Invalid git transport method: " + transport.getClass().getSimpleName() + "; Cannot apply SSH session factory to this type of transport.");
			}
		});
	}
}
