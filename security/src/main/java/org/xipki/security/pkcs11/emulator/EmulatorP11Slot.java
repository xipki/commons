// Copyright (c) 2013-2023 xipki. All rights reserved.
// License Apache License 2.0

package org.xipki.security.pkcs11.emulator;

import org.bouncycastle.asn1.*;
import org.bouncycastle.asn1.gm.GMObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.DSAParameter;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.asn1.x9.ECNamedCurveTable;
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers;
import org.bouncycastle.crypto.params.DSAParameters;
import org.bouncycastle.jcajce.interfaces.EdDSAKey;
import org.bouncycastle.jcajce.interfaces.XDHKey;
import org.bouncycastle.jcajce.provider.asymmetric.util.EC5Util;
import org.bouncycastle.jcajce.provider.asymmetric.util.ECUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.pkcs11.wrapper.Functions;
import org.xipki.pkcs11.wrapper.MechanismInfo;
import org.xipki.pkcs11.wrapper.PKCS11KeyId;
import org.xipki.pkcs11.wrapper.TokenException;
import org.xipki.security.EdECConstants;
import org.xipki.security.HashAlgo;
import org.xipki.security.pkcs11.P11Key;
import org.xipki.security.pkcs11.P11ModuleConf.P11MechanismFilter;
import org.xipki.security.pkcs11.P11ModuleConf.P11NewObjectConf;
import org.xipki.security.pkcs11.P11Slot;
import org.xipki.security.pkcs11.P11SlotId;
import org.xipki.security.util.AlgorithmUtil;
import org.xipki.security.util.KeyUtil;
import org.xipki.util.Hex;
import org.xipki.util.LogUtil;
import org.xipki.util.StringUtil;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.*;
import java.security.interfaces.*;
import java.security.spec.DSAPublicKeySpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.util.*;

import static org.xipki.pkcs11.wrapper.PKCS11Constants.*;
import static org.xipki.util.Args.*;
import static org.xipki.util.IoUtil.read;
import static org.xipki.util.IoUtil.save;

/**
 * {@link P11Slot} for PKCS#11 emulator.
 *
 * @author Lijun Liao (xipki)
 * @since 2.0.0
 */

class EmulatorP11Slot extends P11Slot {

  private static class InfoFilenameFilter implements FilenameFilter {

    @Override
    public boolean accept(File dir, String name) {
      return name.endsWith(INFO_FILE_SUFFIX);
    }

  } // class InfoFilenameFilter

  private static final Logger LOG = LoggerFactory.getLogger(EmulatorP11Slot.class);

  // slotinfo
  private static final String FILE_SLOTINFO = "slot.info";
  private static final String PROP_NAMED_CURVE_SUPPORTED = "namedCurveSupported";

  private static final String DIR_PRIV_KEY = "privkey";
  private static final String DIR_PUB_KEY = "pubkey";
  private static final String DIR_SEC_KEY = "seckey";
  private static final String INFO_FILE_SUFFIX = ".info";
  private static final String VALUE_FILE_SUFFIX = ".value";

  private static final String PROP_ID = "id";
  private static final String PROP_LABEL = "label";
  private static final String PROP_SHA1SUM = "sha1";
  private static final String PROP_ALGO = "algo";
  private static final String PROP_KEYTYPE = "keytype";

  private static final String PROP_ALGORITHM = "algorithm";

  private static final String PROP_KEYSPEC = "keyspec";

  // RSA
  private static final String PROP_RSA_MODUS = "modus";
  private static final String PROP_RSA_PUBLIC_EXPONENT = "publicExponent";

  // DSA
  private static final String PROP_DSA_PRIME = "prime"; // p
  private static final String PROP_DSA_SUBPRIME = "subprime"; // q
  private static final String PROP_DSA_BASE = "base"; // g
  private static final String PROP_DSA_VALUE = "value"; // y

  // EC
  private static final String PROP_EC_PARAMS = "ecParams";
  private static final String PROP_EC_POINT = "ecPoint";

  private static final Map<Long, MechanismInfo> supportedMechs = new HashMap<>();

  private static final FilenameFilter INFO_FILENAME_FILTER = new InfoFilenameFilter();

  private final boolean namedCurveSupported;

  private final File slotDir;

  private final File privKeyDir;

  private final File pubKeyDir;

  private final File secKeyDir;

  private final EmulatorKeyCryptor keyCryptor;

  private final SecureRandom random = new SecureRandom();

  private final int maxSessions;

