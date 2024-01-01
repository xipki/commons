# Change Log

See also <https://github.com/xipki/commons/releases>

## 6.3.4
- Release date: 2024/01/01
- Features:
  - Audit: Allow null event value.
  - Util XiHttpClient: evaluate content type only if SC != OK
  - DataSource: methods getFirst*Value: allow null criteria.
  - Util Exceptions: add constructor with single param 'cause'.
  - Add command xi:export-keycert-pem

## 6.3.3
- Release date: 2023/12/13
  - Command xi:curl: throws Exception if received status code != OK
  - Audit: applicationName is now mandatory, accept also ConfPairs as conf
  - JSON.java: add methods parseConf() which resolves also the ${sys:*} and ${env:*}.
  - Removed parameters hashAlgo and gm in methods to generate CSRs
  - Added BatchReplace to replaces texts in files

## 6.3.2
- Release date: 2023/11/26
- Features:
  - Add new binary of HSM proxy.
  - Moved (repackage) JSON.java from module security to util.
  - Deleted non-common classes (moved to github:xipki/xipki).
  - Simplified password configuration. 
  - Added code to generate a set of hierarchic certificates.
  - Add karaf command xi:exec to execute terminal command.
- Bugfix
  - Fixed "MariaDB JDBC driver does not work with old hikaricp (datasource) configuration".
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
