package pamguard.cli;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import PamModel.parametermanager.FieldNotFoundException;
import PamModel.parametermanager.ManagedParameters;
import PamModel.parametermanager.PamParameterData;
import PamModel.parametermanager.PamParameterSet;

/**
 * Read and write individual fields in a PAMGuard settings object by a text path,
 * e.g. "threshold", "triggerParams.minLength" or "channelBearing[2]". Used by the
 * -set command line option, the setparam network command and -describe.
 * <p>
 * Fields are found through the object's PamParameterSet (ManagedParameters), the
 * same mechanism used to write XML settings, and failing that by reflection, so
 * this works for every settings class whether or not it implements ManagedParameters.
 */
public class SettingsPath {

	/**
	 * Maximum depth to which -describe goes into nested objects.
	 */
	public static final int MAXDEPTH = 5;

	/**
	 * Arrays longer than this are described by their length only.
	 */
	public static final int MAXARRAYLIST = 256;

	/**
	 * Get the value at a path.
	 * @param root settings object
	 * @param path path
	 * @return value
	 * @throws SettingsPathException if the path doesn't exist.
	 */
	public static Object get(Object root, String path) throws SettingsPathException {
		Object obj = root;
		for (String seg : splitPath(path)) {
			obj = getSegment(obj, seg, path);
		}
		return obj;
	}

	/**
	 * Set the value at a path, converting the text value to the field's type.
	 * @param root settings object (modified in place)
	 * @param path path
	 * @param textValue value as text
	 * @return the previous value
	 * @throws SettingsPathException if the path doesn't exist or the value can't be converted.
	 */
	public static Object set(Object root, String path, String textValue) throws SettingsPathException {
		List<String> segs = splitPath(path);
		if (segs.isEmpty()) {
			throw new SettingsPathException("Empty parameter name");
		}
		Object parent = root;
		for (int i = 0; i < segs.size()-1; i++) {
			parent = getSegment(parent, segs.get(i), path);
			if (parent == null) {
				throw new SettingsPathException(String.format("%s in %s is null, so can't be set", segs.get(i), path));
			}
		}
		String last = segs.get(segs.size()-1);
		String fieldName = last;
		Integer index = null;
		int br = last.indexOf('[');
		if (br >= 0) {
			fieldName = last.substring(0, br);
			index = parseIndex(last, path);
		}
		FieldAccess fa = findField(parent, fieldName, path);
		Object current = fa.get();
		try {
			if (index == null) {
				Object newVal = convert(textValue, fa.getType(), current);
				fa.set(newVal);
				return current;
			}
			if (current instanceof List) {
				List list = (List) current;
				if (index < 0 || index >= list.size()) {
					throw new SettingsPathException(String.format("Index %d is out of range for %s (size %d)", index, fieldName, list.size()));
				}
				Object old = list.get(index);
				if (old == null) {
					throw new SettingsPathException(String.format("%s[%d] is null, so its type isn't known", fieldName, index));
				}
				list.set(index, convert(textValue, old.getClass(), old));
				return old;
			}
			if (current == null || !current.getClass().isArray()) {
				throw new SettingsPathException(String.format("%s is not an array or list in %s", fieldName, path));
			}
			if (index < 0 || index >= Array.getLength(current)) {
				throw new SettingsPathException(String.format("Index %d is out of range for %s (length %d)", index, fieldName, Array.getLength(current)));
			}
			Object old = Array.get(current, index);
			Array.set(current, index, convert(textValue, current.getClass().getComponentType(), old));
			return old;
		}
		catch (IllegalArgumentException e) {
			throw new SettingsPathException(String.format("Can't set %s to \"%s\": %s", path, textValue, e.getMessage()));
		}
	}

	/**
	 * Describe every parameter in a settings object.
	 * @param root settings object
	 * @return list of parameters, each a map with name, type, value and, where
	 * available, shortName, description and units.
	 */
	public static List<Map<String, Object>> describe(Object root) {
		List<Map<String, Object>> params = new ArrayList<>();
		if (root != null) {
			describe(root, "", params, new IdentityHashMap<>(), 0);
		}
		return params;
	}