  static {
    // keypair generation
    long[] mechs = {CKM_DSA_KEY_PAIR_GEN, CKM_RSA_X9_31_KEY_PAIR_GEN,  CKM_RSA_PKCS_KEY_PAIR_GEN,
                    CKM_EC_KEY_PAIR_GEN,  CKM_EC_EDWARDS_KEY_PAIR_GEN, CKM_EC_MONTGOMERY_KEY_PAIR_GEN};
    for (long mech : mechs) {
      supportedMechs.put(mech, new MechanismInfo(0, Integer.MAX_VALUE, CKF_GENERATE_KEY_PAIR));
    }

    // secret key generation
    mechs = new long[]{CKM_GENERIC_SECRET_KEY_GEN, CKM_AES_KEY_GEN, CKM_DES3_KEY_GEN, CKM_GENERIC_SECRET_KEY_GEN};
    for (long mech : mechs) {
      supportedMechs.put(mech, new MechanismInfo(0, Integer.MAX_VALUE, CKF_GENERATE));
    }

    // Digest
    mechs = new long[]{CKM_SHA_1, CKM_SHA224, CKM_SHA256, CKM_SHA384, CKM_SHA512,
        CKM_SHA3_224, CKM_SHA3_256, CKM_SHA3_384, CKM_SHA3_512};
    for (long mech : mechs) {
      supportedMechs.put(mech, new MechanismInfo(0, Integer.MAX_VALUE, CKF_DIGEST));
    }

    // HMAC
    mechs = new long[]{CKM_SHA_1_HMAC,    CKM_SHA224_HMAC,   CKM_SHA256_HMAC,   CKM_SHA384_HMAC,   CKM_SHA512_HMAC,
        CKM_SHA3_224_HMAC, CKM_SHA3_256_HMAC, CKM_SHA3_384_HMAC, CKM_SHA3_512_HMAC};
    for (long mech : mechs) {
      supportedMechs.put(mech, new MechanismInfo(0, Integer.MAX_VALUE, CKF_SIGN | CKF_VERIFY));
    }

    // RSA
    supportedMechs.put(CKM_RSA_X_509, new MechanismInfo(0, Integer.MAX_VALUE,
        CKF_DECRYPT | CKF_ENCRYPT | CKF_SIGN | CKF_VERIFY));
    mechs = new long[]{CKM_RSA_PKCS, CKM_SHA1_RSA_PKCS,     CKM_SHA224_RSA_PKCS,
        CKM_SHA256_RSA_PKCS,       CKM_SHA384_RSA_PKCS,
        CKM_SHA512_RSA_PKCS,       CKM_SHA3_224_RSA_PKCS,   CKM_SHA3_256_RSA_PKCS,
        CKM_SHA3_384_RSA_PKCS,     CKM_SHA3_512_RSA_PKCS,
        CKM_RSA_PKCS_PSS,          CKM_SHA1_RSA_PKCS_PSS,   CKM_SHA224_RSA_PKCS_PSS,   CKM_SHA256_RSA_PKCS_PSS,
        CKM_SHA384_RSA_PKCS_PSS,   CKM_SHA512_RSA_PKCS_PSS, CKM_SHA3_224_RSA_PKCS_PSS, CKM_SHA3_256_RSA_PKCS_PSS,
        CKM_SHA3_384_RSA_PKCS_PSS, CKM_SHA3_512_RSA_PKCS_PSS};

    for (long mech : mechs) {
      supportedMechs.put(mech, new MechanismInfo(0, Integer.MAX_VALUE, CKF_SIGN | CKF_VERIFY));
    }

    // DSA
    mechs = new long[]{CKM_DSA, CKM_DSA_SHA1, CKM_DSA_SHA224,     CKM_DSA_SHA256,     CKM_DSA_SHA384,
        CKM_DSA_SHA512,   CKM_DSA_SHA3_224,   CKM_DSA_SHA3_256,   CKM_DSA_SHA3_384,   CKM_DSA_SHA3_512,
        CKM_ECDSA,        CKM_ECDSA_SHA1,     CKM_ECDSA_SHA224,   CKM_ECDSA_SHA256,   CKM_ECDSA_SHA384,
        CKM_ECDSA_SHA512, CKM_ECDSA_SHA3_224, CKM_ECDSA_SHA3_256, CKM_ECDSA_SHA3_384, CKM_ECDSA_SHA3_512};
    for (long mech : mechs) {
      supportedMechs.put(mech, new MechanismInfo(0, Integer.MAX_VALUE, CKF_SIGN | CKF_VERIFY));
    }

    // EDDSA
    supportedMechs.put(CKM_EDDSA, new MechanismInfo(0, Integer.MAX_VALUE, CKF_SIGN | CKF_VERIFY));

    // SM2
    mechs = new long[]{CKM_VENDOR_SM2_KEY_PAIR_GEN, CKM_VENDOR_SM2_SM3, CKM_VENDOR_SM2};
    for (long mech : mechs) {
      supportedMechs.put(mech, new MechanismInfo(0, Integer.MAX_VALUE, CKF_SIGN | CKF_VERIFY));
    }
  }

  EmulatorP11Slot(
      String moduleName, File slotDir, P11SlotId slotId, boolean readOnly,
      EmulatorKeyCryptor keyCryptor, P11MechanismFilter mechanismFilter, P11NewObjectConf newObjectConf,
      Integer numSessions, List<Long> secretKeyTypes, List<Long> keypairTypes)
      throws TokenException {
    super(moduleName, slotId, readOnly, secretKeyTypes, keypairTypes, newObjectConf);

    this.slotDir = notNull(slotDir, "slotDir");
    this.keyCryptor = notNull(keyCryptor, "privateKeyCryptor");
    this.maxSessions = numSessions == null ? 20 : positive(numSessions, "numSessions");

    this.privKeyDir = new File(slotDir, DIR_PRIV_KEY);
    if (!this.privKeyDir.exists()) {
      this.privKeyDir.mkdirs();
    }

    this.pubKeyDir = new File(slotDir, DIR_PUB_KEY);
    if (!this.pubKeyDir.exists()) {
      this.pubKeyDir.mkdirs();
    }

    this.secKeyDir = new File(slotDir, DIR_SEC_KEY);
    if (!this.secKeyDir.exists()) {
      this.secKeyDir.mkdirs();
    }

    File slotInfoFile = new File(slotDir, FILE_SLOTINFO);
    if (slotInfoFile.exists()) {
      Properties props = loadProperties(slotInfoFile);
      this.namedCurveSupported = Boolean.parseBoolean(props.getProperty(PROP_NAMED_CURVE_SUPPORTED, "true"));
    } else {
      this.namedCurveSupported = true;
    }

    initMechanisms(supportedMechs, mechanismFilter);
  } // constructor

  File slotDir() {
    return slotDir;
  }

  private List<File> getFilesForLabel(File dir, String label) throws TokenException {
    List<File> ret = new LinkedList<>();
    File[] infoFiles = dir.listFiles(INFO_FILENAME_FILTER);
    if (infoFiles != null) {
      for (File infoFile : infoFiles) {
        if (!infoFile.isFile()) {
          continue;
        }

        Properties props = loadProperties(infoFile);
        if (label.equals(props.getProperty("label"))) {
          ret.add(infoFile);
        }
      }
    }

    return ret;
  }

