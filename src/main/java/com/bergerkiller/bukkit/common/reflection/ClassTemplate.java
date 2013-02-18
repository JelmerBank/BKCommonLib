package com.bergerkiller.bukkit.common.reflection;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.bergerkiller.bukkit.common.reflection.SafeField;
import com.bergerkiller.bukkit.common.utils.CommonUtil;

/**
 * Uses reflection to transfer/copy all the fields of a class
 */
public class ClassTemplate<T> {
	private Class<T> type;
	private List<SafeField<?>> fields;

	/**
	 * Initializes a new ClassTemplate not pointing to any Class<br>
	 * Call setClass to define the Class to use
	 */
	public ClassTemplate() {
	}

	/**
	 * Initializes a new ClassTemplate pointing to the Class specified
	 * 
	 * @param type of Class
	 */
	public ClassTemplate(Class<T> type) {
		setClass(type);
	}

	/**
	 * Initializes this Class Template to represent the Class and fields of the type specified
	 * 
	 * @param type to set the Class to
	 */
	protected void setClass(Class<T> type) {
		this.type = type;
		this.fields = new ArrayList<SafeField<?>>();
		if (this.type != null) {
			try {
				this.fillFields(type);
			} catch (Throwable t) {
				t.printStackTrace();
			}
		}
	}

	private void fillFields(Class<?> clazz) {
		if (clazz == null) {
			return;
		}
		Field[] declared = clazz.getDeclaredFields();
		ArrayList<SafeField<?>> newFields = new ArrayList<SafeField<?>>(declared.length);
		for (Field field : declared) {
			if (!Modifier.isStatic(field.getModifiers())) {
				newFields.add(new SafeField<Object>(field));
			}
		}
		this.fields.addAll(0, newFields);
		this.fillFields(clazz.getSuperclass());
	}

	/**
	 * Gets the Class type represented by this Template
	 * 
	 * @return Class type
	 */
	public Class<T> getType() {
		return this.type;
	}

	/**
	 * Gets all the fields declared in this Class
	 * 
	 * @return Declared fields
	 */
	public List<SafeField<?>> getFields() {
		return Collections.unmodifiableList(fields);
	}

	/**
	 * Gets a new instance of this Class, using the empty constructor
	 * 
	 * @return A new instance, or null if an error occurred/empty constructor is not available
	 */
	public T newInstance() {
		try {
			return (T) this.type.newInstance();
		} catch (Throwable t) {
			t.printStackTrace();
		}
		return null;
	}

	/**
	 * Checks whether a given object is an instance of the class represented by this Template
	 * 
	 * @param object to check
	 * @return True if the object is an instance, False if not
	 */
	public boolean isInstance(Object object) {
		return this.type.isInstance(object);
	}

	/**
	 * Checks whether the specified clazz object can be assigned to the type represented
	 * by this ClassTemplate. This template taken as A, and clazz as B, this is equivalent to:<br>
	 * <b>B instanceof A</b>
	 * 
	 * @param clazz to check
	 * @return True if the clazz can be assigned to this template type, False if not
	 */
	public boolean isAssignableFrom(Class<?> clazz) {
		return this.type.isAssignableFrom(clazz);
	}

	/**
	 * Checks whether the object class equals the class represented by this Template
	 * 
	 * @param object to check
	 * @return True if the object is a direct instance, False if not
	 */
	public boolean isType(Object object) {
		return object != null && isType(object.getClass());
	}

	/**
	 * Checks whether a given class instance equals the class represented by this Template
	 * 
	 * @param clazz to check
	 * @return True if the clazz is not null and equals the class of this template
	 */
	public boolean isType(Class<?> clazz) {
		return clazz != null && this.type.equals(clazz);
	}

	/**
	 * Transfers all the fields from one class instance to the other
	 * 
	 * @param from instance
	 * @param to instance
	 */
	public void transfer(Object from, Object to) {
		for (FieldAccessor<?> field : this.fields) {
			field.transfer(from, to);
		}
	}

	/**
	 * Checks whether this Class Template is properly initialized and can be used
	 * 
	 * @return True if this template is valid for use, False if not
	 */
	public boolean isValid() {
		return this.type != null;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder(500);
		builder.append("Class path: ").append(this.getType().getName()).append('\n');
		builder.append("Fields (").append(this.fields.size()).append("):");
		for (FieldAccessor<?> field : this.fields) {
			builder.append("\n    ").append(field.toString());
		}
		return builder.toString();
	}

	/**
	 * Attempts to find the constructor for this Class using the parameter types
	 * 
	 * @param parameterTypes of the constructor
	 * @return Constructor
	 */
	public SafeConstructor<T> getConstructor(Class<?>... parameterTypes) {
		return new SafeConstructor<T>(this.getType(), parameterTypes);
	}

	/**
	 * Attempts to find the field by name
	 * 
	 * @param name of the field
	 * @return field
	 */
	public <K> SafeField<K> getField(String name) {
		return new SafeField<K>(this.getType(), name);
	}

	/**
	 * Attempts to find the method by name
	 * 
	 * @param name of the method
	 * @param arguments of the method
	 * @return method
	 */
	public <K> SafeMethod<K> getMethod(String name, Class<?>... parameterTypes) {
		return new SafeMethod<K>(this.getType(), name, parameterTypes);
	}

	/**
	 * Gets a statically declared field value
	 * 
	 * @param name of the static field
	 * @return Static field value
	 */
	public <K> K getStaticFieldValue(String name) {
		return SafeField.get(getType(), name);
	}

	/**
	 * Sets a statically declared field value
	 * 
	 * @param name of the static field
	 * @param value to set the static field to
	 */
	public <K> void setStaticFieldValue(String name, K value) {
		SafeField.setStatic(getType(), name, value);
	}

	/**
	 * Attempts to create a new template for the class at the path specified
	 * 
	 * @param path to the class
	 * @return a new template, or null if the template could not be made
	 */
	public static ClassTemplate<?> create(String path) {
		return create(CommonUtil.getClass(path));
	}

	/**
	 * Attempts to create a new template for the class of the class instance specified<br>
	 * If something fails, an empty instance is returned
	 * 
	 * @param value of the class to create the template for
	 * @return a new template
	 */
	@SuppressWarnings("unchecked")
	public static <T> ClassTemplate<T> create(T value) {
		return create((Class<T>) value.getClass());
	}

	/**
	 * Attempts to create a new template for the class specified<br>
	 * If something fails, an empty instance is returned
	 * 
	 * @param clazz to create
	 * @return a new template
	 */
	public static <T> ClassTemplate<T> create(Class<T> clazz) {
		return new ClassTemplate<T>(clazz);
	}
}