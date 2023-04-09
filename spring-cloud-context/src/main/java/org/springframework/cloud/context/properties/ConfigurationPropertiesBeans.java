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

package org.springframework.cloud.context.properties;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.context.properties.ConfigurationPropertiesBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Collects references to <code>@ConfigurationProperties</code> beans in the context and
 * its parent.
 *
 * @author Dave Syer
 */
@Component
public class ConfigurationPropertiesBeans implements BeanPostProcessor, ApplicationContextAware {

	private Map<String, ConfigurationPropertiesBean> beans = new HashMap<>();

	private ApplicationContext applicationContext;

	private ConfigurableListableBeanFactory beanFactory;

	private String refreshScope;

	private boolean refreshScopeInitialized;

	private ConfigurationPropertiesBeans parent;

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
		if (applicationContext.getAutowireCapableBeanFactory() instanceof ConfigurableListableBeanFactory) {
			this.beanFactory = (ConfigurableListableBeanFactory) applicationContext.getAutowireCapableBeanFactory();
		}
		if (applicationContext.getParent() != null && applicationContext.getParent()
				.getAutowireCapableBeanFactory() instanceof ConfigurableListableBeanFactory) {
			// 父容器的 BeanFactory
			ConfigurableListableBeanFactory listable = (ConfigurableListableBeanFactory) applicationContext.getParent()
					.getAutowireCapableBeanFactory();
			// 拿到 父容器 记录的信息
			String[] names = listable.getBeanNamesForType(ConfigurationPropertiesBeans.class);
			if (names.length == 1) {
				this.parent = (ConfigurationPropertiesBeans) listable.getBean(names[0]);
				// 将父容器的记录信息 扩展到当前类
				this.beans.putAll(this.parent.beans);
			}
		}
	}

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		// 是 refreshScope 的bean 就不管
		if (isRefreshScoped(beanName)) {
			return bean;
		}
		// bean 有 @ConfigurationProperties 就不会返回 null
		ConfigurationPropertiesBean propertiesBean = ConfigurationPropertiesBean.get(this.applicationContext, bean,
				beanName);
		if (propertiesBean != null) {
			// 记录下来 有@ConfigurationProperties注解 标注的bean
			this.beans.put(beanName, propertiesBean);
		}
		return bean;
	}

	private boolean isRefreshScoped(String beanName) {
		if (this.refreshScope == null && !this.refreshScopeInitialized) {
			this.refreshScopeInitialized = true;
			for (String scope : this.beanFactory.getRegisteredScopeNames()) {
				if (this.beanFactory.getRegisteredScope(
						scope) instanceof org.springframework.cloud.context.scope.refresh.RefreshScope) {
					this.refreshScope = scope;
					break;
				}
			}
		}
		if (beanName == null || this.refreshScope == null) {
			return false;
		}
		// 是 refreshScope 的bean
		return this.beanFactory.containsBeanDefinition(beanName)
				&& this.refreshScope.equals(this.beanFactory.getBeanDefinition(beanName).getScope());
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		return bean;
	}

	public Set<String> getBeanNames() {
		return new HashSet<String>(this.beans.keySet());
	}

}
