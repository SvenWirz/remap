package com.remondis.remap.regression.omitReadOnlyInterfacePropertyBug;

public class B implements BIface {
  private String name;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Override
  public String getReadOnlyProperty() {
    return "constant";
  }
}