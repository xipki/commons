// Copyright (c) 2013-2024 xipki. All rights reserved.
// License Apache License 2.0

package org.xipki.security.pkcs11;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.password.PasswordResolverException;
import org.xipki.pkcs11.wrapper.ModuleInfo;
import org.xipki.pkcs11.wrapper.PKCS11Constants;
import org.xipki.pkcs11.wrapper.PKCS11Exception;
import org.xipki.pkcs11.wrapper.PKCS11Module;
import org.xipki.pkcs11.wrapper.PKCS11Token;
import org.xipki.pkcs11.wrapper.Slot;
import org.xipki.pkcs11.wrapper.SlotInfo;
import org.xipki.pkcs11.wrapper.TokenException;
import org.xipki.pkcs11.wrapper.TokenInfo;
import org.xipki.util.Args;
import org.xipki.util.CollectionUtil;
import org.xipki.util.IoUtil;
import org.xipki.util.LogUtil;
import org.xipki.util.StringUtil;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * {@link P11Module} based on the ipkcs11wrapper or jpkcs11wrapper.
 *
 * @author Lijun Liao (xipki)
 * @since 2.0.0
 */

class NativeP11Module extends P11Module {

  public static final String TYPE = "native";

  private static final Logger LOG = LoggerFactory.getLogger(NativeP11Module.class);

  private final PKCS11Module module;

  private String description;

  private NativeP11Module(PKCS11Module module, P11ModuleConf moduleConf) throws TokenException {
    super(moduleConf);

    if (CollectionUtil.isNotEmpty(moduleConf.getNativeLibraryProperties())) {
      throw new TokenException("nativeLibraries[i].properties is present but not allowed.");
    }

    this.module = Args.notNull(module, "module");

    try {
      ModuleInfo info = module.getInfo();
      this.description = StringUtil.concatObjects("PKCS#11 wrapper",
          "\n\tPath: ", moduleConf.getNativeLibrary(),
          "\n\tCryptoki Version: ", info.getCryptokiVersion(),
          "\n\tManufacturerID: ", info.getManufacturerID(),
          "\n\tLibrary Description: ", info.getLibraryDescription(),
          "\n\tLibrary Version: ", info.getLibraryVersion());
    } catch (TokenException ex) {
      this.description = StringUtil.concatObjects("PKCS#11 wrapper",
          "\n\tPath ", moduleConf.getNativeLibrary());
    }
    LOG.info("PKCS#11 module\n{}", this.description);

    Slot[] slotList;
    try {
      slotList = module.getSlotList(false);
    } catch (Throwable th) {
      final String msg = "could not getSlotList of module";
      LogUtil.error(LOG, th, msg);
      throw new TokenException(msg);
    }

    final int size = (slotList == null) ? 0 : slotList.length;
    if (size == 0) {
      throw new TokenException("no slot could be found");
    }

    for (int i = 0; i < size; i++) {
      Slot slot = slotList[i];
      SlotInfo slotInfo;
      try {
        slotInfo = slot.getSlotInfo();
      } catch (TokenException ex) {
        LOG.error("ignore slot[{}] (id={} with error", i, slot.getSlotID());
        slotList[i] = null;
        continue;
      }

      if (!slotInfo.isTokenPresent()) {
        slotList[i] = null;
        LOG.info("ignore slot[{}] (id={} without token", i, slot.getSlotID());
      }
    }

    StringBuilder msg = new StringBuilder();

    Set<P11Slot> slots = new HashSet<>();
    for (int i = 0; i < slotList.length; i++) {
      Slot slot = slotList[i];
      if (slot == null) {
        continue;
      }

      P11SlotId slotId = new P11SlotId(i, slot.getSlotID());
      if (!moduleConf.isSlotIncluded(slotId)) {
        LOG.info("skipped slot {}", slotId);
        continue;
      }

      TokenInfo ti = slot.getToken().getTokenInfo();
      if (!ti.isTokenInitialized()) {
        LOG.info("slot {} not initialized, skipped it.", slotId);
        continue;
      }

      if (LOG.isDebugEnabled()) {
        msg.append("--------------------Slot ").append(i).append("--------------------\n");
        msg.append("id: ").append(slot.getSlotID()).append("\n");
        try {
          msg.append(slot.getSlotInfo()).append("\n");
        } catch (TokenException ex) {
          msg.append("error: ").append(ex.getMessage());
        }
      }

      char[] pwd;
      try {
        pwd = moduleConf.getPasswordRetriever().getPassword(slotId);
      } catch (PasswordResolverException ex) {
        throw new TokenException("PasswordResolverException: " + ex.getMessage(), ex);
      }

      long userType = Optional.ofNullable(
          slot.getModule().nameToCode(PKCS11Constants.Category.CKU, getConf().getUserType())).orElseThrow(
          () -> new TokenException("Unknown user type " + getConf().getUserType()));

      PKCS11Token token = new PKCS11Token(slot.getToken(), moduleConf.isReadOnly(), userType, null,
          pwd == null ? null : Collections.singletonList(pwd), moduleConf.getNumSessions());
      token.setMaxMessageSize(moduleConf.getMaxMessageSize());
      if (moduleConf.getNewSessionTimeout() != null) {
        token.setTimeOutWaitNewSession(moduleConf.getNewSessionTimeout());
      }

      P11Slot p11Slot = new NativeP11Slot(slotId, token, moduleConf.getP11NewObjectConf(),
          moduleConf.getSecretKeyTypes(), moduleConf.getKeyPairTypes());

      slots.add(p11Slot);
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("{}", msg);
    }

    setSlots(slots);
  } // constructor

