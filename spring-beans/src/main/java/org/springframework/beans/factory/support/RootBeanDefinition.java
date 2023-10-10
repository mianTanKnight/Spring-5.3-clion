/*
 * Copyright 2002-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.beans.factory.support;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;

import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.core.ResolvableType;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * A root bean definition represents the merged bean definition that backs
 * a specific bean in a Spring BeanFactory at runtime. It might have been created
 * from multiple original bean definitions that inherit from each other,
 * typically registered as {@link GenericBeanDefinition GenericBeanDefinitions}.
 * A root bean definition is essentially the 'unified' bean definition view at runtime.
 *
 * <p>Root bean definitions may also be used for registering individual bean definitions
 * in the configuration phase. However, since Spring 2.5, the preferred way to register
 * bean definitions programmatically is the {@link GenericBeanDefinition} class.
 * GenericBeanDefinition has the advantage that it allows to dynamically define
 * parent dependencies, not 'hard-coding' the role as a root bean definition.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @see GenericBeanDefinition
 * @see ChildBeanDefinition
 * bean的生命周期
 * RootBean 通常是指没有父类的bean
 */
@SuppressWarnings("serial")
public class RootBeanDefinition extends AbstractBeanDefinition {

	@Nullable
	private BeanDefinitionHolder decoratedDefinition; //beanDefinition包装类

	@Nullable
	private AnnotatedElement qualifiedElement; //获得其注解信息

	/**
	 * Determines if the definition needs to be re-merged.
	 * 在 RootBeanDefinition 中的 stale 字段表示这个 BeanDefinition 是否已经过时或不再是最新的。
	 * 一般情况下，当一个 BeanDefinition 在某个地方被修改时，
	 * 可能会将这个 stale 设置为 true，表明该定义可能已经不再是最新的或已经被其他定义覆盖。
	 *
	 * */
	volatile boolean stale;

	//是否允许cache
	boolean allowCaching = true;

	//表示beanFactory的Method的唯一性
	boolean isFactoryMethodUnique;

	@Nullable
	volatile ResolvableType targetType;

	/**
	 *  Package-visible field for caching the determined Class of a given bean definition.
	 *  根据bean定义确定的class缓存,包可见字段
	 * */
	@Nullable
	volatile Class<?> resolvedTargetType;

	/**
	 * Package-visible field for caching if the bean is a factory bean.
	 * 是否是factoryBean
	 * */
	@Nullable
	volatile Boolean isFactoryBean;

	/**
	 * Package-visible field for caching the return type of a generically typed factory method.
	 *
	 * */
	@Nullable
	volatile ResolvableType factoryMethodReturnType;

	/**
	 * Package-visible field for caching a unique factory method candidate for introspection.
	 *
	 * */
	@Nullable
	volatile Method factoryMethodToIntrospect;

	/** Package-visible field for caching a resolved destroy method name (also for inferred). */
	@Nullable
	volatile String resolvedDestroyMethodName;

	/** Common lock for the four constructor fields below. */
	final Object constructorArgumentLock = new Object();

	/**
	 *  Package-visible field for caching the resolved constructor or factory method.
	 * Excutable是java.lang.reflect下的抽象类，也是Method和Constructor类的超类，可以看下jdk文档的注释
	 * 获得类信息
	 *
	 * */
	@Nullable
	Executable resolvedConstructorOrFactoryMethod;

	/**
	 *  Package-visible field that marks the constructor arguments as resolved.
	 *  这个字段用于在 Spring 框架内部追踪构造函数参数的解析状态。
	 *  在 Spring 容器初始化和处理 Bean 时，需要确保构造函数参数的解析是正确完成的。这个字段帮助 Spring 跟踪是否已经完成了构造函数参数的解析
	 * */
	boolean constructorArgumentsResolved = false;

	/**
	 * Package-visible field for caching fully resolved constructor arguments.
	 * 解析出来的构造参数
	 * */
	@Nullable
	Object[] resolvedConstructorArguments;

