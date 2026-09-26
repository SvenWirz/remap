package com.remondis.remap.benchmark;

public record FlatDestinationRecord(long id, String firstName, String lastName, String street, String city,
    String zipCode, int age, boolean active, double score, Integer loginCount) {
}
