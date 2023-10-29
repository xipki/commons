// Copyright (c) 2013-2023 xipki. All rights reserved.
// License Apache License 2.0

package org.xipki.security.pkcs11.hsmproxy;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.pkcs11.wrapper.MechanismInfo;
import org.xipki.pkcs11.wrapper.PKCS11KeyId;
import org.xipki.pkcs11.wrapper.TokenException;
import org.xipki.pkcs11.wrapper.params.ExtraParams;
import org.xipki.security.pkcs11.P11Key;
import org.xipki.security.pkcs11.P11ModuleConf.P11MechanismFilter;
import org.xipki.security.pkcs11.P11ModuleConf.P11NewObjectConf;
import org.xipki.security.pkcs11.P11Params;
import org.xipki.security.pkcs11.P11Slot;
import org.xipki.security.pkcs11.P11SlotId;
import org.xipki.security.pkcs11.hsmproxy.ProxyMessage.*;
import org.xipki.security.util.KeyUtil;
import org.xipki.util.LogUtil;
import org.xipki.util.cbor.ByteArrayCborEncoder;
import org.xipki.util.exception.EncodeException;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.List;
import java.util.Map;

/**
 * {@link P11Slot} based on the HSM proxy.
 *
 * @author Lijun Liao (xipki)
 */
class HsmProxyP11Slot extends P11Slot {

  private static final Logger LOG = LoggerFactory.getLogger(HsmProxyP11Slot.class);

  private final HsmProxyP11Module module;

  HsmProxyP11Slot(P11SlotId slotId, boolean readOnly, HsmProxyP11Module module, P11MechanismFilter mechanismFilter,
                  P11NewObjectConf newObjectConf, List<Long> secretKeyTypes, List<Long> keyPairTypes)
      throws TokenException {
    super(module.getName(), slotId, readOnly, secretKeyTypes, keyPairTypes, newObjectConf);

    this.module = module;
    initMechanisms(getSupportedMechanisms(), mechanismFilter);
  }

  private Map<Long, MechanismInfo> getSupportedMechanisms() throws TokenException {
    GetMechanismInfosResponse resp = (GetMechanismInfosResponse) send(ProxyAction.mechInfos, null);
    return resp == null ? null : resp.getMechamismInfoMap();
  }

  @Override
  public final void close() {
  }

  @Override
  public P11Key getKey(PKCS11KeyId keyId) throws TokenException {
    KeyIdMessage req = new KeyIdMessage(keyId);
    return toP11Key(send(ProxyAction.keyByKeyId, req));
  }

  @Override
  public P11Key getKey(byte[] keyId, String keyLabel) throws TokenException {
    IdLabelMessage req = new IdLabelMessage(keyId, keyLabel);
    return toP11Key(send(ProxyAction.keyByIdLabel, req));
  }

  @Override
  public PKCS11KeyId getKeyId(byte[] keyId, String keyLabel) throws TokenException {
    IdLabelMessage req = new IdLabelMessage(keyId, keyLabel);
    return toPKCS11KeyId(send(ProxyAction.keyIdByIdLabel, req));
  }

  @Override
  public byte[] sign(long mechanism, P11Params params, ExtraParams extraParams,
                     long keyHandle, byte[] content) throws TokenException {
    SignRequest req = new SignRequest(keyHandle, mechanism, params, extraParams, content);
    ByteArrayMessage resp = (ByteArrayMessage) send(ProxyAction.sign, req);
    return resp == null ? null : resp.getValue();
  }

  @Override
  public PublicKey getPublicKey(long handle) throws TokenException {
    LongMessage req = new LongMessage(handle);
    ByteArrayMessage resp = (ByteArrayMessage) send(ProxyAction.publicKeyByHandle, req);
    try {
      return resp == null ? null : KeyUtil.generatePublicKey(
          SubjectPublicKeyInfo.getInstance(resp.getValue()));
    } catch (InvalidKeySpecException ex) {
      throw new TokenException("error parsing SubjectPublicKeyInfo", ex);
    }
  }

  @Override
  public byte[] digestSecretKey(long mechanism, long handle) throws TokenException {
    DigestSecretKeyRequest req = new DigestSecretKeyRequest(mechanism, handle);
    ByteArrayMessage resp = ((ByteArrayMessage) send(ProxyAction.digestSecretKey, req));
    return resp == null ? null : resp.getValue();
  }

  @Override
  public boolean objectExistsByIdLabel(byte[] id, String label) throws TokenException {
    IdLabelMessage req = new IdLabelMessage(id, label);
    return ((BooleanMessage) send(ProxyAction.objectExistsByIdLabel, req)).getValue();
  }

  @Override
  public int destroyAllObjects() {
    try {
      return ((IntMessage) send(ProxyAction.destroyAllObjects, null)).getValue();
    } catch (TokenException e) {
      LogUtil.warn(LOG, e, "error destroyAllObjects()");
      return 0;
    }
  }