  private PublicKey readPublicKey(byte[] keyId) throws TokenException {
    File pubKeyFile = getInfoFile(pubKeyDir, hex(keyId));
    Properties props = loadProperties(pubKeyFile);

    String algorithm = props.getProperty(PROP_ALGORITHM);
    if (PKCSObjectIdentifiers.rsaEncryption.getId().equals(algorithm)) {
      BigInteger exp = new BigInteger(props.getProperty(PROP_RSA_PUBLIC_EXPONENT), 16);
      BigInteger mod = new BigInteger(props.getProperty(PROP_RSA_MODUS), 16);

      RSAPublicKeySpec keySpec = new RSAPublicKeySpec(mod, exp);
      try {
        return KeyUtil.generateRSAPublicKey(keySpec);
      } catch (InvalidKeySpecException ex) {
        throw new TokenException(ex.getMessage(), ex);
      }
    } else if (X9ObjectIdentifiers.id_dsa.getId().equals(algorithm)) {
      BigInteger prime = new BigInteger(props.getProperty(PROP_DSA_PRIME), 16); // p
      BigInteger subPrime = new BigInteger(props.getProperty(PROP_DSA_SUBPRIME), 16); // q
      BigInteger base = new BigInteger(props.getProperty(PROP_DSA_BASE), 16); // g
      BigInteger value = new BigInteger(props.getProperty(PROP_DSA_VALUE), 16); // y

      DSAPublicKeySpec keySpec = new DSAPublicKeySpec(value, prime, subPrime, base);
      try {
        return KeyUtil.generateDSAPublicKey(keySpec);
      } catch (InvalidKeySpecException ex) {
        throw new TokenException(ex.getMessage(), ex);
      }
    } else if (X9ObjectIdentifiers.id_ecPublicKey.getId().equals(algorithm)) {
      byte[] ecParams = decodeHex(props.getProperty(PROP_EC_PARAMS));
      byte[] asn1EncodedPoint = decodeHex(props.getProperty(PROP_EC_POINT));
      byte[] ecPoint = DEROctetString.getInstance(asn1EncodedPoint).getOctets();
      try {
        return KeyUtil.createECPublicKey(ecParams, ecPoint);
      } catch (InvalidKeySpecException ex) {
        throw new TokenException(ex.getMessage(), ex);
      }
    } else if (EdECConstants.id_X25519.getId().equals(algorithm) || EdECConstants.id_ED25519.getId().equals(algorithm)
        || EdECConstants.id_X448.getId().equals(algorithm)       || EdECConstants.id_ED448.getId().equals(algorithm)) {
      byte[] encodedPoint = decodeHex(props.getProperty(PROP_EC_POINT));
      SubjectPublicKeyInfo pkInfo = new SubjectPublicKeyInfo(
          new AlgorithmIdentifier(new ASN1ObjectIdentifier(algorithm)), encodedPoint);
      try {
        return KeyUtil.generatePublicKey(pkInfo);
      } catch (InvalidKeySpecException ex) {
        throw new TokenException("error  key algorithm " + algorithm);
      }
    } else {
      throw new TokenException("unknown key algorithm " + algorithm);
    }
  } // method readPublicKey

  private Properties loadProperties(File file) throws TokenException {
    try {
      try (InputStream stream = Files.newInputStream(file.toPath())) {
        Properties props = new Properties();
        props.load(stream);
        return props;
      }
    } catch (IOException ex) {
      throw new TokenException("could not load properties from the file " + file.getPath(), ex);
    }
  }

  private static byte[] getKeyIdFromInfoFilename(String fileName) {
    return decodeHex(fileName.substring(0, fileName.length() - INFO_FILE_SUFFIX.length()));
  }

  private static File getInfoFile(File dir, String hextId) {
    return new File(dir, hextId + INFO_FILE_SUFFIX);
  }

  private static File getValueFile(File dir, String hextId) {
    return new File(dir, hextId + VALUE_FILE_SUFFIX);
  }

  @Override
  public void close() {
    LOG.info("close slot " + slotId);
  }

  private boolean removePkcs11Entry(File dir, PKCS11KeyId objectId) throws TokenException {
    byte[] id = objectId.getId();
    String label = objectId.getLabel();
    if (id != null) {
      String hextId = hex(id);
      File infoFile = getInfoFile(dir, hextId);
      if (!infoFile.exists()) {
        return false;
      }

      if (StringUtil.isBlank(label)) {
        return deletePkcs11Entry(dir, id);
      } else {
        Properties props = loadProperties(infoFile);
        return label.equals(props.getProperty("label")) && deletePkcs11Entry(dir, id);
      }
    }

    // id is null, delete all entries with the specified label
    boolean deleted = false;
    File[] infoFiles = dir.listFiles(INFO_FILENAME_FILTER);
    if (infoFiles != null) {
      for (File infoFile : infoFiles) {
        if (!infoFile.isFile()) {
          continue;
        }

        Properties props = loadProperties(infoFile);
        if (label.equals(props.getProperty("label"))) {
          if (deletePkcs11Entry(dir, getKeyIdFromInfoFilename(infoFile.getName()))) {
            deleted = true;
          }
        }
      }
    }

    return deleted;
  } // method removePkcs11Entry

  private static boolean deletePkcs11Entry(File dir, byte[] objectId) {
    String hextId = hex(objectId);
    File infoFile = getInfoFile(dir, hextId);
    boolean b1 = !infoFile.exists() || infoFile.delete();

    File valueFile = getValueFile(dir, hextId);
    boolean b2 = !valueFile.exists() || valueFile.delete();

    return b1 || b2;
  } // method deletePkcs11Entry

  private int deletePkcs11Entry(File dir, byte[] id, String label) throws TokenException {
    if (StringUtil.isBlank(label)) {
      return deletePkcs11Entry(dir, id) ? 1 : 0;
    }

    if (id != null && id.length > 0) {
      String hextId = hex(id);
      File infoFile = getInfoFile(dir, hextId);
      if (!infoFile.exists()) {
        return 0;
      }

      Properties props = loadProperties(infoFile);
      if (!label.equals(props.get(PROP_LABEL))) {
        return 0;
      }

      return deletePkcs11Entry(dir, id) ? 1 : 0;
    }

    File[] infoFiles = dir.listFiles(INFO_FILENAME_FILTER);
    if (infoFiles == null || infoFiles.length == 0) {
      return 0;
    }

    List<byte[]> ids = new LinkedList<>();

    for (File infoFile : infoFiles) {
      Properties props = loadProperties(infoFile);
      if (label.equals(props.getProperty(PROP_LABEL))) {
        ids.add(getKeyIdFromInfoFilename(infoFile.getName()));
      }
    }

    if (ids.isEmpty()) {
      return 0;
    }

    for (byte[] m : ids) {
      deletePkcs11Entry(dir, m);
    }
    return ids.size();
  } // method deletePkcs11Entry

  private PKCS11KeyId savePkcs11SecretKey(byte[] id, String label, long keyType, SecretKey secretKey)
      throws TokenException {
    long handle = random.nextLong() & 0x7FFFFFFFFFFFFFFFL;
    byte[] encryptedValue = keyCryptor.encrypt(secretKey);
    return savePkcs11Entry(CKO_SECRET_KEY, handle, id, label, keyType, secretKey.getAlgorithm(), encryptedValue,
        Integer.toString(secretKey.getEncoded().length * 8));
  }

  private PKCS11KeyId savePkcs11PrivateKey(
      byte[] id, String label, long keyType, PrivateKey privateKey, String keyspec) throws TokenException {
    long handle = random.nextLong() & 0x7FFFFFFFFFFFFFFFL;
    byte[] encryptedPrivKeyInfo = keyCryptor.encrypt(privateKey);
    return savePkcs11Entry(CKO_PRIVATE_KEY, handle, id, label, keyType, privateKey.getAlgorithm(),
        encryptedPrivKeyInfo, keyspec);
  }

