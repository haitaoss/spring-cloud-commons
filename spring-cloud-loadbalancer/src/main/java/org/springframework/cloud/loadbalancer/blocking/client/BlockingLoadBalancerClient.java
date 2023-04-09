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

package org.springframework.cloud.loadbalancer.blocking.client;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.*;
import org.springframework.cloud.client.loadbalancer.reactive.ReactiveLoadBalancer;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClientConfiguration;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.ReflectionUtils;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.URI;
import java.util.Set;

import static org.springframework.cloud.client.loadbalancer.reactive.ReactiveLoadBalancer.REQUEST;

/**
 * The default {@link LoadBalancerClient} implementation.
 *
 * @author Olga Maciaszek-Sharma
 * @since 2.2.0
 */
@SuppressWarnings({ "unchecked", "rawtypes" })
public class BlockingLoadBalancerClient implements LoadBalancerClient {

	private final LoadBalancerClientFactory loadBalancerClientFactory;

	private final LoadBalancerProperties properties;

	public BlockingLoadBalancerClient(LoadBalancerClientFactory loadBalancerClientFactory,
			LoadBalancerProperties properties) {
		this.loadBalancerClientFactory = loadBalancerClientFactory;
		this.properties = properties;

	}

	@Override
	public <T> T execute(String serviceId, LoadBalancerRequest<T> request) throws IOException {
		/**
		 * 根据 serviceId 获取配置的 hint 值，默认是 default
		 * 可以设置 spring.cloud.loadbalancer.hint.serviceName=hint1 来设置该值
		 * 注：看 {@link LoadBalancerProperties}。
		 * */
		String hint = getHint(serviceId);

		// 装饰成 LoadBalancerRequestAdapter
		LoadBalancerRequestAdapter<T, DefaultRequestContext> lbRequest = new LoadBalancerRequestAdapter<>(request,
				new DefaultRequestContext(request, hint));

		/**
		 * 从 loadBalancerClientFactory 中获取 LoadBalancerLifecycle 类型的bean
		 *
		 * 注：LoadBalancerClientFactory 继承 NamedContextFactory , 会根据 serviceId 创建一个IOC容器，再从这个指定的IOC容器中获取bean，创建的IOC容器会存到Map中
		 * */
		Set<LoadBalancerLifecycle> supportedLifecycleProcessors = LoadBalancerLifecycleValidator
				.getSupportedLifecycleProcessors(
						loadBalancerClientFactory.getInstances(serviceId, LoadBalancerLifecycle.class),
						DefaultRequestContext.class, Object.class, ServiceInstance.class);

		// 回调 LoadBalancerLifecycle#onStart 生命周期方法
		supportedLifecycleProcessors.forEach(lifecycle -> lifecycle.onStart(lbRequest));

		/**
		 * 负载均衡选择出唯一的 serviceInstance
		 * {@link BlockingLoadBalancerClient#choose(String, Request)}
		 * */
		ServiceInstance serviceInstance = choose(serviceId, lbRequest);

		// 实例为空，就报错
		if (serviceInstance == null) {
			// 回调 LoadBalancerLifecycle#onComplete 生命周期方法
			supportedLifecycleProcessors.forEach(lifecycle -> lifecycle.onComplete(
					new CompletionContext<>(CompletionContext.Status.DISCARD, lbRequest, new EmptyResponse())));
			throw new IllegalStateException("No instances available for " + serviceId);
		}
		// 使用 实例 执行请求
		return execute(serviceId, serviceInstance, lbRequest);
	}

