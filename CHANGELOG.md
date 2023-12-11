# Change Log

See also <https://github.com/xipki/commons/releases>

## 6.3.3
- Release date: 202y/mm/dd
  - Feature: command xi:curl: throws Exception if received status code != OK
  - Feature: Audit: applicationName is now mandatory, accept also ConfPairs as conf
  - Feature: JSON.java: add methods parseConf() which resolves also the ${sys:*} and ${env:*}.
  - Feature: remove parameters hashAlgo and gm in methods to generate CSRs
  - Feature: add BatchReplace to replaces texts in filess

## 6.3.2
- Release date: 2023/11/26
  - Feature: add new binary of HSM proxy.
  - Move (repackage) JSON.java from module security to util.
  - Deleted non-common classes (moved to github:xipki/xipki).
  - Feature: Simplified password configuration. 
  - Feature: add code to generate a set of hierarchic certificates.
  - Feature: add karaf command xi:exec to execute terminal command.
  - Bugfix: Fixed "MariaDB JDBC driver does not work with old hikaricp (datasource) configuration".
- Dependenciees 
  - Bouncycastle: 1.76 -> 1.77
  - ipkcs11wrapper: 1.0.7 -> 1.0.8
  
## 6.3.1
- Release date: 2023/10/15
- Features
  - Extend Properties to use the place holder ${env:name} for environment and ${sys:name} for system property.
  - Replace JSON backend from gson to jackson.
  - Add method to read certificate fields without parsing it as complete Certificate object.
  - Extend Properties to use the place holder ${env:name} for environment and ${sys:name} for system property.
  - Add CBOR encoder / decoder
  - Base64Url: add method to encode / decode without ending '='.
  - Add modules servlet3-common and servlet5-common
  - Remove the usa of Statement, replaced by PreparedStatement.
  - Add always a new-line to the PEM encoded object.
- Dependencies
  - Bouncycastle: 1.73 -> 1.76
  - ipkcs11wrapper: 1.0.5 -> 1.0.7

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
