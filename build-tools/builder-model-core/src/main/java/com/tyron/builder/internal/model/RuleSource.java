package com.tyron.builder.internal.model;

/**
 * A marker type for a class that is a collection of rules.
 * <p>
 * A rule source is not used like a regular Java object.
 * It is a stateless container of methods and possibly constants.
 * <p>
 * Please consult the “Rule based model configuration” chapter of the Gradle User Manual for general information about “rules”.
 *
 * <h3>Rule methods</h3>
 * <p>
 * Each method that is annotated with one of the following is considered a rule:
 * <ul>
 * <li>{@link Model}</li>
 * <li>{@link Defaults}</li>
 * <li>{@link Mutate}</li>
 * <li>{@link Finalize}</li>
 * <li>{@link Validate}</li>
 * <li>{@link Rules}</li>
 * <li>{@link org.gradle.platform.base.ComponentType}</li>
 * <li>{@link org.gradle.platform.base.ComponentBinaries}</li>
 * <li>{@link org.gradle.platform.base.BinaryTasks}</li>
 * </ul>
 * <p>
 * Each annotation specifies the type of the rule, which affects when it will be executed.
 * <p>
 * The following constraints apply to all rule methods:
 * <ul>
 * <li>A method may only be annotated by at most one of the above annotations.</li>
 * <li>A rule method may be {@code static} or not; it makes no difference.</li>
 * <li>A rule method cannot be generic (i.e. cannot have type parameters).</li>
 * <li>With the exception of {@link Model} methods, all methods must have at least one parameter.</li>
 * <li>With the exception of {@link Model} methods, all methods must have a {@code void} return type.</li>
 * </ul>
 * <p>
 * See {@link Model} for information on the significance of the return type of a {@link Model} method.
 *
 * <h4>Subjects and inputs</h4>
 * <p>
 * Method rules declare the subject and any inputs as parameters to the method.
 * With the exception of {@link Model} methods, the subject of the rule is the, required, first parameter and all subsequent parameters are inputs.
 * For a non-void {@link Model} method, the subject (i.e. model element being created) is the return object.
 * For a void {@link Model} method, the subject is the first method parameter.
 * <p>
 * The {@link Path} annotation can be placed on any parameter (except the subject of {@link Model} rules) to indicate the model element to bind to.
 * If there is no {@link Path} annotation, a “by-type” binding will be attempted.
 * The binding scope is determined by how the rule source is applied.
 *
 * <h3>General class constraints</h3>
 * <p>
 * Along with the constraints on individual rule methods by their associated annotation, the following are general constraints of rule source implementations:
 * <ul>
 * <li>Constructors are not allowed.</li>
 * <li>Inheritance hierarchies are not allowed (i.e. all rules sources must directly extend {@link RuleSource}).</li>
 * <li>Instance variables are not allowed.</li>
 * <li>Non-final static variables are not allowed (i.e. constants are allowed).</li>
 * <li>Methods cannot be overloaded.</li>
 * <li>Implementations cannot be generic (i.e. cannot use type parameters).</li>
 * </ul>
 */
public class RuleSource {
}