  private long savePkcs11PublicKey(byte[] id, String label, long keyType, PublicKey publicKey, String keyspec)
      throws TokenException {
    String hexId = hex(id);
    StringBuilder sb = new StringBuilder(100)
        .append(propertyToString(PROP_ID, hexId))
        .append(propertyToString(PROP_LABEL, label))
        .append(propertyToString(PROP_KEYTYPE, Long.toString(keyType)));

    if (keyspec != null) {
      sb.append(propertyToString(PROP_KEYSPEC, keyspec));
    }

    if (publicKey instanceof RSAPublicKey) {
      RSAPublicKey rsaKey = (RSAPublicKey) publicKey;
      sb.append(propertyToString(PROP_ALGORITHM, PKCSObjectIdentifiers.rsaEncryption.getId()))
          .append(propertyToString(PROP_RSA_MODUS,           rsaKey.getModulus()))
          .append(propertyToString(PROP_RSA_PUBLIC_EXPONENT, rsaKey.getPublicExponent()));
    } else if (publicKey instanceof DSAPublicKey) {
      DSAPublicKey dsaKey = (DSAPublicKey) publicKey;
      sb.append(propertyToString(PROP_ALGORITHM, X9ObjectIdentifiers.id_dsa.getId()))
          .append(propertyToString(PROP_DSA_PRIME,    dsaKey.getParams().getP()))
          .append(propertyToString(PROP_DSA_SUBPRIME, dsaKey.getParams().getQ()))
          .append(propertyToString(PROP_DSA_BASE,     dsaKey.getParams().getG()))
          .append(propertyToString(PROP_DSA_VALUE,    dsaKey.getY()));
    } else if (publicKey instanceof ECPublicKey) {
      sb.append(PROP_ALGORITHM).append('=').append(X9ObjectIdentifiers.id_ecPublicKey.getId()).append('\n');

      ECPublicKey ecKey = (ECPublicKey) publicKey;
      ECParameterSpec paramSpec = ecKey.getParams();

      // ecdsaParams
      org.bouncycastle.jce.spec.ECParameterSpec bcParamSpec = EC5Util.convertSpec(paramSpec);
      ASN1ObjectIdentifier curveOid = ECUtil.getNamedCurveOid(bcParamSpec);
      if (curveOid == null) {
        throw new TokenException("EC public key is not of namedCurve");
      }

      byte[] encodedParams;
      try {
        encodedParams = namedCurveSupported ? curveOid.getEncoded() : ECNamedCurveTable.getByOID(curveOid).getEncoded();
      } catch (IOException | NullPointerException ex) {
        throw new TokenException(ex.getMessage(), ex);
      }

      sb.append(propertyToString(PROP_EC_PARAMS, encodedParams));

      // EC point
      java.security.spec.ECPoint pointW = ecKey.getW();
      int keysize = (paramSpec.getCurve().getField().getFieldSize() + 7) / 8;
      byte[] ecPoint = new byte[1 + keysize * 2];
      ecPoint[0] = 4; // uncompressed
      bigIntToBytes("Wx", pointW.getAffineX(), ecPoint, 1, keysize);
      bigIntToBytes("Wy", pointW.getAffineY(), ecPoint, 1 + keysize, keysize);

      byte[] encodedEcPoint;
      try {
        encodedEcPoint = new DEROctetString(ecPoint).getEncoded();
      } catch (IOException ex) {
        throw new TokenException("could not ASN.1 encode the ECPoint");
      }
      sb.append(propertyToString(PROP_EC_POINT, encodedEcPoint));
    } else if (publicKey instanceof EdDSAKey || publicKey instanceof XDHKey) {
      String algorithm = publicKey.getAlgorithm();
      ASN1ObjectIdentifier curveOid = EdECConstants.getCurveOid(algorithm);
      if (curveOid == null) {
        throw new TokenException("Invalid EdDSA key algorithm " + algorithm);
      }
      sb.append(propertyToString(PROP_ALGORITHM, curveOid.getId()));

      byte[] encodedParams;
      try {
        encodedParams = curveOid.getEncoded();
      } catch (IOException | NullPointerException ex) {
        throw new TokenException(ex.getMessage(), ex);
      }

      sb.append(propertyToString(PROP_EC_PARAMS, encodedParams));

      // EC point
      SubjectPublicKeyInfo spki = SubjectPublicKeyInfo.getInstance(publicKey.getEncoded());
      byte[] encodedEcPoint = spki.getPublicKeyData().getOctets();
      sb.append(propertyToString(PROP_EC_POINT, encodedEcPoint));
    } else {
      throw new IllegalArgumentException("unsupported public key " + publicKey.getClass().getName());
    }

    try {
      save(getInfoFile(pubKeyDir, hexId), StringUtil.toUtf8Bytes(sb.toString()));
    } catch (IOException ex) {
      throw new TokenException(ex.getMessage(), ex);
    }

    return (Arrays.hashCode(id) & 0xFFFFFFFFL) << 8 + 1;
  } // method savePkcs11PublicKey

  private static String propertyToString(String propKey, byte[] propValue) {
    return propKey + "=" + Hex.encode(propValue) + "\n";
  }

  private static String propertyToString(String propKey, String propValue) {
    return propKey + "=" + propValue + "\n";
  }

  private static String propertyToString(String propKey, BigInteger propValue) {
    return propKey + "=" + hex(propValue.toByteArray()) + "\n";
  }

  private static void bigIntToBytes(String numName, BigInteger num, byte[] dest, int destPos, int length)
      throws TokenException {
    if (num.signum() != 1) {
      throw new TokenException(numName + " is not positive");
    }
    byte[] bytes = num.toByteArray();
    if (bytes.length == length) {
      System.arraycopy(bytes, 0, dest, destPos, length);
    } else if (bytes.length < length) {
      System.arraycopy(bytes, 0, dest, destPos + length - bytes.length, bytes.length);
    } else {
      if (bytes.length == length + 1 && bytes[0] == 0) {
        System.arraycopy(bytes, 1, dest, destPos, length);
      } else {
        throw new TokenException("num is too large");
      }
    }
  }

