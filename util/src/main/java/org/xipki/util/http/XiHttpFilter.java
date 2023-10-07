// Copyright (c) 2013-2023 xipki. All rights reserved.
// License Apache License 2.0
package org.xipki.util.http;

import org.xipki.util.exception.ServletException0;

import java.io.IOException;

/**
 * HTTP filter.
 *
 * @author Lijun Liao (xipki)
 */
public interface XiHttpFilter {

  void destroy();

  void doFilter(XiHttpRequest request, XiHttpResponse response) throws IOException, ServletException0;

}
