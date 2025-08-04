package org.apache.nifi.stateless.engine;

import org.apache.nifi.stateless.config.ExtensionClientDefinition;
import org.apache.nifi.stateless.config.SslContextDefinition;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class DefaultStatelessEngineConfiguration implements StatelessEngineConfiguration {

    private final File workingDirectory;
    private final File narDirectory;
    private final File extensionsDirectory;
    private final Collection<File> readOnlyExtensionsDirectories;
    private final File krb5File;
    private final Optional<File> contentRepositoryDirectory;
    private final SslContextDefinition sslContext;
    private final String sensitivePropsKey;
    private final List<ExtensionClientDefinition> extensionClients;
    private final boolean logExtensionDiscovery;
    private final String statusTaskInterval;
    private final String processorStartTimeout;
    private final String componentEnableTimeout;

    public DefaultStatelessEngineConfiguration(
            final File workingDirectory,
            final File narDirectory,
            final File extensionsDirectory,
            final Collection<File> readOnlyExtensionsDirectories,
            final File krb5File,
            final File contentRepositoryDirectory,
            final SslContextDefinition sslContext,
            final String sensitivePropsKey,
            final List<ExtensionClientDefinition> extensionClients,
            final boolean logExtensionDiscovery,
            final String statusTaskInterval,
            final String processorStartTimeout,
            final String componentEnableTimeout
    ) {
        this.workingDirectory = workingDirectory;
        this.narDirectory = narDirectory;
        this.extensionsDirectory = extensionsDirectory;
        this.readOnlyExtensionsDirectories = readOnlyExtensionsDirectories;
        this.krb5File = krb5File;
        this.contentRepositoryDirectory = Optional.ofNullable(contentRepositoryDirectory);
        this.sslContext = sslContext;
        this.sensitivePropsKey = sensitivePropsKey;
        this.extensionClients = extensionClients;
        this.logExtensionDiscovery = logExtensionDiscovery;
        this.statusTaskInterval = statusTaskInterval;
        this.processorStartTimeout = processorStartTimeout;
        this.componentEnableTimeout = componentEnableTimeout;
    }

    @Override
    public File getWorkingDirectory() {
        return workingDirectory;
    }

    @Override
    public File getNarDirectory() {
        return narDirectory;
    }

    @Override
    public File getExtensionsDirectory() {
        return extensionsDirectory;
    }

    @Override
    public Collection<File> getReadOnlyExtensionsDirectories() {
        return readOnlyExtensionsDirectories;
    }

    @Override
    public File getKrb5File() {
        return krb5File;
    }

    @Override
    public Optional<File> getContentRepositoryDirectory() {
        return contentRepositoryDirectory;
    }

    @Override
    public SslContextDefinition getSslContext() {
        return sslContext;
    }

    @Override
    public String getSensitivePropsKey() {
        return sensitivePropsKey;
    }

    @Override
    public List<ExtensionClientDefinition> getExtensionClients() {
        return extensionClients;
    }

    @Override
    public boolean isLogExtensionDiscovery() {
        return logExtensionDiscovery;
    }

    @Override
    public String getStatusTaskInterval() {
        return statusTaskInterval;
    }

    @Override
    public String getProcessorStartTimeout() {
        return processorStartTimeout;
    }

    @Override
    public String getComponentEnableTimeout() {
        return componentEnableTimeout;
    }

    // Add Builder as inner class or separately
}
