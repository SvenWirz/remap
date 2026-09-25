package com.remondis.remap;

import static com.remondis.remap.ReflectionUtil.isGetter;
import static com.remondis.remap.ReflectionUtil.isSetter;
import static com.remondis.remap.ReflectionUtil.toPropertyName;
import static java.util.Objects.isNull;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Util class to get a list of all properties of a class.
 *
 * @author schuettec
 */
class Properties {

  /**
   * A readable string representation for a {@link PropertyDescriptor}.
   *
   * @param pd The pd
   * @return Returns a readable string.
   */
  static String asStringWithType(PropertyDescriptor pd) {
    return asStringWithType(pd, false);
  }

  /**
   * A readable string representation for a {@link PropertyDescriptor}.
   *
   * @param pd The pd
   * @param detailed If <code>false</code> simple names should be used. If <code>true</code> fully qualified names
   *        should be used.
   * @return Returns a readable string.
   */
  static String asStringWithType(PropertyDescriptor pd, boolean detailed) {
    Class<?> clazz = Properties.getPropertyClass(pd);
    return String.format("Property '%s' (%s) in %s", pd.getName(), pd.getPropertyType()
        .getName(), (detailed ? clazz.getName() : clazz.getSimpleName()));
  }

  /**
   * A readable string representation for a {@link PropertyDescriptor}.
   *
   * @param pd The pd
   * @return Returns a readable string.
   */
  static String asString(PropertyDescriptor pd) {
    return asString(pd, false);
  }

  /**
   * A readable string representation for a {@link PropertyDescriptor}.
   *
   * @param pd The pd
   * @param detailed If <code>false</code> simple names should be used. If <code>true</code> fully qualified names
   *        should be used.
   * @return Returns a readable string.
   */
  static String asString(PropertyDescriptor pd, boolean detailed) {
    Class<?> clazz = Properties.getPropertyClass(pd);
    return String.format("Property '%s' in %s", pd.getName(), (detailed ? clazz.getName() : clazz.getSimpleName()));
  }

  /**
   * Returns the class declaring the property.
   *
   * @param propertyDescriptor the {@link PropertyDescriptor}
   * @return Returns the declaring class.
   */
  static Class<?> getPropertyClass(PropertyDescriptor propertyDescriptor) {
    return propertyDescriptor.getReadMethod()
        .getDeclaringClass();
  }

  /**
   * Creates a message showing all currently unmapped properties.
   *
   * @param unmapped The set of unmapped properties.
   * @return Returns the message.
   */
  static String createUnmappedMessage(Set<PropertyDescriptor> unmapped) {
    StringBuilder msg = new StringBuilder("The following properties are unmapped:\n");
    for (PropertyDescriptor pd : unmapped) {
      String getter = pd.getReadMethod()
          .getName();
      Method writeMethod = pd.getWriteMethod();
      String setter = isNull(writeMethod) ? "none" : writeMethod.getName();
      msg.append("- ")
          .append(asString(pd))
          .append("\n\taccess methods: ")
          .append(getter)
          .append("() / ")
          .append(setter)
          .append("()\n");
    }
    return msg.toString();
  }

  /**
   * Returns a {@link Set} of properties with read and write access.
   *
   * @param inspectType The type to inspect.
   * @param targetType The type of mapping target.
   * @param fluentSetters if true, setters that return a value are allowed in the mapping.
   * @return Returns the list of {@link PropertyDescriptor}s that grant read and write access.
   * @throws MappingException Thrown on any introspection error.
   */
  static Set<PropertyDescriptor> getProperties(Class<?> inspectType, Target targetType, boolean fluentSetters) {
    try {
      if (inspectType.isRecord()) {
        return getRecordProperties(inspectType, targetType);
      }
      Set<PropertyDescriptor> result = extractBaseProperties(inspectType, targetType, fluentSetters);
      mergeInterfaceProperties(inspectType, targetType, result);
      return result;
    } catch (IntrospectionException e) {
      throw new MappingException(String.format("Cannot introspect the type %s.", inspectType.getName()));
    }
  }

  /**
   * Returns the property of the specified type that is read by the method with the specified name. For records, a
   * getter named like a record component - for example <code>getName()</code> for the component <code>name</code> - is
   * resolved to the record component.
   *
   * @param inspectType The type to inspect.
   * @param targetType The type of mapping target.
   * @param readMethodName The name of the read method.
   * @param fluentSetters if true, setters that return a value are allowed in the mapping.
   * @return Returns the property read by the specified method or an empty {@link Optional} if the method does not
   *         read a property of the specified type.
   * @throws MappingException Thrown on any introspection error.
   */
  static Optional<PropertyDescriptor> getPropertyByReadMethod(Class<?> inspectType, Target targetType,
      String readMethodName, boolean fluentSetters) {
    Set<PropertyDescriptor> properties = getProperties(inspectType, targetType, fluentSetters);
    Optional<PropertyDescriptor> property = findByReadMethodName(properties, readMethodName);
    if (property.isPresent() || !inspectType.isRecord()) {
      return property;
    }
    try {
      return findByReadMethodName(getRecordGetterProperties(inspectType), readMethodName)
          .map(getter -> findPropertyDescriptor(properties, getter.getName()));
    } catch (IntrospectionException e) {
      throw new MappingException(String.format("Cannot introspect the type %s.", inspectType.getName()));
    }
  }

