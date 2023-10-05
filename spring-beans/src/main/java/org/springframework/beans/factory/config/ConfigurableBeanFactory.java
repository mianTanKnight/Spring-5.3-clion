/*
 * Copyright 2002-2020 the original author or authors.
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

package org.springframework.beans.factory.config;

import java.beans.PropertyEditor;
import java.security.AccessControlContext;

import org.springframework.beans.PropertyEditorRegistrar;
import org.springframework.beans.PropertyEditorRegistry;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.HierarchicalBeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.lang.Nullable;
import org.springframework.util.StringValueResolver;

/**
 * Configuration interface to be implemented by most bean factories. Provides
 * facilities to configure a bean factory, in addition to the bean factory
 * client methods in the {@link org.springframework.beans.factory.BeanFactory}
 * interface.
 *
 * <p>This bean factory interface is not meant to be used in normal application
 * code: Stick to {@link org.springframework.beans.factory.BeanFactory} or
 * {@link org.springframework.beans.factory.ListableBeanFactory} for typical
 * needs. This extended interface is just meant to allow for framework-internal
 * plug'n'play and for special access to bean factory configuration methods.
 *
 * ConfigurableBeanFactory 接口在 Spring 框架中提供了一个高级别的抽象，它包含了对 BeanFactory 的各种配置选项。
 * 它定义了如何管理和操纵 beans，如何处理它们的依赖关系，如何进行类型转换，如何处理各种不同的 bean 作用域等等。
 *
 * 下面总结一下 ConfigurableBeanFactory 提供的主要功能：
 *
 * 两种类加载器：
 * 它允许您指定一个自定义的类加载器来加载 bean 类。
 * 它还提供了一个临时类加载器，用于特定的加载场景。
 * 解析和转换：
 * 提供了解析表达式和类型转换的功能。
 * 允许您设置自定义的类型转换器 (ConversionService) 和自定义的属性编辑器 (PropertyEditorRegistrar)。
 * 处理 bean 实例：
 * 提供了管理 bean 生命周期的方法，包括初始化和销毁的回调方法。
 * 允许您注册 BeanPostProcessor，这些处理器可以在 bean 初始化的不同阶段进行拦截和操作。
 * 依赖管理：
 * 允许您查询和管理 bean 之间的依赖关系。
 * 提供了检查和处理循环依赖的机制。
 * 作用域管理：
 * 允许您注册自定义的作用域。
 * 提供了处理不同作用域 bean 的机制（例如单例和原型作用域）。
 * 其他配置和服务：
 * 允许您设置其他相关服务，比如 AutowireCandidateResolver 和 BeanExpressionResolver 等。
 * 提供了各种配置选项，如是否缓存 bean 元数据，是否允许循环引用等。
 * 通过这些功能，ConfigurableBeanFactory 提供了一种灵活且强大的方式来管理 Spring 容器中的 beans 和它们之间的关系。
 *
 * @author Juergen Hoeller
 * @since 03.11.2003
 * @see org.springframework.beans.factory.BeanFactory
 * @see org.springframework.beans.factory.ListableBeanFactory
 * @see ConfigurableListableBeanFactory
 */
public interface ConfigurableBeanFactory extends HierarchicalBeanFactory, SingletonBeanRegistry {

	/**
	 * Scope identifier for the standard singleton scope: {@value}.
	 * <p>Custom scopes can be added via {@code registerScope}.
	 * @see #registerScope
	 */
	String SCOPE_SINGLETON = "singleton";

	/**
	 * Scope identifier for the standard prototype scope: {@value}.
	 * <p>Custom scopes can be added via {@code registerScope}.
	 * @see #registerScope
	 */
	String SCOPE_PROTOTYPE = "prototype";


	/**
	 * Set the parent of this bean factory.
	 * <p>Note that the parent cannot be changed: It should only be set outside
	 * a constructor if it isn't available at the time of factory instantiation.
	 * @param parentBeanFactory the parent BeanFactory
	 * @throws IllegalStateException if this factory is already associated with
	 * a parent BeanFactory
	 * @see #getParentBeanFactory()
	 */
	void setParentBeanFactory(BeanFactory parentBeanFactory) throws IllegalStateException;

	/**
	 * Set the class loader to use for loading bean classes.
	 * Default is the thread context class loader.
	 * <p>Note that this class loader will only apply to bean definitions
	 * that do not carry a resolved bean class yet. This is the case as of
	 * Spring 2.0 by default: Bean definitions only carry bean class names,
	 * to be resolved once the factory processes the bean definition.
	 * @param beanClassLoader the class loader to use,
	 * or {@code null} to suggest the default class loader
	 */
	void setBeanClassLoader(@Nullable ClassLoader beanClassLoader);

