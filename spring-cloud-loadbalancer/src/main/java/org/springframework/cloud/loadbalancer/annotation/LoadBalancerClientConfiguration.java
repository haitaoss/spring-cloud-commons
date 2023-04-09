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

package org.springframework.cloud.loadbalancer.annotation;

import reactor.util.retry.RetrySpec;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.ConditionalOnBlockingDiscoveryEnabled;
import org.springframework.cloud.client.ConditionalOnDiscoveryEnabled;
import org.springframework.cloud.client.ConditionalOnReactiveDiscoveryEnabled;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.cloud.loadbalancer.core.ReactorLoadBalancer;
import org.springframework.cloud.loadbalancer.core.RetryAwareServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.core.RoundRobinLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author Spencer Gibb
 * @author Olga Maciaszek-Sharma
 * @author Tim Ysewyn
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnDiscoveryEnabled
public class LoadBalancerClientConfiguration {

	private static final int REACTIVE_SERVICE_INSTANCE_SUPPLIER_ORDER = 193827465;

	/**
	 * 反应式负载均衡器。其依赖 ServiceInstanceListSupplier
	 * 设置了 @ConditionalOnMissingBean 条件，若想自定义可以往容器中配置这个类型的bean
	 * @param environment
	 * @param loadBalancerClientFactory
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean
	public ReactorLoadBalancer<ServiceInstance> reactorServiceInstanceLoadBalancer(Environment environment,
			LoadBalancerClientFactory loadBalancerClientFactory) {
		String name = environment.getProperty(LoadBalancerClientFactory.PROPERTY_NAME);
		return new RoundRobinLoadBalancer(
				loadBalancerClientFactory.getLazyProvider(name, ServiceInstanceListSupplier.class), name);
	}

	/**
	 * 默认配置了多种策略的 ServiceInstanceListSupplier，都设置了 @ConditionalOnMissingBean 条件，从而保证只有一个会生效。
	 * 可以通过设置属性 spring.cloud.loadbalancer.configurations 从而制定使用哪一种 ServiceInstanceListSupplier
	 */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnReactiveDiscoveryEnabled
	@Order(REACTIVE_SERVICE_INSTANCE_SUPPLIER_ORDER)
	public static class ReactiveSupportConfiguration {

		@Bean
		@ConditionalOnBean(ReactiveDiscoveryClient.class)
		@ConditionalOnMissingBean
		@ConditionalOnProperty(value = "spring.cloud.loadbalancer.configurations", havingValue = "default",
				matchIfMissing = true)
		public ServiceInstanceListSupplier discoveryClientServiceInstanceListSupplier(
				ConfigurableApplicationContext context) {
			return ServiceInstanceListSupplier.builder().withDiscoveryClient().withCaching().build(context);
		}

		@Bean
		@ConditionalOnBean(ReactiveDiscoveryClient.class)
		@ConditionalOnMissingBean
		@ConditionalOnProperty(value = "spring.cloud.loadbalancer.configurations", havingValue = "zone-preference")
		public ServiceInstanceListSupplier zonePreferenceDiscoveryClientServiceInstanceListSupplier(
				ConfigurableApplicationContext context) {
			return ServiceInstanceListSupplier.builder().withDiscoveryClient().withZonePreference().withCaching()
					.build(context);
		}

		@Bean
		@ConditionalOnBean({ ReactiveDiscoveryClient.class, WebClient.Builder.class })
		@ConditionalOnMissingBean
		@ConditionalOnProperty(value = "spring.cloud.loadbalancer.configurations", havingValue = "health-check")
		public ServiceInstanceListSupplier healthCheckDiscoveryClientServiceInstanceListSupplier(
				ConfigurableApplicationContext context) {
			return ServiceInstanceListSupplier.builder().withDiscoveryClient().withHealthChecks().build(context);
		}

		@Bean
		@ConditionalOnBean(ReactiveDiscoveryClient.class)
		@ConditionalOnMissingBean
		@ConditionalOnProperty(value = "spring.cloud.loadbalancer.configurations",
				havingValue = "request-based-sticky-session")
		public ServiceInstanceListSupplier requestBasedStickySessionDiscoveryClientServiceInstanceListSupplier(
				ConfigurableApplicationContext context) {
			return ServiceInstanceListSupplier.builder().withDiscoveryClient().withRequestBasedStickySession()
					.build(context);
		}

		@Bean
		@ConditionalOnBean(ReactiveDiscoveryClient.class)
		@ConditionalOnMissingBean
		@ConditionalOnProperty(value = "spring.cloud.loadbalancer.configurations",
				havingValue = "same-instance-preference")
		public ServiceInstanceListSupplier sameInstancePreferenceServiceInstanceListSupplier(
				ConfigurableApplicationContext context) {
			return ServiceInstanceListSupplier.builder().withDiscoveryClient().withSameInstancePreference()
					.build(context);
		}

	}

	/**
	 * 同上 {@link ReactiveSupportConfiguration}，只不过构造的是 堵塞式的 ServiceInstanceListSupplier
	 */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnBlockingDiscoveryEnabled
	@Order(REACTIVE_SERVICE_INSTANCE_SUPPLIER_ORDER + 1)
	public static class BlockingSupportConfiguration {

