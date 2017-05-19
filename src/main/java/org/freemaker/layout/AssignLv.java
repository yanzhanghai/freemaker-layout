package org.freemaker.layout;

import java.util.List;

import freemarker.template.SimpleHash;
import freemarker.template.TemplateMethodModelEx;
import freemarker.template.TemplateModelException;

public class AssignLv implements TemplateMethodModelEx {
	private ThreadLocal<SimpleHash> t=new ThreadLocal<SimpleHash>();
	@SuppressWarnings("rawtypes")
	@Override
	public Object exec(List arguments) throws TemplateModelException {
		if (arguments == null || arguments.size() != 2) {
			throw new TemplateModelException("arguments error");
		}
		if(t.get()==null){
			throw new TemplateModelException("fmModel error");
		}
		t.get().put(arguments.get(0).toString(), arguments.get(1));
		return "";
	}

	public SimpleHash getFmModel() {
		return t.get();
	}

	public void setFmModel(SimpleHash fmModel) {
		this.t.set(fmModel);
	}

}
