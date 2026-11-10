package PamController.settings.output.xml;

import java.awt.Color;
import java.io.File;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.net.URI;
import java.net.URL;
import java.util.TimeZone;

/**
 * Types which are written to XML as a single text value, rather than field by field,
 * because their fields are private implementation details (e.g. URI, TimeZone, Color)
 * and the class can be recreated from the text. Also creates objects when reading
 * XML, in the same way that Java serialisation does when a class has no default
 * constructor.
 *
 * @author Jamie Macaulay
 */
public class XMLValueTypes {

	private XMLValueTypes() {
	}

	/**
	 * @param clazz a class
	 * @return true if objects of this class are written as a single text value.
	 */
	public static boolean isValueType(Class<?> clazz) {
		if (clazz == null) {
			return false;
		}
		return URI.class.isAssignableFrom(clazz) || URL.class.isAssignableFrom(clazz)
				|| TimeZone.class.isAssignableFrom(clazz) || Color.class.isAssignableFrom(clazz)
				|| File.class.isAssignableFrom(clazz) || clazz == Class.class;
	}

	/**
	 * Write a value type as text.
	 * @param value object of a value type
	 * @return text which {@link #fromString(Class, String)} turns back into the object.
	 */
	public static String toString(Object value) {
		if (value instanceof TimeZone) {
			return ((TimeZone) value).getID();
		}
		if (value instanceof Class) {
			return ((Class<?>) value).getName();
		}
		if (value instanceof Color) {
			return String.format("#%08X", ((Color) value).getRGB());
		}
		return value.toString();
	}

	/**
	 * Read a value type from text.
	 * @param clazz class (may be a subclass of the value type, e.g. ZoneInfo)
	 * @param text text written by {@link #toString(Object)}
	 * @return object, or null if the text can't be read.
	 */
	public static Object fromString(Class<?> clazz, String text) {
		if (text == null) {
			return null;
		}
		try {
			if (URI.class.isAssignableFrom(clazz)) {
				return new URI(text);
			}
			if (URL.class.isAssignableFrom(clazz)) {
				return new URL(text);
			}
			if (TimeZone.class.isAssignableFrom(clazz)) {
				return TimeZone.getTimeZone(text);
			}
			if (Color.class.isAssignableFrom(clazz)) {
				String hex = text.startsWith("#") ? text.substring(1) : text;
				return new Color((int) Long.parseLong(hex, 16), true);
			}
			if (File.class.isAssignableFrom(clazz)) {
				return new File(text);
			}
			if (clazz == Class.class) {
				return Class.forName(text);
			}
		}
		catch (Exception e) {
			System.out.printf("XML: can't read %s \"%s\": %s\n", clazz.getName(), text, e.getMessage());
		}
		return null;
	}

	/**
	 * The fields of an object which are stored when it's serialised (i.e. not static or 
	 * transient) and which aren't constants, in the class and its superclasses. Only for
	 * PAMGuard classes, not Java's own, whose private fields can't be accessed. 
	 * @param clazz class
	 * @return list of fields, with the class's own fields first. A field hidden by one of 
	 * the same name in a subclass isn't included. 
	 */
	public static java.util.List<java.lang.reflect.Field> storedFields(Class<?> clazz) {
		java.util.List<java.lang.reflect.Field> fields = new java.util.ArrayList<>();
		java.util.Set<String> names = new java.util.HashSet<>();
		for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
			if (isJavaClass(c)) {
				break;
			}
			for (java.lang.reflect.Field f : c.getDeclaredFields()) {
				int mod = f.getModifiers();
				if ((mod & (java.lang.reflect.Modifier.STATIC | java.lang.reflect.Modifier.TRANSIENT | java.lang.reflect.Modifier.FINAL)) != 0) {
					continue;
				}
				if (f.isSynthetic() || names.contains(f.getName())) {
					continue;
				}
				names.add(f.getName());
				fields.add(f);
			}
		}
		return fields;
	}

	/**
	 * @return true for Java's own classes (java.*, javax.*, sun.*, jdk.*)
	 */
	public static boolean isJavaClass(Class<?> c) {
		String n = c.getName();
		return n.startsWith("java.") || n.startsWith("javax.") || n.startsWith("sun.") || n.startsWith("jdk.") || n.startsWith("com.sun.");
	}

	/**
	 * Create an object of a class. Uses the class's no argument constructor (even if
	 * it's private), otherwise, for Serializable classes, creates it the way
	 * that Java serialisation does, which is how settings are normally read from a psfx.
	 * @param clazz class to create
	 * @return new object or null if it can't be created.
	 */
	public static Object newInstance(Class<?> clazz) {
		if (clazz == null || clazz.isInterface() || java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())) {
			return null;
		}
		try {
			Constructor<?> constructor = clazz.getDeclaredConstructor();
			constructor.setAccessible(true);
			return constructor.newInstance();
		}
		catch (Exception | LinkageError e) {
		}
		if (Serializable.class.isAssignableFrom(clazz)) {
			try {
				Constructor<?> constructor = sun.reflect.ReflectionFactory.getReflectionFactory()
						.newConstructorForSerialization(clazz);
				if (constructor != null) {
					return constructor.newInstance();
				}
			}
			catch (Exception | LinkageError e) {
			}
		}
		return null;
	}

}
