/*
 * Copyright 2022-Present Entando Inc. (http://www.entando.com) All rights reserved.
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
package org.entando.entando.aps.system.services.controller.control;

import org.springframework.mock.web.MockHttpServletRequest;

import com.agiletec.aps.BaseTestCase;
import com.agiletec.aps.system.EntThreadLocal;
import com.agiletec.aps.system.RequestContext;
import com.agiletec.aps.system.SystemConstants;
import com.agiletec.aps.system.services.controller.ControllerManager;
import com.agiletec.aps.system.services.controller.control.ControlServiceInterface;
import com.agiletec.aps.system.services.lang.ILangManager;
import com.agiletec.aps.system.services.lang.Lang;
import javax.servlet.ServletContext;
import org.entando.entando.aps.system.services.tenant.ITenantManager;
import org.entando.entando.ent.exception.EntException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.context.WebApplicationContext;

/**
 * @author E.Santoboni
 */
class TestTenantController extends BaseTestCase {

    private ControlServiceInterface tenantController;

    @BeforeEach
    void init() throws Exception {
        try {
            this.tenantController = this.getApplicationContext().getBean(TenantController.class);
        } catch (Throwable e) {
            throw new Exception(e);
        }
    }

    @Test
    void testService_1() throws EntException {
        EntThreadLocal.init();
        RequestContext reqCtx = this.createExtRequestContext("tenant1.test.serv.run", this.getApplicationContext());
        int status = this.tenantController.service(reqCtx, ControllerManager.CONTINUE);
        Assertions.assertEquals(ControllerManager.CONTINUE, status);
        Assertions.assertEquals("tenant1", EntThreadLocal.get(ITenantManager.THREAD_LOCAL_TENANT_CODE));
    }
    
    @Test
    void testService_2() throws EntException {
        EntThreadLocal.init();
        RequestContext reqCtx = this.createExtRequestContext("test.serv.run", this.getApplicationContext());
        int status = this.tenantController.service(reqCtx, ControllerManager.CONTINUE);
        Assertions.assertEquals(ControllerManager.CONTINUE, status);
        Assertions.assertNull(EntThreadLocal.get(ITenantManager.THREAD_LOCAL_TENANT_CODE));
    }
    
    public RequestContext createExtRequestContext(String serverName, ApplicationContext applicationContext) {
        RequestContext reqCtx = this.getRequestContext();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName(serverName);
        request.addHeader("Host", serverName);
        request.setContextPath("/Entando");
        request.setAttribute(RequestContext.REQCTX, reqCtx);
        reqCtx.setRequest(request);
        return reqCtx;
    }

}
