package cn.haitaoss.refresh.listener;

import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.ApplicationListener;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-03-08 10:02
 * 监听environment改变
 */
public class EnvironmentChangeEventListener implements ApplicationListener<EnvironmentChangeEvent> {

	@Override
	public void onApplicationEvent(EnvironmentChangeEvent event) {
		System.out.println("EnvironmentChangeEventListener.onApplicationEvent--->" + event);
	}

}
