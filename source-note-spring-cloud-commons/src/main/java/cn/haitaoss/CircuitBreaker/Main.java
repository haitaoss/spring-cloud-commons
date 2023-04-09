package cn.haitaoss.CircuitBreaker;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.web.client.RestTemplate;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-03-17 16:06
 *
 */
public class Main {
    /**
     * Spring Cloud Circuit Breaker Spring Cloud 断路器
     *      Spring Cloud Circuit breaker提供了跨不同断路器实现的抽象。它提供了一个一致的API用于您的应用，让您，开发人员，选择最适合您的应用需求的断路器实现。
     *      Spring Cloud支持以下断路器实现：
     *          Resilience4J: https://github.com/resilience4j/resilience4j
     *          Sentinel: https://github.com/alibaba/Sentinel
     *          Spring Retry: https://github.com/spring-projects/spring-retry
     *
     *  Core Concepts （核心概念）
     *      若要在代码中创建断路器，可以使用 CircuitBreakerFactory API。当您在类路径中包含  Spring Cloud Circuit Breaker starter 时，
     *      将自动为您创建一个实现此API的bean。以下示例显示了如何使用此API的简单示例
     *
     *  Configuration  (配置)
     *      您可以通过创建类型为 Customizer 的Bean来配置断路器。 Customizer 接口有一个方法（称为 customize ），它接受 Object 进行定制
     * */
    @Autowired
    private RestTemplate rest;
    @Autowired
    private CircuitBreakerFactory cbFactory;


    public String slow() {
        /**
         * CircuitBreakerFactory.create API创建一个名为 CircuitBreaker 的类的实例。
         * run 方法接受 Supplier 和 Function 。 Supplier 是您要包装在断路器中的代码。
         * Function 是在断路器跳闸时运行的后备。向函数传递导致触发回退的 Throwable 。
         * 如果不想提供回退，可以选择排除回退。
         * */
        return cbFactory.create("slow").run(() -> rest.getForObject("/slow", String.class), throwable -> "fallback");
    }
}
