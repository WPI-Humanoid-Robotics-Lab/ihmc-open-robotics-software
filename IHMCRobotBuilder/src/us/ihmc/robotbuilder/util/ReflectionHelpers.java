package us.ihmc.robotbuilder.util;

import javaslang.Tuple;
import javaslang.collection.HashMap;
import javaslang.control.Option;
import javaslang.control.Try;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.reflect.Modifier.isPublic;
import static java.lang.reflect.Modifier.isStatic;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

/**
 * Methods facilitating the use of reflection in Java.
 */
public class ReflectionHelpers
{
   private static final String GET_PREFIX = "get";

   /**
    * Returns generic parameters for a field represented by the given getter.
    * For example returns [Integer, String] for Map&lt;Integer, String&gt; getSomeMap().
    * An empty list is returned if the return type is not generic.
    * @param getter field getter
    * @return generic parameters
    */
   public static List<Class<?>> getGenericParameters(Method getter)
   {
      Type returnType = getter.getGenericReturnType();
      if (returnType instanceof ParameterizedType)
      {
         ParameterizedType genericType = (ParameterizedType)returnType;
         return Arrays.stream(genericType.getActualTypeArguments())
                      .filter(type -> type instanceof Class)
                      .map(type -> (Class<?>)type)
                      .collect(toList());
      }
      return emptyList();
   }

   /**
    * Gets the list of getters of the given Java class.
    * @param bean class to analyze
    * @return list of getters
    */
   public static List<Method> listGetters(Class<?> bean)
   {
      return Arrays.stream(bean.getMethods())
                   .filter(ReflectionHelpers::isGetter)
                   .collect(toList());
   }

   /**
    * Checks whether the given {@link Method} is a getter.
    * @param method method
    * @return true if the method is a getter, false otherwise
    */
   public static boolean isGetter(Method method)
   {
      return isPublic(method.getModifiers()) && !isStatic(method.getModifiers()) &&
            method.getParameterCount() == 0 && method.getName().startsWith(GET_PREFIX) &&
            !method.isSynthetic() && !method.isBridge();
   }

   private static Optional<Method> findSetter(Class<?> clazz, String propertyName, Class<?> propertyType, Class<?> expectedReturnType, String[] prefixes)
   {
      return Arrays.stream(clazz.getMethods())
                   .filter(method -> Arrays.stream(prefixes).anyMatch(method.getName()::startsWith))
                   .filter(method -> method.getName().contains(propertyName))
                   .filter(method -> expectedReturnType == null || expectedReturnType.isAssignableFrom(method.getReturnType()))
                   .filter(method -> method.getParameterCount() == 1 && method.getParameterTypes()[0].isAssignableFrom(propertyType))
                   .findFirst();
   }

   /**
    * Get properties for an immutable Java class that uses the getProperty/withProperty style.
    * @param clazz class
    * @param <Bean> class type
    * @return list of properties (getProperty/withProperty pairs)
    */
   public static <Bean> List<ImmutableReflectionProperty<Bean>> propertiesForBeanWithImmutableSetters(Class<Bean> clazz)
   {
      return listGetters(clazz)
            .stream()
            .map(getMethod -> {
               String propertyName = propertyNameFromGetter(getMethod);
               return findSetter(clazz, propertyName, getMethod.getReturnType(), clazz, new String[] {"with", "set"})
                     .map(setter -> new ImmutableReflectionProperty<Bean>(propertyName, getMethod.getReturnType(), getGenericParameters(getMethod), wrapGetter(getMethod), wrapWither(setter)));
            })
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(toList());
   }

   /**
    * Returns the list of properties for a mutable Java class that uses the getProperty/setProperty style.
    * @param clazz class
    * @param <Bean> class type
    * @return list of properties (getProperty/setProperty pairs)
    */
   public static <Bean> List<MutableReflectionProperty<Bean>> propertiesForBeanWithMutableSetters(Class<Bean> clazz)
   {
      return listGetters(clazz)
            .stream()
            .map(getMethod -> {
               String propertyName = propertyNameFromGetter(getMethod);
               return findSetter(clazz, propertyName, getMethod.getReturnType(), null, new String[]{"set"})
                     .map(setter -> new MutableReflectionProperty<Bean>(propertyName, getMethod.getReturnType(), getGenericParameters(getMethod), wrapGetter(getMethod), wrapSetter(setter)));
            })
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(toList());
   }

