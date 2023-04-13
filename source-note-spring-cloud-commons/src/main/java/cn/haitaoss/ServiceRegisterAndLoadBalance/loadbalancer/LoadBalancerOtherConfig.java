package cn.haitaoss.ServiceRegisterAndLoadBalance.loadbalancer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.*;
import org.springframework.cloud.client.loadbalancer.reactive.ReactiveLoadBalancer;
import org.springframework.cloud.loadbalancer.blocking.client.BlockingLoadBalancerClient;
import org.springframework.cloud.loadbalancer.blocking.retry.BlockingLoadBalancedRetryFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.support.HttpRequestWrapper;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.backoff.BackOffPolicy;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-04-06 15:31
 *
 */
@Slf4j
public class LoadBalancerOtherConfig {

	/**
	 * 看 {@link LoadBalancerAutoConfiguration} 就知道为啥要加上 @LoadBalanced
	 * @return
	 */
	@Bean
	@LoadBalanced // 得写上这个 才会被增强为负载均衡的
	public RestTemplate restTemplate() {
		return new RestTemplate();
	}

	/**
	 * 响应式负载均衡
	 * @param customizerProvider
	 * @return
	 */
	@Bean
	@LoadBalanced
	public WebClient.Builder webClientBuilder(ObjectProvider<WebClientCustomizer> customizerProvider) {
		WebClient.Builder builder = WebClient.builder();
		customizerProvider.orderedStream().forEach((customizer) -> customizer.customize(builder));
		return builder;
	}

	@Bean
	@ConditionalOnMissingBean
	public WebClientCustomizer webClientCustomizer() {
		return new WebClientCustomizer() {
			@Override
			public void customize(WebClient.Builder webClientBuilder) {
				webClientBuilder.filter((req, next) -> {
					final ClientRequest.Builder request = ClientRequest.from(req);
					request.header("token", "haitaoss");
					return next.exchange(request.build());
				});
			}
		};
	}

	/**
	 *
	 * 对于 RetryLoadBalancerInterceptor 会依赖 LoadBalancedRetryFactory 用来生成 重试相关的参数
	 * {@link LoadBalancerAutoConfiguration.RetryInterceptorAutoConfiguration#loadBalancerInterceptor(LoadBalancerClient, LoadBalancerProperties, LoadBalancerRequestFactory, LoadBalancedRetryFactory, ReactiveLoadBalancer.Factory)}
	 * @return
	 */
	@Bean
	public LoadBalancedRetryFactory retryFactory(ReactiveLoadBalancer.Factory<ServiceInstance> loadBalancerFactory) {
		return new BlockingLoadBalancedRetryFactory(loadBalancerFactory) {
			@Override
			public BackOffPolicy createBackOffPolicy(String service) {
				return super.createBackOffPolicy(service);
			}

			@Override
			public LoadBalancedRetryPolicy createRetryPolicy(String serviceId,
					ServiceInstanceChooser serviceInstanceChooser) {
				return super.createRetryPolicy(serviceId, serviceInstanceChooser);
			}

			@Override
			public RetryListener[] createRetryListeners(String service) {
				return new RetryListener[] { new RetryListener() {
					@Override
					public <T, E extends Throwable> boolean open(RetryContext context, RetryCallback<T, E> callback) {
						return false;
					}

					@Override
					public <T, E extends Throwable> void close(RetryContext context, RetryCallback<T, E> callback,
							Throwable throwable) {

					}

					@Override
					public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback,
							Throwable throwable) {

					}
				} };
			}
		};
	}

	/**
	 * 用来增强 RestTemplate
	 * {@link LoadBalancerAutoConfiguration#loadBalancedRestTemplateInitializerDeprecated(ObjectProvider)}
	 * @return
	 */
	public RestTemplateCustomizer restTemplateCustomizer() {
		return new RestTemplateCustomizer() {
			@Override
			public void customize(RestTemplate restTemplate) {
				List<ClientHttpRequestInterceptor> list = new ArrayList<>(restTemplate.getInterceptors());
				list.add(new ClientHttpRequestInterceptor() {
					@Override
					public ClientHttpResponse intercept(HttpRequest request, byte[] body,
							ClientHttpRequestExecution execution) throws IOException {
						log.info("restTemplateCustomizer.intercept... 拦截执行");
						return execution.execute(request, body);
					}
				});
				restTemplate.setInterceptors(list);
			}
		};
	}

	/**
	 * 执行负载均衡请求时会回调 LoadBalancerLifecycle 的方法，可以在这里修改request信息
	 * {@link BlockingLoadBalancerClient#execute(String, LoadBalancerRequest)}
	 *
	 * 这么注册是全局的，每个 serviceInstance 都会用到。如果想为不同的 serviceInstance 设置 单独的，可以通过
	 * LoadBalancerClientSpecification、@LoadBalancerClient 配置
	 * @return
	 */
	@Bean
	public LoadBalancerLifecycle<Object, Object, ServiceInstance> loadBalancerLifecycle() {
		return new LoadBalancerLifecycle<Object, Object, ServiceInstance>() {
			@Override
			public void onStart(Request<Object> request) {
				log.info("loadBalancerLifecycle.onStart...负载均衡前");
			}

			@Override
			public void onStartRequest(Request<Object> request, Response<ServiceInstance> lbResponse) {
				log.info("loadBalancerLifecycle.onStartRequest...负载均衡后确定了serviceInstance，准备执行请求了");
			}

			@Override
			public void onComplete(CompletionContext<Object, ServiceInstance, Object> completionContext) {
				log.info("loadBalancerLifecycle.onComplete...处理结束后");
			}
		};
	}

	/**
	 * 负载均衡 RestTemplate 执行请求时 会使用这个 对Request进行最后的转换
	 *
	 * {@link LoadBalancerRequestFactory#createRequest(HttpRequest, byte[], ClientHttpRequestExecution)}
	 * @return
	 */
	@Bean
	public LoadBalancerRequestTransformer transformer() {
		return new LoadBalancerRequestTransformer() {
			@Override
			public HttpRequest transformRequest(HttpRequest request, ServiceInstance instance) {
				return new HttpRequestWrapper(request) {
					@Override
					public HttpHeaders getHeaders() {
						HttpHeaders headers = new HttpHeaders();
						headers.putAll(super.getHeaders());
						headers.add("X-InstanceId", instance.getInstanceId());
						log.info("LoadBalancerRequestTransformer.transformRequest...");
						return headers;
					}
				};
			}
		};
	}

}
