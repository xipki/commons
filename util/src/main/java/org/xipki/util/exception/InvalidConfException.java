// Copyright (c) 2013-2023 xipki. All rights reserved.
// License Apache License 2.0

package org.xipki.util.exception;

/**
 * Invalid configuration exception.
 *
 * @author Lijun Liao (xipki)
 * @since 2.0.0
 */

public class InvalidConfException extends Exception {

  public InvalidConfException(String message) {
    super(message);
  }

  public InvalidConfException(String message, Throwable cause) {
    super(message, cause);
  }

}