   /**
    * Returns the list of properties for an immutable Java class that uses the getProperty/Constructor taking all properties style.
    * @param clazz class
    * @param <Bean> class type
    * @return list of properties (that are settable in the constructor)
    */
   public static <Bean> List<ImmutableReflectionProperty<Bean>> propertiesForImmutableBeanWithBuilderConstructor(Class<Bean> clazz)
   {
      List<Method> getters = listGetters(clazz);
      Optional<Constructor<Bean>> constructorOpt =
            findBuilderConstructor(clazz, getters.stream().map(ReflectionHelpers::propertyNameFromGetter).collect(toList()));

      return constructorOpt.map(constructor -> getPropertiesFromConstructorAndGetters(getters, constructor))
                           .map(stream -> stream.collect(toList()))
                           .orElse(emptyList());
   }

   private static <Bean> Stream<ImmutableReflectionProperty<Bean>> getPropertiesFromConstructorAndGetters(List<Method> getters, Constructor<Bean> constructor)
   {
      return getters.stream().filter(getter -> hasConstructorProperty(constructor, propertyNameFromGetter(getter))).map(getter -> {
         String propertyName = propertyNameFromGetter(getter);
         ReflectedSetter<Bean, Bean> setter = (bean, value) -> createBeanWithChangedAttribute(constructor, bean, getters, propertyName, value);
         return new ImmutableReflectionProperty<>(propertyName, getter.getReturnType(), getGenericParameters(getter), wrapGetter(getter), setter);
      });
   }

   private static <Bean> Try<Bean> createBeanWithChangedAttribute(Constructor<Bean> constructor, Bean sourceBean, List<Method> getters, String propertyName, Object newPropertyValue)
   {
      HashMap<String, Method> gettersByName = getters.stream()
                                                     .map(getter -> Tuple.of(propertyNameFromGetter(getter).toLowerCase(), getter))
                                                     .collect(HashMap.collector());

      Try<Object>[] parameterTrys = Arrays.stream(constructor.getParameters())
                                       .map(parameter ->
                                         {
                                            String paramName = parameter.getName().toLowerCase();
                                            if (propertyName.toLowerCase().equals(paramName))
                                            {
                                               return Try.of(() -> newPropertyValue);
                                            }
                                            else
                                            {
                                               Option<Method> getterOpt = gettersByName.get(paramName);
                                               return getterOpt.map(ReflectionHelpers::wrapGetter)
                                                               .map(wrappedGetter -> wrappedGetter.getValue(sourceBean))
                                                               .getOrElse(Try.failure(new Exception("Getter " + paramName + " does not exist")));
                                            }
                                         })
                                       .toArray((IntFunction<Try<Object>[]>)Try[]::new);

      Object[] parameters = new Object[parameterTrys.length];
      for (int i = 0; i < parameterTrys.length; i++)
      {
         if (parameterTrys[i].isFailure())
            return Try.failure(parameterTrys[i].getCause());
         parameters[i] = parameterTrys[i].get();
      }

      return Try.of(() -> constructor.newInstance(parameters));
   }

   private static <Bean> Optional<Constructor<Bean>> findBuilderConstructor(Class<Bean> clazz, List<String> propertyNames)
   {
      Set<String> transformedPropertyNames = propertyNames.stream().map(String::toLowerCase).collect(Collectors.toSet());
      //noinspection unchecked
      return (Optional)
            Arrays.stream(clazz.getDeclaredConstructors())
                  .sorted((c1, c2) -> -Integer.compare(c1.getParameterCount(), c2.getParameterCount()))
                  .filter(constructor -> hasConstructorAllProperties(constructor, transformedPropertyNames))
                  .findFirst();
   }

