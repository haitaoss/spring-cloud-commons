package cn.haitaoss.refresh.endpoint;

import cn.haitaoss.refresh.controller.HelloController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.annotation.EndpointExtension;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.web.ExposableWebEndpoint;
import org.springframework.boot.actuate.endpoint.web.WebOperation;
import org.springframework.boot.actuate.endpoint.web.servlet.AbstractWebMvcEndpointHandlerMapping;
import org.springframework.cloud.endpoint.RefreshEndpoint;
import org.springframework.stereotype.Component;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-03-15 16:23
 *
 */
@Component
@EndpointExtension(filter = TrueEndpointFilter.class, endpoint = RefreshEndpoint.class)
public class RefreshEndpointExtend {
    @Autowired
    private RefreshEndpoint refreshEndpoint;

    @Autowired
    private HelloController helloController;

    /**
     * {@link AbstractWebMvcEndpointHandlerMapping#registerMappingForOperation(ExposableWebEndpoint, WebOperation)}
     * */
    @ReadOperation
    public Object refresh() {
        // 刷新
        refreshEndpoint.refresh();
        // 简单查看数据
        return helloController.show();
    }
}

