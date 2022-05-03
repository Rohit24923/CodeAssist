//package com.tyron.builder.api.internal.artifacts.transform;
//
//import com.google.common.collect.ImmutableList;
//import com.tyron.builder.api.Action;
//import com.tyron.builder.api.internal.DomainObjectContext;
//import com.tyron.builder.api.internal.attributes.ImmutableAttributes;
//import com.tyron.builder.api.internal.project.ProjectInternal;
//import com.tyron.builder.api.internal.tasks.NodeExecutionContext;
//import com.tyron.builder.api.internal.tasks.TaskDependencyContainer;
//import com.tyron.builder.api.internal.tasks.TaskDependencyResolveContext;
//import com.tyron.builder.internal.Try;
//import com.tyron.builder.internal.execution.fingerprint.InputFingerprinter;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import javax.annotation.Nullable;
//import java.io.File;
//
///**
// * A single transformation step.
// *
// * Transforms a subject by invoking a transformer on each of the subjects files.
// */
//public class TransformationStep implements Transformation, TaskDependencyContainer {
//    private static final Logger LOGGER = LoggerFactory.getLogger(TransformationStep.class);
//
//    private final Transformer transformer;
//    private final TransformerInvocationFactory transformerInvocationFactory;
//    private final ProjectInternal owningProject;
//    private final InputFingerprinter globalInputFingerprinter;
//
//    public TransformationStep(Transformer transformer, TransformerInvocationFactory transformerInvocationFactory, DomainObjectContext owner, InputFingerprinter globalInputFingerprinter) {
//        this.transformer = transformer;
//        this.transformerInvocationFactory = transformerInvocationFactory;
//        this.globalInputFingerprinter = globalInputFingerprinter;
//        this.owningProject = owner.getProject();
//    }
//
//    public Transformer getTransformer() {
//        return transformer;
//    }
//
//    @Nullable
//    public ProjectInternal getOwningProject() {
//        return owningProject;
//    }
//
//    @Override
//    public boolean endsWith(Transformation otherTransform) {
//        return this == otherTransform;
//    }
//
//    @Override
//    public int stepsCount() {
//        return 1;
//    }
//
//    public CacheableInvocation<TransformationSubject> createInvocation(TransformationSubject subjectToTransform, TransformUpstreamDependencies upstreamDependencies, @Nullable NodeExecutionContext context) {
//        if (LOGGER.isInfoEnabled()) {
//            LOGGER.info("Transforming {} with {}", subjectToTransform.getDisplayName(), transformer.getDisplayName());
//        }
//
//        InputFingerprinter inputFingerprinter = context != null ? context.getService(InputFingerprinter.class) : globalInputFingerprinter;
//
//        Try<ArtifactTransformDependencies> resolvedDependencies = upstreamDependencies.computeArtifacts();
//        return resolvedDependencies
//            .map(dependencies -> {
//                ImmutableList<File> inputArtifacts = subjectToTransform.getFiles();
//                if (inputArtifacts.isEmpty()) {
//                    return CacheableInvocation.cached(Try.successful(subjectToTransform.createSubjectFromResult(ImmutableList.of())));
//                } else if (inputArtifacts.size() > 1) {
//                    return CacheableInvocation.nonCached(() ->
//                        doTransform(subjectToTransform, inputFingerprinter, dependencies, inputArtifacts)
//                    );
//                } else {
//                    File inputArtifact = inputArtifacts.get(0);
//                    return transformerInvocationFactory.createInvocation(transformer, inputArtifact, dependencies, subjectToTransform, inputFingerprinter)
//                        .map(subjectToTransform::createSubjectFromResult);
//                }
//            })
//            .getOrMapFailure(failure -> CacheableInvocation.cached(Try.failure(failure)));
//    }
//
//    private Try<TransformationSubject> doTransform(TransformationSubject subjectToTransform, InputFingerprinter inputFingerprinter, ArtifactTransformDependencies dependencies, ImmutableList<File> inputArtifacts) {
//        ImmutableList.Builder<File> builder = ImmutableList.builder();
//        for (File inputArtifact : inputArtifacts) {
//            Try<ImmutableList<File>> result = transformerInvocationFactory.createInvocation(transformer, inputArtifact, dependencies, subjectToTransform, inputFingerprinter).invoke();
//
//            if (result.getFailure().isPresent()) {
//                return Try.failure(result.getFailure().get());
//            }
//            builder.addAll(result.get());
//        }
//        return Try.successful(subjectToTransform.createSubjectFromResult(builder.build()));
//    }
//
//    public void isolateParametersIfNotAlready() {
//        transformer.isolateParametersIfNotAlready();
//    }
//
//    @Override
//    public boolean requiresDependencies() {
//        return transformer.requiresDependencies();
//    }
//
//    @Override
//    public String getDisplayName() {
//        return transformer.getDisplayName();
//    }
//
//    @Override
//    public void visitTransformationSteps(Action<? super TransformationStep> action) {
//        action.execute(this);
//    }
//
//    public ImmutableAttributes getFromAttributes() {
//        return transformer.getFromAttributes();
//    }
//
//    @Override
//    public String toString() {
//        return transformer.getDisplayName();
//    }
//
//    @Override
//    public void visitDependencies(TaskDependencyResolveContext context) {
//        transformer.visitDependencies(context);
//    }
//}