   private static boolean hasConstructorAllProperties(Constructor<?> constructor, Set<String> propertyNames)
   {
      return Arrays.stream(constructor.getParameters())
                   .map(Parameter::getName)
                   .map(String::toLowerCase)
                   .allMatch(propertyNames::contains);
   }

   private static boolean hasConstructorProperty(Constructor<?> constructor, String propertyName)
   {
      return Arrays.stream(constructor.getParameters())
                   .map(Parameter::getName)
                   .map(String::toLowerCase)
                   .anyMatch(propertyName.toLowerCase()::equals);
   }

   private static <BeanClass> ReflectedGetter<BeanClass> wrapGetter(Method getter)
   {
      return object -> Try.of(() -> getter.invoke(object));
   }

   private static <BeanClass> ReflectedSetter<BeanClass, Void> wrapSetter(Method setter)
   {
      return (object, value) -> Try.of(() -> { setter.invoke(object, value); return null; });
   }

   private static <BeanClass> ReflectedSetter<BeanClass, BeanClass> wrapWither(Method setter)
   {
      //noinspection unchecked
      return (object, value) -> Try.of(() -> (BeanClass)setter.invoke(object, value));
   }

   private static String propertyNameFromGetter(Method getter)
   {
      if (!getter.getName().startsWith(GET_PREFIX))
         return getter.getName();
      return getter.getName().substring(GET_PREFIX.length());
   }

   /**
    * Wraps reflection call to a setter into a functional interface for
    * better readability and error handling.
    * @param <BeanClass> base class type
    * @param <SetterReturnType> setter return type (usually either BeanClass or Void)
    */
   public interface ReflectedSetter<BeanClass, SetterReturnType>
   {
      Try<SetterReturnType> withValue(BeanClass object, Object value);
   }

   /**
    * Wraps reflection call to a getter into a functional interface for
    * better readability and error handling.
    * @param <BeanClass> base class type
    */
   public interface ReflectedGetter<BeanClass>
   {
      Try<Object> getValue(BeanClass object);
   }

   private static class ReflectionProperty<BeanClass, SetterReturnType>
   {
      private final String name;
      private final Class<?> type;
      private final List<Class<?>> genericTypes;
      private final ReflectedGetter<BeanClass> getter;
      private final ReflectedSetter<BeanClass, SetterReturnType> setter;

      ReflectionProperty(String name, Class<?> type, List<Class<?>> genericTypes, ReflectedGetter<BeanClass> getter,
                         ReflectedSetter<BeanClass, SetterReturnType> setter)
      {
         this.name = name;
         this.type = type;
         this.genericTypes = genericTypes;
         this.getter = getter;
         this.setter = setter;
      }

      public final String getName()
      {
         return name;
      }

      public Class<?> getType()
      {
         return type;
      }

      public List<Class<?>> getGenericTypes()
      {
         return genericTypes;
      }

      public ReflectedGetter<BeanClass> getGetter()
      {
         return getter;
      }

      public ReflectedSetter<BeanClass, SetterReturnType> getSetter()
      {
         return setter;
      }
   }

   /**
    * Wraps a getter & setter into a modifiable property.
    * @param <BeanClass> bean class
    */
   public static class MutableReflectionProperty<BeanClass> extends ReflectionProperty<BeanClass, Void>
   {

      MutableReflectionProperty(String name, Class<?> type, List<Class<?>> genericTypes, ReflectedGetter<BeanClass> getter,
                                ReflectedSetter<BeanClass, Void> setter)
      {
         super(name, type, genericTypes, getter, setter);
      }
   }

   /**
    * Wraps a getter & immutable setter into a modifiable property.
    * @param <BeanClass> bean class
    */
   public static class ImmutableReflectionProperty<BeanClass> extends ReflectionProperty<BeanClass, BeanClass>
   {

      ImmutableReflectionProperty(String name, Class<?> type, List<Class<?>> genericTypes, ReflectedGetter<BeanClass> getter,
                                  ReflectedSetter<BeanClass, BeanClass> setter)
      {
         super(name, type, genericTypes, getter, setter);
      }
   }
}
