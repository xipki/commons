// Copyright (c) 2013-2023 xipki. All rights reserved.
// License Apache License 2.0

package org.xipki.servlet5;

import jakarta.servlet.http.HttpServletRequest;
import org.xipki.security.X509Cert;
import org.xipki.util.http.XiHttpRequest;

import java.io.IOException;
import java.io.InputStream;

/**
 * HTTP request metadata retriever.
 * @author Lijun Liao
 */

public class HttpRequestWrapperImpl implements XiHttpRequest {

  private final HttpServletRequest req;

  public HttpRequestWrapperImpl(HttpServletRequest req) {
    this.req = req;
  }

  @Override
  public String getHeader(String headerName) {
    return req.getHeader(headerName);
  }

  @Override
  public String getParameter(String paramName) {
    return req.getParameter(paramName);
  }

  @Override
  public X509Cert getTlsClientCert() throws IOException {
    return ServletHelper.getTlsClientCert(req);
  }

  @Override
  public String getMethod() {
    return req.getMethod();
  }

  @Override
  public String getServletPath() {
    return req.getServletPath();
  }

  @Override
  public String getContentType() {
    return req.getContentType();
  }

  @Override
  public Object getAttribute(String name) {
    return req.getAttribute(name);
  }

  @Override
  public String getRequestURI() {
    return req.getRequestURI();
  }

  @Override
  public InputStream getInputStream() throws IOException {
    return req.getInputStream();
  }

}