  private PKCS11KeyId savePkcs11Entry(
      long objectClass, long handle, byte[] id, String label, long keyType, String algo, byte[] value, String keyspec)
      throws TokenException {
    notBlank(label, "label");
    notNull(value, "value");

    String hexId = hex(notNull(id, "id"));

    StringBuilder str = new StringBuilder();
    str.append(propertyToString(PROP_ID, hexId))
        .append(propertyToString(PROP_LABEL, label))
        .append(propertyToString(PROP_KEYTYPE, Long.toString(keyType)));

    if (algo != null) {
      str.append(propertyToString(PROP_ALGO, algo));
    }

    if (keyspec != null) {
      str.append(propertyToString(PROP_KEYSPEC, keyspec));
    }

    str.append(propertyToString(PROP_SHA1SUM, HashAlgo.SHA1.hexHash(value)));

    File dir = (objectClass == CKO_SECRET_KEY) ? secKeyDir : privKeyDir;

    try {
      save(getInfoFile(dir, hexId), StringUtil.toUtf8Bytes(str.toString()));
      save(getValueFile(dir, hexId), value);
    } catch (IOException ex) {
      throw new TokenException("could not save " + ckoCodeToName(objectClass).substring(4));
    }

    return new PKCS11KeyId(handle, objectClass, keyType, id, label);
  } // method savePkcs11Entry

  @Override
  public int destroyAllObjects() {
    throw new UnsupportedOperationException("destroyAllObjects() is not supported yet.");
  }

  @Override
  public long[] destroyObjectsByHandle(long[] handles) {
    throw new UnsupportedOperationException("removeObjects(long[] handle) is not supported yet.");
  }

  @Override
  public int destroyObjectsByIdLabel(byte[] id, String label) throws TokenException {
    if ((id == null || id.length == 0) && StringUtil.isBlank(label)) {
      throw new IllegalArgumentException("at least one of id and label may not be null");
    }

    return deletePkcs11Entry(privKeyDir, id, label) +
        deletePkcs11Entry(pubKeyDir, id, label) +
        deletePkcs11Entry(secKeyDir, id, label);
  } // method removeObjects

  @Override
  public P11Key getKey(byte[] keyId, String keyLabel) throws TokenException {
    return getKey(getKeyId(keyId, keyLabel));
  }

  @Override
  public P11Key getKey(PKCS11KeyId keyId) throws TokenException {
    String hexId = hex(keyId.getId());
    long keyType = keyId.getKeyType();

    try {
      EmulatorP11Key ret;
      if (keyId.getObjectCLass() == CKO_SECRET_KEY) {
        File infoFile = getInfoFile(secKeyDir, hexId);
        if (!infoFile.exists()) {
          return null;
        }

        // secret key
        Properties props = loadProperties(infoFile);
        String keyAlgo = props.getProperty(PROP_ALGO);
        byte[] encodedValue = read(getValueFile(secKeyDir, hexId));

        byte[] keyValue = keyCryptor.decrypt(encodedValue);
        SecretKey key = new SecretKeySpec(keyValue, keyAlgo);
        ret = new EmulatorP11Key(this, keyId, key, maxSessions, random);
      } else {
        // keypair
        byte[] encodedValue = read(getValueFile(privKeyDir, hexId));
        PrivateKey privateKey = keyCryptor.decryptPrivateKey(encodedValue);

        Properties props = loadProperties(getInfoFile(pubKeyDir, hexId));

        if (keyType == CKK_RSA) {
          BigInteger mod = new BigInteger(props.getProperty(PROP_RSA_MODUS), 16);
          BigInteger e = new BigInteger(props.getProperty(PROP_RSA_PUBLIC_EXPONENT), 16);
          ret = new EmulatorP11Key(this, keyId, privateKey, maxSessions, random);
          ret.setRsaMParameters(mod, e);
        } else if (keyType == CKK_DSA) {
          BigInteger p = new BigInteger(props.getProperty(PROP_DSA_PRIME), 16); // p
          BigInteger q = new BigInteger(props.getProperty(PROP_DSA_SUBPRIME), 16); // q
          BigInteger g = new BigInteger(props.getProperty(PROP_DSA_BASE), 16); // g
          ret = new EmulatorP11Key(this, keyId, privateKey, maxSessions, random);
          ret.setDsaParameters(p, q, g);
        } else if (keyType == CKK_EC || keyType == CKK_EC_EDWARDS || keyType == CKK_EC_MONTGOMERY) {
          byte[] ecParams = decodeHex(props.getProperty(PROP_EC_PARAMS));
          ASN1ObjectIdentifier curveId = ASN1ObjectIdentifier.getInstance(ecParams);
          ret = new EmulatorP11Key(this, keyId, privateKey, maxSessions, random);
          ret.setEcParams(curveId);
        } else {
          throw new TokenException("unknown key type " + ckkCodeToName(keyType));
        }
      }

      ret.sign(true);
      return ret;
    } catch (Exception e) {
      throw new TokenException(e);
    }
  }

  @Override
  protected PublicKey getPublicKey(P11Key identity) throws TokenException {
    return readPublicKey(identity.getKeyId().getId());
  }

  @Override
  public boolean objectExistsByIdLabel(byte[] id, String label) throws TokenException {
    if (id == null) {
      List<File> files = getFilesForLabel(privKeyDir, label);
      if (files.isEmpty()) {
        files = getFilesForLabel(secKeyDir, label);
      }
      return !files.isEmpty();
    }

    String hexId = hex(id);
    File file = getInfoFile(privKeyDir, hexId);
    if (!file.exists()) {
      file = getInfoFile(secKeyDir, hexId);
    }

    if (!file.exists()) {
      return false;
    }

    if (label != null) {
      Properties props = loadProperties(file);
      String label2 = props.getProperty(PROP_LABEL);
      return label.equals(label2);
    } else {
      return true;
    }
  }

