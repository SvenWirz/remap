package com.remondis.remap;

/**
 * A bean that is loaded in a throwaway class loader by the class loader leak regression test. This class must only
 * depend on <code>java.*</code> types.
 */
public class LeakTestBean {
  private String value;

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }
}
