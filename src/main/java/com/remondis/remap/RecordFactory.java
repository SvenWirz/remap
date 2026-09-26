package com.remondis.remap;

import static com.remondis.remap.ReflectionUtil.defaultValue;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Creates instances of a record type that is used as mapping destination. Records are immutable, so the values of all
 * transformations are collected first and then passed to the canonical constructor of the record. The construction
 * plan - the canonical constructor, the constructor argument provided by each transformation and the default arguments
 * - is resolved once when the mapper is created, so mapping a record does not require any reflective lookup.
 *
 * @param <D> The record type.
 */
final class RecordFactory<D> {

  private final Class<D> recordType;
  private final RecordComponent[] components;
  private final Constructor<D> canonicalConstructor;

  /**
   * The constructor arguments for components not provided by a transformation, for example omitted components:
   * <code>null</code> for reference types and the default value for primitive types.
   */
  private final Object[] defaultArguments;

  /**
   * The indexes of components of primitive types, because the canonical constructor does not accept <code>null</code>
   * for them.
   */
  private final int[] primitiveArgumentIndexes;

  private final Transformation[] transformations;

  /**
   * The index of the constructor argument provided by the transformation at the same position in
   * {@link #transformations}.
   */
  private final int[] argumentIndexes;

  RecordFactory(Class<D> recordType, Collection<Transformation> mappings) {
    this.recordType = recordType;
    this.components = recordType.getRecordComponents();
    this.canonicalConstructor = canonicalConstructor(recordType, components);
    this.defaultArguments = new Object[components.length];
    List<Integer> primitiveIndexes = new ArrayList<>();
    Map<String, Integer> argumentIndexByComponentName = new HashMap<>();
    for (int i = 0; i < components.length; i++) {
      Class<?> componentType = components[i].getType();
      if (componentType.isPrimitive()) {
        defaultArguments[i] = defaultValue(componentType);
        primitiveIndexes.add(i);
      }
      argumentIndexByComponentName.put(components[i].getName(), i);
    }
    this.primitiveArgumentIndexes = toArray(primitiveIndexes);

    List<Transformation> valueTransformations = new ArrayList<>();
    List<Integer> indexes = new ArrayList<>();
    for (Transformation transformation : mappings) {
      String destinationPropertyName = transformation.getDestinationPropertyName();
      if (transformation instanceof OmitTransformation || destinationPropertyName == null) {
        continue;
      }
      Integer argumentIndex = argumentIndexByComponentName.get(destinationPropertyName);
      if (argumentIndex == null) {
        throw new MappingException(
            String.format("The property '%s' is not a component of record type %s - this is an implementation fault.",
                destinationPropertyName, recordType.getName()));
      }
      valueTransformations.add(transformation);
      indexes.add(argumentIndex);
    }
    this.transformations = valueTransformations.toArray(new Transformation[0]);
    this.argumentIndexes = toArray(indexes);
  }

  /**
   * Creates a new record instance from the values computed by the transformations for the specified source object.
   *
   * @param source The source object.
   * @return Returns the new record instance.
   * @throws MappingException Thrown if a transformation fails or the record cannot be created.
   */
  D newInstance(Object source) {
    Object[] arguments = defaultArguments.clone();
    for (int i = 0; i < transformations.length; i++) {
      MappedResult result = transformations[i].computeValue(source);
      if (result.hasValue()) {
        arguments[argumentIndexes[i]] = result.getValue();
      }
    }
    denyNullForPrimitiveComponents(arguments);
    try {
      return canonicalConstructor.newInstance(arguments);
    } catch (InvocationTargetException e) {
      // The canonical constructor rejected the arguments, for example a validation in a compact constructor.
      Throwable cause = e.getCause() == null ? e : e.getCause();
      throw MappingException.recordConstructionFailed(recordType, cause);
    } catch (ReflectiveOperationException | IllegalArgumentException e) {
      throw MappingException.recordConstructionFailed(recordType, e);
    }
  }

  private void denyNullForPrimitiveComponents(Object[] arguments) {
    for (int index : primitiveArgumentIndexes) {
      if (arguments[index] == null) {
        throw MappingException.nullForPrimitiveRecordComponent(recordType, components[index]);
      }
    }
  }

  private static <D> Constructor<D> canonicalConstructor(Class<D> recordType, RecordComponent[] components) {
    Class<?>[] parameterTypes = new Class<?>[components.length];
    for (int i = 0; i < components.length; i++) {
      parameterTypes[i] = components[i].getType();
    }
    Constructor<D> constructor;
    try {
      constructor = recordType.getDeclaredConstructor(parameterTypes);
    } catch (NoSuchMethodException e) {
      throw MappingException.newInstanceFailed(recordType, e);
    }
    try {
      // Records that are not public, like records declared within a class, are common.
      constructor.setAccessible(true);
    } catch (RuntimeException e) {
      // Ignore here: invoking the inaccessible constructor fails with an exception that is reported on mapping.
    }
    return constructor;
  }

  private static int[] toArray(List<Integer> values) {
    return values.stream()
        .mapToInt(Integer::intValue)
        .toArray();
  }

}
