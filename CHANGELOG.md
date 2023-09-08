# Change Log

See also <https://github.com/xipki/commons/releases>

## 6.3.1
- Release date: 2023/mm/dd
- audit
  - N/A
- audit-extra
  - N/A
- datasource
  - Feature: extend Properties to use the place holder ${env:name} for environment and ${sys:name} for system property.
- password
  - N/A
- security
  - N/A
- util
  - Feature: extend Properties to use the place holder ${env:name} for environment and ${sys:name} for system property.
- xipki-tomcat-password
  - N/A
- Dependencies
  - N/A

## 6.3.0
- Release date: 2023/04/29
- audit
  - change groupId: org.xipki -> org.xipki.commons
- audit-extra
  - change groupId: org.xipki -> org.xipki.commons
  - close the datasource when shut down the DatabaseMacAuditService.
- datasource
  - change groupId: org.xipki -> org.xipki.commons
  - ScriptRunner: better print and error handling.
- password
  - change groupId: org.xipki -> org.xipki.commons
- security
  - change groupId: org.xipki -> org.xipki.commons
  - NativeP11Slot: throws TokenException instead NullPointException.
  - NativeP11Module: allow the specification of vendor's CKU and CKM.
- util
  - change groupId: org.xipki -> org.xipki.commons
- xipki-tomcat-password
  - change groupId: org.xipki -> org.xipki.commons
- Dependencies
  - ipkcs11wrapper: 1.0.4 --> 1.0.5
  - bouncycastle: 1.72 --> 1.73
## 6.2.0 (as in [xipki/xipki/CHANGELOG.md](https://github.com/xipki/xipki/blob/master/CHANGELOG.md))
