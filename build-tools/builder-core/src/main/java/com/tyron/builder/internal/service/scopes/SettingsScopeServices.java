package com.tyron.builder.internal.service.scopes;

import com.tyron.builder.api.internal.GradleInternal;
import com.tyron.builder.api.internal.SettingsInternal;
import com.tyron.builder.api.internal.file.FileLookup;
import com.tyron.builder.api.internal.file.FileResolver;
import com.tyron.builder.api.internal.plugins.PluginRegistry;
import com.tyron.builder.configuration.ConfigurationTargetIdentifier;
import com.tyron.builder.internal.reflect.service.DefaultServiceRegistry;
import com.tyron.builder.internal.reflect.service.ServiceRegistry;

public class SettingsScopeServices extends DefaultServiceRegistry {
    private final SettingsInternal settings;

    public SettingsScopeServices(final ServiceRegistry parent, final SettingsInternal settings) {
        super(parent);
        this.settings = settings;
        register(registration -> {
            for (PluginServiceRegistry pluginServiceRegistry : parent.getAll(PluginServiceRegistry.class)) {
                pluginServiceRegistry.registerSettingsServices(registration);
            }
        });
    }

    protected FileResolver createFileResolver(FileLookup fileLookup) {
        return fileLookup.getFileResolver(settings.getSettingsDir());
    }

    protected PluginRegistry createPluginRegistry(PluginRegistry parentRegistry) {
        return parentRegistry.createChild(settings.getClassLoaderScope());
    }

    protected ConfigurationTargetIdentifier createConfigurationTargetIdentifier() {
        return ConfigurationTargetIdentifier.of(settings);
    }

    protected GradleInternal createGradleInternal() {
        return (GradleInternal) settings.getGradle();
    }
}
