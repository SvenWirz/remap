package com.remondis.remap.records;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.remondis.remap.AssertMapping;
import com.remondis.remap.FieldSelector;
import com.remondis.remap.Mapper;
import com.remondis.remap.Mapping;
import com.remondis.remap.MappingException;

/**
 * Tests the record support beyond the basic mapping operations: record properties, selector validation, record
 * construction and the limitations of record support.
 */
public class RecordSupportTest {

  // ==================== Record properties ====================

  @Test
  public void shouldMapGettersOfRecordAsReadOnlySourceProperties() {
    Mapper<BeanStyleRecord, NamePojo> mapper = Mapping.from(BeanStyleRecord.class)
        .to(NamePojo.class)
        .mapper();

    NamePojo result = mapper.map(new BeanStyleRecord("Smith"));

    assertThat(result.getName()).isEqualTo("Smith");
    assertThat(result.getDisplayName()).isEqualTo("Mr. Smith");
  }

  @Test
  public void shouldNotOmitGettersOfRecordWhenOmittingOthers() {
    Mapper<BeanStyleRecord, NamePojo> mapper = Mapping.from(BeanStyleRecord.class)
        .to(NamePojo.class)
        .omitOthers()
        .mapper();

    NamePojo result = mapper.map(new BeanStyleRecord("Smith"));

    assertThat(result.getDisplayName()).isEqualTo("Mr. Smith");
  }

  @Test
  public void shouldResolveGetterNamedLikeComponentToComponent() {
    Mapper<BeanStyleRecord, FullNamePojo> mapper = Mapping.from(BeanStyleRecord.class)
        .to(FullNamePojo.class)
        .reassign(BeanStyleRecord::getName)
        .to(FullNamePojo::getFullName)
        .omitInSource(BeanStyleRecord::getDisplayName)
        .mapper();

    assertThat(mapper.map(new BeanStyleRecord("Smith"))
        .getFullName()).isEqualTo("Smith");
    AssertMapping.of(mapper)
        .expectReassign(BeanStyleRecord::name)
        .to(FullNamePojo::getFullName)
        .expectOmitInSource(BeanStyleRecord::getDisplayName)
        .ensure();
  }

  @Test
  public void shouldNotUseGettersOfRecordAsDestinationProperties() {
    assertThatThrownBy(() -> Mapping.from(NamePojo.class)
        .to(BeanStyleRecord.class)
        .mapper()).isInstanceOf(MappingException.class)
        .hasMessageContaining("Property 'displayName' in NamePojo");

    Mapper<NamePojo, BeanStyleRecord> mapper = Mapping.from(NamePojo.class)
        .to(BeanStyleRecord.class)
        .omitInSource(NamePojo::getDisplayName)
        .mapper();

    NamePojo source = new NamePojo();
    source.setName("Smith");
    assertThat(mapper.map(source)).isEqualTo(new BeanStyleRecord("Smith"));
  }

  @Test
  public void shouldNameComponentsByComponentNameEvenIfAccessorLooksLikeGetter() {
    // The accessor isActive() is also a Java Bean getter of the property "active", which must not be a second property.
    Mapper<FlagRecord, FlagPojo> mapper = Mapping.from(FlagRecord.class)
        .to(FlagPojo.class)
        .mapper();

    assertThat(mapper.map(new FlagRecord(true))
        .isIsActive()).isTrue();
  }

  // ==================== Selector validation ====================

  @Test
  public void shouldAcceptMethodReferenceToInterfaceMethodOfRecord() {
    Mapper<NamedRecord, FullNamePojo> mapper = Mapping.from(NamedRecord.class)
        .to(FullNamePojo.class)
        .reassign(Named::name)
        .to(FullNamePojo::getFullName)
        .mapper();

    assertThat(mapper.map(new NamedRecord("Smith"))
        .getFullName()).isEqualTo("Smith");
  }

  @Test
  public void shouldDenyMethodReferenceToMethodOfOtherType() {
    assertThatThrownBy(() -> Mapping.from(PersonPojo.class)
        .to(PersonRecord.class)
        .omitInDestination(Selectors::email)).isInstanceOf(MappingException.class)
        .hasMessageContaining("The method reference " + Selectors.class.getName() + "::email does not select a "
            + "property of record type " + PersonRecord.class.getName());
  }

  @Test
  public void shouldDenyMethodReferenceToMethodOfRecordThatIsNoProperty() {
    assertThatThrownBy(() -> Mapping.from(NamedRecord.class)
        .to(FullNamePojo.class)
        .omitInSource(NamedRecord::greeting)).isInstanceOf(MappingException.class)
        .hasMessageContaining("::greeting does not select a property of record type");
  }

  @Test
  public void shouldDenySelectorThatIsNoMethodReference() {
    FieldSelector<PersonRecord> anonymousSelector = new FieldSelector<PersonRecord>() {
      private static final long serialVersionUID = 1L;

      @Override
      public void selectField(PersonRecord destination) {
        destination.email();
      }
    };

    assertThatThrownBy(() -> Mapping.from(PersonPojo.class)
        .to(PersonRecord.class)
        .omitInDestination(anonymousSelector)).isInstanceOf(MappingException.class)
        .hasMessageContaining("The specified selector is not a lambda expression or method reference");
  }