  private static Optional<PropertyDescriptor> findByReadMethodName(Set<PropertyDescriptor> properties,
      String readMethodName) {
    return properties.stream()
        .filter(pd -> pd.getReadMethod()
            .getName()
            .equals(readMethodName))
        .findFirst();
  }

  /**
   * Returns the properties of a record type. The record components are read by their accessor methods and written by
   * the canonical constructor, so they are mapping sources as well as mapping targets. Other getter methods of a record
   * - for example derived values or getters added for bean-based libraries - are read-only properties and therefore
   * only mapping sources, just like read-only properties of Java Beans. Records used as mapping source were
   * introspected as Java Beans before ReMap supported records, so this keeps existing mappings working. A getter named
   * like a record component - for example <code>getName()</code> for the component <code>name</code> - is an alias of
   * the component and not a property of its own.
   */
  private static Set<PropertyDescriptor> getRecordProperties(Class<?> recordType, Target targetType)
      throws IntrospectionException {
    Set<PropertyDescriptor> result = new HashSet<>();
    Set<String> componentNames = new HashSet<>();
    Set<String> accessorNames = new HashSet<>();
    for (RecordComponent component : recordType.getRecordComponents()) {
      result.add(new PropertyDescriptor(component.getName(), component.getAccessor(), null));
      componentNames.add(component.getName());
      accessorNames.add(component.getAccessor()
          .getName());
    }
    if (Target.SOURCE.equals(targetType)) {
      for (PropertyDescriptor getterProperty : getRecordGetterProperties(recordType)) {
        boolean isComponentAlias = componentNames.contains(getterProperty.getName());
        // An accessor like isActive() of the component isActive is also a Java Bean getter of the property active.
        boolean isComponentAccessor = accessorNames.contains(getterProperty.getReadMethod()
            .getName());
        if (!isComponentAlias && !isComponentAccessor) {
          result.add(getterProperty);
        }
      }
    }
    return result;
  }

  /**
   * Returns the getter-based properties of a record type as read-only properties, determined the same way as the
   * source properties of Java Beans.
   */
  private static Set<PropertyDescriptor> getRecordGetterProperties(Class<?> recordType) throws IntrospectionException {
    Set<PropertyDescriptor> beanProperties = extractBaseProperties(recordType, Target.SOURCE, false);
    mergeInterfaceProperties(recordType, Target.SOURCE, beanProperties);
    Set<PropertyDescriptor> result = new HashSet<>();
    for (PropertyDescriptor beanProperty : beanProperties) {
      result.add(new PropertyDescriptor(beanProperty.getName(), beanProperty.getReadMethod(), null));
    }
    return result;
  }

  /**
   * Extracts the initial set of properties using JavaBeans introspection.
   */
  private static Set<PropertyDescriptor> extractBaseProperties(Class<?> type, Target targetType, boolean fluentSetters)
      throws IntrospectionException {

    BeanInfo beanInfo = Introspector.getBeanInfo(type);
    Stream<PropertyDescriptor> stream = Arrays.stream(beanInfo.getPropertyDescriptors());

    if (fluentSetters && targetType == Target.DESTINATION) {
      stream = stream.map(pd -> pd.getWriteMethod() == null ? checkForAndSetFluentWriteMethod(type, pd) : pd);
    }

    return stream.filter(pd -> !"class".equals(pd.getName()))
        .filter(Properties::hasGetter)
        .filter(pd -> Target.SOURCE.equals(targetType) || hasSetter(pd))
        .collect(Collectors.toSet());
  }

