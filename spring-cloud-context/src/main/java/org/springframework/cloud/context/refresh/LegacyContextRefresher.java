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

package org.springframework.cloud.context.refresh;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.cloud.bootstrap.BootstrapApplicationListener;
import org.springframework.cloud.bootstrap.BootstrapConfigFileApplicationListener;
import org.springframework.cloud.context.scope.refresh.RefreshScope;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;

import static org.springframework.cloud.util.PropertyUtils.BOOTSTRAP_ENABLED_PROPERTY;

/**
 * @author Dave Syer
 * @author Venil Noronha
 */
public class LegacyContextRefresher extends ContextRefresher {

	@Deprecated
	public LegacyContextRefresher(ConfigurableApplicationContext context, RefreshScope scope) {
		super(context, scope);
	}

	public LegacyContextRefresher(ConfigurableApplicationContext context, RefreshScope scope,
			RefreshAutoConfiguration.RefreshProperties properties) {
		super(context, scope, properties);
	}

	@Override
	protected void updateEnvironment() {
		addConfigFilesToEnvironment();
	}

	/* For testing. */ ConfigurableApplicationContext addConfigFilesToEnvironment() {
		ConfigurableApplicationContext capture = null;
		try {
			// new 一个新的 Environment
			StandardEnvironment environment = copyEnvironment(getContext().getEnvironment());

			Map<String, Object> map = new HashMap<>();
			map.put("spring.jmx.enabled", false);
			map.put("spring.main.sources", "");
			// gh-678 without this apps with this property set to REACTIVE or SERVLET fail
			map.put("spring.main.web-application-type", "NONE");
			map.put(BOOTSTRAP_ENABLED_PROPERTY, Boolean.TRUE.toString());
			environment.getPropertySources().addFirst(new MapPropertySource(REFRESH_ARGS_PROPERTY_SOURCE, map));
			// 启动一个 SpringBoot 从而实现属性文件的重新加载
			SpringApplicationBuilder builder = new SpringApplicationBuilder(Empty.class).bannerMode(Banner.Mode.OFF)
					.web(WebApplicationType.NONE).environment(environment);
			/**
			 * BootstrapApplicationListener 是用来接收 ApplicationEnvironmentPreparedEvent 事件，然后完成 bootstrap.properties 属性的加载
			 * BootstrapConfigFileApplicationListener 啥事没干，方法是空实现
			 * */
			// Just the listeners that affect the environment (e.g. excluding logging
			// listener because it has side effects)
			builder.application().setListeners(
					Arrays.asList(new BootstrapApplicationListener(), new BootstrapConfigFileApplicationListener()));
			capture = builder.run(); // 启动
			if (environment.getPropertySources().contains(REFRESH_ARGS_PROPERTY_SOURCE)) {
				environment.getPropertySources().remove(REFRESH_ARGS_PROPERTY_SOURCE);
			}
			// 原来的 Environment
			MutablePropertySources target = getContext().getEnvironment().getPropertySources();
			String targetName = null;
			// 遍历新的 environment
			for (PropertySource<?> source : environment.getPropertySources()) {
				String name = source.getName();
				// 存在同名的
				if (target.contains(name)) {
					targetName = name;
				}
				// 不是这些的才需要 替换或者新加
				if (!this.standardSources.contains(name)) {
					if (target.contains(name)) {
						// 将新的内容 替换调原来的
						target.replace(name, source);
					}
					else {
						if (targetName != null) {
							target.addAfter(targetName, source);
							// update targetName to preserve ordering
							targetName = name;
						}
						else {
							// targetName was null so we are at the start of the list
							target.addFirst(source);
							targetName = name;
						}
					}
				}
			}
		}
		finally {
			ConfigurableApplicationContext closeable = capture;
			while (closeable != null) {
				try {
					closeable.close();
				}
				catch (Exception e) {
					// Ignore;
				}
				if (closeable.getParent() instanceof ConfigurableApplicationContext) {
					closeable = (ConfigurableApplicationContext) closeable.getParent();
				}
				else {
					break;
				}
			}
		}
		return capture;
	}

}
