package cn.haitaoss.ServiceRegisterAndLoadBalance.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import javax.servlet.http.HttpServletRequest;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-04-04 10:02
 *
 */
@Slf4j
public class BaseApp {
    @Autowired(required = false)
    // @LoadBalanced 容器就只有一个实例，所以写不写都行
    private RestTemplate restTemplate;

    @Autowired
    private WebClient.Builder webClientBuilder;

    @Autowired
    @Lazy
    private HttpServletRequest request;

    @RequestMapping("/{serviceName}")
    public Object call(@PathVariable("serviceName") String serviceName) {
        return restTemplate.getForEntity(String.format("http://%s/name", serviceName), String.class)
                .getBody();
    }

    @RequestMapping("/2/{serviceName}")
    public Object call2(@PathVariable("serviceName") String serviceName) {
        return webClientBuilder.build()
                .get()
                .uri(String.format("http://%s/name", serviceName))
                .header("x", "x")
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    @Value("${spring.application.name}")
    private String name;

    @RequestMapping("/name")
    public Object name() {
        log.info("请求体信息--->{}", request.getHeader("token"));
        return name;
    }
}
