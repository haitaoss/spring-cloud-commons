package cn.haitaoss.ServiceRegisterAndLoadBalance.loadbalancer;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.DefaultResponse;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.loadbalancer.core.*;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.core.env.Environment;
import reactor.core.publisher.Mono;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-04-04 09:30
 * 根据ThreadLoca的值选定服务
 */
public class MyLoadBalancer implements ReactorServiceInstanceLoadBalancer {

	private String serviceId;

	/**
	 * 服务列表
	 */
	private ObjectProvider<ServiceInstanceListSupplier> serviceInstanceListSupplierProvider;

	public static final ThreadLocal<Integer> SERVICE_INDEX = ThreadLocal.withInitial(() -> 0);

	public MyLoadBalancer(Environment environment, LoadBalancerClientFactory loadBalancerClientFactory) {
		serviceId = environment.getProperty(LoadBalancerClientFactory.PROPERTY_NAME);
		serviceInstanceListSupplierProvider = loadBalancerClientFactory.getLazyProvider(serviceId,
				ServiceInstanceListSupplier.class);
	}

	@Override
	public Mono<Response<ServiceInstance>> choose(Request request) {
		ServiceInstanceListSupplier supplier = serviceInstanceListSupplierProvider
				.getIfAvailable(NoopServiceInstanceListSupplier::new);

		return supplier.get(request).next()
				.map(serviceInstances -> new DefaultResponse(serviceInstances.get(SERVICE_INDEX.get())));
	}

}
