// Copyright (c) 2013-2023 xipki. All rights reserved.
// License Apache License 2.0

package org.xipki.security.pkcs11;

import org.xipki.security.HashAlgo;

import static org.xipki.pkcs11.wrapper.PKCS11Constants.*;
/**
 * PKCS#11 params.
 *
 * @author Lijun Liao (xipki)
 * @since 2.0.0
 */

public interface P11Params {

  class P11ByteArrayParams implements P11Params {

    private final byte[] bytes;

    public P11ByteArrayParams(byte[] bytes) {
      this.bytes = bytes;
    }

    public byte[] getBytes() {
      return bytes;
    }

  }

  class P11RSAPkcsPssParams implements P11Params {

    private final long hashAlgorithm;

    private final long maskGenerationFunction;

    private final int saltLength;

    public P11RSAPkcsPssParams(long hashAlgorithm, long maskGenerationFunction, int saltLength) {
      this.hashAlgorithm = hashAlgorithm;
      this.maskGenerationFunction = maskGenerationFunction;
      this.saltLength = saltLength;
    }

    public P11RSAPkcsPssParams(HashAlgo hashAlgo) {
      this.saltLength = hashAlgo.getLength();

      switch (hashAlgo) {
        case SHA1:
          this.hashAlgorithm = CKM_SHA_1;
          this.maskGenerationFunction = CKG_MGF1_SHA1;
          break;
        case SHA224:
          this.hashAlgorithm = CKM_SHA224;
          this.maskGenerationFunction = CKG_MGF1_SHA224;
          break;
        case SHA256:
          this.hashAlgorithm = CKM_SHA256;
          this.maskGenerationFunction = CKG_MGF1_SHA256;
          break;
        case SHA384:
          this.hashAlgorithm = CKM_SHA384;
          this.maskGenerationFunction = CKG_MGF1_SHA384;
          break;
        case SHA512:
          this.hashAlgorithm = CKM_SHA512;
          this.maskGenerationFunction = CKG_MGF1_SHA512;
          break;
        case SHA3_224:
          this.hashAlgorithm = CKM_SHA3_224;
          this.maskGenerationFunction = CKG_MGF1_SHA3_224;
          break;
        case SHA3_256:
          this.hashAlgorithm = CKM_SHA3_256;
          this.maskGenerationFunction = CKG_MGF1_SHA3_256;
          break;
        case SHA3_384:
          this.hashAlgorithm = CKM_SHA3_384;
          this.maskGenerationFunction = CKG_MGF1_SHA3_384;
          break;
        case SHA3_512:
          this.hashAlgorithm = CKM_SHA3_512;
          this.maskGenerationFunction = CKG_MGF1_SHA3_512;
          break;
        default:
          throw new IllegalStateException("unsupported Hash algorithm " + hashAlgo);
      }
    }

    public long getHashAlgorithm() {
      return hashAlgorithm;
    }

    public long getMaskGenerationFunction() {
      return maskGenerationFunction;
    }

    public int getSaltLength() {
      return saltLength;
    }

  }

}
