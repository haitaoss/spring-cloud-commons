package cn.haitaoss.utils;

import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerUriTools;
import org.springframework.cloud.client.loadbalancer.reactive.ExchangeFilterFunctionUtils;
import org.springframework.web.reactive.function.client.ClientRequest;

import java.net.URI;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-04-11 08:53
 *
 */
public class Main {
    public static void main(String[] args) {
        /**
         * 负载均衡的HTTP请求，会使用这个工具类根据 ServiceInstance 的值替换 原始URL中的 协议名、主机名和端口
         *
         * 比如：
         *      {@link ExchangeFilterFunctionUtils#buildClientRequest(ClientRequest, ServiceInstance, String, boolean)}
         * */
        URI original = URI.create("http://s1:8090/name?key=x&value=v1");
        DefaultServiceInstance serviceInstance = new DefaultServiceInstance();
        serviceInstance.setUri(URI.create("http://localhost:8080/xx"));
        // 只会 替换 schema、host、post 其他的都会保留
        URI uri = LoadBalancerUriTools.reconstructURI(serviceInstance, original);
        System.out.println("uri = " + uri);
    }
}
