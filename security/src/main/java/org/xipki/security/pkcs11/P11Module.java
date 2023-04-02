// Copyright (c) 2013-2023 xipki. All rights reserved.
// License Apache License 2.0

package org.xipki.security.pkcs11;

import org.xipki.pkcs11.wrapper.TokenException;
import org.xipki.util.CompareUtil;

import java.util.*;

import static org.xipki.util.Args.notNull;

/**
 * PKCS#11 module.
 *
 * @author Lijun Liao (xipki)
 * @since 2.0.0
 */

public abstract class P11Module {

  protected final P11ModuleConf conf;

  private final Map<P11SlotId, P11Slot> slots = new HashMap<>();

  private final List<P11SlotId> slotIds = new ArrayList<>();

  public P11Module(P11ModuleConf conf) {
    this.conf = notNull(conf, "conf");
  }

  public abstract void close();

  public abstract String getDescription();

  public String getName() {
    return conf.getName();
  }

  public boolean isReadOnly() {
    return conf.isReadOnly();
  }

  public P11ModuleConf getConf() {
    return conf;
  }

  protected void setSlots(Set<P11Slot> slots) {
    this.slots.clear();
    this.slotIds.clear();
    for (P11Slot slot : slots) {
      this.slots.put(slot.getSlotId(), slot);
      this.slotIds.add(slot.getSlotId());
    }
  }

  /**
   * Returns slot for the given {@code slotId}.
   *
   * @param slotId
   *          slot identifier. Must not be {@code null}.
   * @return the slot
   * @throws TokenException
   *         if PKCS#11 token error occurs
   */
  public P11Slot getSlot(P11SlotId slotId) throws TokenException {
    notNull(slotId, "slotId");
    P11Slot slot = slots.get(slotId);
    if (slot == null) {
      throw new TokenException("unknown slot " + slotId);
    }
    return slot;
  } // method getSlot

  void destroySlot(long slotId) {
    P11SlotId p11SlotId = null;
    for (P11SlotId si : slots.keySet()) {
      if (CompareUtil.equalsObject(si.getId(), slotId)) {
        p11SlotId = si;
        break;
      }
    }
    if (p11SlotId != null) {
      P11Slot slot = slots.remove(p11SlotId);
      if (slot != null) {
        slot.close();
      }
    }
  }

  public List<P11SlotId> getSlotIds() {
    return slotIds;
  }

  public P11SlotId getSlotIdForIndex(int index) throws TokenException {
    for (P11SlotId id : slotIds) {
      if (id.getIndex() == index) {
        return id;
      }
    }
    throw new TokenException("could not find slot with index " + index);
  }

  public P11SlotId getSlotIdForId(long id) throws TokenException {
    for (P11SlotId slotId : slotIds) {
      if (slotId.getId() == id) {
        return slotId;
      }
    }
    throw new TokenException("could not find slot with id " + id);
  }

}
