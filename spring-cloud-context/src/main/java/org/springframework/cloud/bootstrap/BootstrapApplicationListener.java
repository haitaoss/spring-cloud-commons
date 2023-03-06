/*
 * Copyright 2012-2020 the original author or authors.
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

package org.springframework.cloud.bootstrap;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.Banner.Mode;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.ParentContextApplicationContextInitializer;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.boot.context.logging.LoggingApplicationListener;
import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.boot.origin.Origin;
import org.springframework.boot.origin.OriginLookup;
import org.springframework.cloud.bootstrap.encrypt.EnvironmentDecryptApplicationInitializer;
import org.springframework.cloud.bootstrap.support.OriginTrackedCompositePropertySource;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.SmartApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.PropertySource.StubPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import static org.springframework.cloud.util.PropertyUtils.bootstrapEnabled;
import static org.springframework.cloud.util.PropertyUtils.useLegacyProcessing;

/**
 * A listener that prepares a SpringApplication (e.g. populating its Environment) by
 * delegating to {@link ApplicationContextInitializer} beans in a separate bootstrap
 * context. The bootstrap context is a SpringApplication created from sources defined in
 * spring.factories as {@link BootstrapConfiguration}, and initialized with external
 * config taken from "bootstrap.properties" (or yml), instead of the normal
 * "application.properties".
 *
 * @author Dave Syer
 */
