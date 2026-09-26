package com.remondis.remap.records;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.remondis.remap.AssertMapping;
import com.remondis.remap.Mapper;
import com.remondis.remap.Mapping;
import com.remondis.remap.MappingException;

public class RecordMappingTest {

  // ==================== POJO -> Record ====================

  @Test
  public void shouldMapPojoToRecord_implicit() {
    Mapper<PersonPojo, PersonRecord> mapper = Mapping.from(PersonPojo.class)
        .to(PersonRecord.class)
        .mapper();

    PersonPojo source = new PersonPojo("Alice", 30, "alice@example.com", true);
    PersonRecord result = mapper.map(source);

    assertThat(result).isEqualTo(new PersonRecord("Alice", 30, "alice@example.com", true));
  }

  @Test
  public void shouldMapPojoToRecord_withReassign() {
    Mapper<PersonPojo, PersonResourceRecord> mapper = Mapping.from(PersonPojo.class)
        .to(PersonResourceRecord.class)
        .reassign(PersonPojo::getName)
        .to(PersonResourceRecord::fullName)
        .reassign(PersonPojo::getAge)
        .to(PersonResourceRecord::yearsOld)
        .reassign(PersonPojo::getEmail)
        .to(PersonResourceRecord::emailAddress)
        .reassign(PersonPojo::isActive)
        .to(PersonResourceRecord::isActive)
        .mapper();

    PersonPojo source = new PersonPojo("Bob", 25, "bob@example.com", false);
    PersonResourceRecord result = mapper.map(source);

    assertThat(result).isEqualTo(new PersonResourceRecord("Bob", 25, "bob@example.com", false));
  }

  @Test
  public void shouldMapPojoToRecord_withReplace() {
    Mapper<PersonPojo, PersonResourceRecord> mapper = Mapping.from(PersonPojo.class)
        .to(PersonResourceRecord.class)
        .replace(PersonPojo::getName, PersonResourceRecord::fullName)
        .with(name -> name.toUpperCase())
        .reassign(PersonPojo::getAge)
        .to(PersonResourceRecord::yearsOld)
        .reassign(PersonPojo::getEmail)
        .to(PersonResourceRecord::emailAddress)
        .reassign(PersonPojo::isActive)
        .to(PersonResourceRecord::isActive)
        .mapper();

    PersonPojo source = new PersonPojo("Charlie", 35, "charlie@example.com", true);
    PersonResourceRecord result = mapper.map(source);

    assertThat(result.fullName()).isEqualTo("CHARLIE");
  }

  @Test
  public void shouldMapPojoToRecord_withOmitInSource() {
    Mapper<PersonPojo, PersonRecord> mapper = Mapping.from(PersonPojo.class)
        .to(PersonRecord.class)
        .omitInSource(PersonPojo::getEmail)
        .omitInDestination(PersonRecord::email)
        .mapper();

    PersonPojo source = new PersonPojo("Alice", 30, "alice@example.com", true);
    PersonRecord result = mapper.map(source);

    assertThat(result).isEqualTo(new PersonRecord("Alice", 30, null, true));
  }

  @Test
  public void shouldMapPojoToRecord_withOmitInDestination() {
    Mapper<PersonPojo, PersonWithExtraRecord> mapper = Mapping.from(PersonPojo.class)
        .to(PersonWithExtraRecord.class)
        .omitInSource(PersonPojo::getEmail)
        .omitInSource(PersonPojo::isActive)
        .omitInDestination(PersonWithExtraRecord::extra)
        .mapper();

    PersonPojo source = new PersonPojo("Alice", 30, "alice@example.com", true);
    PersonWithExtraRecord result = mapper.map(source);

    assertThat(result).isEqualTo(new PersonWithExtraRecord("Alice", 30, null));
  }

  // ==================== Record -> POJO ====================

