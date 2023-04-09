package cn.haitaoss.ServiceRegisterAndLoadBalance;

import cn.haitaoss.ServiceRegisterAndLoadBalance.common.Base;
import cn.haitaoss.ServiceRegisterAndLoadBalance.common.BaseApp;
import cn.haitaoss.ServiceRegisterAndLoadBalance.loadbalancer.LoadBalancerClientConfig;
import cn.haitaoss.ServiceRegisterAndLoadBalance.loadbalancer.LoadBalancerOtherConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-03-17 14:45
 *
 */
@Base
@Import({LoadBalancerClientConfig.class, LoadBalancerOtherConfig.class})
public class Main extends BaseApp {
    public static void main(String[] args) {
        /**
         * TODOHAITAO: 2023/4/7
         * 验证方式 运行 Main、Client1、Client2
         * 然后访问:
         *      - 堵塞式 GET http://localhost:8080/s1
         *      - 响应式 GET http://localhost:8080/2/s1
         * */
        // 采用那种方式对 RestTemplate 进行增强，看 org.springframework.cloud.client.loadbalancer.LoadBalancerAutoConfiguration
        System.setProperty("spring.cloud.loadbalancer.retry.enabled", "false");
        System.setProperty("spring.profiles.active", "loadbalance");
        ConfigurableApplicationContext context = SpringApplication.run(Main.class);
    }
}

