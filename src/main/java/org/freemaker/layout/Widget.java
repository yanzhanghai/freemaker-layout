package org.freemaker.layout;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.ui.Model;

public interface Widget {
	public String exec(HttpServletRequest request,Model model,Map<String,Object> param);

	public String exec(HttpServletRequest httpServletRequest,Model model);
}