  @Test
  public void shouldMapRecordToPojo_implicit() {
    Mapper<PersonRecord, PersonPojo> mapper = Mapping.from(PersonRecord.class)
        .to(PersonPojo.class)
        .mapper();

    PersonRecord source = new PersonRecord("Diana", 28, "diana@example.com", false);
    PersonPojo result = mapper.map(source);

    assertThat(result.getName()).isEqualTo("Diana");
    assertThat(result.getAge()).isEqualTo(28);
    assertThat(result.getEmail()).isEqualTo("diana@example.com");
    assertThat(result.isActive()).isFalse();
  }

  @Test
  public void shouldMapRecordToPojo_withReassign() {
    Mapper<PersonRecord, PersonResourcePojo> mapper = Mapping.from(PersonRecord.class)
        .to(PersonResourcePojo.class)
        .reassign(PersonRecord::name)
        .to(PersonResourcePojo::getFullName)
        .reassign(PersonRecord::age)
        .to(PersonResourcePojo::getYearsOld)
        .reassign(PersonRecord::email)
        .to(PersonResourcePojo::getEmailAddress)
        .reassign(PersonRecord::active)
        .to(PersonResourcePojo::isIsActive)
        .mapper();

    PersonRecord source = new PersonRecord("Eve", 40, "eve@example.com", true);
    PersonResourcePojo result = mapper.map(source);

    assertThat(result.getFullName()).isEqualTo("Eve");
    assertThat(result.getYearsOld()).isEqualTo(40);
    assertThat(result.getEmailAddress()).isEqualTo("eve@example.com");
    assertThat(result.isIsActive()).isTrue();
  }

  @Test
  public void shouldMapRecordToPojo_withReplace() {
    Mapper<PersonRecord, PersonResourcePojo> mapper = Mapping.from(PersonRecord.class)
        .to(PersonResourcePojo.class)
        .replace(PersonRecord::name, PersonResourcePojo::getFullName)
        .with(name -> "Dr. " + name)
        .reassign(PersonRecord::age)
        .to(PersonResourcePojo::getYearsOld)
        .reassign(PersonRecord::email)
        .to(PersonResourcePojo::getEmailAddress)
        .reassign(PersonRecord::active)
        .to(PersonResourcePojo::isIsActive)
        .mapper();

    PersonRecord source = new PersonRecord("Frank", 50, "frank@example.com", false);
    PersonResourcePojo result = mapper.map(source);

    assertThat(result.getFullName()).isEqualTo("Dr. Frank");
  }

  // ==================== Record -> Record ====================

  @Test
  public void shouldMapRecordToRecord_implicit() {
    Mapper<PersonRecord, PersonRecord> mapper = Mapping.from(PersonRecord.class)
        .to(PersonRecord.class)
        .mapper();

    PersonRecord source = new PersonRecord("Grace", 22, "grace@example.com", true);
    PersonRecord result = mapper.map(source);

    assertThat(result).isEqualTo(source)
        .isNotSameAs(source);
  }

  @Test
  public void shouldMapRecordToRecord_withReassign() {
    Mapper<PersonRecord, PersonResourceRecord> mapper = Mapping.from(PersonRecord.class)
        .to(PersonResourceRecord.class)
        .reassign(PersonRecord::name)
        .to(PersonResourceRecord::fullName)
        .reassign(PersonRecord::age)
        .to(PersonResourceRecord::yearsOld)
        .reassign(PersonRecord::email)
        .to(PersonResourceRecord::emailAddress)
        .reassign(PersonRecord::active)
        .to(PersonResourceRecord::isActive)
        .mapper();

    PersonRecord source = new PersonRecord("Hank", 60, "hank@example.com", false);
    PersonResourceRecord result = mapper.map(source);

    assertThat(result).isEqualTo(new PersonResourceRecord("Hank", 60, "hank@example.com", false));
  }

