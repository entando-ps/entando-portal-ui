/*
 * Copyright 2015-Present Entando Inc. (http://www.entando.com) All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */
package org.entando.entando.aps.servlet;

import java.io.IOException;
import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.entando.entando.aps.system.services.controller.executor.ExecutorBeanContainer;
import org.entando.entando.aps.system.services.controller.executor.ExecutorServiceInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.agiletec.aps.system.RequestContext;
import com.agiletec.aps.system.SystemConstants;
import com.agiletec.aps.system.services.baseconfig.ConfigInterface;
import com.agiletec.aps.system.services.controller.ControllerManager;
import com.agiletec.aps.util.ApsWebApplicationUtils;
import freemarker.cache.TemplateLoader;

import freemarker.ext.jsp.TaglibFactory;
import freemarker.ext.servlet.AllHttpScopesHashModel;
import freemarker.ext.servlet.ServletContextHashModel;
import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapper;
import freemarker.template.ObjectWrapper;
import freemarker.template.TemplateExceptionHandler;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;
import java.io.Reader;
import java.io.StringReader;
import java.security.SecureRandom;
import org.apache.commons.lang3.StringUtils;
import org.entando.entando.aps.system.services.guifragment.GuiFragment;
import org.entando.entando.aps.system.services.guifragment.IGuiFragmentManager;

/**
 * Servlet di controllo, punto di ingresso per le richieste di pagine del portale.
 * Predispone il contesto di richiesta, invoca il controller e ne gestisce lo stato di uscita.
 * @author M.Diana - W.Ambu
 */
public class ControllerServlet extends freemarker.ext.servlet.FreemarkerServlet {

	private static final Logger _logger = LoggerFactory.getLogger(ControllerServlet.class);

	@Override
	protected void service(HttpServletRequest request, HttpServletResponse response) 
			throws ServletException, IOException {
		request.setCharacterEncoding("UTF-8");
		RequestContext reqCtx = this.initRequestContext(request, response);
		int status = this.controlRequest(request, reqCtx);
		if (status == ControllerManager.REDIRECT) {
			_logger.debug("Redirection");
			this.redirect(reqCtx, response);
		} else if (status == ControllerManager.OUTPUT) {
			_logger.debug("Output");
			try {
				this.initFreemarker(request, response, reqCtx);
				this.executePage(request, reqCtx);
			} catch (Throwable t) {
				_logger.error("Error building response", t);
				throw new ServletException("Error building response", t);
			}
		} else if (status == ControllerManager.ERROR) {
			_logger.debug("Error");
			this.outputError(reqCtx, response);
		} else {
			_logger.error("Error: final status = {} - request: {}",
					ControllerManager.getStatusDescription(status),
					request.getServletPath());
			throw new ServletException("Service not available");
		}
		return;
	}

    protected RequestContext initRequestContext(HttpServletRequest request,
            HttpServletResponse response) {
        RequestContext reqCtx = new RequestContext();
        _logger.debug("Request:" + request.getServletPath());
        request.setAttribute(RequestContext.REQCTX, reqCtx);
        reqCtx.setRequest(request);
        reqCtx.setResponse(response);
        ConfigInterface configManager = (ConfigInterface) ApsWebApplicationUtils.getBean(SystemConstants.BASE_CONFIG_MANAGER, request);
        String cspEnabled = configManager.getParam(SystemConstants.PAR_CSP_ENABLED);
        if (Boolean.TRUE.equals(Boolean.valueOf(cspEnabled))) {
            String currentToken = this.createSecureRandomString();
            reqCtx.addExtraParam(SystemConstants.EXTRAPAR_CSP_NONCE_TOKEN, currentToken);
            String cspParams = "script-src 'nonce-" + currentToken + "'";
            String extraConfig = configManager.getParam(SystemConstants.PAR_CSP_HEADER_EXTRA_CONFIG);
            if (!StringUtils.isBlank(extraConfig)) {
                cspParams += " " + extraConfig;
            }
            response.setHeader("content-security-policy", cspParams);
        }
        return reqCtx;
    }

