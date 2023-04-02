// Copyright (c) 2013-2023 xipki. All rights reserved.
// License Apache License 2.0

package org.xipki.security.pkcs11.emulator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.password.PasswordResolverException;
import org.xipki.pkcs11.wrapper.TokenException;
import org.xipki.security.pkcs11.P11Module;
import org.xipki.security.pkcs11.P11ModuleConf;
import org.xipki.security.pkcs11.P11Slot;
import org.xipki.security.pkcs11.P11SlotId;
import org.xipki.util.IoUtil;
import org.xipki.util.StringUtil;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.xipki.util.Args.notNull;

/**
 * {@link P11Module} for PKCS#11 emulator.
 *
 * @author Lijun Liao (xipki)
 * @since 2.0.0
 */

class EmulatorP11Module extends P11Module {

  public static final String TYPE = "emulator";

  public static final String DFLT_BASEDIR = System.getProperty("java.io.tmpdir") + File.separator + "pkcs11-emulator";

  private static final Logger LOG = LoggerFactory.getLogger(EmulatorP11Module.class);

  private final String description;

  private EmulatorP11Module(P11ModuleConf moduleConf) throws TokenException {
    super(moduleConf);

    File baseDir;
    String modulePath = moduleConf.getNativeLibrary().trim();
    String parametersStr = "";
    if (!modulePath.isEmpty()) {
      int idx = modulePath.indexOf('?');
      if (idx != -1) {
        parametersStr = modulePath.substring(idx);
        modulePath = modulePath.substring(0, idx);
      }
    }

    if (modulePath.isEmpty()) {
      baseDir = new File(DFLT_BASEDIR);
      LOG.info("Use existing default base directory: " + DFLT_BASEDIR);
    } else {
      baseDir = new File(IoUtil.expandFilepath(modulePath));
      LOG.info("Use explicit base directory: " + baseDir.getPath());
    }

    if (!baseDir.exists()) {
      try {
        createExampleRepository(baseDir, 2);
      } catch (IOException ex) {
        throw new TokenException("could not initialize the base direcotry: " + baseDir.getPath(), ex);
      }

      LOG.info("create and initialize the base directory: " + baseDir.getPath());
    }

    this.description = StringUtil.concat("PKCS#11 emulator", "\nPath: ",
        baseDir.getAbsolutePath() + parametersStr);
    LOG.info("PKCS#11 module\n{}", this.description);

    File[] children = baseDir.listFiles();

    if (children == null || children.length == 0) {
      LOG.error("found no slots");
      setSlots(Collections.emptySet());
      return;
    }

    Set<Integer> allSlotIndexes = new HashSet<>();
    Set<Long> allSlotIdentifiers = new HashSet<>();

    List<P11SlotId> slotIds = new LinkedList<>();

    for (File child : children) {
      if ((child.isDirectory() && child.canRead() && !child.exists())) {
        LOG.warn("ignore path {}, it does not point to a readable existing directory", child.getPath());
        continue;
      }

      String filename = child.getName();
      String[] tokens = filename.split("-");
      if (tokens.length != 2) {
        LOG.warn("ignore dir {}, invalid filename syntax", child.getPath());
        continue;
      }

      int slotIndex;
      long slotId;
      try {
        slotIndex = Integer.parseInt(tokens[0]);
        slotId = Long.parseLong(tokens[1]);
      } catch (NumberFormatException ex) {
        LOG.warn("ignore dir {}, invalid filename syntax", child.getPath());
        continue;
      }

      if (allSlotIndexes.contains(slotIndex)) {
        LOG.error("ignore slot dir {}, the same slot index has been assigned", filename);
        continue;
      }

      if (allSlotIdentifiers.contains(slotId)) {
        LOG.error("ignore slot dir {}, the same slot identifier has been assigned", filename);
        continue;
      }

      allSlotIndexes.add(slotIndex);
      allSlotIdentifiers.add(slotId);

      P11SlotId slotIdentifier = new P11SlotId(slotIndex, slotId);
      if (!moduleConf.isSlotIncluded(slotIdentifier)) {
        LOG.info("skipped slot {}", slotId);
        continue;
      }

      slotIds.add(slotIdentifier);
    } // end for

    Set<P11Slot> slots = new HashSet<>();
    for (P11SlotId slotId : slotIds) {
      List<char[]> pwd;
      try {
        pwd = moduleConf.getPasswordRetriever().getPassword(slotId);
      } catch (PasswordResolverException ex) {
        throw new TokenException("PasswordResolverException: " + ex.getMessage(), ex);
      }

      File slotDir = new File(baseDir, slotId.getIndex() + "-" + slotId.getId());

      if (pwd == null) {
        throw new TokenException("no password is configured");
      }

      if (pwd.size() != 1) {
        throw new TokenException(pwd.size() + " passwords are configured, but 1 is permitted");
      }

      char[] firstPwd = pwd.get(0);

      slots.add(new EmulatorP11Slot(moduleConf.getName(), slotDir, slotId,
          moduleConf.isReadOnly(), new EmulatorKeyCryptor(firstPwd), moduleConf.getP11MechanismFilter(),
          moduleConf.getP11NewObjectConf(), moduleConf.getNumSessions(),
          moduleConf.getSecretKeyTypes(), moduleConf.getKeyPairTypes()));
    }

    setSlots(slots);
  } // constructor

  private static boolean parseBoolean(String value, String name) throws TokenException {
      if ("true".equalsIgnoreCase(value)) {
        return true;
      } else if ("false".equalsIgnoreCase(value)) {
        return false;
      } else {
        throw new TokenException("invalid " + name + ": " + value);
      }
  }

  public static P11Module getInstance(P11ModuleConf moduleConf) throws TokenException {
    notNull(moduleConf, "moduleConf");
    return new EmulatorP11Module(moduleConf);
  }

  @Override
  public String getDescription() {
    return description;
  }

  @Override
  public void close() {
    LOG.info("close PKCS#11 module: {}", getName());
  }

  private void createExampleRepository(File dir, int numSlots) throws IOException {
    for (int i = 0; i < numSlots; i++) {
      File slotDir = new File(dir, i + "-" + (800000 + i));
      slotDir.mkdirs();

      File slotInfoFile = new File(slotDir, "slot.info");
      IoUtil.save(slotInfoFile, StringUtil.toUtf8Bytes("namedCurveSupported=true\n"));
    }
  }

}
