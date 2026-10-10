package com.aether.guest.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Explicit storage capability. Not interception of arbitrary guest file IO. */
public interface GuestStorage {
    InputStream openInput(String path) throws IOException;
    OutputStream openOutput(String path, boolean append) throws IOException;
    boolean exists(String path) throws IOException;
}
