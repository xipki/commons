// Copyright (c) 2013-2023 xipki. All rights reserved.
// License Apache License 2.0

package org.xipki.servlet5;

import jakarta.servlet.http.HttpServletRequest;
import org.xipki.security.X509Cert;
import org.xipki.security.util.HttpRequestMetadataRetriever;

import java.io.IOException;

/**
 * HTTP request metadata retriever.
 *
 */

public class HttpRequestMetadataRetrieverImpl implements HttpRequestMetadataRetriever {

  private final HttpServletRequest req;

  public HttpRequestMetadataRetrieverImpl(HttpServletRequest req) {
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

}
