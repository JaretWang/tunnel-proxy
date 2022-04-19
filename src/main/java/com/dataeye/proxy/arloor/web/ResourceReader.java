package com.dataeye.proxy.arloor.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;

public class ResourceReader {
    private static final Logger logger = LoggerFactory.getLogger(ResourceReader.class);

    public static byte[] readFile(String path) {
        try {
            return Files.readAllBytes(FileSystems.getDefault().getPath(path));
        } catch (IOException e) {
            logger.error("error read file {}", path);
        }
        return null;
    }
}