  @Override
  public PKCS11KeyId getKeyId(byte[] keyId, String keyLabel) throws TokenException {
    if ((keyId == null || keyId.length == 0) && StringUtil.isBlank(keyLabel)) {
      return null;
    }

    if (keyId == null) {
      // private keys for given label
      List<File> infoFiles = getFilesForLabel(privKeyDir, keyLabel);
      boolean isSecretKey = infoFiles.isEmpty();
      if (isSecretKey) {
        // secret keys for given label
        infoFiles = getFilesForLabel(secKeyDir, keyLabel);
      }

      long objClass = isSecretKey ? CKO_SECRET_KEY : CKO_PRIVATE_KEY;

      if (infoFiles.isEmpty()) {
        return null;
      } else if (infoFiles.size() > 1) {
        throw new TokenException("found more than 1 " + ckoCodeToName(objClass) + " with label=" + keyLabel);
      }

      File infoFile = infoFiles.get(0);
      keyId = getKeyIdFromInfoFilename(infoFile.getName());
      Properties props = loadProperties(infoFile);

      long keyHandle = (Arrays.hashCode(keyId) & 0xFFFFFFFFL) << 8;
      long keyType   = Long.parseLong(props.getProperty(PROP_KEYTYPE));
      String label = props.getProperty(PROP_LABEL);

      PKCS11KeyId keyObjectId = new PKCS11KeyId(keyHandle, objClass, keyType, keyId, keyLabel);

      Long pubicKeyHandle = null;
      if (!isSecretKey) {
        pubicKeyHandle = keyHandle + 1;
        keyObjectId.setPublicKeyHandle(pubicKeyHandle);
      }

      return keyObjectId;
    } else {
      // keyId != null
      String hexId = hex(keyId);
      File keyInfoFile = getInfoFile(privKeyDir, hexId);
      boolean isSecretKey = !keyInfoFile.exists();
      if (isSecretKey) {
        keyInfoFile = getInfoFile(secKeyDir, hexId);
      }

      if (!keyInfoFile.exists()) {
        return null;
      }

      long objClass = isSecretKey ? CKO_SECRET_KEY : CKO_PRIVATE_KEY;

      byte[] id = getKeyIdFromInfoFilename(keyInfoFile.getName());
      Properties props = loadProperties(keyInfoFile);

      String label = props.getProperty(PROP_LABEL);
      if (keyLabel != null && !keyLabel.equals(label)) {
        // label does not match
        return null;
      }

      keyLabel = label;

      long keyHandle = (Arrays.hashCode(keyId) & 0xFFFFFFFFL) << 8;
      long keyType   = Long.parseLong(props.getProperty(PROP_KEYTYPE));

      Long publicKeyHandle = null;
      if (!isSecretKey) {
        publicKeyHandle = keyHandle + 1;
      }

      PKCS11KeyId objectId = new PKCS11KeyId(keyHandle, objClass, keyType, keyId, keyLabel);
      objectId.setPublicKeyHandle(publicKeyHandle);

      return objectId;
    }
  }

  @Override
  protected PKCS11KeyId doGenerateSecretKey(long keyType, Integer keysize, P11NewKeyControl control)
      throws TokenException {
    if (keysize != null && keysize % 8 != 0) {
      throw new IllegalArgumentException("keysize is not multiple of 8: " + keysize);
    }

    long mech;
    if (CKK_AES == keyType) {
      mech = CKM_AES_KEY_GEN;
    } else if (CKK_DES3 == keyType) {
      mech = CKM_DES3_KEY_GEN;
      keysize = 192;
    } else if (CKK_GENERIC_SECRET == keyType) {
      mech = CKM_GENERIC_SECRET_KEY_GEN;
    } else if (CKK_SHA_1_HMAC == keyType || CKK_SHA224_HMAC   == keyType || CKK_SHA256_HMAC   == keyType
        || CKK_SHA384_HMAC    == keyType || CKK_SHA512_HMAC   == keyType || CKK_SHA3_224_HMAC == keyType
        || CKK_SHA3_256_HMAC  == keyType || CKK_SHA3_384_HMAC == keyType || CKK_SHA3_512_HMAC == keyType) {
      mech = CKM_GENERIC_SECRET_KEY_GEN;
    } else {
      throw new IllegalArgumentException("unsupported key type " + codeToName(Category.CKK, keyType));
    }
    assertMechanismSupported(mech, CKF_GENERATE_KEY_PAIR);

    byte[] keyBytes = new byte[keysize / 8];
    random.nextBytes(keyBytes);
    SecretKey key = new SecretKeySpec(keyBytes, getSecretKeyAlgorithm(keyType));
    return saveSecretP11Entity(keyType, key, control);
  }

  @Override
  protected PKCS11KeyId doImportSecretKey(long keyType, byte[] keyValue, P11NewKeyControl control)
      throws TokenException {
    SecretKey key = new SecretKeySpec(keyValue, getSecretKeyAlgorithm(keyType));
    return saveSecretP11Entity(keyType, key, control);
  }

  private static String getSecretKeyAlgorithm(long keyType) {
    String algorithm = (CKK_GENERIC_SECRET == keyType) ? "generic"
        : (CKK_AES           == keyType) ? "AES"
        : (CKK_SHA_1_HMAC    == keyType) ? "HMACSHA1"
        : (CKK_SHA224_HMAC   == keyType) ? "HMACSHA224"
        : (CKK_SHA256_HMAC   == keyType) ? "HMACSHA256"
        : (CKK_SHA384_HMAC   == keyType) ? "HMACSHA384"
        : (CKK_SHA512_HMAC   == keyType) ? "HMACSHA512"
        : (CKK_SHA3_224_HMAC == keyType) ? "HMACSHA3-224"
        : (CKK_SHA3_256_HMAC == keyType) ? "HMACSHA3-256"
        : (CKK_SHA3_384_HMAC == keyType) ? "HMACSHA3-384"
        : (CKK_SHA3_512_HMAC == keyType) ? "HMACSHA3-512" : null;

    if (algorithm == null) {
      throw new IllegalArgumentException("unsupported keyType " + keyType);
    }

    return algorithm;
  }