	private static void describe(Object obj, String prefix, List<Map<String, Object>> params,
			IdentityHashMap<Object, Boolean> seen, int depth) {
		if (seen.containsKey(obj) || depth > MAXDEPTH) {
			return;
		}
		seen.put(obj, true);
		PamParameterSet ps = getParameterSet(obj);
		if (ps == null) {
			return;
		}
		for (PamParameterData pd : ps.getParameterCollection()) {
			String name = prefix + pd.getFieldName();
			Object value;
			try {
				value = pd.getData();
			}
			catch (Exception e) {
				continue;
			}
			Class type = pd.getDataClass();
			if (value != null && !isSimple(type) && !type.isArray() && isSimple(value.getClass())) {
				type = value.getClass();
			}
			if (value != null && !isSimple(value.getClass()) && !value.getClass().isArray()
					&& !(value instanceof Collection) && !(value instanceof Map)) {
				// nested object: list its fields instead.
				Map<String, Object> p = new LinkedHashMap<>();
				p.put("name", name);
				p.put("type", value.getClass().getName());
				p.put("object", true);
				addInfo(p, pd);
				params.add(p);
				describe(value, name + ".", params, seen, depth+1);
				continue;
			}
			Map<String, Object> p = new LinkedHashMap<>();
			p.put("name", name);
			p.put("type", typeName(type, value));
			p.put("value", jsonValue(value));
			if (type != null && type.isEnum()) {
				List<String> choices = new ArrayList<>();
				for (Object c : type.getEnumConstants()) {
					choices.add(((Enum) c).name());
				}
				p.put("choices", choices);
			}
			p.put("settable", isSettable(type));
			addInfo(p, pd);
			params.add(p);
		}
	}

	private static void addInfo(Map<String, Object> p, PamParameterData pd) {
		if (pd.getShortName() != null && !pd.getShortName().equals(pd.getFieldName())) {
			p.put("shortName", pd.getShortName());
		}
		if (pd.getToolTip() != null && pd.getToolTip().length() > 0) {
			p.put("description", pd.getToolTip());
		}
		if (pd.getPostTitle() != null && pd.getPostTitle().length() > 0) {
			p.put("units", pd.getPostTitle());
		}
	}

