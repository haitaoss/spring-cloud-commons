package cn.haitaoss.ServiceRegisterAndLoadBalance.loadbalancer;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.loadbalancer.core.RandomLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ReactorLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-04-04 09:23
 * 随机的负载均衡器
 */
public class MyRandomLoadBalancer {

	@Bean
	public ReactorLoadBalancer<ServiceInstance> roundRobinLoadBalancer(Environment environment,
			LoadBalancerClientFactory loadBalancerClientFactory) {
		String name = environment.getProperty(LoadBalancerClientFactory.PROPERTY_NAME);
		return new RandomLoadBalancer(
				loadBalancerClientFactory.getLazyProvider(name, ServiceInstanceListSupplier.class), name);
	}

}
