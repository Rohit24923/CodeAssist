package com.tyron.builder.api;

import com.google.common.collect.ImmutableSet;
import com.google.common.hash.HashCode;
import com.tyron.builder.api.file.FileCollection;
import com.tyron.builder.api.file.RelativePath;
import com.tyron.builder.api.internal.AbstractTask;
import com.tyron.builder.internal.Cast;
import com.tyron.builder.internal.execution.history.InputChangesInternal;
import com.tyron.builder.api.internal.file.FileCollectionFactory;
import com.tyron.builder.api.internal.file.temp.TemporaryFileProvider;
import com.tyron.builder.internal.hash.ClassLoaderHierarchyHasher;
import com.tyron.builder.internal.hash.Hashes;
import com.tyron.builder.internal.hash.PrimitiveHasher;
import com.tyron.builder.internal.logging.StandardOutputCapture;
import com.tyron.builder.api.internal.project.ProjectInternal;
import com.tyron.builder.api.internal.project.taskfactory.TaskIdentity;
import com.tyron.builder.internal.reflect.service.ServiceRegistry;
import com.tyron.builder.internal.resources.ResourceLock;
import com.tyron.builder.internal.snapshot.impl.ImplementationSnapshot;
import com.tyron.builder.api.internal.tasks.DefaultTaskInputs;
import com.tyron.builder.api.internal.tasks.DefaultTaskOutputs;
import com.tyron.builder.api.internal.tasks.InputChangesAwareTaskAction;
import com.tyron.builder.api.internal.tasks.TaskContainerInternal;
import com.tyron.builder.api.internal.tasks.TaskDestroyablesInternal;
import com.tyron.builder.api.internal.tasks.TaskInputsInternal;
import com.tyron.builder.api.internal.tasks.TaskLocalStateInternal;
import com.tyron.builder.api.internal.tasks.TaskMutator;
import com.tyron.builder.api.internal.tasks.TaskStateInternal;
import com.tyron.builder.api.internal.tasks.properties.PropertyVisitor;
import com.tyron.builder.api.internal.tasks.properties.PropertyWalker;
import com.tyron.builder.api.logging.Logger;
import com.tyron.builder.api.logging.Logging;
import com.tyron.builder.api.logging.LoggingManager;
import com.tyron.builder.api.BuildProject;
import com.tyron.builder.api.internal.tasks.DefaultTaskDependency;
import com.tyron.builder.api.tasks.Internal;
import com.tyron.builder.api.tasks.TaskDependency;
import com.tyron.builder.api.tasks.TaskDestroyables;
import com.tyron.builder.api.tasks.TaskLocalState;
import com.tyron.builder.api.internal.TaskOutputsInternal;
import com.tyron.builder.util.internal.GFileUtils;
import com.tyron.builder.util.Path;
import com.tyron.builder.internal.logging.LoggingManagerInternal;
import com.tyron.builder.internal.logging.slf4j.ContextAwareTaskLogger;
import com.tyron.builder.internal.logging.slf4j.DefaultContextAwareTaskLogger;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import bsh.NameSpace;
import bsh.This;
import bsh.XThis;

public class DefaultTask extends AbstractTask {

    private static final Logger BUILD_LOGGER = Logging.getLogger(Task.class);

    private final TaskStateInternal state;
    private final TaskMutator taskMutator;
    private String name;
    private ServiceRegistry servcices;
    private LoggingManagerInternal loggingManager;
    private final ContextAwareTaskLogger logger = new DefaultContextAwareTaskLogger(BUILD_LOGGER);

    public String toString() {
        return taskIdentity.name;
    }

    private List<InputChangesAwareTaskAction> actions;

    private final DefaultTaskDependency dependencies;

    /**
     * "lifecycle dependencies" are dependencies declared via an explicit {@link Task#dependsOn(Object...)}
     */
    private final DefaultTaskDependency lifecycleDependencies;

    private final DefaultTaskDependency mustRunAfter;
    private final DefaultTaskDependency shouldRunAfter;
    private final TaskDependency finalizedBy;

    private final TaskInputsInternal inputs;
    private final TaskOutputsInternal outputs;

    private final List<? extends ResourceLock> sharedResources = new ArrayList<>();

    private boolean enabled = true;
    private boolean didWork;
    private String description;

    private String group;

    private final TaskIdentity<?> taskIdentity;
    private final ProjectInternal project;
    
    public DefaultTask() {
        this(taskInfo());
    }

