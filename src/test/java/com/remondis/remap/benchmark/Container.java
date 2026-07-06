package com.remondis.remap.benchmark;

import java.util.List;

public class Container {
  private List<String> tags;
  private List<Address> addresses;

  public List<String> getTags() {
    return tags;
  }

  public void setTags(List<String> tags) {
    this.tags = tags;
  }

  public List<Address> getAddresses() {
    return addresses;
  }

  public void setAddresses(List<Address> addresses) {
    this.addresses = addresses;
  }
}
