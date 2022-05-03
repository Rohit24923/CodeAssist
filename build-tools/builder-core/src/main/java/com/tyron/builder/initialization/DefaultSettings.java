package com.tyron.builder.initialization;

import com.tyron.builder.api.Action;
import com.tyron.builder.StartParameter;
import com.tyron.builder.api.UnknownProjectException;
import com.tyron.builder.api.initialization.ConfigurableIncludedBuild;
import com.tyron.builder.api.initialization.ProjectDescriptor;
import com.tyron.builder.api.initialization.Settings;
import com.tyron.builder.api.initialization.dsl.ScriptHandler;
import com.tyron.builder.groovy.scripts.ScriptSource;
import com.tyron.builder.internal.Actions;
import com.tyron.builder.api.internal.GradleInternal;
import com.tyron.builder.api.internal.SettingsInternal;
import com.tyron.builder.api.internal.file.FileResolver;
import com.tyron.builder.api.internal.initialization.ClassLoaderScope;
import com.tyron.builder.api.internal.project.ProjectRegistry;
import com.tyron.builder.internal.reflect.service.ServiceRegistry;
import com.tyron.builder.internal.service.scopes.ServiceRegistryFactory;
import com.tyron.builder.api.provider.ProviderFactory;
import com.tyron.builder.caching.configuration.BuildCacheConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

public class DefaultSettings implements SettingsInternal {
    private ScriptSource settingsScript;

    private StartParameter startParameter;

    private File settingsDir;

    private DefaultProjectDescriptor rootProjectDescriptor;

    private DefaultProjectDescriptor defaultProjectDescriptor;

    private final GradleInternal gradle;

    private final ClassLoaderScope classLoaderScope;
    private final ClassLoaderScope baseClassLoaderScope;
    private final ScriptHandler scriptHandler;
    private final ServiceRegistry services;

    private final List<IncludedBuildSpec> includedBuildSpecs = new ArrayList<>();
//    private final DependencyResolutionManagementInternal dependencyResolutionManagement;


    public DefaultSettings(
            ServiceRegistryFactory serviceRegistryFactory,
            GradleInternal gradle,
            ClassLoaderScope classLoaderScope,
            ClassLoaderScope baseClassLoaderScope,
            ScriptHandler settingsScriptHandler,
            File settingsDir,
            ScriptSource settingsScript,
            StartParameter startParameter
    ) {
        this.gradle = gradle;
        this.classLoaderScope = classLoaderScope;
        this.baseClassLoaderScope = baseClassLoaderScope;
        this.scriptHandler = settingsScriptHandler;
        this.settingsDir = settingsDir;
        this.settingsScript = settingsScript;
        this.startParameter = startParameter;
        this.services = serviceRegistryFactory.createFor(this);
        this.rootProjectDescriptor = createProjectDescriptor(null, settingsDir.getName(), settingsDir);
//        this.dependencyResolutionManagement = services.get(DependencyResolutionManagementInternal.class);
    }

    public DefaultProjectDescriptor createProjectDescriptor(@Nullable DefaultProjectDescriptor parent, String name, File dir) {
        return new DefaultProjectDescriptor(parent, name, dir, getProjectDescriptorRegistry(), getFileResolver());
    }

    private ProjectDescriptorRegistry getProjectDescriptorRegistry() {
        return services.get(ProjectDescriptorRegistry.class);
    }

    private FileResolver getFileResolver() {
        return services.get(FileResolver.class);
    }

    @Override
    public void include(Iterable<String> projectPaths) {
        for (String projectPath : projectPaths) {
            String subPath = "";
            String[] pathElements = removeTrailingColon(projectPath).split(":");
            DefaultProjectDescriptor parentProjectDescriptor = rootProjectDescriptor;
            for (String pathElement : pathElements) {
                subPath = subPath + ":" + pathElement;
                DefaultProjectDescriptor projectDescriptor = getProjectDescriptorRegistry().getProject(subPath);
                if (projectDescriptor == null) {
                    parentProjectDescriptor = createProjectDescriptor(parentProjectDescriptor, pathElement, new File(parentProjectDescriptor.getProjectDir(), pathElement));
                } else {
                    parentProjectDescriptor = projectDescriptor;
                }
            }
        }
    }