  @Test
  public void shouldMapRecordToRecord_withReplace() {
    Mapper<PersonRecord, PersonResourceRecord> mapper = Mapping.from(PersonRecord.class)
        .to(PersonResourceRecord.class)
        .replace(PersonRecord::name, PersonResourceRecord::fullName)
        .with(n -> n + " (verified)")
        .replace(PersonRecord::age, PersonResourceRecord::yearsOld)
        .with(a -> a + 1)
        .reassign(PersonRecord::email)
        .to(PersonResourceRecord::emailAddress)
        .reassign(PersonRecord::active)
        .to(PersonResourceRecord::isActive)
        .mapper();

    PersonRecord source = new PersonRecord("Ivy", 29, "ivy@example.com", true);
    PersonResourceRecord result = mapper.map(source);

    assertThat(result.fullName()).isEqualTo("Ivy (verified)");
    assertThat(result.yearsOld()).isEqualTo(30);
  }

  // ==================== Nested / Hierarchical Mapping ====================

  @Test
  public void shouldMapPojoToRecord_withNestedMapper() {
    Mapper<AddressPojo, AddressRecord> addressMapper = Mapping.from(AddressPojo.class)
        .to(AddressRecord.class)
        .mapper();

    Mapper<PersonWithAddressPojo, PersonWithAddressRecord> mapper = Mapping.from(PersonWithAddressPojo.class)
        .to(PersonWithAddressRecord.class)
        .useMapper(addressMapper)
        .mapper();

    PersonWithAddressPojo source = new PersonWithAddressPojo("John", new AddressPojo("Main St", "Springfield"));
    PersonWithAddressRecord result = mapper.map(source);

    assertThat(result).isEqualTo(new PersonWithAddressRecord("John", new AddressRecord("Main St", "Springfield")));
  }

  @Test
  public void shouldMapRecordToPojo_withNestedMapper() {
    Mapper<AddressRecord, AddressPojo> addressMapper = Mapping.from(AddressRecord.class)
        .to(AddressPojo.class)
        .mapper();

    Mapper<PersonWithAddressRecord, PersonWithAddressPojo> mapper = Mapping.from(PersonWithAddressRecord.class)
        .to(PersonWithAddressPojo.class)
        .useMapper(addressMapper)
        .mapper();

    PersonWithAddressRecord source = new PersonWithAddressRecord("Jane", new AddressRecord("Oak Ave", "Shelbyville"));
    PersonWithAddressPojo result = mapper.map(source);

    assertThat(result.getName()).isEqualTo("Jane");
    assertThat(result.getAddress()
        .getStreet()).isEqualTo("Oak Ave");
    assertThat(result.getAddress()
        .getCity()).isEqualTo("Shelbyville");
  }

  @Test
  public void shouldMapRecordToRecord_withNestedMapper() {
    Mapper<AddressRecord, AddressRecord> addressMapper = Mapping.from(AddressRecord.class)
        .to(AddressRecord.class)
        .mapper();

    Mapper<PersonWithAddressRecord, PersonWithAddressRecord> mapper = Mapping.from(PersonWithAddressRecord.class)
        .to(PersonWithAddressRecord.class)
        .useMapper(addressMapper)
        .mapper();

    PersonWithAddressRecord source = new PersonWithAddressRecord("Kate", new AddressRecord("Elm St", "Capital City"));
    PersonWithAddressRecord result = mapper.map(source);

    assertThat(result).isEqualTo(source);
    assertThat(result.address()).isNotSameAs(source.address());
  }

  // ==================== AssertMapping ====================

  @Test
  public void shouldAssertPojoToRecordMapping() {
    Mapper<PersonPojo, PersonRecord> mapper = Mapping.from(PersonPojo.class)
        .to(PersonRecord.class)
        .mapper();

    AssertMapping.of(mapper)
        .expectOthersToBeOmitted()
        .ensure();
  }

  @Test
  public void shouldAssertRecordToPojoMapping() {
    Mapper<PersonRecord, PersonPojo> mapper = Mapping.from(PersonRecord.class)
        .to(PersonPojo.class)
        .mapper();

    AssertMapping.of(mapper)
        .expectOthersToBeOmitted()
        .ensure();
  }

