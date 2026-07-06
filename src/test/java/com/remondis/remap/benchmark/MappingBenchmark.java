package com.remondis.remap.benchmark;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import com.remondis.remap.Mapper;
import com.remondis.remap.Mapping;

/**
 * JMH benchmarks covering the mapping hot paths of ReMap:
 * <ul>
 * <li>{@link #mapFlatBean(MapperState)}: implicit mapping of a flat bean with 10 properties.</li>
 * <li>{@link #mapNestedBean(MapperState)}: mapping with a nested object using a registered mapper.</li>
 * <li>{@link #mapNestedBeanConcurrent(MapperState)}: same as above, but with 4 threads sharing one mapper
 * instance.</li>
 * <li>{@link #mapCollections(CollectionState)}: mapping of collections (reference elements and nested mapping per
 * element).</li>
 * <li>{@link #createMapper(MapperState)}: mapper creation (configuration time).</li>
 * </ul>
 * See {@link BenchmarkRunner} for how to run the benchmarks.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class MappingBenchmark {

  @State(Scope.Benchmark)
  public static class MapperState {
    Mapper<FlatSource, FlatDestination> flatMapper;
    Mapper<Address, AddressDto> addressMapper;
    Mapper<Person, PersonDto> personMapper;

    FlatSource flatSource;
    Person person;

    @Setup
    public void setup() {
      flatMapper = Mapping.from(FlatSource.class)
          .to(FlatDestination.class)
          .mapper();
      addressMapper = Mapping.from(Address.class)
          .to(AddressDto.class)
          .mapper();
      personMapper = Mapping.from(Person.class)
          .to(PersonDto.class)
          .useMapper(addressMapper)
          .mapper();

      flatSource = new FlatSource();
      flatSource.setId(4711L);
      flatSource.setFirstName("John");
      flatSource.setLastName("Doe");
      flatSource.setStreet("Main Street");
      flatSource.setCity("Springfield");
      flatSource.setZipCode("12345");
      flatSource.setAge(42);
      flatSource.setActive(true);
      flatSource.setScore(0.75d);
      flatSource.setLoginCount(1337);

      person = new Person();
      person.setName("John Doe");
      person.setAge(42);
      person.setAddress(newAddress(1));
    }
  }

  @State(Scope.Benchmark)
  public static class CollectionState {
    @Param({
        "10", "1000"
    })
    int collectionSize;

    Mapper<Container, ContainerDto> containerMapper;
    Container container;

    @Setup
    public void setup() {
      Mapper<Address, AddressDto> addressMapper = Mapping.from(Address.class)
          .to(AddressDto.class)
          .mapper();
      containerMapper = Mapping.from(Container.class)
          .to(ContainerDto.class)
          .useMapper(addressMapper)
          .mapper();

      container = new Container();
      container.setTags(IntStream.range(0, collectionSize)
          .mapToObj(i -> "tag-" + i)
          .collect(Collectors.toList()));
      List<Address> addresses = IntStream.range(0, collectionSize)
          .mapToObj(MappingBenchmark::newAddress)
          .collect(Collectors.toList());
      container.setAddresses(addresses);
    }
  }

  @Benchmark
  public FlatDestination mapFlatBean(MapperState state) {
    return state.flatMapper.map(state.flatSource);
  }

  @Benchmark
  public PersonDto mapNestedBean(MapperState state) {
    return state.personMapper.map(state.person);
  }

  @Benchmark
  @Threads(4)
  public PersonDto mapNestedBeanConcurrent(MapperState state) {
    return state.personMapper.map(state.person);
  }

  @Benchmark
  public ContainerDto mapCollections(CollectionState state) {
    return state.containerMapper.map(state.container);
  }

  @Benchmark
  public Mapper<Person, PersonDto> createMapper(MapperState state) {
    return Mapping.from(Person.class)
        .to(PersonDto.class)
        .useMapper(state.addressMapper)
        .mapper();
  }

  static Address newAddress(int i) {
    Address address = new Address();
    address.setStreet("Street " + i);
    address.setCity("City " + i);
    address.setZipCode(String.valueOf(10000 + i));
    address.setNumber(i);
    return address;
  }
}
