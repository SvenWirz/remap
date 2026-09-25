package com.remondis.remap;

import java.beans.PropertyDescriptor;
import java.io.Serializable;
import java.lang.invoke.MethodHandleInfo;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;

/**
 * Resolves the property selected by a {@link FieldSelector} or {@link TypedSelector} on a record type. Records are
 * final and cannot be proxied, so the selected property cannot be tracked by an {@link InvocationSensor}. Instead the
 * selector - a serializable method reference like <code>Person::name</code> - is inspected using its
 * {@link SerializedLambda}: The referenced method must be a parameterless instance method of the record type or one of
 * its interfaces that reads a property of the record.
 */
final class MethodReferenceResolver {

  private static final String LAMBDA_METHOD_PREFIX = "lambda$";

  private MethodReferenceResolver() {
  }

  /**
   * Returns the property of the specified record type that is selected by the specified method reference.
   *
   * @param target Defines if the properties are validated against source or target rules.
   * @param recordType The record type.
   * @param selector The selector, which must be a method reference.
   * @param fluentSetters if true, setters that return a value are allowed in the mapping.
   * @return Returns the selected property.
   * @throws MappingException Thrown if the selector is not a method reference selecting a property of the record type.
   */
  static PropertyDescriptor getPropertyFromMethodReference(Target target, Class<?> recordType, Serializable selector,
      boolean fluentSetters) {
    SerializedLambda methodReference = toSerializedLambda(recordType, selector);
    if (methodReference.getImplMethodName()
        .startsWith(LAMBDA_METHOD_PREFIX)) {
      throw MappingException.inlineLambdaOnRecord(recordType);
    }
    if (!isParameterlessInstanceMethodOf(recordType, methodReference)) {
      throw MappingException.notARecordProperty(recordType, methodReference);
    }
    return Properties.getPropertyByReadMethod(recordType, target, methodReference.getImplMethodName(), fluentSetters)
        .orElseThrow(() -> MappingException.notARecordProperty(recordType, methodReference));
  }

  private static SerializedLambda toSerializedLambda(Class<?> recordType, Serializable selector) {
    Method writeReplace;
    try {
      writeReplace = selector.getClass()
          .getDeclaredMethod("writeReplace");
    } catch (NoSuchMethodException e) {
      // The selector is not a lambda expression or method reference, e.g. an anonymous class.
      throw MappingException.noMethodReferenceOnRecord(recordType);
    }
    Object replacement;
    try {
      writeReplace.setAccessible(true);
      replacement = writeReplace.invoke(selector);
    } catch (ReflectiveOperationException | RuntimeException e) {
      // An InaccessibleObjectException is thrown if the package of the selector is not open to ReMap.
      throw MappingException.inaccessibleMethodReference(recordType, e);
    }
    if (replacement instanceof SerializedLambda) {
      return (SerializedLambda) replacement;
    } else {
      throw MappingException.noMethodReferenceOnRecord(recordType);
    }
  }

  /**
   * Checks that the method reference refers to a parameterless instance method declared by the record type, one of its
   * interfaces or superclasses. This denies static methods and methods of other types that happen to have the name of a
   * property.
   */
  private static boolean isParameterlessInstanceMethodOf(Class<?> recordType, SerializedLambda methodReference) {
    int kind = methodReference.getImplMethodKind();
    boolean isInstanceMethod = kind == MethodHandleInfo.REF_invokeVirtual
        || kind == MethodHandleInfo.REF_invokeInterface;
    boolean isParameterless = methodReference.getImplMethodSignature()
        .startsWith("()");
    boolean isUnbound = methodReference.getCapturedArgCount() == 0;
    return isInstanceMethod && isParameterless && isUnbound
        && isInClassHierarchy(recordType, methodReference.getImplClass());
  }

  private static boolean isInClassHierarchy(Class<?> type, String internalClassName) {
    String className = internalClassName.replace('/', '.');
    ClassHierarchyIterator hierarchy = new ClassHierarchyIterator(type);
    while (hierarchy.hasNext()) {
      if (hierarchy.next()
          .getName()
          .equals(className)) {
        return true;
      }
    }
    return false;
  }

}