  @Test
  public void shouldAssertRecordToRecordMapping_withReassign() {
    Mapper<PersonRecord, PersonResourceRecord> mapper = Mapping.from(PersonRecord.class)
        .to(PersonResourceRecord.class)
        .reassign(PersonRecord::name)
        .to(PersonResourceRecord::fullName)
        .reassign(PersonRecord::age)
        .to(PersonResourceRecord::yearsOld)
        .reassign(PersonRecord::email)
        .to(PersonResourceRecord::emailAddress)
        .reassign(PersonRecord::active)
        .to(PersonResourceRecord::isActive)
        .mapper();

    AssertMapping.of(mapper)
        .expectReassign(PersonRecord::name)
        .to(PersonResourceRecord::fullName)
        .expectReassign(PersonRecord::age)
        .to(PersonResourceRecord::yearsOld)
        .expectReassign(PersonRecord::email)
        .to(PersonResourceRecord::emailAddress)
        .expectReassign(PersonRecord::active)
        .to(PersonResourceRecord::isActive)
        .ensure();
  }

  @Test
  public void shouldAssertRecordMapping_withReplace() {
    Mapper<PersonPojo, PersonResourceRecord> mapper = Mapping.from(PersonPojo.class)
        .to(PersonResourceRecord.class)
        .replace(PersonPojo::getName, PersonResourceRecord::fullName)
        .with(name -> name == null ? null : name.toUpperCase())
        .reassign(PersonPojo::getAge)
        .to(PersonResourceRecord::yearsOld)
        .reassign(PersonPojo::getEmail)
        .to(PersonResourceRecord::emailAddress)
        .reassign(PersonPojo::isActive)
        .to(PersonResourceRecord::isActive)
        .mapper();

    AssertMapping.of(mapper)
        .expectReplace(PersonPojo::getName, PersonResourceRecord::fullName)
        .andTest(name -> name == null ? null : name.toUpperCase())
        .expectReassign(PersonPojo::getAge)
        .to(PersonResourceRecord::yearsOld)
        .expectReassign(PersonPojo::getEmail)
        .to(PersonResourceRecord::emailAddress)
        .expectReassign(PersonPojo::isActive)
        .to(PersonResourceRecord::isActive)
        .ensure();
  }

  // ==================== Map into an existing record ====================

  @Test
  public void shouldDenyMappingIntoExistingRecord() {
    Mapper<PersonPojo, PersonRecord> mapper = Mapping.from(PersonPojo.class)
        .to(PersonRecord.class)
        .mapper();

    PersonPojo source = new PersonPojo("Alice", 30, "alice@example.com", true);
    PersonRecord existing = new PersonRecord("Old", 0, "old@example.com", false);

    assertThatThrownBy(() -> mapper.map(source, existing)).isInstanceOf(MappingException.class)
        .hasMessageContaining("Mapping into an existing instance of record type " + PersonRecord.class.getName())
        .hasMessageContaining("Use Mapper.map(source)");
  }

  @Test
  public void shouldCreateNewRecordWhenMappingIntoNull() {
    Mapper<PersonPojo, PersonRecord> mapper = Mapping.from(PersonPojo.class)
        .to(PersonRecord.class)
        .mapper();

    PersonPojo source = new PersonPojo("Alice", 30, "alice@example.com", true);
    PersonRecord result = mapper.map(source, null);

    assertThat(result).isEqualTo(new PersonRecord("Alice", 30, "alice@example.com", true));
  }

  // ==================== Error Cases ====================

  @Test
  public void shouldThrowOnUnmappedRecordField() {
    assertThatThrownBy(() -> Mapping.from(PersonPojo.class)
        .to(PersonWithExtraRecord.class)
        .mapper()).isInstanceOf(MappingException.class)
        .hasMessageContaining("The following properties are unmapped")
        .hasMessageContaining("Property 'extra' in PersonWithExtraRecord");
  }

