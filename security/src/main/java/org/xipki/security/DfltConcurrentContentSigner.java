// Copyright (c) 2013-2024 xipki. All rights reserved.
// License Apache License 2.0

package org.xipki.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.util.Args;
import org.xipki.util.CollectionUtil;
import org.xipki.util.LogUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An implementation of {@link ConcurrentContentSigner}.
 *
 * @author Lijun Liao (xipki)
 * @since 2.0.0
 */

public class DfltConcurrentContentSigner implements ConcurrentContentSigner {

  private static final Logger LOG = LoggerFactory.getLogger(DfltConcurrentContentSigner.class);

  private static final AtomicInteger NAME_INDEX = new AtomicInteger(1);

  private static int defaultSignServiceTimeout = 10000; // 10 seconds

  private final ArrayBlockingQueue<XiContentSigner> signers;

  private final String name;

  private final SignAlgo algorithm;

  private final boolean mac;

  private byte[] sha1OfMacKey;

  private final Key signingKey;

  private PublicKey publicKey;

  private X509Cert[] certificateChain;

  static {
    final String propKey = "org.xipki.security.signservice.timeout";
    String str = System.getProperty(propKey);

    if (str != null) {
      int vi = Integer.parseInt(str);
      // valid value is between 0 and 60 seconds
      if (vi < 0 || vi > 60 * 1000) {
        LOG.error("invalid {}: {}", propKey, vi);
      } else {
        LOG.info("use {}: {}", propKey, vi);
        defaultSignServiceTimeout = vi;
      }
    }
  } // method static

  public DfltConcurrentContentSigner(boolean mac, List<XiContentSigner> signers) throws NoSuchAlgorithmException {
    this(mac, signers, null);
  }

  public DfltConcurrentContentSigner(boolean mac, List<XiContentSigner> signers, Key signingKey)
      throws NoSuchAlgorithmException {
    Args.notEmpty(signers, "signers");

    this.mac = mac;
    this.algorithm = SignAlgo.getInstance(signers.get(0).getAlgorithmIdentifier());
    this.signers = new ArrayBlockingQueue<>(signers.size());
    this.signers.addAll(signers);

    this.signingKey = signingKey;
    this.name = "defaultSigner-" + NAME_INDEX.getAndIncrement();
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public boolean isMac() {
    return mac;
  }

  public void setSha1DigestOfMacKey(byte[] digest) {
    if (digest == null) {
      this.sha1OfMacKey = null;
    } else if (digest.length == 20) {
      this.sha1OfMacKey = Arrays.copyOf(digest, 20);
    } else {
      throw new IllegalArgumentException("invalid sha1Digest.length (" + digest.length + " != 20)");
    }
  }

  @Override
  public byte[] getSha1OfMacKey() {
    return (sha1OfMacKey == null) ? null : Arrays.copyOf(sha1OfMacKey, 20);
  }

  @Override
  public SignAlgo getAlgorithm() {
    return algorithm;
  }

  @Override
  public XiContentSigner borrowSigner() throws NoIdleSignerException {
    return borrowSigner(defaultSignServiceTimeout);
  }

  /**
   * Borrows a signer.
   *
   * @param soTimeout timeout in milliseconds, 0 for infinitely.
   */
  @Override
  public XiContentSigner borrowSigner(int soTimeout) throws NoIdleSignerException {
    XiContentSigner signer = null;
    try {
      signer = signers.poll(soTimeout, TimeUnit.MILLISECONDS);
    } catch (InterruptedException ex) {
    }

    return Optional.ofNullable(signer).orElseThrow(
        () -> new NoIdleSignerException("no idle signer available"));
  }

  @Override
  public void requiteSigner(XiContentSigner signer) {
    signers.add(signer);
  }

  @Override
  public void initialize(String conf) throws XiSecurityException {
  }

  @Override
  public Key getSigningKey() {
    return signingKey;
  }

  @Override
  public void setCertificateChain(X509Cert[] certificateChain) {
    if (CollectionUtil.isEmpty(certificateChain)) {
      this.certificateChain = null;
      return;
    }

    this.certificateChain = certificateChain;
    setPublicKey(certificateChain[0].getPublicKey());
  }

  @Override
  public PublicKey getPublicKey() {
    return publicKey;
  }

  @Override
  public void setPublicKey(PublicKey publicKey) {
    this.publicKey = publicKey;
  }

  @Override
  public X509Cert getCertificate() {
    return CollectionUtil.isEmpty(certificateChain) ? null : certificateChain[0];
  }

  @Override
  public X509Cert[] getCertificateChain() {
    return certificateChain;
  }

  @Override
  public boolean isHealthy() {
    XiContentSigner signer = null;
    try {
      signer = borrowSigner();
      OutputStream stream = signer.getOutputStream();
      stream.write(new byte[]{1, 2, 3, 4});
      byte[] signature = signer.getSignature();
      return signature != null && signature.length > 0;
    } catch (Exception ex) {
      LogUtil.error(LOG, ex);
      return false;
    } finally {
      if (signer != null) {
        requiteSigner(signer);
      }
    }
  }

  @Override
  public void close() {
  }

  @Override
  public byte[] sign(byte[] data) throws NoIdleSignerException, SignatureException {
    XiContentSigner signer = borrowSigner();
    try {
      OutputStream signatureStream = signer.getOutputStream();
      try {
        signatureStream.write(data);
      } catch (IOException ex) {
        throw new SignatureException("could not write data to SignatureStream: " + ex.getMessage(), ex);
      }
      return signer.getSignature();
    } finally {
      requiteSigner(signer);
    }
  } // method sign

  @Override
  public byte[][] sign(byte[][] data) throws NoIdleSignerException, SignatureException {
    byte[][] signatures = new byte[data.length][];
    XiContentSigner signer = borrowSigner();

    try {
      for (int i = 0; i < data.length; i++) {
        OutputStream signatureStream = signer.getOutputStream();
        try {
          signatureStream.write(data[i]);
        } catch (IOException ex) {
          throw new SignatureException("could not write data to SignatureStream: " + ex.getMessage(), ex);
        }
        signatures[i] = signer.getSignature();
      }
    } finally {
      requiteSigner(signer);
    }

    return signatures;
  } // method sign

}
