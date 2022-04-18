package nl.andrewl.distribugit;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public class Utils {
	public static void deleteNoThrow(Path path) {
		try {
			delete(path);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void delete(Path path) throws IOException {
		if (path == null) return;
		if (!Files.exists(path)) return;
		if (Files.isRegularFile(path)) {
			Files.delete(path);
		} else {
			Files.walkFileTree(path, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					Files.delete(file);
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
					Files.delete(dir);
					return FileVisitResult.CONTINUE;
				}
			});
		}
	}
}