  @Test
  public void shouldDenyMappingNullToRecord() {
    Mapper<PersonPojo, PersonRecord> mapper = Mapping.from(PersonPojo.class)
        .to(PersonRecord.class)
        .mapper();

    assertThatThrownBy(() -> mapper.map((PersonPojo) null)).isInstanceOf(MappingException.class)
        .hasMessage("Mapper cannot map null object.");
  }

  // ==================== Collection Mapping ====================

  @Test
  public void shouldMapCollectionOfPojosToRecords() {
    Mapper<PersonPojo, PersonRecord> mapper = Mapping.from(PersonPojo.class)
        .to(PersonRecord.class)
        .mapper();

    List<PersonPojo> sources = List.of(new PersonPojo("A", 1, "a@x.com", true),
        new PersonPojo("B", 2, "b@x.com", false));

    List<PersonRecord> results = mapper.map(sources);

    assertThat(results).containsExactly(new PersonRecord("A", 1, "a@x.com", true),
        new PersonRecord("B", 2, "b@x.com", false));
  }

  // ==================== Primitive Defaults ====================

  @Test
  public void shouldSetPrimitiveDefaultsForOmittedRecordFields() {
    Mapper<PersonPojo, PersonRecord> mapper = Mapping.from(PersonPojo.class)
        .to(PersonRecord.class)
        .omitInSource(PersonPojo::getAge)
        .omitInDestination(PersonRecord::age)
        .omitInSource(PersonPojo::isActive)
        .omitInDestination(PersonRecord::active)
        .mapper();

    PersonPojo source = new PersonPojo("Alice", 30, "alice@example.com", true);
    PersonRecord result = mapper.map(source);

    assertThat(result).isEqualTo(new PersonRecord("Alice", 0, "alice@example.com", false));
  }

  // ==================== Set Operation ====================

  @Test
  public void shouldMapPojoToRecord_withSet() {
    Mapper<PersonPojo, PersonWithExtraRecord> mapper = Mapping.from(PersonPojo.class)
        .to(PersonWithExtraRecord.class)
        .omitInSource(PersonPojo::getEmail)
        .omitInSource(PersonPojo::isActive)
        .set(PersonWithExtraRecord::extra)
        .with(source -> source.getName() + "-extra")
        .mapper();

    PersonWithExtraRecord result = mapper.map(new PersonPojo("John", 30, "john@example.com", true));

    assertThat(result).isEqualTo(new PersonWithExtraRecord("John", 30, "John-extra"));
  }

  // ==================== Restructure Operation ====================

  @Test
  public void shouldMapPojoToRecord_withRestructure() {
    Mapper<PersonPojo, PersonWithAddressRecord> mapper = Mapping.from(PersonPojo.class)
        .to(PersonWithAddressRecord.class)
        .omitOtherSourceProperties()
        .restructure(PersonWithAddressRecord::address)
        .applying(config -> config.replace(PersonPojo::getEmail, AddressRecord::street)
            .withSkipWhenNull(email -> email)
            .replace(PersonPojo::getName, AddressRecord::city)
            .withSkipWhenNull(name -> name))
        .mapper();

    PersonWithAddressRecord result = mapper.map(new PersonPojo("Dortmund", 30, "Main Street 1", false));

    assertThat(result)
        .isEqualTo(new PersonWithAddressRecord("Dortmund", new AddressRecord("Main Street 1", "Dortmund")));
  }

  // ==================== Selector Validation ====================

  @Test
  public void shouldThrowOnInlineLambdaForRecordSelector() {
    assertThatThrownBy(() -> Mapping.from(PersonPojo.class)
        .to(PersonRecord.class)
        .omitInDestination(record -> record.email())).isInstanceOf(MappingException.class)
        .hasMessageContaining("must be selected by method references like PersonRecord::componentName")
        .hasMessageContaining("Inline lambdas cannot be analyzed");
  }

}