  public static P11Module getInstance(P11ModuleConf moduleConf) throws TokenException {
    String userTypeStr = Args.notNull(moduleConf, "moduleConf").getUserType();
    Long userType = PKCS11Constants.nameToCode(PKCS11Constants.Category.CKU, userTypeStr);

    if (userType != null) {
      if (userType == PKCS11Constants.CKU_SO) {
        throw new TokenException("CKU_SO is not allowed in P11Module, too dangerous.");
      }
    }

    String path = moduleConf.getNativeLibrary();
    path = IoUtil.expandFilepath(path, false);

    PKCS11Module module;
    try {
      module = PKCS11Module.getInstance(path);
    } catch (IOException ex) {
      final String msg = "could not load the PKCS#11 module: " + path;
      LogUtil.error(LOG, ex, msg);
      throw new TokenException(msg, ex);
    }

    try {
      module.initialize();
    } catch (PKCS11Exception ex) {
      if (ex.getErrorCode() != PKCS11Constants.CKR_CRYPTOKI_ALREADY_INITIALIZED) {
        LogUtil.error(LOG, ex);
        closeModule(moduleConf.getNativeLibrary(), module);
        throw ex;
      } else {
        LOG.info("PKCS#11 module already initialized");
        try {
          LOG.info("pkcs11.getInfo():\n{}", module.getInfo());
        } catch (TokenException e2) {
          LOG.debug("module.getInfo()", e2);
        }
      }
    } catch (Throwable th) {
      LOG.error("unexpected Exception", th);
      closeModule(moduleConf.getNativeLibrary(), module);
      throw new TokenException(th.getMessage());
    }

    return new NativeP11Module(module, moduleConf);
  } // method getInstance

  @Override
  public String getDescription() {
    return description;
  }

  @Override
  public void close() {
    for (P11SlotId slotId : getSlotIds()) {
      try {
        getSlot(slotId).close();
      } catch (Throwable th) {
        LogUtil.error(LOG, th, "could not close PKCS#11 slot " + slotId);
      }
    }

    closeModule(conf.getNativeLibrary(), module);
  }

  private static void closeModule(String path, PKCS11Module module) {
    if (module == null) {
      return;
    }

    LOG.info("close PKCS#11 module {}", path);
    try {
      module.finalize(null);
    } catch (Throwable th) {
      LogUtil.error(LOG, th, "could not close module " + path);
    }
  }
}
