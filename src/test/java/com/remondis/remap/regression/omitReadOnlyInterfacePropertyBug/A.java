package com.remondis.remap.regression.omitReadOnlyInterfacePropertyBug;

public class A {
  private String name;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }
}