public class BootstrapApplicationListener implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

    /**
     * Property source name for bootstrap.
     */
    public static final String BOOTSTRAP_PROPERTY_SOURCE_NAME = "bootstrap";

    /**
     * The default order for this listener.
     */
    public static final int DEFAULT_ORDER = Ordered.HIGHEST_PRECEDENCE + 5;

    /**
     * The name of the default properties.
     */
    public static final String DEFAULT_PROPERTIES = "springCloudDefaultProperties";

    private int order = DEFAULT_ORDER;

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment environment = event.getEnvironment();
        /**
         * 属性 spring.cloud.bootstrap.enabled 是 false 就return
         * */
        if (!bootstrapEnabled(environment) && !useLegacyProcessing(environment)) {
            return;
        }
        /**
         * bootstrap context 不监听事件，因为下面的代码中会构造出一个 bootstrap context 然后也会触发这个事件，
         * 这里的代码就是避免 bootstrap context 执行具体的逻辑的
         * */
        // don't listen to events in a bootstrap context
        if (environment.getPropertySources()
                .contains(BOOTSTRAP_PROPERTY_SOURCE_NAME)) {
            return;
        }
        ConfigurableApplicationContext context = null;
        // 读取这个属性值作为 配置文件的名称
        String configName = environment.resolvePlaceholders("${spring.cloud.bootstrap.name:bootstrap}");
        // 遍历 ApplicationContextInitializer
        for (ApplicationContextInitializer<?> initializer : event.getSpringApplication()
                .getInitializers()) {
            // 如果是 ParentContextApplicationContextInitializer 类型的，就返回其 parent 属性值
            if (initializer instanceof ParentContextApplicationContextInitializer) {
                context = findBootstrapContext((ParentContextApplicationContextInitializer) initializer, configName);
            }
        }
        if (context == null) {
            /**
             * 1. 构造一个 Environment，主要是设置这三个属性
             *      spring.config.name : 默认就是 bootstrap
             *      spring.config.location
             *      spring.config.additional-location
             *
             * 2. 使用 SpringBoot 构造出 Context，说白了就是利用 SpringBoot 加载 application.yml 的逻辑来加载 bootstrap.yml
             *
             * 3. 将 context 中的 Environment 追加到 event.getSpringApplication() 中，从而实现 bootstrap.properties 文件的加载
             *
             * Tips：就是SpringBoot的知识，懂的自然懂
             * */
            context = bootstrapServiceContext(environment, event.getSpringApplication(), configName);
            // 添加一个事件监听器，其作用是 若 event.getSpringApplication() 启动失败了，就关闭 context
            event.getSpringApplication()
                    .addListeners(new CloseContextOnFailureApplicationListener(context));
        }

        /**
         * 1. 添加 BootstrapMarkerConfiguration 用来标记已经处理过了
         * 2. 将 context 中的 ApplicationContextInitializer 追加到 event.getSpringApplication() 中
         *
         * Tips：可以将 event.getSpringApplication() 理解成主容器，context 理解成临时容器。
         * */
        apply(context, event.getSpringApplication(), environment);
    }

    private ConfigurableApplicationContext findBootstrapContext(ParentContextApplicationContextInitializer initializer,
                                                                String configName) {
        Field field = ReflectionUtils.findField(ParentContextApplicationContextInitializer.class, "parent");
        ReflectionUtils.makeAccessible(field);
        // 返回 parent 属性的值
        ConfigurableApplicationContext parent = safeCast(ConfigurableApplicationContext.class,
                ReflectionUtils.getField(field, initializer)
        );
        // 是否使用父属性
        if (parent != null && !configName.equals(parent.getId())) {
            parent = safeCast(ConfigurableApplicationContext.class, parent.getParent());
        }
        return parent;
    }

    private <T> T safeCast(Class<T> type, Object object) {
        try {
            return type.cast(object);
        } catch (ClassCastException e) {
            return null;
        }
    }

    private ConfigurableApplicationContext bootstrapServiceContext(ConfigurableEnvironment environment,
                                                                   final SpringApplication application,
                                                                   String configName) {
        StandardEnvironment bootstrapEnvironment = new StandardEnvironment();
        MutablePropertySources bootstrapProperties = bootstrapEnvironment.getPropertySources();
        for (PropertySource<?> source : bootstrapProperties) {
            bootstrapProperties.remove(source.getName());
        }
        // 这两个属性是 SpringBoot 读取配置文件的目录
        String configLocation = environment.resolvePlaceholders("${spring.cloud.bootstrap.location:}");
        String configAdditionalLocation = environment.resolvePlaceholders(
                "${spring.cloud.bootstrap.additional-location:}");
        Map<String, Object> bootstrapMap = new HashMap<>();
        bootstrapMap.put("spring.config.name", configName);
        // if an app (or test) uses spring.main.web-application-type=reactive, bootstrap
        // will fail
        // force the environment to use none, because if though it is set below in the
        // builder
        // the environment overrides it
        bootstrapMap.put("spring.main.web-application-type", "none");
        /**
         * 这两个属性是 设置属性文件的搜索目录 或者是 属性文件，原理就得看 SpringBoot 的知识了
         *
         * {@link ConfigDataEnvironmentPostProcessor#postProcessEnvironment(ConfigurableEnvironment, ResourceLoader, Collection)}
         * */
        if (StringUtils.hasText(configLocation)) {
            bootstrapMap.put("spring.config.location", configLocation);
        }
        if (StringUtils.hasText(configAdditionalLocation)) {
            bootstrapMap.put("spring.config.additional-location", configAdditionalLocation);
        }
        // 添加一个 PropertySource
        bootstrapProperties.addFirst(new MapPropertySource(BOOTSTRAP_PROPERTY_SOURCE_NAME, bootstrapMap));
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (source instanceof StubPropertySource) {
                continue;
            }
            // 将 environment 的内容扩展进去
            bootstrapProperties.addLast(source);
        }
        // 构造一个 SpringApplicationBuilder
        // TODO: is it possible or sensible to share a ResourceLoader?
        SpringApplicationBuilder builder = new SpringApplicationBuilder().profiles(environment.getActiveProfiles())
                .bannerMode(Mode.OFF)
                .environment(bootstrapEnvironment)
                // Don't use the default properties in this builder
                .registerShutdownHook(false)
                .logStartupInfo(false)
                .web(WebApplicationType.NONE);
        final SpringApplication builderApplication = builder.application();
        if (builderApplication.getMainApplicationClass() == null) {
            // gh_425:
            // SpringApplication cannot deduce the MainApplicationClass here
            // if it is booted from SpringBootServletInitializer due to the
            // absense of the "main" method in stackTraces.
            // But luckily this method's second parameter "application" here
            // carries the real MainApplicationClass which has been explicitly
            // set by SpringBootServletInitializer itself already.
            builder.main(application.getMainApplicationClass());
        }
        if (environment.getPropertySources()
                .contains("refreshArgs")) {
            // If we are doing a context refresh, really we only want to refresh the
            // Environment, and there are some toxic listeners (like the
            // LoggingApplicationListener) that affect global static state, so we need a
            // way to switch those off.
            builderApplication.setListeners(filterListeners(builderApplication.getListeners()));
        }
        builder.sources(BootstrapImportSelectorConfiguration.class);
        /**
         * 启动 SpringBoot
         * */
        final ConfigurableApplicationContext context = builder.run();
        // gh-214 using spring.application.name=bootstrap to set the context id via
        // `ContextIdApplicationContextInitializer` prevents apps from getting the actual
        // spring.application.name
        // during the bootstrap phase.
        context.setId("bootstrap");
        // Make the bootstrap context a parent of the app context
        addAncestorInitializer(application, context);
        // 移除临时属性
        // It only has properties in it now that we don't want in the parent so remove
        // it (and it will be added back later)
        bootstrapProperties.remove(BOOTSTRAP_PROPERTY_SOURCE_NAME);
        // 将 bootstrapProperties 中的内容追加到 environment 中
        mergeDefaultProperties(environment.getPropertySources(), bootstrapProperties);
        return context;
    }

    private Collection<? extends ApplicationListener<?>> filterListeners(Set<ApplicationListener<?>> listeners) {
        Set<ApplicationListener<?>> result = new LinkedHashSet<>();
        for (ApplicationListener<?> listener : listeners) {
            if (!(listener instanceof LoggingApplicationListener) && !(listener instanceof LoggingSystemShutdownListener)) {
                result.add(listener);
            }
        }
        return result;
    }

    private void mergeDefaultProperties(MutablePropertySources environment, MutablePropertySources bootstrap) {
        String name = DEFAULT_PROPERTIES;
        if (bootstrap.contains(name)) {
            PropertySource<?> source = bootstrap.get(name);
            // 不存在
            if (!environment.contains(name)) {
                // 追加到 environment 中
                environment.addLast(source);
            } else {
                PropertySource<?> target = environment.get(name);
                // 是 MapPropertySource 类型的，就扩展
                if (target instanceof MapPropertySource && target != source && source instanceof MapPropertySource) {
                    Map<String, Object> targetMap = ((MapPropertySource) target).getSource();
                    Map<String, Object> map = ((MapPropertySource) source).getSource();
                    for (String key : map.keySet()) {
                        // 不包含的属性值才添加，也就是不会覆盖
                        if (!target.containsProperty(key)) {
                            targetMap.put(key, map.get(key));
                        }
                    }
                }
            }
        }
        /**
         * 1. 往 environment 添加 名叫 `name` 的PropertySource
         * 2. 将 bootstrap 的内容追加到 environment 中
         * */
        mergeAdditionalPropertySources(environment, bootstrap);
    }

    private void mergeAdditionalPropertySources(MutablePropertySources environment, MutablePropertySources bootstrap) {
        // 获取。第一次肯定没有回返回 null
        PropertySource<?> defaultProperties = environment.get(DEFAULT_PROPERTIES);
        // 这里就是对 null 的情况进行初始化
        ExtendedDefaultPropertySource result = defaultProperties instanceof ExtendedDefaultPropertySource ? (ExtendedDefaultPropertySource) defaultProperties : new ExtendedDefaultPropertySource(
                DEFAULT_PROPERTIES, defaultProperties);
        for (PropertySource<?> source : bootstrap) {
            // 不存在
            if (!environment.contains(source.getName())) {
                // 记录
                result.add(source);
            }
        }
        for (String name : result.getPropertySourceNames()) {
            // 从 bootstrap 中移除 已经记录到  result 中的
            bootstrap.remove(name);
        }
        // 将 result 中的内容 追加或者替换到 environment|bootstrap 中
        addOrReplace(environment, result);
        addOrReplace(bootstrap, result);
    }

    private void addOrReplace(MutablePropertySources environment, PropertySource<?> result) {
        if (environment.contains(result.getName())) {
            environment.replace(result.getName(), result);
        } else {
            environment.addLast(result);
        }
    }

    private void addAncestorInitializer(SpringApplication application, ConfigurableApplicationContext context) {
        boolean installed = false;
        for (ApplicationContextInitializer<?> initializer : application.getInitializers()) {
            if (initializer instanceof AncestorInitializer) {
                installed = true;
                // New parent
                ((AncestorInitializer) initializer).setParent(context);
            }
        }
        if (!installed) {
            application.addInitializers(new AncestorInitializer(context));
        }

    }

    @SuppressWarnings("unchecked")
    private void apply(ConfigurableApplicationContext context, SpringApplication application,
                       ConfigurableEnvironment environment) {
        // 存在这个名字的 就 return
        if (application.getAllSources()
                .contains(BootstrapMarkerConfiguration.class)) {
            return;
        }
        // 添加一个配置类。目的就是上一步的检验
        application.addPrimarySources(Arrays.asList(BootstrapMarkerConfiguration.class));
        @SuppressWarnings("rawtypes") Set target = new LinkedHashSet<>(application.getInitializers());
        // 将 context 中的 ApplicationContextInitializer 追加到 application 中
        target.addAll(getOrderedBeansOfType(context, ApplicationContextInitializer.class));
        application.setInitializers(target);
        addBootstrapDecryptInitializer(application);
    }

    @SuppressWarnings("unchecked")
    private void addBootstrapDecryptInitializer(SpringApplication application) {
        DelegatingEnvironmentDecryptApplicationInitializer decrypter = null;
        Set<ApplicationContextInitializer<?>> initializers = new LinkedHashSet<>();
        for (ApplicationContextInitializer<?> ini : application.getInitializers()) {
            // 装饰一下
            if (ini instanceof EnvironmentDecryptApplicationInitializer) {
                @SuppressWarnings("rawtypes") ApplicationContextInitializer del = ini;
                decrypter = new DelegatingEnvironmentDecryptApplicationInitializer(del);
                initializers.add(ini);
                initializers.add(decrypter);
            } else if (ini instanceof DelegatingEnvironmentDecryptApplicationInitializer) {
                // do nothing
            } else {
                initializers.add(ini);
            }
        }
        ArrayList<ApplicationContextInitializer<?>> target = new ArrayList<ApplicationContextInitializer<?>>(
                initializers);
        application.setInitializers(target);
    }

    private <T> List<T> getOrderedBeansOfType(ListableBeanFactory context, Class<T> type) {
        List<T> result = new ArrayList<T>();
        for (String name : context.getBeanNamesForType(type)) {
            result.add(context.getBean(name, type));
        }
        AnnotationAwareOrderComparator.sort(result);
        return result;
    }

    @Override
    public int getOrder() {
        return this.order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    private static class BootstrapMarkerConfiguration {

    }

    private static class AncestorInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext>, Ordered {

        private ConfigurableApplicationContext parent;

        AncestorInitializer(ConfigurableApplicationContext parent) {
            this.parent = parent;
        }

        public void setParent(ConfigurableApplicationContext parent) {
            this.parent = parent;
        }

        @Override
        public int getOrder() {
            // Need to run not too late (so not unordered), so that, for instance, the
            // ContextIdApplicationContextInitializer runs later and picks up the merged
            // Environment. Also needs to be quite early so that other initializers can
            // pick up the parent (especially the Environment).
            return Ordered.HIGHEST_PRECEDENCE + 5;
        }

        @Override
        public void initialize(ConfigurableApplicationContext context) {
            while (context.getParent() != null && context.getParent() != context) {
                context = (ConfigurableApplicationContext) context.getParent();
            }
            reorderSources(context.getEnvironment());
            new ParentContextApplicationContextInitializer(this.parent).initialize(context);
        }

        private void reorderSources(ConfigurableEnvironment environment) {
            PropertySource<?> removed = environment.getPropertySources()
                    .remove(DEFAULT_PROPERTIES);
            if (removed instanceof ExtendedDefaultPropertySource) {
                ExtendedDefaultPropertySource defaultProperties = (ExtendedDefaultPropertySource) removed;
                environment.getPropertySources()
                        .addLast(new MapPropertySource(DEFAULT_PROPERTIES, defaultProperties.getSource()));
                for (PropertySource<?> source : defaultProperties.getPropertySources()
                        .getPropertySources()) {
                    if (!environment.getPropertySources()
                            .contains(source.getName())) {
                        environment.getPropertySources()
                                .addBefore(DEFAULT_PROPERTIES, source);
                    }
                }
            }
        }

    }

    /**
     * A special initializer designed to run before the property source bootstrap and
     * decrypt any properties needed there (e.g. URL of config server).
     */
    @Order(Ordered.HIGHEST_PRECEDENCE + 9)
    private static class DelegatingEnvironmentDecryptApplicationInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        private ApplicationContextInitializer<ConfigurableApplicationContext> delegate;

        DelegatingEnvironmentDecryptApplicationInitializer(
                ApplicationContextInitializer<ConfigurableApplicationContext> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            this.delegate.initialize(applicationContext);
        }

    }

    private static class ExtendedDefaultPropertySource extends SystemEnvironmentPropertySource implements OriginLookup<String> {

        private final OriginTrackedCompositePropertySource sources;

        private final List<String> names = new ArrayList<>();

        ExtendedDefaultPropertySource(String name, PropertySource<?> propertySource) {
            super(name, findMap(propertySource));
            this.sources = new OriginTrackedCompositePropertySource(name);
        }

        @SuppressWarnings("unchecked")
        private static Map<String, Object> findMap(PropertySource<?> propertySource) {
            if (propertySource instanceof MapPropertySource) {
                return (Map<String, Object>) propertySource.getSource();
            }
            return new LinkedHashMap<String, Object>();
        }

        public CompositePropertySource getPropertySources() {
            return this.sources;
        }

        public List<String> getPropertySourceNames() {
            return this.names;
        }

        public void add(PropertySource<?> source) {
            // Only add map property sources added by boot, see gh-476
            if (source instanceof OriginTrackedMapPropertySource && !this.names.contains(source.getName())) {
                this.sources.addPropertySource(source);
                this.names.add(source.getName());
            }
        }

        @Override
        public Object getProperty(String name) {
            if (this.sources.containsProperty(name)) {
                return this.sources.getProperty(name);
            }
            return super.getProperty(name);
        }

        @Override
        public boolean containsProperty(String name) {
            if (this.sources.containsProperty(name)) {
                return true;
            }
            return super.containsProperty(name);
        }

        @Override
        public String[] getPropertyNames() {
            List<String> names = new ArrayList<>();
            names.addAll(Arrays.asList(this.sources.getPropertyNames()));
            names.addAll(Arrays.asList(super.getPropertyNames()));
            return names.toArray(new String[0]);
        }

        @Override
        public Origin getOrigin(String name) {
            return this.sources.getOrigin(name);
        }

    }

    private static class CloseContextOnFailureApplicationListener implements SmartApplicationListener {

        private final ConfigurableApplicationContext context;

        CloseContextOnFailureApplicationListener(ConfigurableApplicationContext context) {
            this.context = context;
        }

        @Override
        public boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
            return ApplicationFailedEvent.class.isAssignableFrom(eventType);
        }

        @Override
        public void onApplicationEvent(ApplicationEvent event) {
            if (event instanceof ApplicationFailedEvent) {
                this.context.close();
            }

        }

    }

}
