// Copyright (c) 2013-2023 xipki. All rights reserved.
// License Apache License 2.0

package org.xipki.servlet3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.util.LogUtil;
import org.xipki.util.exception.ServletException0;
import org.xipki.util.http.XiHttpFilter;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Filter.
 *
 * @author Lijun Liao (xipki)
 * @since 6.0.0
 */
public abstract class ServletFilter implements Filter {

  private static final Logger LOG = LoggerFactory.getLogger(ServletFilter.class);

  private XiHttpFilter filter0;

  protected abstract XiHttpFilter initFilter(FilterConfig filterConfig) throws ServletException0;

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    try {
      filter0 = initFilter(filterConfig);
    } catch (ServletException0 ex) {
      LOG.error("error initializing ServletFiler", ex);
      throw new ServletException(ex);
    }
  }

  @Override
  public void destroy() {
    if (filter0 != null) {
      filter0.destroy();
      filter0 = null;
    }
  }

  @Override
  public final void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (filter0 == null) {
      throw new ServletException("filter is not initialized");
    }

    if (!(request instanceof HttpServletRequest & response instanceof HttpServletResponse)) {
      throw new ServletException("Only HTTP request is supported");
    }

    try {
      filter0.doFilter(new XiHttpRequestImpl((HttpServletRequest) request),
          new XiHttpResponseImpl((HttpServletResponse) response));
    } catch (ServletException0 ex) {
      throw new ServletException(ex);
    }
  }

}
