/**
 *    Copyright 2009-2018 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.reflection;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;

import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.property.PropertyTokenizer;

/**
 * @author Clinton Begin
 */
// 元类
public class MetaClass {

  private final ReflectorFactory reflectorFactory;
  private final Reflector reflector;

  // 构造函数，type 是传入的类，reflectorFactory 是反射器工厂，用于生成类对应的反射器
  private MetaClass(Class<?> type, ReflectorFactory reflectorFactory) {
    this.reflectorFactory = reflectorFactory;
    this.reflector = reflectorFactory.findForClass(type);
  }

  // 构造函数是 private 类型的，MetaClass 实例需要用 forClass 静态方法来创建
  public static MetaClass forClass(Class<?> type, ReflectorFactory reflectorFactory) {
    return new MetaClass(type, reflectorFactory);
  }

  // 构建 Class 中 Field 属性对应的 MetaClass
  public MetaClass metaClassForProperty(String name) {
    Class<?> propType = reflector.getGetterType(name);  // 获取 type 类中 name 字段的类
    return MetaClass.forClass(propType, reflectorFactory);
  }

  // 寻找属性
  public String findProperty(String name) {
    // 在此方法中创建 StringBuilder 实例是因为之后要递归调用
    StringBuilder prop = buildProperty(name, new StringBuilder());
    return prop.length() > 0 ? prop.toString() : null;
  }

  // 将 name 中的 _ 替换为空字符串
  public String findProperty(String name, boolean useCamelCaseMapping) {
    if (useCamelCaseMapping) {
      name = name.replace("_", "");
    }
    return findProperty(name);
  }

  // 类中所有可读的属性
  public String[] getGetterNames() {
    return reflector.getGetablePropertyNames();
  }

  // 类中所有可写的属性
  public String[] getSetterNames() {
    return reflector.getSetablePropertyNames();
  }

  // 获取 name 对应的字段的类型
  public Class<?> getSetterType(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      MetaClass metaProp = metaClassForProperty(prop.getName());
      return metaProp.getSetterType(prop.getChildren());
    } else {
      return reflector.getSetterType(prop.getName());
    }
  }

  // 获取 name 对应的字段的类型
  public Class<?> getGetterType(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      MetaClass metaProp = metaClassForProperty(prop);
      return metaProp.getGetterType(prop.getChildren());
    }
    // issue #506. Resolve the type inside a Collection Object
    return getGetterType(prop);
  }

  // 生成属性的元类
  private MetaClass metaClassForProperty(PropertyTokenizer prop) {
    Class<?> propType = getGetterType(prop);
    return MetaClass.forClass(propType, reflectorFactory);
  }

  private Class<?> getGetterType(PropertyTokenizer prop) {
    Class<?> type = reflector.getGetterType(prop.getName());
    if (prop.getIndex() != null && Collection.class.isAssignableFrom(type)) {
      Type returnType = getGenericGetterType(prop.getName());
      if (returnType instanceof ParameterizedType) {
        Type[] actualTypeArguments = ((ParameterizedType) returnType).getActualTypeArguments();
        if (actualTypeArguments != null && actualTypeArguments.length == 1) {
          returnType = actualTypeArguments[0];
          if (returnType instanceof Class) {
            type = (Class<?>) returnType;
          } else if (returnType instanceof ParameterizedType) {
            type = (Class<?>) ((ParameterizedType) returnType).getRawType();
          }
        }
      }
    }
    return type;
  }

  private Type getGenericGetterType(String propertyName) {
    try {
      Invoker invoker = reflector.getGetInvoker(propertyName);
      if (invoker instanceof MethodInvoker) {
        // MethodInvoker 中的 Field 字段为 private 类型的
        Field _method = MethodInvoker.class.getDeclaredField("method");
        _method.setAccessible(true);
        Method method = (Method) _method.get(invoker);
        return TypeParameterResolver.resolveReturnType(method, reflector.getType());
      } else if (invoker instanceof GetFieldInvoker) {
        Field _field = GetFieldInvoker.class.getDeclaredField("field");
        _field.setAccessible(true);
        Field field = (Field) _field.get(invoker);
        return TypeParameterResolver.resolveFieldType(field, reflector.getType());
      }
    } catch (NoSuchFieldException | IllegalAccessException ignored) {
    }
    return null;
  }

  // 是否有 name 属性的 set 方法
  public boolean hasSetter(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      if (reflector.hasSetter(prop.getName())) {
        MetaClass metaProp = metaClassForProperty(prop.getName());
        return metaProp.hasSetter(prop.getChildren());
      } else {
        return false;
      }
    } else {
      return reflector.hasSetter(prop.getName());
    }
  }

  // 是否有 name 属性的 get 方法
  public boolean hasGetter(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      if (reflector.hasGetter(prop.getName())) {
        MetaClass metaProp = metaClassForProperty(prop);
        return metaProp.hasGetter(prop.getChildren());
      } else {
        return false;
      }
    } else {
      return reflector.hasGetter(prop.getName());
    }
  }

  // 获取 GetInvoker
  public Invoker getGetInvoker(String name) {
    return reflector.getGetInvoker(name);
  }

  // 获取 SetInvoker
  public Invoker getSetInvoker(String name) {
    return reflector.getSetInvoker(name);
  }

  // 递归生成属性名
  private StringBuilder buildProperty(String name, StringBuilder builder) {
    PropertyTokenizer prop = new PropertyTokenizer(name);  // 实例化 PropertyTokenizer 对象，name 是属性名
    if (prop.hasNext()) {
      String propertyName = reflector.findPropertyName(prop.getName());
      // 这里忽略了 PropertyTokenizer 中的 index
      if (propertyName != null) {
        builder.append(propertyName);
        builder.append(".");
        MetaClass metaProp = metaClassForProperty(propertyName);
        metaProp.buildProperty(prop.getChildren(), builder);
      }
    } else {
      // 如果 name 在 type 中被定义了，那么 propertyName 就不为 null
      String propertyName = reflector.findPropertyName(name);
      if (propertyName != null) {
        builder.append(propertyName);
      }
    }
    return builder;
  }

  // type 类是否定义了默认的构造函数
  public boolean hasDefaultConstructor() {
    return reflector.hasDefaultConstructor();
  }

}
