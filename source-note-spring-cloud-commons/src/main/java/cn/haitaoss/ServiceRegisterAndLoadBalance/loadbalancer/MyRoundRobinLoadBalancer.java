package cn.haitaoss.ServiceRegisterAndLoadBalance.loadbalancer;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClientConfiguration;
import org.springframework.cloud.loadbalancer.core.ReactorLoadBalancer;
import org.springframework.cloud.loadbalancer.core.RoundRobinLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-04-04 09:14
 * 轮询的负载均衡器
 */
public class MyRoundRobinLoadBalancer {
    /**
     * 参考 {@link LoadBalancerClientConfiguration}
     * @param environment
     * @param loadBalancerClientFactory
     * @return
     */
    @Bean
    public ReactorLoadBalancer<ServiceInstance> roundRobinLoadBalancer(Environment environment,
                                                                       LoadBalancerClientFactory loadBalancerClientFactory) {
        String name = environment.getProperty(LoadBalancerClientFactory.PROPERTY_NAME);
        // loadBalancerClientFactory 会构造出多个IOC容器，name参数就是为了定位使用那个IOC容器
        return new RoundRobinLoadBalancer(
                loadBalancerClientFactory.getLazyProvider(name, ServiceInstanceListSupplier.class), name);
    }
}
