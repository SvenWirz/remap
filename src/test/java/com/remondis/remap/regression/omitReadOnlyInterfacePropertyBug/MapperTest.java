package com.remondis.remap.regression.omitReadOnlyInterfacePropertyBug;

import com.remondis.remap.Mapping;
import org.junit.jupiter.api.Test;

/**
 * Reproduces the scenario where a destination class implements an interface with a getter-only
 * property (no setter), and the class manually overrides the getter without a backing field.
 */
public class MapperTest {
  @Test
  public void omitInDestination_shouldNotFailOnGetterOnlyInterfaceProperty() {
    // Should not throw MappingException - omitInDestination should accept getter-only
    // properties silently as they are not mapping targets anyway
    Mapping.from(A.class)
        .to(B.class)
        .omitInDestination(B::getReadOnlyProperty)
        .mapper();
  }
}