    public String createSecureRandomString() {
        int leftLimit = 48;
        int rightLimit = 122;
        int targetStringLength = 64;
        SecureRandom rand = new SecureRandom();
        return rand.ints(leftLimit, rightLimit + 1)
                .filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i >= 97))
                .limit(targetStringLength)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

	protected int controlRequest(HttpServletRequest request,
			RequestContext reqCtx) {
		ControllerManager controller = (ControllerManager) ApsWebApplicationUtils.getBean(SystemConstants.CONTROLLER_MANAGER, request);
		int status = controller.service(reqCtx);
		return status;
	}

	protected void initFreemarker(HttpServletRequest request,
			HttpServletResponse response, RequestContext reqCtx)
			throws TemplateModelException {
		Configuration config = new Configuration();
		DefaultObjectWrapper wrapper = new DefaultObjectWrapper();
		config.setTemplateExceptionHandler(TemplateExceptionHandler.HTML_DEBUG_HANDLER);
		config.setObjectWrapper(wrapper);
		config.setTemplateExceptionHandler(TemplateExceptionHandler.DEBUG_HANDLER);
        IGuiFragmentManager fragmentManager = (IGuiFragmentManager) ApsWebApplicationUtils.getBean(SystemConstants.GUI_FRAGMENT_MANAGER, request);
        config.setTemplateLoader(new EntTemplateLoader(fragmentManager));
		TemplateModel templateModel = this.createModel(wrapper, this.getServletContext(), request, response);
		ExecutorBeanContainer ebc = new ExecutorBeanContainer(config, templateModel);
		reqCtx.addExtraParam(SystemConstants.EXTRAPAR_EXECUTOR_BEAN_CONTAINER, ebc);
	}

	protected void executePage(HttpServletRequest request, RequestContext reqCtx) {
		List<ExecutorServiceInterface> executors = (List<ExecutorServiceInterface>) ApsWebApplicationUtils.getBean("ExecutorServices", request);
		for (int i = 0; i < executors.size(); i++) {
			ExecutorServiceInterface executor = executors.get(i);
			executor.service(reqCtx);
		}
	}

	@Override
	protected TemplateModel createModel(ObjectWrapper wrapper, ServletContext servletContext, 
			HttpServletRequest request, HttpServletResponse response) throws TemplateModelException {
		TemplateModel template = super.createModel(wrapper, servletContext, request, response);
		if (template instanceof AllHttpScopesHashModel) {
			AllHttpScopesHashModel hashModel = ((AllHttpScopesHashModel) template);
			ServletContextHashModel servletContextModel = (ServletContextHashModel) hashModel.get(KEY_APPLICATION);
			if (null == servletContextModel.getServlet()) {
				ServletContextHashModel newServletContextModel = new ServletContextHashModel(this, wrapper);
				servletContextModel = new ServletContextHashModel(this, wrapper);
				servletContext.setAttribute(ATTR_APPLICATION_MODEL, servletContextModel);
				TaglibFactory taglibs = new TaglibFactory(servletContext);
				servletContext.setAttribute(ATTR_JSP_TAGLIBS_MODEL, taglibs);
				hashModel.putUnlistedModel(KEY_APPLICATION, newServletContextModel);
				hashModel.putUnlistedModel(KEY_APPLICATION_PRIVATE, newServletContextModel);
			}
		}
		return template;
	}

	protected void redirect(RequestContext reqCtx, HttpServletResponse response)
			throws ServletException {
		try {
			String url = (String) reqCtx.getExtraParam(RequestContext.EXTRAPAR_REDIRECT_URL);
			response.sendRedirect(url);
		} catch (Exception e) {
			throw new ServletException("Service not available", e);
		}
	}

	protected void outputError(RequestContext reqCtx, HttpServletResponse response) throws ServletException {
		try {
			if (!response.isCommitted()) {
				Integer httpErrorCode = (Integer) reqCtx.getExtraParam("errorCode");
				if (httpErrorCode == null) {
					httpErrorCode = new Integer(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
				}
				response.sendError(httpErrorCode.intValue());
			}
		} catch (IOException e) {
			_logger.error("outputError", e);
			throw new ServletException("Service not available");
		}
	}

	private static final String ATTR_APPLICATION_MODEL = ".freemarker.Application";
	private static final String ATTR_JSP_TAGLIBS_MODEL = ".freemarker.JspTaglibs";
    
    protected static class EntTemplateLoader implements TemplateLoader {
        
        private IGuiFragmentManager guiFragmentManager;
        
        protected EntTemplateLoader(IGuiFragmentManager guiFragmentManager) {
            this.guiFragmentManager = guiFragmentManager;
        }

        @Override
        public Object findTemplateSource(String code) throws IOException {
            try {
                GuiFragment fragment = this.guiFragmentManager.getGuiFragment(code);
                if (null != fragment) {
                    return fragment.getCurrentGui();
                }
                return null;
            } catch (Exception e) {
                throw new IOException("Error extracting fragment " + code, e);
            }
        }

        @Override
        public long getLastModified(Object o) {
            //nothing to do
            return -1;
        }

        @Override
        public Reader getReader(Object templateSource, String encoding) throws IOException {
            return new StringReader((String) templateSource);
        }

        @Override
        public void closeTemplateSource(Object o) throws IOException {
            //nothing to do
        }
        
    }

}
