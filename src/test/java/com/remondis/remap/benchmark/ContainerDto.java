package com.remondis.remap.benchmark;

import java.util.List;

public class ContainerDto {
  private List<String> tags;
  private List<AddressDto> addresses;

  public List<String> getTags() {
    return tags;
  }

  public void setTags(List<String> tags) {
    this.tags = tags;
  }

  public List<AddressDto> getAddresses() {
    return addresses;
  }

  public void setAddresses(List<AddressDto> addresses) {
    this.addresses = addresses;
  }
}