	/**
	 * Return this factory's class loader for loading bean classes
	 * (only {@code null} if even the system ClassLoader isn't accessible).
	 * @see org.springframework.util.ClassUtils#forName(String, ClassLoader)
	 */
	@Nullable
	ClassLoader getBeanClassLoader();

	/**
	 * Specify a temporary ClassLoader to use for type matching purposes.
	 * 指定临时加载器
	 *
	 * 临时类加载器和LTW(加载时编织)：
	 * 对于加载时编织：
	 *
	 * 临时类加载器用于加载并处理（编织）类。它创建了一个独立的命名空间，确保在编织过程中不会影响到主应用类加载器。
	 * 一旦编织完成，处理过的类可以被主应用类加载器或其他类加载器加载。这保证了编织过程不会干扰到应用的正常运行。
	 * 因此，使用临时类加载器可以确保加载时编织的过程不会影响到应用的其它部分，从而提供了一种有效的隔离机制。
	 *
	 * 当我们谈论“编织”或字节码增强时，我们是指在运行时动态修改类的字节码。这是AOP（面向切面编程）和许多其他高级Java特性的基础。
	 *
	 * 问题：
	 * 为什么不直接使用主类加载器来加载和编织类？
	 *
	 * 简单的解释：
	 * 避免冲突：
	 *
	 * 如果主类加载器已经加载了一个类，再试图加载经过编织（修改）的同一个类会产生冲突。使用临时类加载器避免了这个问题。
	 * 隔离：
	 *
	 * 使用不同的类加载器可以将编织的过程和应用的其他部分隔离开来，确保编织不会影响到应用的其他部分。
	 * 示例：
	 * 考虑这样一个场景：
	 *
	 * 你的应用使用了一个库，这个库包含了一个LibraryClass类。
	 * 你想在运行时通过AOP为LibraryClass添加一些额外的逻辑。
	 * 如果你使用主类加载器：
	 *
	 * 当你的应用启动时，主类加载器会加载LibraryClass。
	 * 然后，当你尝试应用AOP时，你需要再次加载经过修改的LibraryClass。
	 * 这时，主类加载器会拒绝加载经过修改的LibraryClass，因为它已经加载了原始的LibraryClass。
	 * 但是，如果你使用一个临时类加载器：
	 *
	 * 你可以使用临时类加载器加载和修改LibraryClass。
	 * 然后，将修改后的LibraryClass传递回主类加载器。
	 * 这样，主类加载器就可以使用修改后的LibraryClass，而不会发生任何冲突。
	 * 这就是为什么使用临时类加载器而不是主类加载器进行类的加载和编织的主要原因。
	 * Default is none, simply using the standard bean ClassLoader.
	 * <p>A temporary ClassLoader is usually just specified if
	 * <i>load-time weaving</i> is involved, to make sure that actual bean
	 * classes are loaded as lazily as possible. The temporary loader is
	 * then removed once the BeanFactory completes its bootstrap phase.
	 * @since 2.5
	 */
	void setTempClassLoader(@Nullable ClassLoader tempClassLoader);

	/**
	 * Return the temporary ClassLoader to use for type matching purposes,
	 * if any.
	 * @since 2.5
	 */
	@Nullable
	ClassLoader getTempClassLoader();

	/**
	 * Set whether to cache bean metadata such as given bean definitions
	 * (in merged fashion) and resolved bean classes. Default is on.
	 * <p>Turn this flag off to enable hot-refreshing of bean definition objects
	 * and in particular bean classes. If this flag is off, any creation of a bean
	 * instance will re-query the bean class loader for newly resolved classes.
	 * 是否开启bean的元数据(元数据 包括 全类名 ,构造参数,依赖等等...)
	 */
	void setCacheBeanMetadata(boolean cacheBeanMetadata);

	/**
	 * Return whether to cache bean metadata such as given bean definitions
	 * (in merged fashion) and resolved bean classes.
	 */
	boolean isCacheBeanMetadata();

