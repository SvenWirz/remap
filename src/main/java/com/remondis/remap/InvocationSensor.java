package com.remondis.remap;

import static java.lang.ClassLoader.getSystemClassLoader;
import static java.util.Objects.isNull;
import static net.bytebuddy.matcher.ElementMatchers.isDeclaredBy;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * The {@link InvocationSensor} tracks get-method invocations on a proxy class and makes the invocation information
 * available to the {@link Mapper}.
 *
 * @author schuettec
 */
public class InvocationSensor<T> {

  /**
   * Associates the interception handler (and thereby the generated proxy) with the sensed type. In contrast to a
   * static map keyed by the class, a {@link ClassValue} does not prevent garbage collection of the sensed type: the
   * handler, the generated proxy class and the sensed type form a reference cycle within the type's class loader that
   * is collected as a whole once the class loader becomes unreachable. A strongly referencing static cache caused a
   * class loader leak in container environments with redeploy cycles.
   */
  private static final ClassValue<InterceptionHandler<?>> INTERCEPTION_HANDLER_CACHE = new ClassValue<InterceptionHandler<?>>() {
    @Override
    protected InterceptionHandler<?> computeValue(Class<?> superType) {
      return createInterceptionHandler(superType);
    }
  };

  private InterceptionHandler<T> interceptionHandler;

  /**
   * Creates a proxy for the given class type. The proxy is created once per type and cached for the lifetime of the
   * type.
   *
   * @param superType the class type for which the proxy should be created
   */
  @SuppressWarnings("unchecked")
  public InvocationSensor(Class<T> superType) {
    this.interceptionHandler = (InterceptionHandler<T>) INTERCEPTION_HANDLER_CACHE.get(superType);
  }

  private static <T> InterceptionHandler<T> createInterceptionHandler(Class<T> superType) {
    ClassLoader classLoader;
    if (isNull(superType.getClassLoader())) {
      classLoader = getSystemClassLoader();
    } else {
      classLoader = superType.getClassLoader();
    }
    try {
      InterceptionHandler<T> interceptionHandler = new InterceptionHandler<>();
      T po = new ByteBuddy().subclass(superType)
          .method(isDeclaredByClassHierarchy(superType))
          .intercept(MethodDelegation.to(interceptionHandler))
          .make()
          .load(classLoader, classLoadingStrategy(superType))
          .getLoaded()
          .getDeclaredConstructor()
          .newInstance();
      interceptionHandler.setProxyObject(po);
      return interceptionHandler;
    } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException ex) {
      throw new MappingException(
          String.format("Error while creating proxy for class '%s'", superType.getCanonicalName()), ex);
    }
  }

  /**
   * Returns the class loading strategy for the proxy class. The proxy is defined via a {@link MethodHandles.Lookup},
   * which is the supported way to define classes since Java 9. The formerly used
   * {@link ClassLoadingStrategy.Default#INJECTION} relies on JDK internals and required
   * <code>--add-opens java.base/java.lang=ALL-UNNAMED</code> on current JDKs. If the sensed type's package is not
   * accessible (e.g. a type of a named module that does not open its package to ReMap), the proxy is loaded into a
   * wrapper class loader instead.
   */
  private static ClassLoadingStrategy<ClassLoader> classLoadingStrategy(Class<?> superType) {
    try {
      return ClassLoadingStrategy.UsingLookup.of(MethodHandles.privateLookupIn(superType, MethodHandles.lookup()));
    } catch (IllegalAccessException | SecurityException e) {
      return ClassLoadingStrategy.Default.WRAPPER;
    }
  }

  /**
   * Creates an {@link ElementMatcher.Junction} for the method description of all superclasses, interfaces and the given
   * type itself so that all of those methods are proxied by the {@link InvocationSensor}.
   *
   * @param type type to get the junction for
   * @return the junction with all superclasses and interfaces including the given typeD
   */
  private static <T> ElementMatcher.Junction<MethodDescription> isDeclaredByClassHierarchy(Class<T> type) {
    ClassHierarchyIterator classHierarchyIterator = new ClassHierarchyIterator(type);
    ElementMatcher.Junction<MethodDescription> methodDescriptionJunction = null;
    while (classHierarchyIterator.hasNext()) {
      Class<?> next = classHierarchyIterator.next();
      if (isNull(methodDescriptionJunction)) {
        methodDescriptionJunction = isDeclaredBy(next);
      } else {
        methodDescriptionJunction = methodDescriptionJunction.or(isDeclaredBy(next));
      }
    }
    return methodDescriptionJunction;
  }

  /**
   * Returns the proxy object get-method calls can be performed on.
   *
   * @return The proxy.
   */
  T getSensor() {
    return interceptionHandler.getProxyObject();
  }

  /**
   * Returns the list of property names that were tracked by get calls.
   *
   * @return Returns the tracked property names.
   */
  List<String> getTrackedPropertyNames() {
    List<String> trackesPropertyNames = interceptionHandler.getTrackedPropertyNames();
    return trackesPropertyNames;
  }

  /**
   * Checks if there were any properties accessed by get calls.
   *
   * @return Returns <code>true</code> if there were at least one interaction with a property. Otherwise
   *         <code>false</code> is returned.
   */
  boolean hasTrackedProperties() {
    boolean hasTrackedProperties = interceptionHandler.hasTrackedProperties();
    return hasTrackedProperties;
  }

}
