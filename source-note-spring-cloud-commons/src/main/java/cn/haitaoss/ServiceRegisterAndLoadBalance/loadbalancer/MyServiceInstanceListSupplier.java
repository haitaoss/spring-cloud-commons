package cn.haitaoss.ServiceRegisterAndLoadBalance.loadbalancer;

import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-04-08 22:07
 *
 */
public class MyServiceInstanceListSupplier {
    @Bean
    public ServiceInstanceListSupplier discoveryClientServiceInstanceListSupplier(
            ConfigurableApplicationContext context) {
        return ServiceInstanceListSupplier.builder()
                .withDiscoveryClient() // 通过 ReactiveDiscoveryClient 获取 List<ServiceInstance>
                .withBlockingDiscoveryClient() // 通过 DiscoveryClient 获取 List<ServiceInstance>

                // 下面配置的是通过什么方式 过滤  List<ServiceInstance>
                // .withZonePreference() // spring.cloud.loadbalancer.zone" 属性值与 serviceInstance.getMetadata().get("zone") 进行批评
                // .withBlockingHealthChecks() // spring.cloud.loadbalancer.healthCheck.* 属性定义的的规则来过滤
                // .withRequestBasedStickySession() //  spring.cloud.loadbalancer.stickySession.instanceIdCookieName 属性值 过滤 serviceInstance.getInstanceId()
                // .withSameInstancePreference() // 第一次得到的 List<ServiceInstance> 会作为首选项返回
                .withCaching() // 会使用到 LoadBalancerCacheManager 缓存 List<ServiceInstance>
                .build(context);
    }
}