	/**
	 * Specify the resolution strategy for expressions in bean definition values.
	 * <p>There is no expression support active in a BeanFactory by default.
	 * An ApplicationContext will typically set a standard expression strategy
	 * here, supporting "#{...}" expressions in a Unified EL compatible style.
	 * @since 3.0
	 * BeanExpressionResolver 是一个接口，它定义了如何在 Spring 的 Bean 定义中评估表达式。
	 * 表达式是一种允许您在 Bean 定义中动态插入值的机制。这使得您的配置可以更加灵活和强大。
	 * 例如:#{someExpression}
	 */
	void setBeanExpressionResolver(@Nullable BeanExpressionResolver resolver);

	/**
	 * Return the resolution strategy for expressions in bean definition values.
	 * @since 3.0
	 */
	@Nullable
	BeanExpressionResolver getBeanExpressionResolver();

	/**
	 * Specify a Spring 3.0 ConversionService to use for converting
	 * property values, as an alternative to JavaBeans PropertyEditors.
	 * @since 3.0
	 * ConversionService 是 Spring 框架的一部分，它提供了一个统一的API来执行从一种类型到另一种类型的转换。这是一个更现代、更灵活的替代方案，相对于旧的 JavaBeans PropertyEditor 接口。
	 *
	 * 在 Spring 配置中，ConversionService 可以用来控制 bean 属性的类型转换。例如，您可能有一个 bean，它有一个接受整数类型的属性，但在配置文件中，该值是以字符串的形式指定的。
	 * ConversionService 可以自动将该字符串值转换为整数，从而消除了在代码中进行显式类型转换的需要。
	 * <bean id="person" class="com.example.Person">
	 *     <property name="age" value="25"/>
	 * </bean>
	 * 虽然value是一个字符串（"25"），但如果您的ApplicationContext有一个ConversionService，它将自动将该值转换为一个int。
	 */
	void setConversionService(@Nullable ConversionService conversionService);

	/**
	 * Return the associated ConversionService, if any.
	 * @since 3.0
	 */
	@Nullable
	ConversionService getConversionService();

	/**
	 * Add a PropertyEditorRegistrar to be applied to all bean creation processes.
	 * <p>Such a registrar creates new PropertyEditor instances and registers them
	 * on the given registry, fresh for each bean creation attempt. This avoids
	 * the need for synchronization on custom editors; hence, it is generally
	 * preferable to use this method instead of {@link #registerCustomEditor}.
	 * @param registrar the PropertyEditorRegistrar to register
	 */
	void addPropertyEditorRegistrar(PropertyEditorRegistrar registrar);

	/**
	 * Register the given custom property editor for all properties of the
	 * given type. To be invoked during factory configuration.
	 * <p>Note that this method will register a shared custom editor instance;
	 * access to that instance will be synchronized for thread-safety. It is
	 * generally preferable to use {@link #addPropertyEditorRegistrar} instead
	 * of this method, to avoid for the need for synchronization on custom editors.
	 * @param requiredType type of the property
	 * @param propertyEditorClass the {@link PropertyEditor} class to register
	 */
	void registerCustomEditor(Class<?> requiredType, Class<? extends PropertyEditor> propertyEditorClass);

	/**
	 * Initialize the given PropertyEditorRegistry with the custom editors
	 * that have been registered with this BeanFactory.
	 * @param registry the PropertyEditorRegistry to initialize
	 */
	void copyRegisteredEditorsTo(PropertyEditorRegistry registry);

	/**
	 * Set a custom type converter that this BeanFactory should use for converting
	 * bean property values, constructor argument values, etc.
	 * <p>This will override the default PropertyEditor mechanism and hence make
	 * any custom editors or custom editor registrars irrelevant.
	 * @since 2.5
	 * @see #addPropertyEditorRegistrar
	 * @see #registerCustomEditor
	 */
	void setTypeConverter(TypeConverter typeConverter);

	/**
	 * Obtain a type converter as used by this BeanFactory. This may be a fresh
	 * instance for each call, since TypeConverters are usually <i>not</i> thread-safe.
	 * <p>If the default PropertyEditor mechanism is active, the returned
	 * TypeConverter will be aware of all custom editors that have been registered.
	 * @since 2.5
	 */
	TypeConverter getTypeConverter();

	/**
	 * Add a String resolver for embedded values such as annotation attributes.
	 * @param valueResolver the String resolver to apply to embedded values
	 * @since 3.0
	 */
	void addEmbeddedValueResolver(StringValueResolver valueResolver);

	/**
	 * Determine whether an embedded value resolver has been registered with this
	 * bean factory, to be applied through {@link #resolveEmbeddedValue(String)}.
	 * @since 4.3
	 */
	boolean hasEmbeddedValueResolver();

