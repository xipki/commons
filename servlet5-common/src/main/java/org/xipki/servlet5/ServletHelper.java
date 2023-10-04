// Copyright (c) 2013-2023 xipki. All rights reserved.
// License Apache License 2.0

package org.xipki.servlet5;

import jakarta.servlet.http.HttpServletResponse;
import org.xipki.util.Base64;
import org.xipki.util.CollectionUtil;
import org.xipki.util.http.XiHttpResponse;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Servlet helper.
 * @author Lijun Liao (xipki)
 */

public class ServletHelper {

  public static void fillResponse(XiHttpResponse restResp, HttpServletResponse resp) throws IOException {
    resp.setStatus(restResp.getStatusCode());
    if (restResp.getContentType() != null) {
      resp.setContentType(restResp.getContentType());
    }

    Map<String, List<String>> headers = restResp.getHeaders();
    if (CollectionUtil.isNotEmpty(headers)) {
      for (Map.Entry<String, List<String>> m : headers.entrySet()) {
        for (String value : m.getValue()) {
          resp.addHeader(m.getKey(), value);
        }
      }
    }

    byte[] body = restResp.getBody();
    if (body == null || body.length == 0) {
      resp.setContentLength(0);
    } else {
      byte[] content;
      if (restResp.isBase64()) {
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