  @Override
  public long[] destroyObjectsByHandle(long[] handles) {
    LongArrayMessage req = new LongArrayMessage(handles);
    try {
      LongArrayMessage resp = ((LongArrayMessage) send(ProxyAction.destroyObjectsByHandle, req));
      return resp == null ? null : resp.getValue();
    } catch (Exception e) {
      LogUtil.warn(LOG, e, "error destroyObjectsByHandle()");
      return handles.clone();
    }
  }

  @Override
  public int destroyObjectsByIdLabel(byte[] id, String label) throws TokenException {
    IdLabelMessage req = new IdLabelMessage(id, label);
    try {
      return ((IntMessage) send(ProxyAction.destroyObjectsByIdLabel, req)).getValue();
    } catch (TokenException e) {
      LogUtil.warn(LOG, e, "error destroyAllObjects()");
      return 0;
    }
  }

  @Override
  public PKCS11KeyId generateSecretKey(long keyType, Integer keysize, P11NewKeyControl control)
      throws TokenException {
    GenerateSecretKeyRequest req = new GenerateSecretKeyRequest(keyType, keysize, control);
    return toPKCS11KeyId(send(ProxyAction.genSecretKey, req));
  } // method generateSecretKey0

  @Override
  public PKCS11KeyId importSecretKey(long keyType, byte[] keyValue, P11NewKeyControl control)
      throws TokenException {
    ImportSecretKeyRequest req = new ImportSecretKeyRequest(keyType, keyValue, control);
    return toPKCS11KeyId(send(ProxyAction.importSecretKey, req));
  } // method importSecretKey0

  @Override
  public PKCS11KeyId generateRSAKeypair(int keysize, BigInteger publicExponent, P11NewKeyControl control)
      throws TokenException {
    GenerateRSAKeyPairRequest req = new GenerateRSAKeyPairRequest(keysize, publicExponent, control);
    return toPKCS11KeyId(send(ProxyAction.genRSAKeypair, req));
  }

  @Override
  public PrivateKeyInfo generateRSAKeypairOtf(int keysize, BigInteger publicExponent) throws TokenException {
    GenerateRSAKeyPairOtfRequest req = new GenerateRSAKeyPairOtfRequest(keysize, publicExponent);
    return toPrivateKeyInfo(send(ProxyAction.genRSAKeypairOtf, req));
  }

  @Override
  public PKCS11KeyId generateDSAKeypair(int plength, int qlength, P11NewKeyControl control)
      throws TokenException {
    GenerateDSAKeyPairByKeysizeRequest req = new GenerateDSAKeyPairByKeysizeRequest(plength, qlength, control);
    return toPKCS11KeyId(send(ProxyAction.genDSAKeypair2, req));
  }

  @Override
  public PKCS11KeyId generateDSAKeypair(BigInteger p, BigInteger q, BigInteger g, P11NewKeyControl control)
      throws TokenException {
    GenerateDSAKeyPairRequest req = new GenerateDSAKeyPairRequest(p, q, g, control);
    return toPKCS11KeyId(send(ProxyAction.genDSAKeypair, req));
  }

  @Override
  public PrivateKeyInfo generateDSAKeypairOtf(BigInteger p, BigInteger q, BigInteger g) throws TokenException {
    GenerateDSAKeyPairOtfRequest req = new GenerateDSAKeyPairOtfRequest(p, q, g);
    return toPrivateKeyInfo(send(ProxyAction.genDSAKeypairOtf, req));
  }

  @Override
  public PKCS11KeyId generateECKeypair(ASN1ObjectIdentifier curveId, P11NewKeyControl control)
      throws TokenException {
    GenerateECKeyPairRequest req = new GenerateECKeyPairRequest(curveId, control);
    return toPKCS11KeyId(send(ProxyAction.genECKeypair, req));
  }

  @Override
  public PrivateKeyInfo generateECKeypairOtf(ASN1ObjectIdentifier curveId)
      throws TokenException {
    GenerateECKeyPairOtfRequest req = new GenerateECKeyPairOtfRequest(curveId);
    return toPrivateKeyInfo(send(ProxyAction.genECKeypair, req));
  }

  @Override
  public PKCS11KeyId generateSM2Keypair(P11NewKeyControl control) throws TokenException {
    GenerateSM2KeyPairRequest req = new GenerateSM2KeyPairRequest(control);
    return toPKCS11KeyId(send(ProxyAction.genSM2Keypair, req));
  }

  @Override
  public PrivateKeyInfo generateSM2KeypairOtf() throws TokenException {
    return toPrivateKeyInfo(send(ProxyAction.genSM2KeypairOtf, null));
  }

  private P11Key toP11Key(ProxyMessage response) throws TokenException {
    if (response == null) {
      return null;
    }

    if (!(response instanceof P11KeyResponse)) {
      throw new TokenException("response is not a KeyIdMessage");
    }

    return ((P11KeyResponse) response).getP11Key(this);
  }

  private static PKCS11KeyId toPKCS11KeyId(ProxyMessage response) throws TokenException {
    if (response == null) {
      return null;
    }

    if (!(response instanceof KeyIdMessage)) {
      throw new TokenException("response is not a KeyIdMessage");
    }

    return ((KeyIdMessage) response).getKeyId();
  }