		@Bean
		@ConditionalOnBean(DiscoveryClient.class)
		@ConditionalOnMissingBean
		@ConditionalOnProperty(value = "spring.cloud.loadbalancer.configurations", havingValue = "default",
				matchIfMissing = true)
		public ServiceInstanceListSupplier discoveryClientServiceInstanceListSupplier(
				ConfigurableApplicationContext context) {
			return ServiceInstanceListSupplier.builder().withBlockingDiscoveryClient().withCaching().build(context);
		}

		@Bean
		@ConditionalOnBean(DiscoveryClient.class)
		@ConditionalOnMissingBean
		@ConditionalOnProperty(value = "spring.cloud.loadbalancer.configurations", havingValue = "zone-preference")
		public ServiceInstanceListSupplier zonePreferenceDiscoveryClientServiceInstanceListSupplier(
				ConfigurableApplicationContext context) {
			return ServiceInstanceListSupplier.builder().withBlockingDiscoveryClient().withZonePreference()
					.withCaching().build(context);
		}

		@Bean
		@ConditionalOnBean({ DiscoveryClient.class, RestTemplate.class })
		@ConditionalOnMissingBean
		@ConditionalOnProperty(value = "spring.cloud.loadbalancer.configurations", havingValue = "health-check")
		public ServiceInstanceListSupplier healthCheckDiscoveryClientServiceInstanceListSupplier(
				ConfigurableApplicationContext context) {
			return ServiceInstanceListSupplier.builder().withBlockingDiscoveryClient().withBlockingHealthChecks()
					.build(context);
		}

		@Bean
		@ConditionalOnBean(DiscoveryClient.class)
		@ConditionalOnMissingBean
		@ConditionalOnProperty(value = "spring.cloud.loadbalancer.configurations",
				havingValue = "request-based-sticky-session")
		public ServiceInstanceListSupplier requestBasedStickySessionDiscoveryClientServiceInstanceListSupplier(
				ConfigurableApplicationContext context) {
			return ServiceInstanceListSupplier.builder().withBlockingDiscoveryClient().withRequestBasedStickySession()
					.build(context);
		}

		@Bean
		@ConditionalOnBean(DiscoveryClient.class)
		@ConditionalOnMissingBean
		@ConditionalOnProperty(value = "spring.cloud.loadbalancer.configurations",
				havingValue = "same-instance-preference")
		public ServiceInstanceListSupplier sameInstancePreferenceServiceInstanceListSupplier(
				ConfigurableApplicationContext context) {
			return ServiceInstanceListSupplier.builder().withBlockingDiscoveryClient().withSameInstancePreference()
					.build(context);
		}

	}

	/**
	 * 通过代理模式对 ServiceInstanceListSupplier 进行增强
	 */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnBlockingDiscoveryEnabled
	@ConditionalOnClass(RetryTemplate.class)
	@Conditional(BlockingOnAvoidPreviousInstanceAndRetryEnabledCondition.class)
	@AutoConfigureAfter(BlockingSupportConfiguration.class)
	@ConditionalOnBean(ServiceInstanceListSupplier.class)
	public static class BlockingRetryConfiguration {

		@Bean
		@ConditionalOnBean(DiscoveryClient.class)
		@Primary
		public ServiceInstanceListSupplier retryAwareDiscoveryClientServiceInstanceListSupplier(
				ServiceInstanceListSupplier delegate) {
			return new RetryAwareServiceInstanceListSupplier(delegate);
		}

	}

	/**
	 * 同上 {@link BlockingRetryConfiguration}
	 */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnBlockingDiscoveryEnabled
	@Conditional(ReactiveOnAvoidPreviousInstanceAndRetryEnabledCondition.class)
	@AutoConfigureAfter(ReactiveSupportConfiguration.class)
	@ConditionalOnBean(ServiceInstanceListSupplier.class)
	@ConditionalOnClass(RetrySpec.class)
	public static class ReactiveRetryConfiguration {

		@Bean
		@ConditionalOnBean(DiscoveryClient.class)
		@Primary
		public ServiceInstanceListSupplier retryAwareDiscoveryClientServiceInstanceListSupplier(
				ServiceInstanceListSupplier delegate) {
			return new RetryAwareServiceInstanceListSupplier(delegate);
		}

	}

	static final class BlockingOnAvoidPreviousInstanceAndRetryEnabledCondition extends AllNestedConditions {

		private BlockingOnAvoidPreviousInstanceAndRetryEnabledCondition() {
			super(ConfigurationPhase.REGISTER_BEAN);
		}

		@ConditionalOnProperty(value = "spring.cloud.loadbalancer.retry.enabled", havingValue = "true",
				matchIfMissing = true)
		static class LoadBalancerRetryEnabled {

		}

		@ConditionalOnProperty(value = "spring.cloud.loadbalancer.retry.avoid-previous-instance", havingValue = "true",
				matchIfMissing = true)
		static class AvoidPreviousInstanceEnabled {

		}

	}

	static final class ReactiveOnAvoidPreviousInstanceAndRetryEnabledCondition extends AllNestedConditions {

		private ReactiveOnAvoidPreviousInstanceAndRetryEnabledCondition() {
			super(ConfigurationPhase.REGISTER_BEAN);
		}

		@ConditionalOnProperty(value = "spring.cloud.loadbalancer.retry.enabled", havingValue = "true")
		static class LoadBalancerRetryEnabled {

		}

		@ConditionalOnProperty(value = "spring.cloud.loadbalancer.retry.avoid-previous-instance", havingValue = "true",
				matchIfMissing = true)
		static class AvoidPreviousInstanceEnabled {

		}

	}

}
