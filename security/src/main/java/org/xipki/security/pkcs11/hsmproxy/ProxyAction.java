// Copyright (c) 2013-2023 xipki. All rights reserved.
// License Apache License 2.0

package org.xipki.security.pkcs11.hsmproxy;

/**
 * The HSM proxy action enumeration.
 *
 * @author Lijun Liao (xipki)
 */

public enum ProxyAction {

  moduleCaps,
  slotIds,

  // mechanism infos
  mechInfos,

  publicKeyByHandle,

  keyByKeyId,
  keyByIdLabel,
  keyIdByIdLabel,

  objectExistsByIdLabel,

  destroyAllObjects,
  destroyObjectsByHandle,
  destroyObjectsByIdLabel,

  genSecretKey,
  importSecretKey,

  genRSAKeypair,
  genRSAKeypairOtf,
  // genDSAKeypairByKeysize
  genDSAKeypair2,
  genDSAKeypair,
  genDSAKeypairOtf,
  genECKeypair,
  genECKeypairOtf,
  genSM2Keypair,
  genSM2KeypairOtf,
  showDetails,
  sign,
  digestSecretKey;

  public static ProxyAction ofNameIgnoreCase(String name) {
    for (ProxyAction m : ProxyAction.values()) {
      if (m.name().equalsIgnoreCase(name)) {
        return m;
      }
    }
    return null;
  }

}
