# 资料

[SpringBoot源码分析](https://github.com/haitaoss/spring-boot/blob/source-v2.7.8/note/springboot-source-note.md)

[Spring源码分析](https://github.com/haitaoss/spring-framework)

[示例代码](../source-note-spring-cloud-commons)

[Spring Cloud Commons 官网文档](https://spring.io/projects/spring-cloud-commons)

> ### Spring Cloud Context features:
>
> - Bootstrap Context : 加载 bootstrap.properties
> - `TextEncryptor` beans
> - Refresh Scope 
> - Spring Boot Actuator endpoints for manipulating the `Environment`
>
> ### Spring Cloud Commons features:
>
> - `DiscoveryClient` interface ：定义 服务发现 接口规范
> - `ServiceRegistry` interface ：定义 服务注册 接口规范
> - Instrumentation for `RestTemplate` to resolve hostnames using `DiscoveryClient

# bootstrap.properties 读取原理

[前置知识：SprinBoot加载application.yml的原理](https://github.com/haitaoss/spring-boot/blob/source-v2.7.8/note/springboot-source-note.md#%E5%B1%9E%E6%80%A7%E6%96%87%E4%BB%B6%E7%9A%84%E5%8A%A0%E8%BD%BD%E9%A1%BA%E5%BA%8F)

```java
/**
 * 1. spring-cloud-context.jar!/META-INF/spring.factories 声明了 BootstrapApplicationListener
 *
 * 2. SpringBoot 启动的生命周期的配置Environment阶段，会发布 ApplicationEnvironmentPreparedEvent 事件，所以 BootstrapApplicationListener 会收到事件
 *      {@link SpringApplication#run(String...)}
 *      {@link SpringApplication#prepareEnvironment(SpringApplicationRunListeners, DefaultBootstrapContext, ApplicationArguments)}
 *      {@link EventPublishingRunListener#environmentPrepared(ConfigurableBootstrapContext, ConfigurableEnvironment)}
 *      {@link EnvironmentPostProcessorApplicationListener#onApplicationEvent(ApplicationEvent)}
 *      {@link BootstrapApplicationListener#onApplicationEvent(org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent)}
 *
 * 3. BootstrapApplicationListener 处理事件的逻辑
 *      3.1 属性 spring.cloud.bootstrap.enabled 是 false 就return
 *
 *      3.2 使用SpringBoot构造一个context从而实现 bootstrap 属性文件的解析
 *          1. 构造一个 Environment，主要是设置这三个属性
 *              spring.config.name : 默认就是 bootstrap
 *              spring.config.location
 *              spring.config.additional-location
 *
 *          2. 使用 SpringBoot 构造出 Context，说白了就是利用 SpringBoot 加载 application.yml 的逻辑来加载 bootstrap.yml
 *
 *          3. 将 context 中的 Environment 追加到 event.getSpringApplication() 中，从而实现 bootstrap.properties 文件的加载
 *
 *          Tips：就是SpringBoot的知识，懂的自然懂
 * */
```