  // ==================== Record construction ====================

  @Test
  public void shouldMapNonPublicRecords() {
    Mapper<PersonPojo, HiddenRecord> toRecord = Mapping.from(PersonPojo.class)
        .to(HiddenRecord.class)
        .reassign(PersonPojo::isActive)
        .to(HiddenRecord::enabled)
        .mapper();
    Mapper<HiddenRecord, PersonRecord> fromRecord = Mapping.from(HiddenRecord.class)
        .to(PersonRecord.class)
        .reassign(HiddenRecord::enabled)
        .to(PersonRecord::active)
        .mapper();

    HiddenRecord hiddenRecord = toRecord.map(new PersonPojo("Alice", 30, "alice@example.com", true));

    assertThat(hiddenRecord).isEqualTo(new HiddenRecord("Alice", 30, "alice@example.com", true));
    assertThat(fromRecord.map(hiddenRecord)).isEqualTo(new PersonRecord("Alice", 30, "alice@example.com", true));
  }

  @Test
  public void shouldReportExceptionOfCanonicalConstructor() {
    Mapper<PersonPojo, ValidatingRecord> mapper = Mapping.from(PersonPojo.class)
        .to(ValidatingRecord.class)
        .omitInSource(PersonPojo::getEmail)
        .omitInDestination(ValidatingRecord::email)
        .mapper();

    assertThatThrownBy(() -> mapper.map(new PersonPojo("Alice", 30, "alice@example.com", true)))
        .isInstanceOf(MappingException.class)
        .hasMessageContaining("Creating a new instance of record type " + ValidatingRecord.class.getName()
            + " failed: java.lang.NullPointerException: email must not be null")
        .hasCauseInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldReportNullForPrimitiveComponent() {
    Mapper<PersonPojo, PersonRecord> mapper = Mapping.from(PersonPojo.class)
        .to(PersonRecord.class)
        .replace(PersonPojo::getAge, PersonRecord::age)
        .with(age -> (Integer) null)
        .mapper();

    assertThatThrownBy(() -> mapper.map(new PersonPojo("Alice", 30, "alice@example.com", true)))
        .isInstanceOf(MappingException.class)
        .hasMessageContaining("The mapping produced null for the component 'age' of primitive type int.");
  }

  @Test
  public void shouldKeepDefaultOfPrimitiveComponentIfSourceValueIsNull() {
    Mapper<NullableAgePojo, AgeRecord> mapper = Mapping.from(NullableAgePojo.class)
        .to(AgeRecord.class)
        .mapper();

    assertThat(mapper.map(new NullableAgePojo())).isEqualTo(new AgeRecord(0));
  }

  @Test
  public void shouldPassDefaultsForOmittedComponents() {
    Mapper<FlagPojo, PersonRecord> mapper = Mapping.from(FlagPojo.class)
        .to(PersonRecord.class)
        .omitOthers()
        .mapper();

    assertThat(mapper.map(new FlagPojo())).isEqualTo(new PersonRecord(null, 0, null, false));
  }

  @Test
  public void shouldSetValueOfComponent() {
    Mapper<PersonPojo, PersonRecord> mapper = Mapping.from(PersonPojo.class)
        .to(PersonRecord.class)
        .omitInSource(PersonPojo::getEmail)
        .set(PersonRecord::email)
        .with("unknown@example.com")
        .mapper();

    assertThat(mapper.map(new PersonPojo("Alice", 30, "alice@example.com", true)))
        .isEqualTo(new PersonRecord("Alice", 30, "unknown@example.com", true));
  }

  @Test
  public void shouldMapRecordsConcurrently() {
    Mapper<PersonPojo, PersonRecord> mapper = Mapping.from(PersonPojo.class)
        .to(PersonRecord.class)
        .mapper();

    List<PersonRecord> results = IntStream.range(0, 10_000)
        .parallel()
        .mapToObj(i -> mapper.map(new PersonPojo("name" + i, i, "mail" + i, i % 2 == 0)))
        .collect(Collectors.toList());

    assertThat(results).hasSize(10_000);
    for (int i = 0; i < results.size(); i++) {
      assertThat(results.get(i)).isEqualTo(new PersonRecord("name" + i, i, "mail" + i, i % 2 == 0));
    }
  }

  // ==================== Collections ====================

  @Test
  public void shouldMapCollectionsOfNestedRecords() {
    Mapper<AddressPojo, AddressRecord> addressMapper = Mapping.from(AddressPojo.class)
        .to(AddressRecord.class)
        .mapper();
    Mapper<AddressesPojo, AddressesRecord> mapper = Mapping.from(AddressesPojo.class)
        .to(AddressesRecord.class)
        .useMapper(addressMapper)
        .mapper();

    AddressesPojo source = new AddressesPojo();
    source.setAddresses(List.of(new AddressPojo("Main St", "Springfield"), new AddressPojo("Elm St", "Dortmund")));

    assertThat(mapper.map(source)
        .addresses())
        .containsExactly(new AddressRecord("Main St", "Springfield"), new AddressRecord("Elm St", "Dortmund"));
  }

  @Test
  public void shouldReplaceCollectionsOfRecords() {
    Mapper<AddressesRecord, CitiesRecord> mapper = Mapping.from(AddressesRecord.class)
        .to(CitiesRecord.class)
        .replaceCollection(AddressesRecord::addresses, CitiesRecord::cities)
        .with(AddressRecord::city)
        .mapper();

    AddressesRecord source = new AddressesRecord(
        List.of(new AddressRecord("Main St", "Springfield"), new AddressRecord("Elm St", "Dortmund")));

    assertThat(mapper.map(source)
        .cities()).containsExactly("Springfield", "Dortmund");
  }

  // ==================== Limitations ====================

  @Test
  public void shouldDenyPropertyPathOnRecord() {
    assertThatThrownBy(() -> Mapping.from(PersonWithAddressRecord.class)
        .to(CityPojo.class)
        .omitInSource(PersonWithAddressRecord::name)
        .replace(PersonWithAddressRecord::address, CityPojo::getCity)
        .withPropertyPath(AddressRecord::city)).isInstanceOf(MappingException.class)
        .hasMessageContaining("because " + AddressRecord.class.getName() + " is a record type")
        .hasMessageContaining("Use replace() with a transformation function instead");
  }

  @Test
  public void shouldDenyPropertyPathThroughRecord() {
    assertThatThrownBy(() -> Mapping.from(PersonHolderPojo.class)
        .to(CityPojo.class)
        .replace(PersonHolderPojo::getPerson, CityPojo::getCity)
        .withPropertyPath(person -> person.getAddress()
            .city()))
        .isInstanceOf(MappingException.class)
        .hasMessageContaining("records and other final types are not supported within property paths");
  }

  @Test
  public void shouldMapNestedRecordValueWithTransformationFunction() {
    // The alternative to a property path on a record type.
    Mapper<PersonWithAddressRecord, CityPojo> mapper = Mapping.from(PersonWithAddressRecord.class)
        .to(CityPojo.class)
        .omitInSource(PersonWithAddressRecord::name)
        .replace(PersonWithAddressRecord::address, CityPojo::getCity)
        .withSkipWhenNull(AddressRecord::city)
        .mapper();

    assertThat(mapper.map(new PersonWithAddressRecord("Alice", new AddressRecord("Main St", "Springfield")))
        .getCity()).isEqualTo("Springfield");
  }

  // ==================== Test types ====================

  /**
   * A record with getters, for example added to use the record with libraries relying on Java Beans.
   */
  public record BeanStyleRecord(String name) {

  public String getName() {
    return name;
  }

  public String getDisplayName() {
    return "Mr. " + name;
  }

  }

  public record FlagRecord(boolean isActive) {
  }

  public interface Named {
    String name();
  }

  public record NamedRecord(String name) implements Named {

  public String greeting() {
    return "Hello " + name;
  }

  }

  /**
   * A record that is not public, like records declared within the class using them.
   */
  record HiddenRecord(String name, int age, String email, boolean enabled) {
  }

  public record ValidatingRecord(String name, int age, String email, boolean active) {
    public ValidatingRecord {
      Objects.requireNonNull(email, "email must not be null");
    }
  }

  public record AgeRecord(int age) {
  }

  public record AddressesRecord(List<AddressRecord> addresses) {
  }

  public record CitiesRecord(List<String> cities) {
  }

  public static class Selectors {
    public static void email(PersonRecord person) {
    }
  }

  public static class NamePojo {
    private String name;
    private String displayName;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getDisplayName() {
      return displayName;
    }

    public void setDisplayName(String displayName) {
      this.displayName = displayName;
    }
  }

  public static class FullNamePojo {
    private String fullName;

    public String getFullName() {
      return fullName;
    }

    public void setFullName(String fullName) {
      this.fullName = fullName;
    }
  }

  public static class FlagPojo {
    private boolean isActive;

    public boolean isIsActive() {
      return isActive;
    }

    public void setIsActive(boolean isActive) {
      this.isActive = isActive;
    }
  }

  public static class NullableAgePojo {
    private Integer age;

    public Integer getAge() {
      return age;
    }

    public void setAge(Integer age) {
      this.age = age;
    }
  }

  public static class AddressesPojo {
    private List<AddressPojo> addresses;

    public List<AddressPojo> getAddresses() {
      return addresses;
    }

    public void setAddresses(List<AddressPojo> addresses) {
      this.addresses = addresses;
    }
  }

  public static class CityPojo {
    private String city;

    public String getCity() {
      return city;
    }

    public void setCity(String city) {
      this.city = city;
    }
  }

  public static class PersonPojoWithAddressRecord {
    private AddressRecord address;

    public AddressRecord getAddress() {
      return address;
    }

    public void setAddress(AddressRecord address) {
      this.address = address;
    }
  }

  public static class PersonHolderPojo {
    private PersonPojoWithAddressRecord person;

    public PersonPojoWithAddressRecord getPerson() {
      return person;
    }

    public void setPerson(PersonPojoWithAddressRecord person) {
      this.person = person;
    }
  }

}