	/**
	 * Package-visible field for caching partly prepared constructor arguments.
	 * 部分已经准备好的参数
	 * */
	@Nullable
	Object[] preparedConstructorArguments;

	/**
	 * Common lock for the two post-processing fields below.
	 * 后置处理锁
	 *  */
	final Object postProcessingLock = new Object();

	/** Package-visible field that indicates MergedBeanDefinitionPostProcessor having been applied. */
	boolean postProcessed = false;

	/**
     * 当beforeInstantiationResolved设置为true，这表示已经调用过postProcessBeforeInstantiation方法并确定了用于实例化的策略。
	 * 当其设置为false或null，表示还未解析是否需要跳过默认的实例化过程。
	 * 实际上，这个字段起到了一个“标记”的作用，以避免重复调用postProcessBeforeInstantiation方法和确定如何实例化bean
	 *
	 *
	 * 确实，postProcessBeforeInstantiation 提供了一个强大的机制，允许开发者在Spring的标准实例化过程之前介入。
	 * 在深入了解这个机制之前，先了解一下Spring Bean的标准实例化流程：
	 * 解析Bean定义：首先，Spring需要解析bean的定义，了解如何创建这个bean，包括所需的构造函数参数、工厂方法等。
	 * 解析依赖：如果bean依赖其他bean，Spring会解析这些依赖并确保它们首先被初始化。
	 * 实例化：此时，Spring会通过反射使用bean的构造函数或工厂方法进行实例化。
	 * InstantiationAwareBeanPostProcessor 的 postProcessBeforeInstantiation 方法是在第3步之前介入的。
	 * 如果该方法返回一个非 null 对象，Spring会使用这个对象作为bean的实例，而不再走标准的反射实例化流程。
	 * 这有什么用呢？
	 * 代理创建：最常见的用例是为了创建代理对象。例如，当使用Spring AOP进行代理时，我们可以在实例化之前就返回一个代理对象。
	 * 条件实例化：基于某些条件，可以决定使用哪个对象作为bean实例。
	 * 外部服务注入：例如，你可以决定从一个外部服务中获取一个对象并返回，而不是在本地创建。
	 * 为了实现这种介入，postProcessBeforeInstantiation 可以：
	 *
	 * 返回一个新的对象（如上面所述的代理对象或从外部服务获取的对象）。
	 * 返回 null 以继续标准的实例化流程。
	 * 现在，回到你提到的 beforeInstantiationResolved。这个字段主要是为了确保 postProcessBeforeInstantiation 只被调用一次，防止重复调用和重复决策，以保持整个实例化过程的效率和一致性。
	 *  */
	@Nullable
	volatile Boolean beforeInstantiationResolved;

	@Nullable
	private Set<Member> externallyManagedConfigMembers;

	@Nullable
	private Set<String> externallyManagedInitMethods;

	@Nullable
	private Set<String> externallyManagedDestroyMethods;


	/**
	 * Create a new RootBeanDefinition, to be configured through its bean
	 * properties and configuration methods.
	 * @see #setBeanClass
	 * @see #setScope
	 * @see #setConstructorArgumentValues
	 * @see #setPropertyValues
	 */
	public RootBeanDefinition() {
		super();
	}

	/**
	 * Create a new RootBeanDefinition for a singleton.
	 * @param beanClass the class of the bean to instantiate
	 * @see #setBeanClass
	 */
	public RootBeanDefinition(@Nullable Class<?> beanClass) {
		super();
		setBeanClass(beanClass);
	}

	/**
	 * Create a new RootBeanDefinition for a singleton bean, constructing each instance
	 * through calling the given supplier (possibly a lambda or method reference).
	 * @param beanClass the class of the bean to instantiate
	 * @param instanceSupplier the supplier to construct a bean instance,
	 * as an alternative to a declaratively specified factory method
	 * @since 5.0
	 * @see #setInstanceSupplier
	 */
	public <T> RootBeanDefinition(@Nullable Class<T> beanClass, @Nullable Supplier<T> instanceSupplier) {
		super();
		setBeanClass(beanClass);
		setInstanceSupplier(instanceSupplier);
	}

