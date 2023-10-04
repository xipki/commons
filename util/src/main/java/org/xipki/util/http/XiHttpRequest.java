// Copyright (c) 2013-2023 xipki. All rights reserved.
// License Apache License 2.0

package org.xipki.security.util;

import org.xipki.security.X509Cert;

import java.io.IOException;
import java.io.InputStream;

/**
 * Interface to retrieve the metadata of HTTP request.
 *
 * @author Lijun Liao (xipki)
 * @since 3.0.1
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
}