    @Override
    public void includeFlat(Iterable<String> projectNames) {
        for (String projectName : projectNames) {
            createProjectDescriptor(rootProjectDescriptor, projectName,
                    new File(rootProjectDescriptor.getProjectDir().getParentFile(), projectName));
        }
    }

    private String removeTrailingColon(String projectPath) {
        if (projectPath.startsWith(":")) {
            return projectPath.substring(1);
        }
        return projectPath;
    }

    @Override
    public Settings getSettings() {
        return this;
    }

    @Override
    public ScriptHandler getBuildscript() {
        return scriptHandler;
    }

    @Override
    public File getSettingsDir() {
        return settingsDir;
    }

    @Override
    public File getRootDir() {
        return null;
    }

    @Override
    public ProjectDescriptor getRootProject() {
        return rootProjectDescriptor;
    }

    @Override
    public ProjectDescriptor project(String path) throws UnknownProjectException {
        DefaultProjectDescriptor projectDescriptor = getProjectDescriptorRegistry().getProject(path);
        if (projectDescriptor == null) {
            throw new UnknownProjectException(String.format("Project with path '%s' could not be found.", path));
        }
        return projectDescriptor;
    }

    @Nullable
    @Override
    public ProjectDescriptor findProject(String path) {
        return getProjectDescriptorRegistry().getProject(path);
    }

    @Override
    public ProjectDescriptor project(File projectDir) throws UnknownProjectException {
        DefaultProjectDescriptor projectDescriptor = getProjectDescriptorRegistry().getProject(projectDir);
        if (projectDescriptor == null) {
            throw new UnknownProjectException(String.format("Project with path '%s' could not be found.", projectDir));
        }
        return projectDescriptor;
    }

    @Nullable
    @Override
    public ProjectDescriptor findProject(File projectDir) {
        return getProjectDescriptorRegistry().getProject(projectDir);
    }

    @Override
    public ProviderFactory getProviders() {
        return services.get(ProviderFactory.class);
    }

    @Override
    public GradleInternal getGradle() {
        return gradle;
    }

    @Override
    public void includeBuild(Object rootProject) {
        includeBuild(rootProject, Actions.doNothing());
    }

    @Override
    public void includeBuild(Object rootProject, Action<ConfigurableIncludedBuild> configuration) {
        File projectDir = getFileResolver().resolve(rootProject);
        includedBuildSpecs.add(IncludedBuildSpec.includedBuild(projectDir, configuration));
    }

    @Override
    public BuildCacheConfiguration getBuildCache() {
        return services.get(BuildCacheConfiguration.class);
    }

    @Override
    public void buildCache(Action<? super BuildCacheConfiguration> action) {
        action.execute(getBuildCache());
    }

    @Override
    public void enableFeaturePreview(String name) {

    }

    @Override
    public StartParameter getStartParameter() {
        return startParameter;
    }

    @Override
    public ProjectRegistry<DefaultProjectDescriptor> getProjectRegistry() {
        return getProjectDescriptorRegistry();
    }

    @Override
    public void setDefaultProject(DefaultProjectDescriptor defaultProjectDescriptor) {
        this.defaultProjectDescriptor = defaultProjectDescriptor;
    }

    @Override
    public DefaultProjectDescriptor getDefaultProject() {
        return defaultProjectDescriptor;
    }


    @Override
    public ClassLoaderScope getBaseClassLoaderScope() {
        return baseClassLoaderScope;
    }

    @Override
    public ClassLoaderScope getClassLoaderScope() {
        return classLoaderScope;
    }

    @Override
    public List<IncludedBuildSpec> getIncludedBuilds() {
        return includedBuildSpecs;
    }
}