	/**
	 * Create a new RootBeanDefinition for a scoped bean, constructing each instance
	 * through calling the given supplier (possibly a lambda or method reference).
	 * @param beanClass the class of the bean to instantiate
	 * @param scope the name of the corresponding scope
	 * @param instanceSupplier the supplier to construct a bean instance,
	 * as an alternative to a declaratively specified factory method
	 * @since 5.0
	 * @see #setInstanceSupplier
	 */
	public <T> RootBeanDefinition(@Nullable Class<T> beanClass, String scope, @Nullable Supplier<T> instanceSupplier) {
		super();
		setBeanClass(beanClass);
		setScope(scope);
		setInstanceSupplier(instanceSupplier);
	}

	/**
	 * Create a new RootBeanDefinition for a singleton,
	 * using the given autowire mode.
	 * @param beanClass the class of the bean to instantiate
	 * @param autowireMode by name or type, using the constants in this interface
	 * @param dependencyCheck whether to perform a dependency check for objects
	 * (not applicable to autowiring a constructor, thus ignored there)
	 */
	public RootBeanDefinition(@Nullable Class<?> beanClass, int autowireMode, boolean dependencyCheck) {
		super();
		setBeanClass(beanClass);
		setAutowireMode(autowireMode);
		if (dependencyCheck && getResolvedAutowireMode() != AUTOWIRE_CONSTRUCTOR) {
			setDependencyCheck(DEPENDENCY_CHECK_OBJECTS);
		}
	}

	/**
	 * Create a new RootBeanDefinition for a singleton,
	 * providing constructor arguments and property values.
	 * @param beanClass the class of the bean to instantiate
	 * @param cargs the constructor argument values to apply
	 * @param pvs the property values to apply
	 */
	public RootBeanDefinition(@Nullable Class<?> beanClass, @Nullable ConstructorArgumentValues cargs,
			@Nullable MutablePropertyValues pvs) {

		super(cargs, pvs);
		setBeanClass(beanClass);
	}

	/**
	 * Create a new RootBeanDefinition for a singleton,
	 * providing constructor arguments and property values.
	 * <p>Takes a bean class name to avoid eager loading of the bean class.
	 * @param beanClassName the name of the class to instantiate
	 */
	public RootBeanDefinition(String beanClassName) {
		setBeanClassName(beanClassName);
	}

	/**
	 * Create a new RootBeanDefinition for a singleton,
	 * providing constructor arguments and property values.
	 * <p>Takes a bean class name to avoid eager loading of the bean class.
	 * @param beanClassName the name of the class to instantiate
	 * @param cargs the constructor argument values to apply
	 * @param pvs the property values to apply
	 */
	public RootBeanDefinition(String beanClassName, ConstructorArgumentValues cargs, MutablePropertyValues pvs) {
		super(cargs, pvs);
		setBeanClassName(beanClassName);
	}

	/**
	 * Create a new RootBeanDefinition as deep copy of the given
	 * bean definition.
	 * @param original the original bean definition to copy from
	 */
	public RootBeanDefinition(RootBeanDefinition original) {
		super(original);
		this.decoratedDefinition = original.decoratedDefinition;
		this.qualifiedElement = original.qualifiedElement;
		this.allowCaching = original.allowCaching;
		this.isFactoryMethodUnique = original.isFactoryMethodUnique;
		this.targetType = original.targetType;
		this.factoryMethodToIntrospect = original.factoryMethodToIntrospect;
	}

	/**
	 * Create a new RootBeanDefinition as deep copy of the given
	 * bean definition.
	 * @param original the original bean definition to copy from
	 */
	RootBeanDefinition(BeanDefinition original) {
		super(original);
	}


	@Override
	public String getParentName() {
		return null;
	}

	@Override
	public void setParentName(@Nullable String parentName) {
		if (parentName != null) {
			throw new IllegalArgumentException("Root bean cannot be changed into a child bean with parent reference");
		}
	}

