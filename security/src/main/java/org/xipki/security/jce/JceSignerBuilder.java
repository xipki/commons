// Copyright (c) 2013-2023 xipki. All rights reserved.
// License Apache License 2.0

package org.xipki.security.jce;

import org.xipki.security.*;
import org.xipki.security.util.X509Util;

import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.cert.CertPathBuilderException;
import java.util.*;

import static org.xipki.util.Args.notNull;
import static org.xipki.util.Args.positive;

/**
 * Builder of {@link ConcurrentContentSigner} for PKCS#11 token.
 *
 * @author Lijun Liao (xipki)
 * @since 2.0.0
 */

public class JceSignerBuilder {

  private final PrivateKey privateKey;

  private final PublicKey publicKey;

  private final String providerName;

  private final Provider provider;

  private final X509Cert[] certificateChain;

  public JceSignerBuilder(PrivateKey privateKey, PublicKey publicKey, X509Cert[] certificateChain, String providerName)
      throws XiSecurityException {
    this(privateKey, publicKey, certificateChain, providerName, null);
  }

  public JceSignerBuilder(PrivateKey privateKey, PublicKey publicKey, X509Cert[] certificateChain, Provider provider)
      throws XiSecurityException {
    this(privateKey, publicKey, certificateChain, null, provider);
  }

  private JceSignerBuilder(PrivateKey privateKey, PublicKey publicKey, X509Cert[] certificateChain,
                           String providerName, Provider provider)
      throws XiSecurityException {
    this.privateKey = notNull(privateKey, "privateKey");
    this.publicKey = notNull(publicKey, "publicKey");
    this.providerName = providerName;
    this.provider = provider;

    X509Cert[] chain = null;
    X509Cert cert;
    if (certificateChain != null && certificateChain.length > 0) {
      final int n = certificateChain.length;
      cert = certificateChain[0];
      if (n > 1) {
        Set<X509Cert> caCerts = new HashSet<>(Arrays.asList(certificateChain).subList(1, n));

        try {
          chain = X509Util.buildCertPath(cert, caCerts);
        } catch (CertPathBuilderException ex) {
          throw new XiSecurityException(ex);
        }
      }
    }

    this.certificateChain = chain;
  } // constructor

  public ConcurrentContentSigner createSigner(SignAlgo signAlgo, int parallelism)
      throws XiSecurityException {
    positive(parallelism, "parallelism");

    List<XiContentSigner> signers = new ArrayList<>(parallelism);

    for (int i = 0; i < parallelism; i++) {
      XiContentSigner signer = new JceSigner(privateKey, signAlgo, providerName, provider);
      signers.add(signer);
    }

    DfltConcurrentContentSigner concurrentSigner;
    try {
      concurrentSigner = new DfltConcurrentContentSigner(false, signers, privateKey);
    } catch (NoSuchAlgorithmException ex) {
      throw new XiSecurityException(ex.getMessage(), ex);
    }

    if (certificateChain != null) {
      concurrentSigner.setCertificateChain(certificateChain);
    } else {
      concurrentSigner.setPublicKey(publicKey);
    }

    return concurrentSigner;
  } // method createSigner

}
