package com.framework.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creating output directories without the two traps of {@link File#mkdirs()}:
 * its boolean result is easy to ignore, and it returns {@code false} both for
 * "failed" and for "already exists".
 */
public final class DirectoryUtils {

    private static final Logger logger = LoggerFactory.getLogger(DirectoryUtils.class);

    private DirectoryUtils() {}

    /**
     * Makes sure {@code dir} exists, creating missing parents too. Never throws:
     * report and screenshot folders are best-effort, and the caller's real work
     * (writing the file) reports its own, more specific error if the folder
     * really is unusable.
     *
     * @return {@code true} if the directory exists when this returns
     */
    public static boolean ensureExists(File dir) {
        if (dir.isDirectory()) {
            return true;
        }
        try {
            Files.createDirectories(dir.toPath());
            return true;
        } catch (IOException | SecurityException e) {
            logger.warn("Could not create directory {}: {}", dir.getAbsolutePath(), e.toString());
            return false;
        }
    }
}
