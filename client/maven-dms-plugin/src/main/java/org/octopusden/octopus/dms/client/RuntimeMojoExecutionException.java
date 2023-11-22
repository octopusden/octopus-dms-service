package org.octopusden.octopus.dms.client;

public class RuntimeMojoExecutionException extends RuntimeException {
    public RuntimeMojoExecutionException(String message) {
        super(message);
    }

    public RuntimeMojoExecutionException(String message, Throwable e) {
        super(message, e);
    }
}
