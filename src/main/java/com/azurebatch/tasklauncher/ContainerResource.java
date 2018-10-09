package com.azurebatch.tasklauncher;

import org.springframework.core.io.AbstractResource;

import java.io.IOException;
import java.io.InputStream;

public class ContainerResource extends AbstractResource {

    private String imagename;

    public ContainerResource(String imageName) {
        this.imagename = imageName;
    }

    @Override
    public String getDescription() {
        return imagename;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        throw new UnsupportedOperationException("getInputStream not supported");
    }
}
