package org.tehlab.whitek0t.fictionbook.dto;

import java.io.IOException;
import java.io.InputStream;

@FunctionalInterface
public interface ResourceDataProvider {
    InputStream getInputStream() throws IOException;
}
