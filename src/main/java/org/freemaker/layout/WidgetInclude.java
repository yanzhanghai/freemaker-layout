package org.freemaker.layout;

import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.ui.ExtendedModelMap;

import freemarker.template.Configuration;
import freemarker.template.TemplateModelException;

public class WidgetInclude {
	private String suffix;

	public void setSuffix(String suffix) {
		this.suffix = suffix;
	}

	private ApplicationContext context;
	private Configuration configuration;
	private static ThreadLocal<HttpServletRequest> t = new ThreadLocal<HttpServletRequest>();
	private static Map<String, Method> methodCache = new HashMap<String, Method>();

	public Object include(String w,Object... arguments)
			throws TemplateModelException {
		if (StringUtils.isEmpty(w)) {
			throw new TemplateModelException("arguments is error");
		}
		if(arguments==null){
			arguments=new Object[0];
		}
		try {
			ExtendedModelMap model = new ExtendedModelMap();
			Method m = methodCache.get(w);
			Object widget = context.getBean(w);
			if (m == null) {
				Class<?>[] parameterTypes = new Class[arguments.length+2];
				parameterTypes[0]=t.get().getClass();
				parameterTypes[1]=ExtendedModelMap.class;
				int i = 2;
				for (Object o : arguments) {
					parameterTypes[i] = o.getClass();
					i++;
				}
				m = MethodUtils.getMatchingAccessibleMethod(widget.getClass(),
						"exec", parameterTypes);
				if (m == null) {
					throw new TemplateModelException(
							"widget {} has no exec method");
				}
				methodCache.put(w, m);
			}
			Object[] args=new Object[2];
			args[0]=t.get();
			args[1]=model;
			String templatePath =(String)m.invoke(widget, ArrayUtils.addAll(args, arguments));
			templatePath = new StringBuffer(templatePath).append(suffix)
					.toString();
			StringWriter result = new StringWriter();
			configuration.getTemplate(templatePath, t.get().getLocale())
					.process(model, result);
			return result.toString();
		} catch (Exception e) {
			throw new TemplateModelException(e);
		}
	}

	public void setApplicationContext(ApplicationContext context) {
		this.context = context;
	}

	public void setRequest(HttpServletRequest requset) {
		t.set(requset);
	}

	public Configuration getConfiguration() {
		return configuration;
	}

	public void setConfiguration(Configuration configuration) {
		this.configuration = configuration;
	}

}