  private static PrivateKeyInfo toPrivateKeyInfo(ProxyMessage response) throws TokenException {
    if (response == null) {
      return null;
    }

    if (!(response instanceof ByteArrayMessage)) {
      throw new TokenException("response is not a ByteArrayMessage");
    }

    byte[] bytes = ((ByteArrayMessage) response).getValue();
    try {
      return PrivateKeyInfo.getInstance(bytes);
    } catch (IllegalArgumentException ex) {
      throw new TokenException("invalid PrivateKeyInfo", ex);
    }
  }

  /**
   * The specified stream remains open after this method returns.
   */
  @Override
  public void showDetails(OutputStream stream, Long objectHandle, boolean verbose) throws IOException {
    ShowDetailsRequest req = new ShowDetailsRequest(objectHandle, verbose);
    byte[] details;
    try {
      details = ((ByteArrayMessage) send(ProxyAction.showDetails, req)).getValue();
    } catch (TokenException e) {
      details = ("ERROR: " + e.getMessage()).getBytes(StandardCharsets.UTF_8);
    }
    stream.write(details);
  }

  private ProxyMessage send(ProxyAction action, ProxyMessage request) throws TokenException {
    ByteArrayCborEncoder encoder = new ByteArrayCborEncoder();
    try {
      encoder.writeArrayStart(2);
      // slot id
      encoder.writeInt(slotId.getId());
      if (request == null) {
        encoder.writeNull();
      } else {
        request.encode(encoder);
      }
    } catch (EncodeException ex) {
      throw new TokenException("Encode error while building request", ex);
    } catch (IOException ex) {
      throw new TokenException("IO error while building request", ex);
    }

    return module.send(action, encoder.toByteArray());
  }

  @Override
  protected PKCS11KeyId doGenerateSecretKey(long keyType, Integer keysize, P11NewKeyControl control) {
    throw new UnsupportedOperationException("doGenerateSecretKey() unsupported");
  }

  @Override
  protected PKCS11KeyId doImportSecretKey(long keyType, byte[] keyValue, P11NewKeyControl control) {
    throw new UnsupportedOperationException("doGenerateSecretKey() unsupported");
  }

  @Override
  protected PKCS11KeyId doGenerateDSAKeypair(BigInteger p, BigInteger q, BigInteger g, P11NewKeyControl control) {
    throw new UnsupportedOperationException("doGenerateSecretKey() unsupported");
  }

  @Override
  protected PKCS11KeyId doGenerateECEdwardsKeypair(ASN1ObjectIdentifier curveId, P11NewKeyControl control) {
    throw new UnsupportedOperationException("doGenerateSecretKey() unsupported");
  }

  @Override
  protected PrivateKeyInfo doGenerateECEdwardsKeypairOtf(ASN1ObjectIdentifier curveId) {
    throw new UnsupportedOperationException("doGenerateSecretKey() unsupported");
  }

  @Override
  protected PKCS11KeyId doGenerateECMontgomeryKeypair(ASN1ObjectIdentifier curveId, P11NewKeyControl control) {
    throw new UnsupportedOperationException("doGenerateSecretKey() unsupported");
  }

  @Override
  protected PrivateKeyInfo doGenerateECMontgomeryKeypairOtf(ASN1ObjectIdentifier curveId) {
    throw new UnsupportedOperationException("doGenerateSecretKey() unsupported");
  }

  @Override
  protected PKCS11KeyId doGenerateECKeypair(ASN1ObjectIdentifier curveId, P11NewKeyControl control) {
    throw new UnsupportedOperationException("doGenerateSecretKey() unsupported");
  }

  @Override
  protected PrivateKeyInfo doGenerateECKeypairOtf(ASN1ObjectIdentifier curveId) {
    throw new UnsupportedOperationException("doGenerateSecretKey() unsupported");
  }

  @Override
  protected PKCS11KeyId doGenerateSM2Keypair(P11NewKeyControl control) {
    throw new UnsupportedOperationException("doGenerateSecretKey() unsupported");
  }

  @Override
  protected PrivateKeyInfo doGenerateSM2KeypairOtf() {
    throw new UnsupportedOperationException("doGenerateSecretKey() unsupported");
  }

  @Override
  protected PKCS11KeyId doGenerateRSAKeypair(int keysize, BigInteger publicExponent, P11NewKeyControl control) {
    throw new UnsupportedOperationException("doGenerateSecretKey() unsupported");
  }

  @Override
  protected PrivateKeyInfo doGenerateRSAKeypairOtf(int keysize, BigInteger publicExponent) {
    throw new UnsupportedOperationException("doGenerateSecretKey() unsupported");
  }

  @Override
  protected PrivateKeyInfo generateDSAKeypairOtf0(BigInteger p, BigInteger q, BigInteger g) {
    throw new UnsupportedOperationException("doGenerateSecretKey() unsupported");
  }

}