	/**
	 * Resolve the given embedded value, e.g. an annotation attribute.
	 * @param value the value to resolve
	 * @return the resolved value (may be the original value as-is)
	 * @since 3.0
	 */
	@Nullable
	String resolveEmbeddedValue(String value);

	/**
	 * Add a new BeanPostProcessor that will get applied to beans created
	 * by this factory. To be invoked during factory configuration.
	 * <p>Note: Post-processors submitted here will be applied in the order of
	 * registration; any ordering semantics expressed through implementing the
	 * {@link org.springframework.core.Ordered} interface will be ignored. Note
	 * that autodetected post-processors (e.g. as beans in an ApplicationContext)
	 * will always be applied after programmatically registered ones.
	 * @param beanPostProcessor the post-processor to register
	 * BeanPostProcessor 是作用于 Spring 容器中所有的 bean 上的。它包含两个回调方法：
	 *
	 * postProcessBeforeInitialization(Object bean, String beanName): 这个方法在任何 bean 的初始化方法（例如，使用 @PostConstruct 注解的方法）调用之前被调用。
	 * postProcessAfterInitialization(Object bean, String beanName): 这个方法在任何 bean 的初始化方法调用之后被调用。
	 * 通过实现 BeanPostProcessor 接口，并将实现类作为 bean 注册到 Spring 容器中，你可以拦截容器管理的所有 bean 的创建过程，并在初始化之前或之后执行自定义逻辑。例如，你可以修改 bean 的属性，或者返回一个代理对象，从而实现 AOP（面向切面编程）。
	 *
	 * 这是一个非常强大的特性，它允许你干预并影响 bean 的生命周期和实例化过程。
	 */
	void addBeanPostProcessor(BeanPostProcessor beanPostProcessor);

	/**
	 * Return the current number of registered BeanPostProcessors, if any.
	 */
	int getBeanPostProcessorCount();

	/**
	 * Register the given scope, backed by the given Scope implementation.
	 * @param scopeName the scope identifier
	 * @param scope the backing Scope implementation
	 */
	void registerScope(String scopeName, Scope scope);

	/**
	 * Return the names of all currently registered scopes.
	 * <p>This will only return the names of explicitly registered scopes.
	 * Built-in scopes such as "singleton" and "prototype" won't be exposed.
	 * @return the array of scope names, or an empty array if none
	 * @see #registerScope
	 */
	String[] getRegisteredScopeNames();

	/**
	 * Return the Scope implementation for the given scope name, if any.
	 * <p>This will only return explicitly registered scopes.
	 * Built-in scopes such as "singleton" and "prototype" won't be exposed.
	 * @param scopeName the name of the scope
	 * @return the registered Scope implementation, or {@code null} if none
	 * @see #registerScope
	 */
	@Nullable
	Scope getRegisteredScope(String scopeName);

	/**
	 * Set the {@code ApplicationStartup} for this bean factory.
	 * <p>This allows the application context to record metrics during application startup.
	 * @param applicationStartup the new application startup
	 * @since 5.3
	 */
	void setApplicationStartup(ApplicationStartup applicationStartup);

	/**
	 * Return the {@code ApplicationStartup} for this bean factory.
	 * @since 5.3
	 */
	ApplicationStartup getApplicationStartup();

	/**
	 * Provides a security access control context relevant to this factory.
	 * @return the applicable AccessControlContext (never {@code null})
	 * @since 3.0
	 */
	AccessControlContext getAccessControlContext();

	/**
	 * Copy all relevant configuration from the given other factory.
	 * <p>Should include all standard configuration settings as well as
	 * BeanPostProcessors, Scopes, and factory-specific internal settings.
	 * Should not include any metadata of actual bean definitions,
	 * such as BeanDefinition objects and bean name aliases.
	 * @param otherFactory the other BeanFactory to copy from
	 */
	void copyConfigurationFrom(ConfigurableBeanFactory otherFactory);

	/**
	 * Given a bean name, create an alias. We typically use this method to
	 * support names that are illegal within XML ids (used for bean names).
	 * <p>Typically invoked during factory configuration, but can also be
	 * used for runtime registration of aliases. Therefore, a factory
	 * implementation should synchronize alias access.
	 * @param beanName the canonical name of the target bean
	 * @param alias the alias to be registered for the bean
	 * @throws BeanDefinitionStoreException if the alias is already in use
	 */
	void registerAlias(String beanName, String alias) throws BeanDefinitionStoreException;

