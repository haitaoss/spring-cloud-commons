package cn.haitaoss.refresh.endpoint;

import org.springframework.boot.actuate.endpoint.EndpointFilter;
import org.springframework.boot.actuate.endpoint.web.ExposableWebEndpoint;

public class TrueEndpointFilter implements EndpointFilter<ExposableWebEndpoint> {

	@Override
	public boolean match(ExposableWebEndpoint endpoint) {
		return true;
	}

}