    protected DefaultTask(TaskInfo taskInfo) {
        super(taskInfo);
        this.taskIdentity = taskInfo.identity;
        this.name = taskIdentity.name;

        this.project = taskInfo.project;
        this.servcices = project.getServices();

        TaskContainerInternal tasks = (TaskContainerInternal) project.getTasks();

        lifecycleDependencies = new DefaultTaskDependency(tasks);
        mustRunAfter = new DefaultTaskDependency(tasks);
        shouldRunAfter = new DefaultTaskDependency(tasks);
        finalizedBy = new DefaultTaskDependency(tasks);
        dependencies = new DefaultTaskDependency(tasks, ImmutableSet.of(lifecycleDependencies));

        state = new TaskStateInternal();
        taskMutator = new TaskMutator(this);

        PropertyWalker emptyWalker = (instance, validationContext, visitor) -> {

        };
        FileCollectionFactory factory =
                project.getServices().get(FileCollectionFactory.class);
        outputs = new DefaultTaskOutputs(this, taskMutator, emptyWalker, factory);
        inputs = new DefaultTaskInputs(this, taskMutator, emptyWalker, factory);
    }



    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DefaultTask that = (DefaultTask) o;
        return this.taskIdentity.equals(that.taskIdentity);
    }

    @Internal
    protected ServiceRegistry getServices() {
        return servcices;
    }

    @Override
    public int hashCode() {
        return taskIdentity.hashCode();
    }

    @Internal
    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Internal
    @Override
    public List<Action<? super Task>> getActions() {
        if (actions == null) {
            actions = new ArrayList<>();
        }
        return Cast.uncheckedNonnullCast(actions);
    }

    @Override
    public void setActions(List<Action<? super Task>> replacements) {
        taskMutator.mutate("Task.setActions(List<Action>)", new Runnable() {
            @Override
            public void run() {
                getTaskActions().clear();
                for (Action<? super Task> action : replacements) {
                    doLast(action);
                }
            }
        });
    }

    @Internal
    @Override
    public TaskDependency getTaskDependencies() {
        return dependencies;
    }

    @Override
    public Task dependsOn(final Object... paths) {
        lifecycleDependencies.add(paths);
        return this;
    }

    @Internal
    @Override
    public Set<Object> getDependsOn() {
        return lifecycleDependencies.getMutableValues();
    }

    @Override
    public void setDependsOn(Iterable<?> dependsOnTasks) {
        lifecycleDependencies.setValues(dependsOnTasks);
    }

    @Internal
    @Override
    public TaskStateInternal getState() {
        return state;
    }

    @Internal
    @Override
    public StandardOutputCapture getStandardOutputCapture() {
        return loggingManager();
    }

    @Override
    public TaskIdentity<?> getTaskIdentity() {
        return this.taskIdentity;
    }

    @Override
    public Path getIdentityPath() {
        return getTaskIdentity().identityPath;
    }

    @Override
    public void setDidWork(boolean didWork) {
        state.setDidWork(didWork);
    }

    @Internal
    @Override
    public boolean getDidWork() {
        return state.getDidWork();
    }

    @Internal
    @Override
    public String getPath() {
        return taskIdentity.getTaskPath();
    }

    @Override
    public Task doFirst(Action<? super Task> action) {
        return doFirst("doFirst {} action", action);
    }

    @Override
    public Task doFirst(String actionName, Action<? super Task> action) {
        if (action == null) {
            throw new InvalidUserDataException("Action must not be null!");
        }
        taskMutator.mutate("Task.doFirst(Action)", new Runnable() {
            @Override
            public void run() {
                getTaskActions().add(0, wrap(action, actionName));
            }
        });
        return this;
    }

    @Override
    public Task doLast(Action<? super Task> action) {
        return doLast("doLast {} action", action);
    }

    @Override
    public Task doLast(String actionName, Action<? super Task> action) {
        if (action == null) {
            throw new InvalidUserDataException("Action must not be null!");
        }
        taskMutator.mutate("Task.doLast(Action)", () -> {
            getTaskActions().add(wrap(action, actionName));
        });
        return this;
    }

    @Internal
    @Override
    public List<InputChangesAwareTaskAction> getTaskActions() {
        if (actions == null) {
            actions = new ArrayList<>(3);
        }
        return actions;
    }

    @Internal
    @Override
    public boolean getEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Internal
    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public void setDescription(String description) {
        this.description = description;
    }

    @Internal
    @Override
    public String getGroup() {
        return this.group;
    }

    @Override
    public void setGroup(String group) {
        this.group = group;
    }

    @Internal
    @Override
    public TaskInputsInternal getInputs() {
        return inputs;
    }

    @Internal
    @Override
    public TaskOutputsInternal getOutputs() {
        return outputs;
    }

    @Internal
    @Override
    public TaskDestroyables getDestroyables() {
        return new TaskDestroyablesInternal() {
            @Override
            public void visitRegisteredProperties(PropertyVisitor visitor) {

            }

            @Override
            public FileCollection getRegisteredFiles() {
                return null;
            }

            @Override
            public void register(Object... paths) {

            }
        };
    }

    @Internal
    @Override
    public TaskLocalState getLocalState() {
        return new TaskLocalStateInternal() {
            @Override
            public void visitRegisteredProperties(PropertyVisitor visitor) {

            }

            @Override
            public FileCollection getRegisteredFiles() {
                return null;
            }

            @Override
            public void register(Object... paths) {

            }
        };
    }

    @Internal
    @Override
    public File getTemporaryDir() {
        File dir = getServices().get(TemporaryFileProvider.class).newTemporaryFile(getName());
        GFileUtils.mkdirs(dir);
        return dir;
    }

    @Override
    public Task mustRunAfter(Object... paths) {
        this.mustRunAfter.add(paths);
        return this;
    }

    @Override
    public void setMustRunAfter(Iterable<?> mustRunAfter) {
        this.mustRunAfter.setValues(mustRunAfter);
    }

    @Internal
    @Override
    public TaskDependency getMustRunAfter() {
        return mustRunAfter;
    }

    @Override
    public Task finalizedBy(Object... paths) {
        return null;
    }

    @Override
    public void setFinalizedBy(Iterable<?> finalizedBy) {

    }

    @Internal
    @Override
    public TaskDependency getFinalizedBy() {
        return finalizedBy;
    }

    @Override
    public TaskDependency shouldRunAfter(Object... paths) {
        return this.shouldRunAfter.add(paths);
    }

    @Override
    public void setShouldRunAfter(Iterable<?> shouldRunAfter) {
        this.shouldRunAfter.setValues(shouldRunAfter);
    }

    @Internal
    @Override
    public TaskDependency getShouldRunAfter() {
        return shouldRunAfter;
    }

    @Internal
    @Override
    public BuildProject getProject() {
        return project;
    }

    @Internal
    @Override
    public TaskDependency getLifecycleDependencies() {
        return lifecycleDependencies;
    }

    @Override
    public List<? extends ResourceLock> getSharedResources() {
        return sharedResources;
    }

    @Override
    public int compareTo(@NotNull Task task) {
        return 0;
    }

    private InputChangesAwareTaskAction wrap(final Action<? super Task> action) {
        return wrap(action, "unnamed action");
    }

    private InputChangesAwareTaskAction wrap(final Action<? super Task> action, String actionName) {
        if (action instanceof InputChangesAwareTaskAction) {
            return (InputChangesAwareTaskAction) action;
        }
        return new TaskActionWrapper(action, actionName);
    }

    private static class TaskActionWrapper implements InputChangesAwareTaskAction {
        private final Action<? super Task> action;
        private final String maybeActionName;

        /**
         * The <i>action name</i> is used to construct a human readable name for
         * the actions to be used in progress logging. It is only used if
         * the wrapped action does not already implement {@link Describable}.
         */
        public TaskActionWrapper(Action<? super Task> action, String maybeActionName) {
            this.action = action;
            this.maybeActionName = maybeActionName;
        }

        @Override
        public void setInputChanges(InputChangesInternal inputChanges) {
        }

        @Override
        public void clearInputChanges() {
        }

        @Override
        public void execute(Task task) {
            ClassLoader original = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(action.getClass().getClassLoader());
            try {
                action.execute(task);
            } finally {
                Thread.currentThread().setContextClassLoader(original);
            }
        }

        public ImplementationSnapshot getActionImplementation(ClassLoaderHierarchyHasher hasher) {
            return ImplementationSnapshot.of(getActionClassName(action), hasher.getClassLoaderHash(action.getClass().getClassLoader()));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof TaskActionWrapper)) {
                return false;
            }

            TaskActionWrapper that = (TaskActionWrapper) o;
            return action.equals(that.action);
        }

        @Override
        public int hashCode() {
            return action.hashCode();
        }

        @Override
        public String getDisplayName() {
            if (action instanceof Describable) {
                return ((Describable) action).getDisplayName();
            }
            return "Execute " + maybeActionName;
        }
    }

    private static String getActionClassName(Object action) {
//        if (action instanceof ScriptOrigin) {
//            ScriptOrigin origin = (ScriptOrigin) action;
//            return origin.getOriginalClassName() + "_" + origin.getContentHash();
//        } else {
//
//        }

        if (Proxy.isProxyClass(action.getClass())) {
            InvocationHandler invocationHandler = Proxy.getInvocationHandler(action);
            if (invocationHandler.getClass().getName().equals("bsh.XThis$Handler")) {
                return BeanShellUtils.getAnonymousName(invocationHandler);
            }
        }

        return action.getClass().getName();
    }

    private static class BeanShellUtils {

        private static String getAnonymousName(InvocationHandler handler) {
            NameSpace namespace = getNamespace(handler);
            NameSpace root = getRootNamespace(namespace);
            return "";
        }

        private static NameSpace getRootNamespace(NameSpace nameSpace) {
            NameSpace current = nameSpace;
            while (current.getParent() != null) {
                current = current.getParent();
            }
            return current;
        }

        private static NameSpace getNamespace(InvocationHandler handler) {
            try {
                Field this$0 = handler.getClass().getDeclaredField("this$0");
                this$0.setAccessible(true);
                Object o = this$0.get(handler);

                Field namespace = This.class.getDeclaredField("namespace");
                namespace.setAccessible(true);
                return (NameSpace) namespace.get(o);
            } catch (ReflectiveOperationException e) {
                throw new Error(e);
            }
        }
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public LoggingManager getLogging() {
        return loggingManager;
    }

    private LoggingManagerInternal loggingManager() {
        if (loggingManager == null) {
            loggingManager = servcices.getFactory(LoggingManagerInternal.class).create();
        }
        return loggingManager;
    }
}