	/**
	 * Get a value in a form that can be written as JSON.
	 * @param value value
	 * @return value, list for arrays, string for anything else.
	 */
	public static Object jsonValue(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof Number || value instanceof Boolean || value instanceof String) {
			if (value instanceof Double && (((Double) value).isNaN() || ((Double) value).isInfinite())) {
				return value.toString();
			}
			if (value instanceof Float && (((Float) value).isNaN() || ((Float) value).isInfinite())) {
				return value.toString();
			}
			return value;
		}
		if (value instanceof Character || value instanceof File) {
			return value.toString();
		}
		if (value instanceof Enum) {
			return ((Enum) value).name();
		}
		if (value.getClass().isArray()) {
			int n = Array.getLength(value);
			if (n > MAXARRAYLIST) {
				Map<String, Object> m = new LinkedHashMap<>();
				m.put("length", n);
				return m;
			}
			List<Object> list = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				Object el = Array.get(value, i);
				list.add(el == null || isSimple(el.getClass()) || el.getClass().isArray() ? jsonValue(el) : el.getClass().getName());
			}
			return list;
		}
		if (value instanceof Collection) {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("size", ((Collection) value).size());
			return m;
		}
		return value.toString();
	}

	private static String typeName(Class type, Object value) {
		if (type == null) {
			return value == null ? "unknown" : value.getClass().getName();
		}
		if (type.isArray()) {
			return typeName(type.getComponentType(), null) + "[]";
		}
		if (type.isPrimitive() || type == String.class) {
			return type.getSimpleName();
		}
		return type.getName();
	}

	private static boolean isSimple(Class type) {
		if (type == null) {
			return false;
		}
		return type.isPrimitive() || type == String.class || Number.class.isAssignableFrom(type)
				|| type == Boolean.class || type == Character.class || type.isEnum() || type == File.class;
	}

	private static boolean isSettable(Class type) {
		if (type == null) {
			return false;
		}
		if (type.isArray()) {
			return isSimple(type.getComponentType());
		}
		return isSimple(type);
	}

	/**
	 * Get the parameter set for an object.
	 * @param obj object
	 * @return parameter set, from ManagedParameters if implemented, otherwise automatically generated.
	 */
	public static PamParameterSet getParameterSet(Object obj) {
		if (obj == null) {
			return null;
		}
		try {
			if (obj instanceof ManagedParameters) {
				PamParameterSet ps = ((ManagedParameters) obj).getParameterSet();
				if (ps != null) {
					return ps;
				}
			}
			return PamParameterSet.autoGenerate(obj, Modifier.STATIC | Modifier.FINAL);
		}
		catch (Exception e) {
			return null;
		}
	}

	private static Object getSegment(Object obj, String seg, String path) throws SettingsPathException {
		if (obj == null) {
			throw new SettingsPathException("Can't find " + seg + " in " + path + " since its parent is null");
		}
		String fieldName = seg;
		Integer index = null;
		int br = seg.indexOf('[');
		if (br >= 0) {
			fieldName = seg.substring(0, br);
			index = parseIndex(seg, path);
		}
		Object val = findField(obj, fieldName, path).get();
		if (index != null && val instanceof List) {
			List list = (List) val;
			if (index < 0 || index >= list.size()) {
				throw new SettingsPathException(String.format("Index %d is out of range for %s (size %d)", index, fieldName, list.size()));
			}
			return list.get(index);
		}
		if (index != null) {
			if (val == null || !val.getClass().isArray()) {
				throw new SettingsPathException(String.format("%s is not an array or list in %s", fieldName, path));
			}
			if (index < 0 || index >= Array.getLength(val)) {
				throw new SettingsPathException(String.format("Index %d is out of range for %s (length %d)", index, fieldName, Array.getLength(val)));
			}
			val = Array.get(val, index);
		}
		return val;
	}

	private static Integer parseIndex(String seg, String path) throws SettingsPathException {
		int br = seg.indexOf('[');
		int end = seg.indexOf(']', br);
		if (end < 0) {
			throw new SettingsPathException("Missing ] in " + path);
		}
		try {
			return Integer.parseInt(seg.substring(br+1, end).trim());
		}
		catch (NumberFormatException e) {
			throw new SettingsPathException("Bad array index in " + path);
		}
	}

	private static List<String> splitPath(String path) {
		List<String> segs = new ArrayList<>();
		for (String s : path.split("\\.")) {
			s = s.trim();
			if (s.length() > 0) {
				segs.add(s);
			}
		}
		return segs;
	}

	/**
	 * Access to one field of an object.
	 */
	private interface FieldAccess {
		Object get() throws SettingsPathException;
		void set(Object value) throws SettingsPathException;
		Class getType();
	}

	private static FieldAccess findField(Object obj, String fieldName, String path) throws SettingsPathException {
		PamParameterSet ps = getParameterSet(obj);
		if (ps != null) {
			PamParameterData pd = null;
			try {
				pd = ps.findParameterData(fieldName);
			}
			catch (FieldNotFoundException e) {
				pd = null;
			}
			if (pd != null) {
				final PamParameterData fpd = pd;
				return new FieldAccess() {
					@Override
					public Object get() throws SettingsPathException {
						try {
							return fpd.getData();
						} catch (Exception e) {
							throw new SettingsPathException("Can't read " + fieldName + ": " + e.getMessage());
						}
					}
					@Override
					public void set(Object value) throws SettingsPathException {
						try {
							if (fpd.getField() != null && !Modifier.isFinal(fpd.getField().getModifiers())) {
								// set the field directly - some setData implementations only work from dialogs.
								fpd.getField().setAccessible(true);
								fpd.getField().set(fpd.getParentObject(), value);
							}
							else if (!fpd.setData(value)) {
								throw new SettingsPathException("Unable to set " + fieldName);
							}
						} catch (IllegalAccessException | IllegalArgumentException e) {
							throw new SettingsPathException("Can't set " + fieldName + ": " + e.getMessage());
						}
					}
					@Override
					public Class getType() {
						return fpd.getField() != null ? fpd.getField().getType() : fpd.getDataClass();
					}
				};
			}
		}
		// fall back on reflection for fields not in the parameter set.
		Class cls = obj.getClass();
		while (cls != null) {
			try {
				Field f = cls.getDeclaredField(fieldName);
				if (Modifier.isStatic(f.getModifiers())) {
					break;
				}
				f.setAccessible(true);
				return new FieldAccess() {
					@Override
					public Object get() throws SettingsPathException {
						try {
							return f.get(obj);
						} catch (Exception e) {
							throw new SettingsPathException("Can't read " + fieldName + ": " + e.getMessage());
						}
					}
					@Override
					public void set(Object value) throws SettingsPathException {
						if (Modifier.isFinal(f.getModifiers())) {
							throw new SettingsPathException(fieldName + " is final and can't be changed");
						}
						try {
							f.set(obj, value);
						} catch (Exception e) {
							throw new SettingsPathException("Can't set " + fieldName + ": " + e.getMessage());
						}
					}
					@Override
					public Class getType() {
						return f.getType();
					}
				};
			} catch (NoSuchFieldException e) {
				cls = cls.getSuperclass();
			} catch (RuntimeException e) {
				break;
			}
		}
		List<String> names = new ArrayList<>();
		if (ps != null) {
			for (PamParameterData pd : ps.getParameterCollection()) {
				names.add(pd.getFieldName());
			}
		}
		throw new SettingsPathException(String.format("No parameter \"%s\" in %s (%s). Parameters are: %s",
				fieldName, path, obj.getClass().getSimpleName(), String.join(", ", names)));
	}

	/**
	 * Convert a text value to a given type.
	 * @param text text
	 * @param type type wanted
	 * @param current current value, used if type is Object.
	 * @return converted value
	 * @throws SettingsPathException if the text can't be converted.
	 */
	public static Object convert(String text, Class type, Object current) throws SettingsPathException {
		String t = text == null ? "" : text.trim();
		if ((type == null || type == Object.class) && current != null) {
			type = current.getClass();
		}
		if (type == null) {
			throw new SettingsPathException("Unknown parameter type");
		}
		if (!type.isPrimitive() && t.equalsIgnoreCase("null")) {
			return null;
		}
		try {
			if (type == String.class) {
				return text;
			}
			if (type == int.class || type == Integer.class) {
				return Integer.decode(t);
			}
			if (type == long.class || type == Long.class) {
				return Long.decode(t);
			}
			if (type == short.class || type == Short.class) {
				return Short.decode(t);
			}
			if (type == byte.class || type == Byte.class) {
				return Byte.decode(t);
			}
			if (type == double.class || type == Double.class) {
				return Double.parseDouble(t);
			}
			if (type == float.class || type == Float.class) {
				return Float.parseFloat(t);
			}
			if (type == boolean.class || type == Boolean.class) {
				if (t.equalsIgnoreCase("true") || t.equals("1") || t.equalsIgnoreCase("yes") || t.equalsIgnoreCase("on")) {
					return true;
				}
				if (t.equalsIgnoreCase("false") || t.equals("0") || t.equalsIgnoreCase("no") || t.equalsIgnoreCase("off")) {
					return false;
				}
				throw new SettingsPathException("\"" + text + "\" is not true or false");
			}
			if (type == char.class || type == Character.class) {
				if (t.length() != 1) {
					throw new SettingsPathException("\"" + text + "\" is not a single character");
				}
				return t.charAt(0);
			}
			if (type == File.class) {
				return new File(t);
			}
			if (type.isEnum()) {
				for (Object c : type.getEnumConstants()) {
					if (((Enum) c).name().equalsIgnoreCase(t)) {
						return c;
					}
				}
				List<String> choices = new ArrayList<>();
				for (Object c : type.getEnumConstants()) {
					choices.add(((Enum) c).name());
				}
				throw new SettingsPathException(String.format("\"%s\" is not one of %s", text, String.join(", ", choices)));
			}
			if (type.isArray() && isSimple(type.getComponentType())) {
				String a = t;
				if (a.startsWith("[") && a.endsWith("]")) {
					a = a.substring(1, a.length()-1);
				}
				String[] bits = a.trim().length() == 0 ? new String[0] : a.split(",");
				Object arr = Array.newInstance(type.getComponentType(), bits.length);
				for (int i = 0; i < bits.length; i++) {
					Array.set(arr, i, convert(bits[i].trim(), type.getComponentType(), null));
				}
				return arr;
			}
		}
		catch (NumberFormatException e) {
			throw new SettingsPathException(String.format("\"%s\" is not a valid %s", text, type.getSimpleName()));
		}
		throw new SettingsPathException(String.format("Parameters of type %s can't be set from text; use an XML settings file", type.getName()));
	}

}