	/**
	 * Register a target definition that is being decorated by this bean definition.
	 */
	public void setDecoratedDefinition(@Nullable BeanDefinitionHolder decoratedDefinition) {
		this.decoratedDefinition = decoratedDefinition;
	}

	/**
	 * Return the target definition that is being decorated by this bean definition, if any.
	 */
	@Nullable
	public BeanDefinitionHolder getDecoratedDefinition() {
		return this.decoratedDefinition;
	}

	/**
	 * Specify the {@link AnnotatedElement} defining qualifiers,
	 * to be used instead of the target class or factory method.
	 * @since 4.3.3
	 * @see #setTargetType(ResolvableType)
	 * @see #getResolvedFactoryMethod()
	 */
	public void setQualifiedElement(@Nullable AnnotatedElement qualifiedElement) {
		this.qualifiedElement = qualifiedElement;
	}

	/**
	 * Return the {@link AnnotatedElement} defining qualifiers, if any.
	 * Otherwise, the factory method and target class will be checked.
	 * @since 4.3.3
	 */
	@Nullable
	public AnnotatedElement getQualifiedElement() {
		return this.qualifiedElement;
	}

	/**
	 * Specify a generics-containing target type of this bean definition, if known in advance.
	 * @since 4.3.3
	 */
	public void setTargetType(ResolvableType targetType) {
		this.targetType = targetType;
	}

	/**
	 * Specify the target type of this bean definition, if known in advance.
	 * @since 3.2.2
	 */
	public void setTargetType(@Nullable Class<?> targetType) {
		this.targetType = (targetType != null ? ResolvableType.forClass(targetType) : null);
	}

	/**
	 * Return the target type of this bean definition, if known
	 * (either specified in advance or resolved on first instantiation).
	 * @since 3.2.2
	 */
	@Nullable
	public Class<?> getTargetType() {
		if (this.resolvedTargetType != null) {
			return this.resolvedTargetType;
		}
		ResolvableType targetType = this.targetType;
		return (targetType != null ? targetType.resolve() : null);
	}

	/**
	 * Return a {@link ResolvableType} for this bean definition,
	 * either from runtime-cached type information or from configuration-time
	 * {@link #setTargetType(ResolvableType)} or {@link #setBeanClass(Class)},
	 * also considering resolved factory method definitions.
	 * @since 5.1
	 * @see #setTargetType(ResolvableType)
	 * @see #setBeanClass(Class)
	 * @see #setResolvedFactoryMethod(Method)
	 */
	@Override
	public ResolvableType getResolvableType() {
		ResolvableType targetType = this.targetType;
		if (targetType != null) {
			return targetType;
		}
		ResolvableType returnType = this.factoryMethodReturnType;
		if (returnType != null) {
			return returnType;
		}
		Method factoryMethod = this.factoryMethodToIntrospect;
		if (factoryMethod != null) {
			return ResolvableType.forMethodReturnType(factoryMethod);
		}
		return super.getResolvableType();
	}

	/**
	 * Determine preferred constructors to use for default construction, if any.
	 * Constructor arguments will be autowired if necessary.
	 * @return one or more preferred constructors, or {@code null} if none
	 * (in which case the regular no-arg default constructor will be called)
	 * @since 5.1
	 */
	@Nullable
	public Constructor<?>[] getPreferredConstructors() {
		return null;
	}

	/**
	 * Specify a factory method name that refers to a non-overloaded method.
	 */
	public void setUniqueFactoryMethodName(String name) {
		Assert.hasText(name, "Factory method name must not be empty");
		setFactoryMethodName(name);
		this.isFactoryMethodUnique = true;
	}

	/**
	 * Specify a factory method name that refers to an overloaded method.
	 * @since 5.2
	 */
	public void setNonUniqueFactoryMethodName(String name) {
		Assert.hasText(name, "Factory method name must not be empty");
		setFactoryMethodName(name);
		this.isFactoryMethodUnique = false;
	}

	/**
	 * Check whether the given candidate qualifies as a factory method.
	 */
	public boolean isFactoryMethod(Method candidate) {
		return candidate.getName().equals(getFactoryMethodName());
	}