	/**
	 * Resolve all alias target names and aliases registered in this
	 * factory, applying the given StringValueResolver to them.
	 * <p>The value resolver may for example resolve placeholders
	 * in target bean names and even in alias names.
	 * @param valueResolver the StringValueResolver to apply
	 * @since 2.5
	 */
	void resolveAliases(StringValueResolver valueResolver);

	/**
	 * Return a merged BeanDefinition for the given bean name,
	 * merging a child bean definition with its parent if necessary.
	 * Considers bean definitions in ancestor factories as well.
	 * @param beanName the name of the bean to retrieve the merged definition for
	 * @return a (potentially merged) BeanDefinition for the given bean
	 * @throws NoSuchBeanDefinitionException if there is no bean definition with the given name
	 * @since 2.5
	 * 当你使用 getMergedBeanDefinition 方法时，如果 bean 有一个父 bean 定义（可以在不同的上下文或父容器中），
	 * 这个方法就会返回一个新的 BeanDefinition 对象，这个对象合并了子 bean 定义和父 bean 定义的属性。这在处理 bean 定义继承时非常有用。
	 *
	 * 例如，如果一个 bean B 继承了 bean A 的定义，并且他们两个都定义了不同的属性或设置，那么通过 getMergedBeanDefinition
	 * 方法获取的 BeanDefinition 对象将包含 bean A 和 B 的所有属性和设置。
	 */
	BeanDefinition getMergedBeanDefinition(String beanName) throws NoSuchBeanDefinitionException;

	/**
	 * Determine whether the bean with the given name is a FactoryBean.
	 * @param name the name of the bean to check
	 * @return whether the bean is a FactoryBean
	 * ({@code false} means the bean exists but is not a FactoryBean)
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * @since 2.5
	 */
	boolean isFactoryBean(String name) throws NoSuchBeanDefinitionException;

	/**
	 * Explicitly control the current in-creation status of the specified bean.
	 * For container-internal use only.
	 * @param beanName the name of the bean
	 * @param inCreation whether the bean is currently in creation
	 * @since 3.1
	 */
	void setCurrentlyInCreation(String beanName, boolean inCreation);

	/**
	 * Determine whether the specified bean is currently in creation.
	 * @param beanName the name of the bean
	 * @return whether the bean is currently in creation
	 * 这个方法允许容器内部代码显式设置一个 bean 是否正在创建中。这在解决循环依赖的问题上有用。当 Spring 容器检测到一个循环依赖时，
	 * 它可以通过这个方法显式标记 bean 是在创建中的状态，然后执行特定的处理逻辑以解决循环依赖的问题。
	 * @since 2.5
	 */
	boolean isCurrentlyInCreation(String beanName);

	/**
	 * Register a dependent bean for the given bean,
	 * to be destroyed before the given bean is destroyed.
	 * @param beanName the name of the bean
	 * @param dependentBeanName the name of the dependent bean
	 * @since 2.5
	 */
	void registerDependentBean(String beanName, String dependentBeanName);

	/**
	 * Return the names of all beans which depend on the specified bean, if any.
	 * @param beanName the name of the bean
	 * @return the array of dependent bean names, or an empty array if none
	 * @since 2.5
	 */
	String[] getDependentBeans(String beanName);

	/**
	 * Return the names of all beans that the specified bean depends on, if any.
	 * @param beanName the name of the bean
	 * @return the array of names of beans which the bean depends on,
	 * or an empty array if none
	 * @since 2.5
	 */
	String[] getDependenciesForBean(String beanName);

	/**
	 * Destroy the given bean instance (usually a prototype instance
	 * obtained from this factory) according to its bean definition.
	 * <p>Any exception that arises during destruction should be caught
	 * and logged instead of propagated to the caller of this method.
	 * @param beanName the name of the bean definition
	 * @param beanInstance the bean instance to destroy
	 */
	void destroyBean(String beanName, Object beanInstance);

	/**
	 * Destroy the specified scoped bean in the current target scope, if any.
	 * <p>Any exception that arises during destruction should be caught
	 * and logged instead of propagated to the caller of this method.
	 * @param beanName the name of the scoped bean
	 */
	void destroyScopedBean(String beanName);

	/**
	 * Destroy all singleton beans in this factory, including inner beans that have
	 * been registered as disposable. To be called on shutdown of a factory.
	 * <p>Any exception that arises during destruction should be caught
	 * and logged instead of propagated to the caller of this method.
	 */
	void destroySingletons();

}
