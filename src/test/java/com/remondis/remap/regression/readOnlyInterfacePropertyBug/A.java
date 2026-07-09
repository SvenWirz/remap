package com.remondis.remap.regression.readOnlyInterfacePropertyBug;

public class A implements B {
  private Long id;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  @Override
  public String getName() {
    return "Test";
  }
}
