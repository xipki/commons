// Copyright (c) 2013-2023 xipki. All rights reserved.
// License Apache License 2.0

package org.xipki.util.http;

import java.util.*;

/**
 *
 * @author Lijun Liao (xipki)
 * @since 6.0.0
 */

public class HttpResponse {

  private final int statusCode;

  private final String contentType;

  private final Map<String, List<String>> headers;

  private final boolean base64;

  private final byte[] body;

  public HttpResponse(int statusCode) {
    this(statusCode, null, null, false, null);
  }

  public HttpResponse(int statusCode, String contentType, Map<String, String> headers, byte[] body) {
    this(statusCode, contentType, headers, false, body);
  }

  public HttpResponse(int statusCode, String contentType, Map<String, String> headers, boolean base64, byte[] body) {
    this.statusCode = statusCode;
    this.base64 = base64;
    this.contentType = contentType;
    this.headers = new HashMap<>();
    if (headers != null) {
      for (Map.Entry<String, String> m : headers.entrySet()) {
        this.headers.put(m.getKey(), Arrays.asList(m.getValue()));
      }
    }
    this.body = body;
  }

  public boolean isBase64() {
    return base64;
  }

  public int getStatusCode() {
    return statusCode;
  }

  public String getContentType() {
    return contentType;
  }

  public Map<String, List<String>> getHeaders() {
    return headers;
  }

  public byte[] getBody() {
    return body;
  }
  public HttpResponse putHeader(String name, String value) {
    List<String> values = headers.computeIfAbsent(name, k -> new ArrayList<>(1));
    values.add(value);
    return this;
  }

}
