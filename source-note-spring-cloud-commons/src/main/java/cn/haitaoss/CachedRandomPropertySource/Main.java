package cn.haitaoss.CachedRandomPropertySource;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.util.random.CachedRandomPropertySource;
import org.springframework.cloud.util.random.CachedRandomPropertySourceEnvironmentPostProcessor;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-03-17 16:13
 *
 */
@SpringBootApplication
public class Main {

	/**
	 * CachedRandomPropertySource (缓存随机属性源) Spring Cloud Context提供了一个基于键缓存随机值的
	 * PropertySource 。 除了缓存功能之外，它的工作原理与Spring靴子的 RandomValuePropertySource 相同。
	 * 如果您希望随机值在Spring Application上下文重新启动后保持一致，那么这个随机值可能会很有用。属性值采用
	 * cachedrandom.[yourkey].[type] 的形式， 其中 yourkey 是该高速缓存中的键。 type 值可以是Spring靴子的
	 * RandomValuePropertySource 支持的任何类型。
	 *
	 * 说白了 就是在本地进程中缓存一个随机值
	 */
	public static void main(String[] args) {
		/**
		 * {@link CachedRandomPropertySourceEnvironmentPostProcessor}
		 * {@link CachedRandomPropertySource}
		 * {@link CachedRandomPropertySource#getProperty(String)}
		 */
		System.setProperty("server.port", "9090");
		System.setProperty("spring.cloud.bootstrap.enabled", "true");
		ConfigurableApplicationContext context = SpringApplication.run(Main.class);
		ConfigurableEnvironment environment = context.getEnvironment();
		System.out.println(environment.getProperty("cachedrandom.app1.long"));
		System.out.println(environment.getProperty("cachedrandom.app1.long"));
		System.out.println(environment.getProperty("cachedrandom.app1.long"));
		System.out.println("===========");
		System.out.println(environment.getProperty("cachedrandom.app2.long"));
		System.out.println(environment.getProperty("cachedrandom.app2.long"));
		System.out.println(environment.getProperty("cachedrandom.app2.long"));
	}

}
