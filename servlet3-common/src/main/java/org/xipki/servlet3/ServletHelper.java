// Copyright (c) 2013-2023 xipki. All rights reserved.
// License Apache License 2.0

package org.xipki.servlet3;

import org.xipki.util.Base64;
import org.xipki.util.CollectionUtil;
import org.xipki.util.http.HttpResponse;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Servlet helper.
 * @author Lijun Liao (xipki)
 */

public class ServletHelper {

  public static void fillResponse(HttpResponse httpResp, HttpServletResponse resp) throws IOException {
    resp.setStatus(httpResp.getStatusCode());
    if (httpResp.getContentType() != null) {
      resp.setContentType(httpResp.getContentType());
    }

    Map<String, List<String>> headers = httpResp.getHeaders();
    if (CollectionUtil.isNotEmpty(headers)) {
      for (Map.Entry<String, List<String>> m : headers.entrySet()) {
        for (String value : m.getValue()) {
          resp.addHeader(m.getKey(), value);
        }
      }
    }

    byte[] body = httpResp.getBody();
    if (body == null || body.length == 0) {
      resp.setContentLength(0);
    } else {
      byte[] content;
      if (httpResp.isBase64()) {
        resp.setHeader("Content-Transfer-Encoding", "base64");
        content = Base64.encodeToByte(body, true);
      } else {
        content = body;
      }

      resp.setContentLength(content.length);
      resp.getOutputStream().write(content);
    }
  }

}
