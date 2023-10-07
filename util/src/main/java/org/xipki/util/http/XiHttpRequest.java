// Copyright (c) 2013-2023 xipki. All rights reserved.
// License Apache License 2.0
package org.xipki.util.http;

import java.io.IOException;
import java.io.InputStream;

/**
 * HTTP request.
 *
 * @author Lijun Liao (xipki)
 */
public interface XiHttpRequest {

  String getHeader(String headerName);

  String getParameter(String paramName);

  String getMethod();

  String getServletPath();

  String getContentType();

  Object getAttribute(String name);

  String getRequestURI();

  InputStream getInputStream() throws IOException;

  void setAttribute(String name, String value);

  String getContextPath();

}
