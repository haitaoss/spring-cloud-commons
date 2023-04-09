package cn.haitaoss.ServiceRegisterAndLoadBalance.loadbalancer;

import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-04-04 09:38
 * 指定不同服务的，负载均衡策略
 */
@LoadBalancerClient(name = "s1",
        configuration = {MyLoadBalancer.class, MyServiceInstanceListSupplier.class})
@LoadBalancerClients({@LoadBalancerClient(name = "s2",
        configuration = MyRandomLoadBalancer.class), @LoadBalancerClient(name = "s3",
        configuration = MyRoundRobinLoadBalancer.class),})
public class LoadBalancerClientConfig {}