	/**
	 * Set a resolved Java Method for the factory method on this bean definition.
	 * @param method the resolved factory method, or {@code null} to reset it
	 * @since 5.2
	 */
	public void setResolvedFactoryMethod(@Nullable Method method) {
		this.factoryMethodToIntrospect = method;
	}

	/**
	 * Return the resolved factory method as a Java Method object, if available.
	 * @return the factory method, or {@code null} if not found or not resolved yet
	 */
	@Nullable
	public Method getResolvedFactoryMethod() {
		return this.factoryMethodToIntrospect;
	}

	/**
	 * Register an externally managed configuration method or field.
	 */
	public void registerExternallyManagedConfigMember(Member configMember) {
		synchronized (this.postProcessingLock) {
			if (this.externallyManagedConfigMembers == null) {
				this.externallyManagedConfigMembers = new LinkedHashSet<>(1);
			}
			this.externallyManagedConfigMembers.add(configMember);
		}
	}

	/**
	 * Determine if the given method or field is an externally managed configuration member.
	 */
	public boolean isExternallyManagedConfigMember(Member configMember) {
		synchronized (this.postProcessingLock) {
			return (this.externallyManagedConfigMembers != null &&
					this.externallyManagedConfigMembers.contains(configMember));
		}
	}

	/**
	 * Get all externally managed configuration methods and fields (as an immutable Set).
	 * @since 5.3.11
	 */
	public Set<Member> getExternallyManagedConfigMembers() {
		synchronized (this.postProcessingLock) {
			return (this.externallyManagedConfigMembers != null ?
					Collections.unmodifiableSet(new LinkedHashSet<>(this.externallyManagedConfigMembers)) :
					Collections.emptySet());
		}
	}

	/**
	 * Register an externally managed configuration initialization method &mdash;
	 * for example, a method annotated with JSR-250's
	 * {@link javax.annotation.PostConstruct} annotation.
	 * <p>The supplied {@code initMethod} may be the
	 * {@linkplain Method#getName() simple method name} for non-private methods or the
	 * {@linkplain org.springframework.util.ClassUtils#getQualifiedMethodName(Method)
	 * qualified method name} for {@code private} methods. A qualified name is
	 * necessary for {@code private} methods in order to disambiguate between
	 * multiple private methods with the same name within a class hierarchy.
	 */
	public void registerExternallyManagedInitMethod(String initMethod) {
		synchronized (this.postProcessingLock) {
			if (this.externallyManagedInitMethods == null) {
				this.externallyManagedInitMethods = new LinkedHashSet<>(1);
			}
			this.externallyManagedInitMethods.add(initMethod);
		}
	}

	/**
	 * Determine if the given method name indicates an externally managed
	 * initialization method.
	 * <p>See {@link #registerExternallyManagedInitMethod} for details
	 * regarding the format for the supplied {@code initMethod}.
	 */
	public boolean isExternallyManagedInitMethod(String initMethod) {
		synchronized (this.postProcessingLock) {
			return (this.externallyManagedInitMethods != null &&
					this.externallyManagedInitMethods.contains(initMethod));
		}
	}

	/**
	 * Determine if the given method name indicates an externally managed
	 * initialization method, regardless of method visibility.
	 * <p>In contrast to {@link #isExternallyManagedInitMethod(String)}, this
	 * method also returns {@code true} if there is a {@code private} externally
	 * managed initialization method that has been
	 * {@linkplain #registerExternallyManagedInitMethod(String) registered}
	 * using a qualified method name instead of a simple method name.
	 * @since 5.3.17
	 */
	boolean hasAnyExternallyManagedInitMethod(String initMethod) {
		synchronized (this.postProcessingLock) {
			if (isExternallyManagedInitMethod(initMethod)) {
				return true;
			}
			return hasAnyExternallyManagedMethod(this.externallyManagedInitMethods, initMethod);
		}
	}

