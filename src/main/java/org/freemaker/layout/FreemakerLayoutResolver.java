package org.freemaker.layout;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.support.RequestContextUtils;
import org.springframework.web.servlet.view.AbstractTemplateViewResolver;
import org.springframework.web.servlet.view.AbstractUrlBasedView;
import org.springframework.web.servlet.view.freemarker.FreeMarkerView;

import freemarker.template.SimpleHash;
import freemarker.template.Template;

public class FreemakerLayoutResolver extends AbstractTemplateViewResolver {
	private static LayoutConfig layoutConfig;

	public FreemakerLayoutResolver() {
		initLayoutConfig();
		setViewClass(requiredViewClass());
	}

	public void initLayoutConfig() {
		if (layoutConfig == null) {
			layoutConfig = new LayoutConfig();
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	protected Class requiredViewClass() {
		return FreeMarkerLayoutView.class;
	}

	protected AbstractUrlBasedView buildView(String viewName) throws Exception {
		AbstractUrlBasedView view=super.buildView(viewName);
		WidgetInclude wInclude=((FreeMarkerLayoutView)view).wInclude;
		wInclude.setSuffix(getSuffix());
		wInclude.setApplicationContext(getApplicationContext());
		return view;
	}

	static class FreeMarkerLayoutView extends FreeMarkerView {
		private AssignLv assignLv = new AssignLv(); // 在模板中可以设置一些变量，在layout中使用
		private WidgetInclude wInclude = new WidgetInclude();

		protected Template getLayout(String path, Locale locale)
				throws IOException {
			String layoutPath = getLayoutUrl(path);
			if (layoutPath == null) {
				return null;
			}
			return getTemplate(layoutPath, locale);
		}

		private String getLayoutUrl(String path) {
			AntPathMatcher pathMatcher = new AntPathMatcher();
			for (String s : layoutConfig.getExcludes()) {
				if (pathMatcher.match(s, path)) {
					return null;
				}
			}
			for (LayoutEntry entry : layoutConfig.getLayouts()) {
				if (pathMatcher.match(entry.getPattern(), path)) {
					return entry.getLayout();
				}
			}
			return layoutConfig.getDefaultLayout();
		}

		@SuppressWarnings("unchecked")
		protected void doRender(@SuppressWarnings("rawtypes") Map model,
				HttpServletRequest request, HttpServletResponse response)
				throws Exception {
			exposeModelAsRequestAttributes(model, request);
			SimpleHash fmModel = buildTemplateModel(model, request, response);
			Locale locale = RequestContextUtils.getLocale(request);
			wInclude.setRequest(request);
			wInclude.setConfiguration(this.getConfiguration());
			fmModel.put("w", wInclude);
			Template layout = getLayout(request.getServletPath(), locale);
			if (layout == null) {// 不需要layout 渲染
				processTemplate(getTemplate(locale), fmModel, response);
				return;
			}
			assignLv.setFmModel(fmModel);
			this.getConfiguration().setSharedVariable("assignLv", assignLv); // body，可以向layout传递一些变量
			Writer writer = new StringWriter();
			Template template = getTemplate(getUrl(), locale);
			template.process(fmModel, writer);
			String body = writer.toString();
			Pattern pattern = Pattern
					.compile("(?<=<body>)[\\S\\s]*(?=</body>)");
			Matcher matcher = pattern.matcher(body);
			if (matcher.find()) {
				fmModel.put("body", matcher.group());
			} else {
				fmModel.put("body", body);
			}
			pattern = Pattern.compile("(?<=<head>)[\\S\\s]*(?=</head>)");
			matcher = pattern.matcher(body);
			if (matcher.find()) {
				String head = matcher.group(0);
				pattern = Pattern.compile("<title.*</title>");
				matcher = pattern.matcher(head);
				String title;
				if (matcher.find()) {
					title = matcher.group();
					head = matcher.replaceAll("");
					fmModel.put("title", title);
				}
				pattern = Pattern.compile("<script[\\s\\S]*?</script>");
				matcher = pattern.matcher(head);
				StringBuffer script = new StringBuffer();
				while (matcher.find()) {
					script.append(matcher.group());
				}
				if (script.length() > 0) {
					head = matcher.replaceAll("");
					fmModel.put("script", script);
				}
				fmModel.put("head", head);
			}
			processTemplate(layout, fmModel, response);
		}
	}

	static class LayoutConfig {
		Logger logger = LoggerFactory.getLogger(getClass());

		@SuppressWarnings("unchecked")
		LayoutConfig() {
			excludes = new ArrayList<String>();
			layouts = new ArrayList<FreemakerLayoutResolver.LayoutEntry>();
			SAXReader reader = new SAXReader();
			Document document = null;
			try {
				document = reader.read(this.getClass().getResourceAsStream(
						"/layouts.xml"));
			} catch (DocumentException e) {
				logger.error("load layouts.xml error", e);
			}
			Element root = document.getRootElement();
			List<Element> elements = root.elements();
			String name = "";
			for (Element element : elements) {
				name = element.getName();
				if ("excludes".equals(name)) {
					List<Element> excludeList = element.elements();
					for (Element exclude : excludeList) {
						this.excludes.add(exclude.getText());
					}
				}
				if ("layout".equals(name)) {
					List<Element> decorators = element.elements();
					for (Element decorator : decorators) {
						this.layouts
								.add(new LayoutEntry(decorator.getTextTrim(),
										element.attributeValue("page")));
					}
				}
				if ("true".equals(element.attributeValue("defalut"))) {
					this.defaultLayout = element.attributeValue("page");
				}
			}
			if (this.defaultLayout == null && this.layouts.size() > 0) {
				this.defaultLayout = this.layouts.get(this.layouts.size() - 1)
						.getLayout();
			}
		}

		private List<String> excludes;
		private List<LayoutEntry> layouts;
		private String defaultLayout;

		public List<String> getExcludes() {
			return excludes;
		}

		public void setExcludes(List<String> excludes) {
			this.excludes = excludes;
		}

		public List<LayoutEntry> getLayouts() {
			return layouts;
		}

		public void setLayouts(List<LayoutEntry> layouts) {
			this.layouts = layouts;
		}

		public String getDefaultLayout() {
			return defaultLayout;
		}

		public void setDefaultLayout(String defaultLayout) {
			this.defaultLayout = defaultLayout;
		}

	}

	static class LayoutEntry {
		private String pattern;
		private String layout;

		LayoutEntry(String pattern, String layout) {
			this.pattern = pattern;
			this.layout = layout;
		}

		public String getPattern() {
			return pattern;
		}

		public void setPattern(String pattern) {
			this.pattern = pattern;
		}

		public String getLayout() {
			return layout;
		}

		public void setLayout(String layout) {
			this.layout = layout;
		}

	}
}
