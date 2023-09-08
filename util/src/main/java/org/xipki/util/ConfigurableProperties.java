// Copyright (c) 2013-2023 xipki. All rights reserved.
// License Apache License 2.0
package org.xipki.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Similar as {@link Properties}, with the extension to use the place-holder ${env:ENVIROMENT-NAME}
 * for the environment, and ${sys:JAVA-PROPERTY-NAME} for the Java system property.
 *
 * @author Lijun Liao (xipki)
 * @since 6.3.1
 */
public class ConfigurableProperties {

  private final ConcurrentHashMap<String, String> map;

  public ConfigurableProperties() {
    map = new ConcurrentHashMap<>(8);
  }

  public synchronized void load(Properties props) throws IOException {
    Objects.requireNonNull(props, "props parameter is null");
    for (String name : props.stringPropertyNames()) {
      setProperty(name, props.getProperty(name));
    }
  }

  /**
   * Reads a property list (key and element pairs) from the input
   * byte stream.
   * <p>
   * The specified stream remains open after this method returns.
   *
   * @param      inStream   the input stream.
   * @throws     IOException  if an error occurred when reading from the
   *             input stream.
   * @throws     NullPointerException if {@code inStream} is null.
   */
  public synchronized void load(InputStream inStream) throws IOException {
    Objects.requireNonNull(inStream, "inStream parameter is null");
    load(new InputStreamReader(inStream));
  }

  /**
   * Reads a property list (key and element pairs) from the input
   * character stream in a simple line-oriented format.
   *
   * The specified stream remains open after this method returns.
   *
   * @param   reader   the input character stream.
   * @throws  IOException  if an error occurred when reading from the
   *          input stream.
   * @throws  NullPointerException if {@code reader} is null.
   */
  public synchronized void load(Reader reader) throws IOException {
    Objects.requireNonNull(reader, "reader parameter is null");
    Properties props = new Properties();
    props.load(reader);

    for (String name : props.stringPropertyNames()) {
      String value = props.getProperty(name);
      setProperty(name, value);
    }
  }

  /**
   * Searches for the property with the specified key in this property list.
   * If the key is not found in this property list, the default property list,
   * and its defaults, recursively, are then checked. The method returns
   * {@code null} if the property is not found.
   *
   * @param   key   the property key.
   * @return  the value in this property list with the specified key value.
   */
  public String getProperty(String key) {
    return map.get(key);
  }

  /**
   * Searches for the property with the specified key in this property list.
   * If the key is not found in this property list, the default property list,
   * and its defaults, recursively, are then checked. The method returns the
   * default value argument if the property is not found.
   *
   * @param   key            the hashtable key.
   * @param   defaultValue   a default value.
   *
   * @return  the value in this property list with the specified key value.
   */
  public String getProperty(String key, String defaultValue) {
    return map.getOrDefault(key, defaultValue);
  }

  /**
   * Returns an unmodifiable set of keys from this property list
   * where the key and its corresponding value are strings,
   * including distinct keys in the default property list if a key
   * of the same name has not already been found from the main
   * properties list.  Properties whose key or value is not
   * of type {@code String} are omitted.
   * <p>
   * The returned set is not backed by this {@code Properties} object.
   * Changes to this {@code Properties} object are not reflected in the
   * returned set.
   *
   * @return  an unmodifiable set of keys in this property list where
   *          the key and its corresponding value are strings,
   *          including the keys in the default property list.
   */
  public Set<String> propertyNames() {
    return map.keySet();
  }

  public int size() {
    return map.size();
  }

  public boolean isEmpty() {
    return map.isEmpty();
  }

  public boolean containsKey(String key) {
    return map.containsKey(key);
  }

  /**
   * Calls the {@code Hashtable} method {@code put}. Provided for
   * parallelism with the {@code getProperty} method. Enforces use of
   * strings for property keys and values. The value returned is the
   * result of the {@code Hashtable} call to {@code put}.
   *
   * @param key the key to be placed into this property list.
   * @param value the value corresponding to {@code key}.
   * @return     the previous value of the specified key in this property
   *             list, or {@code null} if it did not have one.
   */
  public synchronized String setProperty(String key, String value) {
    // resolve value
    List<String> varTypes = new LinkedList<>();
    List<String> varNames = new LinkedList<>();
    List<int[]> positions = new LinkedList<>();
    for (int i = 0; i < value.length();) {
      if (StringUtil.startsWithIgnoreCase(value, "${env:", i)
          || StringUtil.startsWithIgnoreCase(value, "${sys:", i)) {
        int closeIndex = value.indexOf('}', i + 6); // 6 = "${env:".length() and "${sys:".length()
        if (closeIndex == -1) {
          break;
        } else {
          varTypes.add(StringUtil.startsWithIgnoreCase(value, "${env:", i) ? "env" : "sys");
          varNames.add(value.substring(i + 6, closeIndex));
          positions.add(new int[]{i, closeIndex});

          i = closeIndex + 1;
        }
      } else {
        i++;
      }
    }

    if (varTypes.isEmpty()) {
      return map.put(key, value);
    }

    StringBuilder valueBuilder = new StringBuilder();
    int firstStartIndex = positions.get(0)[0];
    if (firstStartIndex > 0) {
      valueBuilder.append(value.substring(0, firstStartIndex));
    }

    int n = positions.size();

    for (int i = 0; i < n; i++) {
      String type = varTypes.get(i);
      String name = varNames.get(i);
      int[] indexes = positions.get(i);

      String thisValue;
      if ("env".equalsIgnoreCase(type)) {
        thisValue = System.getenv(name);
      } else {
        thisValue = System.getProperty(name);
      }

      if (thisValue == null) { // not defined
        thisValue = value.substring(indexes[0], indexes[1] + 1);
      }
      valueBuilder.append(thisValue);

      int nextVarStartIndex = (i == n - 1) ? value.length() : positions.get(i + 1)[0];
      if (nextVarStartIndex > indexes[1] + 1) {
        valueBuilder.append(value.substring(indexes[1] + 1, nextVarStartIndex));
      }
    }

    return map.put(key, valueBuilder.toString());
  }

  public synchronized String remove(String key) {
    return map.remove(key);
  }

  public synchronized void clear() {
    map.clear();
  }

  @Override
  public synchronized String toString() {
    return map.toString();
  }

  @Override
  public synchronized int hashCode() {
    return map.hashCode();
  }

  public Properties toProperties() {
    Properties props = new Properties();
    for (String name : propertyNames()) {
      props.setProperty(name, map.get(name));
    }
    return props;
  }

}
