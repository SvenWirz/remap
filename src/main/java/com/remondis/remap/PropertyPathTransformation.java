package com.remondis.remap;

import static com.remondis.remap.Properties.asString;

import java.beans.PropertyDescriptor;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import com.remondis.propertypath.api.Get;
import com.remondis.propertypath.api.Getter;
import com.remondis.propertypath.api.PropertyPath;

/**
 * The property path operation maps the result of a property path that is applied to the source field to another field.
 * If the property path does not return a value, the mapping is omitted.
 *
 * @param <RS> The type of the source field.
 * @param <X> Tje type of the value returned by a property path.
 * @param <RD> The type of the destination field.
 * @author schuettec
 */
public class PropertyPathTransformation<RS, X, RD> extends Transformation {

  private static final String PROPERTY_PATH_MSG = "Replacing %s\n           with %s\n"
      + "           using property path%s: %s";
  private Get<RS, RD, ?> propertyPath;
  private boolean hasTransformation;

  PropertyPathTransformation(MappingConfiguration<?, ?> mapping, PropertyDescriptor sourceProperty,
      PropertyDescriptor destinationProperty, PropertyPath<RD, RS, ?> propertyPath) {
    super(mapping, sourceProperty, destinationProperty);
    this.propertyPath = createGetter(sourceProperty, propertyPath);
  }

  PropertyPathTransformation(MappingConfiguration<?, ?> mapping, PropertyDescriptor sourceProperty,
      PropertyDescriptor destinationProperty, PropertyPath<X, RS, ?> propertyPath, Function<X, RD> transformation) {
    super(mapping, sourceProperty, destinationProperty);
    this.propertyPath = createGetterAndApply(sourceProperty, propertyPath, transformation);
    this.hasTransformation = true;
  }

  @Override
  public String getSourcePropertyName() {
    return sourceProperty.getName() + "." + propertyPath.toPath();
  }

  @SuppressWarnings("unchecked")
  private Get<RS, RD, ?> createGetter(PropertyDescriptor sourceProperty, PropertyPath<RD, RS, ?> propertyPath) {
    Class<RS> sourceType = (Class<RS>) sourceProperty.getPropertyType();
    return evaluatePropertyPath(sourceProperty, sourceType, () -> Getter.newFor(sourceType)
        .evaluate(propertyPath));
  }

  @SuppressWarnings("unchecked")
  private Get<RS, RD, ?> createGetterAndApply(PropertyDescriptor sourceProperty, PropertyPath<X, RS, ?> propertyPath,
      Function<X, RD> transformation) {
    Class<RS> sourceType = (Class<RS>) sourceProperty.getPropertyType();
    return evaluatePropertyPath(sourceProperty, sourceType, () -> Getter.newFor(sourceType)
        .evaluate(propertyPath)
        .andApply(transformation));
  }

  /**
   * Evaluates a property path on the specified type. Property paths are recorded using proxy objects of the types on
   * the path, which cannot be created for records and other final types. This is reported as {@link MappingException}
   * instead of the exception thrown by the property path library.
   *
   * @param sourceProperty The source property the property path is applied to.
   * @param pathType The type the property path starts at.
   * @param evaluation Evaluates the property path.
   * @return Returns the result of the evaluation.
   */
  static <G> G evaluatePropertyPath(PropertyDescriptor sourceProperty, Class<?> pathType, Supplier<G> evaluation) {
    if (pathType.isRecord()) {
      throw MappingException.propertyPathOnRecord(sourceProperty, pathType);
    }
    try {
      return evaluation.get();
    } catch (RuntimeException e) {
      if (isCausedByTypeNotProxyable(e)) {
        throw MappingException.propertyPathNotEvaluable(sourceProperty, e);
      }
      throw e;
    }
  }

  /**
   * The property path library throws an {@link IllegalArgumentException} if the type the property path starts at
   * cannot be subclassed. For other types on the path that cannot be subclassed it returns <code>null</code>, so the
   * property path fails with a {@link NullPointerException}.
   */
  private static boolean isCausedByTypeNotProxyable(RuntimeException exception) {
    if (exception instanceof IllegalArgumentException) {
      return true;
    }
    for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
      if (cause instanceof NullPointerException) {
        return true;
      }
    }
    return false;
  }

  @Override
  protected MappedResult performValueTransformation(Object source, Object destination) throws MappingException {
    if (source == null) {
      // Skip if source value is null. Property paths are null-friendly.
      return MappedResult.skip();
    }

    try {
      Optional<RD> optional = propertyPath.from((RS) source);
      if (optional.isPresent()) {
        RD destinationValue = optional.get();
        return MappedResult.value(destinationValue);
      } else {
        return MappedResult.skip();
      }
    } catch (Exception e) {
      throw new MappingException(
          String.format("The property path for mapping %s to %s evaluating %s failed with an exception.",
              asString(sourceProperty), asString(destinationProperty), propertyPath.toString()),
          e);
    }
  }

  @Override
  protected void validateTransformation() throws MappingException {
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + (hasTransformation ? 1231 : 1237);
    result = prime * result + ((propertyPath == null) ? 0 : propertyPath.hashCode());
    return result;
  }

  @SuppressWarnings("rawtypes")
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!super.equals(obj)) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    PropertyPathTransformation other = (PropertyPathTransformation) obj;
    if (hasTransformation != other.hasTransformation) {
      return false;
    }
    if (propertyPath == null) {
      if (other.propertyPath != null) {
        return false;
      }
    } else if (!propertyPath.equals(other.propertyPath)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString(boolean detailed) {
    return String.format(PROPERTY_PATH_MSG, asString(sourceProperty, detailed), asString(destinationProperty, detailed),
        hasTransformation ? " with transformation function" : "", propertyPath.toString(detailed));
  }
}