  /**
   * Merges properties from implemented interfaces into the existing property set.
   */
  private static void mergeInterfaceProperties(Class<?> inspectType, Target targetType,
      Set<PropertyDescriptor> result) {
    for (Class<?> iface : inspectType.getInterfaces()) {
      Map<String, GetterSetterHolder> accessorsByProperty = new HashMap<>();

      for (Method method : iface.getDeclaredMethods()) {
        if (Modifier.isStatic(method.getModifiers())) {
          continue;
        }
        boolean isGetter = isGetter(method);
        boolean isSetter = isSetter(method);
        if (!isGetter && !isSetter) {
          continue;
        }

        String name = toPropertyName(method);
        GetterSetterHolder holder = accessorsByProperty.computeIfAbsent(name, k -> new GetterSetterHolder());

        if (isGetter)
          holder.setGetter(method);
        if (isSetter)
          holder.setSetter(method);
      }

      for (Map.Entry<String, GetterSetterHolder> entry : accessorsByProperty.entrySet()) {
        String name = entry.getKey();
        GetterSetterHolder holder = entry.getValue();

        PropertyDescriptor existing = findPropertyDescriptor(result, name);

        if (existing == null) {
          // Interface properties are subject to the same access requirements as the base properties: a getter is
          // always required and mapping targets additionally require a setter. Read-only properties declared by an
          // interface are no mapping targets.
          boolean isMappingCandidate = holder.getGetter() != null
              && (Target.SOURCE.equals(targetType) || holder.getSetter() != null);
          if (!isMappingCandidate) {
            continue;
          }
          try {
            PropertyDescriptor pd = new PropertyDescriptor(name, holder.getGetter(), holder.getSetter());
            result.add(pd);
          } catch (IntrospectionException e) {
            throw new MappingException("Failed to create interface PropertyDescriptor for: " + name, e);
          }
        } else {
          result.remove(existing);
          Method getter = chooseGetter(existing.getReadMethod(), holder.getGetter());
          Method setter = chooseSetter(existing.getWriteMethod(), holder.getSetter());
          try {
            result.add(new PropertyDescriptor(name, getter, setter));
          } catch (IntrospectionException e) {
            throw new MappingException("Failed to create PropertyDescriptor for: " + name, e);
          }
        }
      }
    }
  }

  /**
   * Selects a getter method preferring compatible interface override if available.
   */
  private static Method chooseGetter(Method oldGetter, Method newGetter) {
    if (newGetter == null)
      return oldGetter;
    if (oldGetter == null)
      return newGetter;

    Type oldType = oldGetter.getGenericReturnType();
    Type newType = newGetter.getGenericReturnType();

    if (oldType instanceof Class<?> && newType instanceof Class<?> && ((Class<?>) oldType).getName()
        .equals(((Class<?>) newType).getName())) {
      return newGetter;
    }

    return oldGetter;
  }

  /**
   * Selects a setter method preferring compatible interface override if available.
   */
  private static Method chooseSetter(Method oldSetter, Method newSetter) {
    if (newSetter == null)
      return oldSetter;
    if (oldSetter == null)
      return newSetter;

    if (oldSetter.getParameterCount() == 1 && newSetter.getParameterCount() == 1) {
      Type oldType = oldSetter.getGenericParameterTypes()[0];
      Type newType = newSetter.getGenericParameterTypes()[0];

      if (oldType instanceof Class<?> && newType instanceof Class<?> && ((Class<?>) oldType).getName()
          .equals(((Class<?>) newType).getName())) {
        return newSetter;
      }
    }

    return oldSetter;
  }

  /**
   * Finds a PropertyDescriptor object in the existing set list
   */
  private static PropertyDescriptor findPropertyDescriptor(Set<PropertyDescriptor> properties, String propertyName) {
    return properties.stream()
        .filter(pd -> pd.getName()
            .equals(propertyName))
        .findFirst()
        .orElse(null);
  }

  /**
   * Tries to see if a fluent setXXX method exists even though it was not found by the initial retrospection.
   * If a setter exists set it as the property descriptors write method.
   */
  static PropertyDescriptor checkForAndSetFluentWriteMethod(Class<?> inspectType, PropertyDescriptor pd) {
    String writeMethodName = pd.getName();
    writeMethodName = "set" + writeMethodName.substring(0, 1)
        .toUpperCase() + writeMethodName.substring(1);
    try {
      Method setMethod = inspectType.getDeclaredMethod(writeMethodName, pd.getPropertyType());
      if (Modifier.isPublic(setMethod.getModifiers())) {
        /*
         * Create a new PropertyDescriptor instance, because the one supplied here comes from the Java Introspector and
         * is cached VM-wide (?). Due to this caching, it is not possible to deactivate fluent setters for other mapper
         * instances. We have to create a new PropertyDescriptor to avoid this.
         */
        PropertyDescriptor clone = new PropertyDescriptor(pd.getName(), pd.getReadMethod(), setMethod);
        return clone;
      }
    } catch (NoSuchMethodException e) {
      // just ignore, the method does not have to exist
    } catch (IntrospectionException e) {
      throw new RuntimeException(e);
    }
    return pd;
  }

  private static boolean hasGetter(PropertyDescriptor pd) {
    return pd.getReadMethod() != null;
  }

  private static boolean hasSetter(PropertyDescriptor pd) {
    return pd.getWriteMethod() != null;
  }

  private static class GetterSetterHolder {
    private Method getter;
    private Method setter;

    public Method getGetter() {
      return getter;
    }

    public void setGetter(Method getter) {
      this.getter = getter;
    }

    public Method getSetter() {
      return setter;
    }

    public void setSetter(Method setter) {
      this.setter = setter;
    }
  }

}