	/**
	 * Get all externally managed initialization methods (as an immutable Set).
	 * <p>See {@link #registerExternallyManagedInitMethod} for details
	 * regarding the format for the initialization methods in the returned set.
	 * @since 5.3.11
	 */
	public Set<String> getExternallyManagedInitMethods() {
		synchronized (this.postProcessingLock) {
			return (this.externallyManagedInitMethods != null ?
					Collections.unmodifiableSet(new LinkedHashSet<>(this.externallyManagedInitMethods)) :
					Collections.emptySet());
		}
	}

	/**
	 * Register an externally managed configuration destruction method &mdash;
	 * for example, a method annotated with JSR-250's
	 * {@link javax.annotation.PreDestroy} annotation.
	 * <p>The supplied {@code destroyMethod} may be the
	 * {@linkplain Method#getName() simple method name} for non-private methods or the
	 * {@linkplain org.springframework.util.ClassUtils#getQualifiedMethodName(Method)
	 * qualified method name} for {@code private} methods. A qualified name is
	 * necessary for {@code private} methods in order to disambiguate between
	 * multiple private methods with the same name within a class hierarchy.
	 */
	public void registerExternallyManagedDestroyMethod(String destroyMethod) {
		synchronized (this.postProcessingLock) {
			if (this.externallyManagedDestroyMethods == null) {
				this.externallyManagedDestroyMethods = new LinkedHashSet<>(1);
			}
			this.externallyManagedDestroyMethods.add(destroyMethod);
		}
	}

	/**
	 * Determine if the given method name indicates an externally managed
	 * destruction method.
	 * <p>See {@link #registerExternallyManagedDestroyMethod} for details
	 * regarding the format for the supplied {@code destroyMethod}.
	 */
	public boolean isExternallyManagedDestroyMethod(String destroyMethod) {
		synchronized (this.postProcessingLock) {
			return (this.externallyManagedDestroyMethods != null &&
					this.externallyManagedDestroyMethods.contains(destroyMethod));
		}
	}

	/**
	 * Determine if the given method name indicates an externally managed
	 * destruction method, regardless of method visibility.
	 * <p>In contrast to {@link #isExternallyManagedDestroyMethod(String)}, this
	 * method also returns {@code true} if there is a {@code private} externally
	 * managed destruction method that has been
	 * {@linkplain #registerExternallyManagedDestroyMethod(String) registered}
	 * using a qualified method name instead of a simple method name.
	 * @since 5.3.17
	 */
	boolean hasAnyExternallyManagedDestroyMethod(String destroyMethod) {
		synchronized (this.postProcessingLock) {
			if (isExternallyManagedDestroyMethod(destroyMethod)) {
				return true;
			}
			return hasAnyExternallyManagedMethod(this.externallyManagedDestroyMethods, destroyMethod);
		}
	}

	private static boolean hasAnyExternallyManagedMethod(Set<String> candidates, String methodName) {
		if (candidates != null) {
			for (String candidate : candidates) {
				int indexOfDot = candidate.lastIndexOf('.');
				if (indexOfDot > 0) {
					String candidateMethodName = candidate.substring(indexOfDot + 1);
					if (candidateMethodName.equals(methodName)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * Get all externally managed destruction methods (as an immutable Set).
	 * <p>See {@link #registerExternallyManagedDestroyMethod} for details
	 * regarding the format for the destruction methods in the returned set.
	 * @since 5.3.11
	 */
	public Set<String> getExternallyManagedDestroyMethods() {
		synchronized (this.postProcessingLock) {
			return (this.externallyManagedDestroyMethods != null ?
					Collections.unmodifiableSet(new LinkedHashSet<>(this.externallyManagedDestroyMethods)) :
					Collections.emptySet());
		}
	}


	@Override
	public RootBeanDefinition cloneBeanDefinition() {
		return new RootBeanDefinition(this);
	}

	@Override
	public boolean equals(@Nullable Object other) {
		return (this == other || (other instanceof RootBeanDefinition && super.equals(other)));
	}

	@Override
	public String toString() {
		return "Root bean: " + super.toString();
	}

}