	@Override
	public <T> T execute(String serviceId, ServiceInstance serviceInstance, LoadBalancerRequest<T> request)
			throws IOException {
		// 装饰一下 serviceInstance
		DefaultResponse defaultResponse = new DefaultResponse(serviceInstance);

		// 从 loadBalancerClientFactory 中获取 LoadBalancerLifecycle 类型的bean
		Set<LoadBalancerLifecycle> supportedLifecycleProcessors = LoadBalancerLifecycleValidator
				.getSupportedLifecycleProcessors(
						loadBalancerClientFactory.getInstances(serviceId, LoadBalancerLifecycle.class),
						DefaultRequestContext.class, Object.class, ServiceInstance.class);
		Request lbRequest = request instanceof Request ? (Request) request : new DefaultRequest<>();

		// 回调 LoadBalancerLifecycle#onStartRequest 生命周期方法
		supportedLifecycleProcessors
				.forEach(lifecycle -> lifecycle.onStartRequest(lbRequest, new DefaultResponse(serviceInstance)));
		try {
			/**
			 * 执行请求
			 * {@link LoadBalancerRequestFactory#createRequest(HttpRequest, byte[], ClientHttpRequestExecution)}
			 * */
			T response = request.apply(serviceInstance);
			Object clientResponse = getClientResponse(response);
			// 回调 LoadBalancerLifecycle#onComplete 生命周期方法
			supportedLifecycleProcessors
					.forEach(lifecycle -> lifecycle.onComplete(new CompletionContext<>(CompletionContext.Status.SUCCESS,
							lbRequest, defaultResponse, clientResponse)));
			return response;
		}
		catch (IOException iOException) {
			supportedLifecycleProcessors.forEach(lifecycle -> lifecycle.onComplete(
					new CompletionContext<>(CompletionContext.Status.FAILED, iOException, lbRequest, defaultResponse)));
			throw iOException;
		}
		catch (Exception exception) {
			supportedLifecycleProcessors.forEach(lifecycle -> lifecycle.onComplete(
					new CompletionContext<>(CompletionContext.Status.FAILED, exception, lbRequest, defaultResponse)));
			ReflectionUtils.rethrowRuntimeException(exception);
		}
		return null;
	}

	private <T> Object getClientResponse(T response) {
		ClientHttpResponse clientHttpResponse = null;
		if (response instanceof ClientHttpResponse) {
			clientHttpResponse = (ClientHttpResponse) response;
		}
		if (clientHttpResponse != null) {
			try {
				return new ResponseData(clientHttpResponse, null);
			}
			catch (IOException ignored) {
			}
		}
		return response;
	}

	@Override
	public URI reconstructURI(ServiceInstance serviceInstance, URI original) {
		return LoadBalancerUriTools.reconstructURI(serviceInstance, original);
	}

	@Override
	public ServiceInstance choose(String serviceId) {
		return choose(serviceId, REQUEST);
	}

	@Override
	public <T> ServiceInstance choose(String serviceId, Request<T> request) {
		/**
		 * 通过 loadBalancerClientFactory 获取 ReactiveLoadBalancer 实例。
		 *
		 * 其实是会构造一个IOC容器，而IOC容器会注册这个配置类 {@link LoadBalancerClientConfiguration}
		 * 这个配置的目的是生成 ServiceInstanceListSupplier、ReactorLoadBalancer
		 * 然后从IOC容器中获取 ReactiveLoadBalancer 类型的bean
		 *
		 * 注：ReactorLoadBalancer 依赖 ServiceInstanceListSupplier 得到 List<ServiceInstance> 然后根据其负载均衡策略得到唯一的 serviceInstance
		 * 		而 ServiceInstanceListSupplier 默认是通过获取 DiscoveryClient 得到 List<ServiceInstance>，然后根据 ServiceInstanceListSupplier
		 * 		的逻辑过滤掉一些
		 * */
		ReactiveLoadBalancer<ServiceInstance> loadBalancer = loadBalancerClientFactory.getInstance(serviceId);
		if (loadBalancer == null) {
			return null;
		}
		// 选择出 ServiceInstance
		Response<ServiceInstance> loadBalancerResponse = Mono.from(loadBalancer.choose(request)).block();
		if (loadBalancerResponse == null) {
			return null;
		}
		return loadBalancerResponse.getServer();
	}

	private String getHint(String serviceId) {
		String defaultHint = properties.getHint().getOrDefault("default", "default");
		String hintPropertyValue = properties.getHint().get(serviceId);
		return hintPropertyValue != null ? hintPropertyValue : defaultHint;
	}

}