  @Override
  protected PKCS11KeyId doGenerateRSAKeypair(int keysize, BigInteger publicExponent, P11NewKeyControl control)
      throws TokenException {
    KeyPair keypair;
    try {
      keypair = KeyUtil.generateRSAKeypair(keysize, publicExponent, random);
    } catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidAlgorithmParameterException ex) {
      throw new TokenException(ex.getMessage(), ex);
    }
    return saveKeyPairP11Entity(CKK_RSA, keypair, control, Integer.toString(keysize));
  }

  @Override
  protected PrivateKeyInfo doGenerateRSAKeypairOtf(int keysize, BigInteger publicExponent)
      throws TokenException {
    try {
      KeyPair kp = KeyUtil.generateRSAKeypair(keysize, publicExponent, random);
      return KeyUtil.toPrivateKeyInfo((RSAPrivateCrtKey) kp.getPrivate());
    } catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidAlgorithmParameterException | IOException ex) {
      throw new TokenException(ex.getMessage(), ex);
    }
  }

  @Override
  protected PKCS11KeyId doGenerateDSAKeypair(BigInteger p, BigInteger q, BigInteger g, P11NewKeyControl control)
      throws TokenException {
    DSAParameters dsaParams = new DSAParameters(p, q, g);
    KeyPair keypair;
    try {
      keypair = KeyUtil.generateDSAKeypair(dsaParams, random);
    } catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidAlgorithmParameterException ex) {
      throw new TokenException(ex.getMessage(), ex);
    }
    return saveKeyPairP11Entity(CKK_DSA, keypair, control, Integer.toString(p.bitLength()));
  }

  @Override
  protected PrivateKeyInfo generateDSAKeypairOtf0(BigInteger p, BigInteger q, BigInteger g)
      throws TokenException {
    DSAParameters spec = new DSAParameters(p, q, g);
    try {
      KeyPair kp = KeyUtil.generateDSAKeypair(spec, random);
      DSAParameter parameter = new DSAParameter(spec.getP(), spec.getQ(), spec.getG());
      AlgorithmIdentifier algId = new AlgorithmIdentifier(X9ObjectIdentifiers.id_dsa, parameter);

      byte[] publicKey = new ASN1Integer(((DSAPublicKey) kp.getPublic()).getY()).getEncoded();

      // DSA private keys are represented as BER-encoded ASN.1 type INTEGER.
      DSAPrivateKey priv = (DSAPrivateKey) kp.getPrivate();
      return new PrivateKeyInfo(algId, new ASN1Integer(priv.getX()), null, publicKey);
    } catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidAlgorithmParameterException | IOException ex) {
      throw new TokenException(ex.getMessage(), ex);
    }
  }

  @Override
  protected PKCS11KeyId doGenerateSM2Keypair(P11NewKeyControl control) throws TokenException {
    return doGenerateECKeypair(GMObjectIdentifiers.sm2p256v1, control);
  }

  @Override
  protected PrivateKeyInfo doGenerateSM2KeypairOtf() throws TokenException {
    return doGenerateECKeypairOtf(GMObjectIdentifiers.sm2p256v1);
  }

  @Override
  protected PKCS11KeyId doGenerateECEdwardsKeypair(ASN1ObjectIdentifier curveOid, P11NewKeyControl control)
      throws TokenException {
    KeyPair keypair;
    try {
      if (!EdECConstants.isEdwardsCurve(curveOid)) {
        throw new TokenException("unknown curve  " + curveOid.getId());
      }

      keypair = KeyUtil.generateEdECKeypair(curveOid, random);
    } catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidAlgorithmParameterException ex) {
      throw new TokenException(ex.getMessage(), ex);
    }
    return saveKeyPairP11Entity(CKK_EC_EDWARDS, keypair, control, EdECConstants.getName(curveOid));
  }

  @Override
  protected PrivateKeyInfo doGenerateECEdwardsKeypairOtf(ASN1ObjectIdentifier curveId)
      throws TokenException {
    try {
      KeyPair kp = KeyUtil.generateEdECKeypair(curveId, random);
      return PrivateKeyInfo.getInstance(kp.getPrivate().getEncoded());
    } catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidAlgorithmParameterException ex) {
      throw new TokenException(ex.getMessage(), ex);
    }
  }

  @Override
  protected PKCS11KeyId doGenerateECMontgomeryKeypair(ASN1ObjectIdentifier curveOid, P11NewKeyControl control)
      throws TokenException {
    KeyPair keypair;
    try {
      if (!EdECConstants.isMontgomeryCurve(curveOid)) {
        throw new TokenException("unknown curve  " + curveOid.getId());
      }

      keypair = KeyUtil.generateEdECKeypair(curveOid, random);
    } catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidAlgorithmParameterException ex) {
      throw new TokenException(ex.getMessage(), ex);
    }
    return saveKeyPairP11Entity(CKK_EC_MONTGOMERY, keypair, control, EdECConstants.getName(curveOid));
  }

  @Override
  protected PrivateKeyInfo doGenerateECMontgomeryKeypairOtf(ASN1ObjectIdentifier curveId)
      throws TokenException {
    try {
      KeyPair kp = KeyUtil.generateEdECKeypair(curveId, random);
      return PrivateKeyInfo.getInstance(kp.getPrivate().getEncoded());
    } catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidAlgorithmParameterException ex) {
      throw new TokenException(ex.getMessage(), ex);
    }
  }

  @Override
  protected PKCS11KeyId doGenerateECKeypair(ASN1ObjectIdentifier curveId, P11NewKeyControl control)
      throws TokenException {
    KeyPair keypair;
    try {
      keypair = KeyUtil.generateECKeypair(curveId, random);
    } catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidAlgorithmParameterException ex) {
      throw new TokenException(ex.getMessage(), ex);
    }

    String curveName = AlgorithmUtil.getCurveName(curveId);
    if (curveName == null) {
      curveName = curveId.getId();
    }

    return saveKeyPairP11Entity(CKK_EC, keypair, control, curveName);
  }

  @Override
  protected PrivateKeyInfo doGenerateECKeypairOtf(ASN1ObjectIdentifier curveId) throws TokenException {
    try {
      AlgorithmIdentifier keyAlgId = new AlgorithmIdentifier(X9ObjectIdentifiers.id_ecPublicKey, curveId);

      KeyPair kp = KeyUtil.generateECKeypair(curveId, random);
      ECPublicKey pub = (ECPublicKey) kp.getPublic();

      int fieldBitSize = pub.getParams().getCurve().getField().getFieldSize();
      byte[] publicKey = KeyUtil.getUncompressedEncodedECPoint(pub.getW(), fieldBitSize);

      int orderBitLength = pub.getParams().getOrder().bitLength();

      ECPrivateKey priv = (ECPrivateKey) kp.getPrivate();
      return new PrivateKeyInfo(keyAlgId,
          new org.bouncycastle.asn1.sec.ECPrivateKey(
              orderBitLength, priv.getS(), new DERBitString(publicKey), null));
    } catch (IOException | NoSuchAlgorithmException | NoSuchProviderException | InvalidAlgorithmParameterException ex) {
      throw new TokenException(ex.getMessage(), ex);
    }
  }

  private PKCS11KeyId saveKeyPairP11Entity(long keyType, KeyPair keypair, P11NewObjectControl control, String keySpec)
      throws TokenException {
    byte[] id = control.getId();
    if (id == null) {
      id = generateId();
    }

    String label = control.getLabel();

    long publicKeyHandle = savePkcs11PublicKey(id, label, keyType, keypair.getPublic(), keySpec);
    PKCS11KeyId privateKeyId = savePkcs11PrivateKey(id, label, keyType, keypair.getPrivate(), keySpec);
    privateKeyId.setPublicKeyHandle(publicKeyHandle);
    return privateKeyId;
  }

  private PKCS11KeyId saveSecretP11Entity(long keyType, SecretKey key, P11NewObjectControl control)
      throws TokenException {
    byte[] id = control.getId();
    if (id == null) {
      id = generateId();
    }
    String label = control.getLabel();

    return savePkcs11SecretKey(id, label, keyType, key);
  }

  @Override
  public void showDetails(OutputStream stream, Long objectHandle, boolean verbose) throws IOException {
    if (verbose) {
      printSupportedMechanism(stream);
    }

    if (objectHandle != null) {
      stream.write(("\nDetails of object with handle " + objectHandle +
          "\n").getBytes(StandardCharsets.UTF_8));

      long keyClass;
      File infoFile = null;
      if ((objectHandle & 0xFF) == 1) {
        keyClass = CKO_PUBLIC_KEY;
        int hashCode = (int) (objectHandle >> 8);
        // public key
        infoFile = getInfoFileForHashCode(pubKeyDir, hashCode);
      } else {
        int hashCode = (int) (objectHandle >> 8);
        keyClass = CKO_PRIVATE_KEY;
        infoFile = getInfoFileForHashCode(privKeyDir, hashCode);
        if (infoFile == null) {
          keyClass = CKO_SECRET_KEY;
          infoFile = getInfoFileForHashCode(secKeyDir, hashCode);
        }
      }

      if (infoFile == null) {
        stream.write("  error: CKR_OBJECT_HANDLE_INVALID\n".getBytes(StandardCharsets.UTF_8));
        return;
      }

      Properties properties = new Properties();
      properties.load(Files.newBufferedReader(infoFile.toPath()));
      properties.put("CLASS", ckoCodeToName(keyClass));

      Set<String> names = properties.stringPropertyNames();
      int nameLen = 0;
      for (String name: names) {
        nameLen = Math.max(nameLen, name.length());
      }

      String text = "";

      for (String name : properties.stringPropertyNames()) {
        if (name.equals(PROP_SHA1SUM) || name.equals("handle")) {
          continue;
        }

        String nameText = name + ": ";
        if (name.length() < nameLen) {
          char[] padding = new char[nameLen - name.length()];
          Arrays.fill(padding, ' ');
          nameText += new String(padding);
        }

        String value = properties.getProperty(name);
        String valueText;
        switch (name) {
          case PROP_KEYTYPE:
            valueText = ckkCodeToName(Long.parseLong(value));
            break;
          case PROP_DSA_BASE:
          case PROP_DSA_PRIME:
          case PROP_DSA_SUBPRIME:
          case PROP_DSA_VALUE:
          case PROP_RSA_MODUS:
          case PROP_RSA_PUBLIC_EXPONENT:
          case PROP_EC_PARAMS:
          case PROP_EC_POINT:
          case PROP_ID:
            byte[] bytes;
            if (name.equals(PROP_EC_POINT)) {
              bytes = ASN1OctetString.getInstance(Hex.decode(value)).getOctets();
            } else {
              bytes = Hex.decode(value);
            }
            valueText = "byte[" + bytes.length + "]\n" + Functions.toString("    ", bytes);
            break;
          default:
            valueText = value;
        }

        text += "  " + nameText + valueText + "\n";
      }
      stream.write(text.getBytes(StandardCharsets.UTF_8));
    } else {
      stream.write("\nList of objects:\n".getBytes(StandardCharsets.UTF_8));

      // Secret Keys
      File[] keyInfoFiles = secKeyDir.listFiles(INFO_FILENAME_FILTER);

      int no = 0;
      if (keyInfoFiles != null) {
        for (File keyInfoFile : keyInfoFiles) {
          String text = formatNumber(++no, 3) + ". " + objectToString(CKO_SECRET_KEY, keyInfoFile) + "\n";
          stream.write(("  " + text).getBytes(StandardCharsets.UTF_8));
        }
      }

      // Public keys
      keyInfoFiles = privKeyDir.listFiles(INFO_FILENAME_FILTER);
      if (keyInfoFiles != null) {
        for (File keyInfoFile : keyInfoFiles) {
          String text = formatNumber(++no, 3) + ". " + objectToString(CKO_PRIVATE_KEY, keyInfoFile) + "\n";
          stream.write(("  " + text).getBytes(StandardCharsets.UTF_8));
        }
      }

      // Public keys
      keyInfoFiles = pubKeyDir.listFiles(INFO_FILENAME_FILTER);
      if (keyInfoFiles != null) {
        for (File keyInfoFile : keyInfoFiles) {
          String text = formatNumber(++no, 3) + ". " + objectToString(CKO_PUBLIC_KEY, keyInfoFile) + "\n";
          stream.write(("  " + text).getBytes(StandardCharsets.UTF_8));
        }
      }
    }
  }

  private static File getInfoFileForHashCode(File dir, int hashCode) {
    File[] files = dir.listFiles(INFO_FILENAME_FILTER);
    for (File file : files) {
      byte[] id = getKeyIdFromInfoFilename(file.getName());
      if (hashCode == Arrays.hashCode(id)) {
        return file;
      }
    }
    return null;
  }

  private String objectToString(long objClass, File infoFile) {
    byte[] id = getKeyIdFromInfoFilename(infoFile.getName());
    try {
      Properties props = loadProperties(infoFile);

      long handle = (Arrays.hashCode(id) & 0xFFFFFFFFL) << 8;
      if (objClass == CKO_PUBLIC_KEY) {
        handle += 1;
      }
      long keyType = Long.parseLong(props.getProperty(PROP_KEYTYPE));
      String label = props.getProperty(PROP_LABEL);
      String keyspec = props.getProperty(PROP_KEYSPEC, "");

      return "handle=" + handle + ", id=" + hex(id) + ", label=" + (label == null ? "<N/A>" : label) +
          ", " + ckoCodeToName(objClass).substring(4) + ": " +
          ckkCodeToName(keyType).substring(4) + "/" + keyspec;
    } catch (Exception e) {
      String message =
          "Error reading object saved in file " + infoFile.getParentFile().getName() + "/" + infoFile.getName();
      LogUtil.warn(LOG, e, message);
      return message;
    }
  }

  private byte[] generateId() throws TokenException {
    while (true) {
      byte[] id = new byte[newObjectConf.getIdLength()];
      random.nextBytes(id);
      if (!(objectExistsByIdLabel(id, null))) {// not duplicated
        return id;
      }
    }
  }

  private String generateLabel(String label) throws TokenException {
    String tmpLabel = label;
    int idx = 0;
    while (objectExistsByIdLabel(null, tmpLabel)) {
      idx++;
      tmpLabel = label + "-" + idx;
    }
    return label;
  }

}
