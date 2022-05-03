package com.tyron.builder.execution;

import static com.tyron.builder.cache.FileLockManager.LockMode.OnDemand;
import static com.tyron.builder.cache.internal.filelock.LockOptionsBuilder.mode;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.tyron.builder.api.UncheckedIOException;
import com.tyron.builder.api.execution.TaskExecutionGraph;
import com.tyron.builder.api.execution.TaskExecutionListener;
import com.tyron.builder.api.internal.changedetection.state.CrossBuildFileHashCache;
import com.tyron.builder.api.internal.changedetection.state.DefaultResourceSnapshotterCacheService;
import com.tyron.builder.api.internal.changedetection.state.LineEndingNormalizingFileSystemLocationSnapshotHasher;
import com.tyron.builder.api.internal.changedetection.state.ResourceEntryFilter;
import com.tyron.builder.api.internal.changedetection.state.ResourceFilter;
import com.tyron.builder.api.internal.changedetection.state.ResourceSnapshotterCacheService;
import com.tyron.builder.api.internal.file.FileCollectionFactory;
import com.tyron.builder.api.internal.file.FileOperations;
import com.tyron.builder.api.internal.file.temp.TemporaryFileProvider;
import com.tyron.builder.api.internal.project.ProjectInternal;
import com.tyron.builder.api.internal.tasks.TaskExecuter;
import com.tyron.builder.api.internal.tasks.execution.CatchExceptionTaskExecuter;
import com.tyron.builder.api.internal.tasks.execution.CleanupStaleOutputsExecuter;
import com.tyron.builder.api.internal.tasks.execution.EventFiringTaskExecuter;
import com.tyron.builder.api.internal.tasks.execution.ExecuteActionsTaskExecuter;
import com.tyron.builder.api.internal.tasks.execution.FinalizePropertiesTaskExecuter;
import com.tyron.builder.api.internal.tasks.execution.ResolveTaskExecutionModeExecuter;
import com.tyron.builder.api.internal.tasks.execution.SkipOnlyIfTaskExecuter;
import com.tyron.builder.api.internal.tasks.execution.SkipTaskWithNoActionsExecuter;
import com.tyron.builder.cache.CacheBuilder;
import com.tyron.builder.cache.CacheRepository;
import com.tyron.builder.cache.PersistentCache;
import com.tyron.builder.cache.PersistentIndexedCache;
import com.tyron.builder.cache.PersistentIndexedCacheParameters;
import com.tyron.builder.internal.cache.StringInterner;
import com.tyron.builder.cache.internal.scopes.DefaultBuildScopedCache;
import com.tyron.builder.cache.scopes.BuildScopedCache;
import com.tyron.builder.caching.local.internal.BuildCacheTempFileStore;
import com.tyron.builder.caching.local.internal.DefaultBuildCacheTempFileStore;
import com.tyron.builder.caching.local.internal.DirectoryBuildCacheService;
import com.tyron.builder.caching.local.internal.LocalBuildCacheService;
import com.tyron.builder.execution.plan.ExecutionNodeAccessHierarchies;
import com.tyron.builder.execution.taskgraph.TaskListenerInternal;
import com.tyron.builder.initialization.BuildCancellationToken;
import com.tyron.builder.initialization.DefaultBuildCancellationToken;
import com.tyron.builder.internal.event.ListenerManager;
import com.tyron.builder.internal.execution.BuildOutputCleanupRegistry;
import com.tyron.builder.internal.execution.ExecutionEngine;
import com.tyron.builder.internal.execution.OutputChangeListener;
import com.tyron.builder.internal.execution.fingerprint.FileCollectionFingerprinterRegistry;
import com.tyron.builder.internal.execution.fingerprint.FileCollectionSnapshotter;
import com.tyron.builder.internal.execution.fingerprint.InputFingerprinter;
import com.tyron.builder.internal.execution.fingerprint.impl.DefaultFileCollectionFingerprinterRegistry;
import com.tyron.builder.internal.execution.fingerprint.impl.FingerprinterRegistration;
import com.tyron.builder.internal.execution.history.ExecutionHistoryStore;
import com.tyron.builder.internal.execution.history.OutputFilesRepository;
import com.tyron.builder.internal.file.Deleter;
import com.tyron.builder.internal.file.FileAccessTracker;
import com.tyron.builder.internal.fingerprint.DirectorySensitivity;
import com.tyron.builder.internal.fingerprint.LineEndingSensitivity;
import com.tyron.builder.internal.fingerprint.classpath.impl.DefaultClasspathFingerprinter;
import com.tyron.builder.internal.fingerprint.classpath.impl.DefaultCompileClasspathFingerprinter;
import com.tyron.builder.internal.fingerprint.hashing.FileSystemLocationSnapshotHasher;
import com.tyron.builder.internal.fingerprint.impl.AbsolutePathFileCollectionFingerprinter;
import com.tyron.builder.internal.fingerprint.impl.DefaultInputFingerprinter;
import com.tyron.builder.internal.fingerprint.impl.RelativePathFileCollectionFingerprinter;
import com.tyron.builder.internal.hash.ChecksumService;
import com.tyron.builder.internal.hash.ClassLoaderHierarchyHasher;
import com.tyron.builder.internal.operations.BuildOperationExecutor;
import com.tyron.builder.internal.reflect.service.DefaultServiceRegistry;
import com.tyron.builder.internal.resource.local.DefaultPathKeyFileStore;
import com.tyron.builder.internal.resource.local.PathKeyFileStore;
import com.tyron.builder.internal.serialize.HashCodeSerializer;
import com.tyron.builder.internal.service.scopes.ExecutionGradleServices;
import com.tyron.builder.internal.snapshot.ValueSnapshotter;
import com.tyron.builder.internal.work.AsyncWorkTracker;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class ProjectExecutionServices extends DefaultServiceRegistry {

    private final ProjectInternal projectInternal;

    public ProjectExecutionServices(ProjectInternal project) {
        super("Configured project services for '" + project.getPath() + "'", project.getServices());

        this.projectInternal = project;

        BuildCancellationToken token = new DefaultBuildCancellationToken();
        add(BuildCancellationToken.class, token);

        addProvider(new ExecutionGradleServices());
    }

    protected BuildScopedCache createBuildScopedCache(
            CacheRepository cacheRepository
    ) {
        File gradle = new File(projectInternal.getBuildDir(), ".gradle");
        return new DefaultBuildScopedCache(gradle, cacheRepository);
    }

    ChecksumService createChecksumService() {
        return new ChecksumService() {
            @Override
            public HashCode md5(File file) {
                try {
                    return Hashing.md5().hashBytes(FileUtils.readFileToByteArray(file));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            public HashCode sha1(File file) {
                try {
                    return Hashing.sha1().hashBytes(FileUtils.readFileToByteArray(file));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            public HashCode sha256(File file) {
                try {
                    return Hashing.sha256().hashBytes(FileUtils.readFileToByteArray(file));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            public HashCode sha512(File file) {
                try {
                    return Hashing.sha512().hashBytes(FileUtils.readFileToByteArray(file));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            public HashCode hash(File src, String algorithm) {
                switch (algorithm) {
                    case "md5": return md5(src);
                    case "sha1": return sha1(src);
                    case "sha256": return sha256(src);
                    default: return sha512(src);
                }
            }
        };
    }

    FileAccessTracker createFileAccessTracker() {
        return file -> {

        };
    }

    PathKeyFileStore createPathKeyFileStore(
            ChecksumService checksumService
    ) {
        return new DefaultPathKeyFileStore(checksumService, projectInternal.getBuildDir());
    }

    LocalBuildCacheService createLocalBuildCacheService(
        CacheRepository cacheRepository,
        ChecksumService checksumService,
        TemporaryFileProvider temporaryFileProvider,
        FileAccessTracker fileAccessTracker
    ) {
        File buildDir = projectInternal.getBuildDir();

        PathKeyFileStore pathKeyFileStore = new DefaultPathKeyFileStore(checksumService, new File(buildDir, ".gradle"));
        PersistentCache cache = cacheRepository.cache(buildDir)
                .withDisplayName("Build cache")
                .withLockOptions(mode(OnDemand))
                .withCrossVersionCache(CacheBuilder.LockTarget.DefaultTarget)
                .open();
        BuildCacheTempFileStore tempFileStore = new DefaultBuildCacheTempFileStore(temporaryFileProvider);

        return new DirectoryBuildCacheService(
                pathKeyFileStore,
                cache,
                tempFileStore,
                fileAccessTracker,
                ".failed"
        );
    }

    FileSystemLocationSnapshotHasher createFileSystemLocationSnapshotHasher() {
        return LineEndingNormalizingFileSystemLocationSnapshotHasher.DEFAULT;
    }

    FileCollectionFingerprinterRegistry createFileCollectionFingerprinterRegistry(
            List<FingerprinterRegistration> registrations,
            FileCollectionSnapshotter fileCollectionSnapshotter,
            FileSystemLocationSnapshotHasher fileSystemLocationSnapshotHasher
    ){
        return new DefaultFileCollectionFingerprinterRegistry(registrations);
    }

    FingerprinterRegistration createAbsolutePathDefaultFingerprinter(
            FileCollectionSnapshotter fileCollectionSnapshotter,
            FileSystemLocationSnapshotHasher fileSystemLocationSnapshotHasher
    ) {
        return FingerprinterRegistration.registration(
                DirectorySensitivity.DEFAULT,
                LineEndingSensitivity.DEFAULT,
                new AbsolutePathFileCollectionFingerprinter(
                        DirectorySensitivity.DEFAULT,
                        fileCollectionSnapshotter,
                        fileSystemLocationSnapshotHasher
                )
        );
    }

    FingerprinterRegistration createAbsolutePathIgnoreDirectoryFingerprinter(
            FileCollectionSnapshotter fileCollectionSnapshotter,
            FileSystemLocationSnapshotHasher fileSystemLocationSnapshotHasher
    ) {
        return FingerprinterRegistration.registration(
                DirectorySensitivity.IGNORE_DIRECTORIES,
                LineEndingSensitivity.DEFAULT,
                new AbsolutePathFileCollectionFingerprinter(
                        DirectorySensitivity.IGNORE_DIRECTORIES,
                        fileCollectionSnapshotter,
                        fileSystemLocationSnapshotHasher
                )
        );
    }

    FingerprinterRegistration createIgnoreDirectoryNormalizeLineEndingsRelativePathInputFingerprinter(
            FileSystemLocationSnapshotHasher hasher,
            FileCollectionSnapshotter fileCollectionSnapshotter,
            StringInterner interner
    ) {
        return FingerprinterRegistration.registration(
                DirectorySensitivity.IGNORE_DIRECTORIES,
                LineEndingSensitivity.NORMALIZE_LINE_ENDINGS,
                new RelativePathFileCollectionFingerprinter(
                        interner,
                        DirectorySensitivity.IGNORE_DIRECTORIES,
                        fileCollectionSnapshotter,
                        hasher
                )
        );
    }

    FingerprinterRegistration createIgnoreDirectoryDefaultLineEndingsRelativePathInputFingerprinter(
            FileSystemLocationSnapshotHasher hasher,
            FileCollectionSnapshotter fileCollectionSnapshotter,
            StringInterner interner
    ) {
        return FingerprinterRegistration.registration(
                DirectorySensitivity.IGNORE_DIRECTORIES,
                LineEndingSensitivity.DEFAULT,
                new RelativePathFileCollectionFingerprinter(
                        interner,
                        DirectorySensitivity.IGNORE_DIRECTORIES,
                        fileCollectionSnapshotter,
                        hasher
                )
        );
    }

    FingerprinterRegistration createCompileClassPathFingerprinter(
            ResourceSnapshotterCacheService resourceSnapshotterCacheService,
            FileCollectionSnapshotter fileCollectionSnapshotter,
            StringInterner interner
    ) {
        return FingerprinterRegistration.registration(
                DirectorySensitivity.DEFAULT,
                LineEndingSensitivity.DEFAULT,
                new DefaultCompileClasspathFingerprinter(
                        resourceSnapshotterCacheService,
                        fileCollectionSnapshotter,
                        interner
                )
        );
    }

    FingerprinterRegistration createClassPathNormalizerIgnoreDirectoriesInputFingerprinter(
            ResourceSnapshotterCacheService resourceSnapshotterCacheService,
            FileCollectionSnapshotter fileCollectionSnapshotter,
            StringInterner interner
    ) {
        return FingerprinterRegistration.registration(
                DirectorySensitivity.IGNORE_DIRECTORIES,
                LineEndingSensitivity.DEFAULT,
                new DefaultClasspathFingerprinter(
                        resourceSnapshotterCacheService,
                        fileCollectionSnapshotter,
                        ResourceFilter.FILTER_NOTHING,
                        ResourceEntryFilter.FILTER_NOTHING,
                        Collections.emptyMap(),
                        interner,
                        LineEndingSensitivity.DEFAULT
                )
        );
    }


    FingerprinterRegistration createClassPathNormalizerInputFingerprinter(
            ResourceSnapshotterCacheService resourceSnapshotterCacheService,
            FileCollectionSnapshotter fileCollectionSnapshotter,
            StringInterner interner
    ) {
        return FingerprinterRegistration.registration(
                DirectorySensitivity.DEFAULT,
                LineEndingSensitivity.DEFAULT,
                new DefaultClasspathFingerprinter(
                        resourceSnapshotterCacheService,
                        fileCollectionSnapshotter,
                        ResourceFilter.FILTER_NOTHING,
                        ResourceEntryFilter.FILTER_NOTHING,
                        Collections.emptyMap(),
                        interner,
                        LineEndingSensitivity.DEFAULT
                )
        );
    }

    InputFingerprinter createInputFingerprinter(
            FileCollectionSnapshotter fileCollectionSnapshotter,
            FileCollectionFingerprinterRegistry fileCollectionFingerprinterRegistry,
            ValueSnapshotter valueSnapshotter
    ) {
        return new DefaultInputFingerprinter(
                fileCollectionSnapshotter,
                fileCollectionFingerprinterRegistry,
                valueSnapshotter
        );
    }

    protected TaskExecuter createTaskExecuter(
            TaskExecutionGraph taskExecutionGraph,
            ExecutionHistoryStore executionHistoryStore,
            BuildOperationExecutor buildOperationExecutor,
            BuildOutputCleanupRegistry cleanupRegistry,
            Deleter deleter,
            OutputChangeListener outputChangeListener,
            OutputFilesRepository outputFilesRepository,
            AsyncWorkTracker asyncWorkTracker,
            ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
            ExecutionEngine executionEngine,
            InputFingerprinter inputFingerprinter,
            ListenerManager listenerManager,
            FileCollectionFactory factory,
            TaskExecutionListener taskExecutionListener,
            TaskListenerInternal taskListenerInternal,
            FileOperations fileOperations
    ) {
        TaskExecuter executer = new ExecuteActionsTaskExecuter(
                ExecuteActionsTaskExecuter.BuildCacheState.ENABLED,
                executionHistoryStore,
                buildOperationExecutor,
                asyncWorkTracker,
                classLoaderHierarchyHasher,
                executionEngine,
                inputFingerprinter,
                listenerManager,
                factory,
                fileOperations
        );
        executer = new CleanupStaleOutputsExecuter(
                buildOperationExecutor,
                cleanupRegistry,
                deleter,
                outputChangeListener,
                outputFilesRepository,
                executer
        );
        executer = new FinalizePropertiesTaskExecuter(executer);
        executer = new ResolveTaskExecutionModeExecuter(executer);
        executer = new SkipTaskWithNoActionsExecuter(executer, taskExecutionGraph);
        executer = new SkipOnlyIfTaskExecuter(executer);
        executer = new CatchExceptionTaskExecuter(executer);
        executer = new EventFiringTaskExecuter(buildOperationExecutor, taskExecutionListener, taskListenerInternal, executer);
        return executer;
    }

    ExecutionNodeAccessHierarchies.InputNodeAccessHierarchy createInputNodeAccessHierarchies(ExecutionNodeAccessHierarchies hierarchies) {
        return hierarchies.createInputHierarchy();
    }
}
