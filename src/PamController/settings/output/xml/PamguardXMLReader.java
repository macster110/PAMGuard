package PamController.settings.output.xml;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Array;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.ClassUtils;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import PamModel.parametermanager.ManagedParameters;
import PamModel.parametermanager.PamParameterData;
import PamModel.parametermanager.PamParameterSet;
import PamUtils.XMLUtils;

public class PamguardXMLReader {

//	private static String tstFile = "C:\\PamguardTest\\NOAA\\XMLTest1.xml";
//		private static String tstFile = "C:\\PamguardTest\\NOAA\\Drift_10_Humpbacks._19700101_000000.xml";


//	public static void main(String[] args) {
//		// tester to try unpacking a single file and creating an object out of it. 
//		PamguardXMLReader reader = new PamguardXMLReader(tstFile);
//		//		reader.unpackFile(tstFile);
//	}

	private ArrayList<ModuleNode> moduleNodes;

	public PamguardXMLReader(String fileName) {
		unpackFile(fileName);
	}
	
	public PamguardXMLReader(Document doc) {
		NodeList childNodes = doc.getChildNodes();
		moduleNodes = new ArrayList<>();
		findModuleNodes(childNodes, moduleNodes);
	}

	private void unpackFile(String fileName) {

		Document doc = XMLUtils.createDocument(fileName);
		NodeList childNodes = doc.getChildNodes();
		moduleNodes = new ArrayList<>();
		findModuleNodes(childNodes, moduleNodes);
//		System.out.printf("Document contains %d module nodes\n", moduleNodes.size());
//		for (int i = 0; i < moduleNodes.size(); i++) {
//			System.out.println(moduleNodes.get(i));
//			unpackModuleNode(moduleNodes.get(i));
//		}

	}

	/**
	 * Unpack a module node, creating a class and settings
	 * it's data. 
	 */
	public Object[] unpackModuleNode(ModuleNode moduleNode) {
		if (moduleNode == null) {
			return null;
		}
		// first need to find the configuration node, then unpack that. 
		ArrayList<Node> settingsNodes = findNodesByType(moduleNode.getNode(), "SETTINGS", null);
		Object[] settings = new Object[settingsNodes.size()];
		for (int i = 0; i < settingsNodes.size(); i++) {
			Node node = settingsNodes.get(i);
			settings[i] = unpackSettingsNode(node);
		}
		return settings;
	}
	
	/**
	 * Get the SETTINGS nodes of a module node. There is one for each settings object. 
	 * @param moduleNode module node
	 * @return list of settings nodes. 
	 */
	public ArrayList<Node> getSettingsNodes(ModuleNode moduleNode) {
		return findNodesByType(moduleNode.getNode(), "SETTINGS", null);
	}
	
	/**
	 * Get the Java class name of the object in a SETTINGS node.  
	 * @param settingsNode settings node
	 * @return class name
	 */
	public String getSettingsClassName(Node settingsNode) {
		return getAttrString(settingsNode.getAttributes(), "Class");
	}
	
	/**
	 * Unpack a SETTINGS node into an existing settings object, changing only 
	 * the fields which are in the XML. This is what's needed to apply a partial set 
	 * of settings, e.g. from the -settings command line option.  
	 * @param settingsNode settings node
	 * @param dataObj existing settings object, which is modified. 
	 * @return number of the object's fields which weren't in the XML (or couldn't be read)
	 */
	public int unpackSettingsNodeInto(Node settingsNode, Object dataObj) {
		return unpackSettingsNode(settingsNode, dataObj);
	}
	
	public Object unpackSettingsNode(Node settingsNode) {
		String className = getAttrString(settingsNode.getAttributes(), "Class");
		Class<?> moduleClass = null;
		int errors = 0;
		Object newObject = null;
		try {
			newObject = createObject(className);
			errors = unpackSettingsNode(settingsNode, newObject);
		} catch (SecurityException e) {
			e.printStackTrace();
		} 

		return newObject;
	}
	
	/**
	 * Try to create an object from a class name assuming no parameters. 
	 * @param className
	 * @return
	 */
	Object createObject(String className) {
		Class<?> cls = loadClass(className);
		if (cls == null) {
			System.out.println("PamguardXMLReader: unknown class " + className);
			return null;
		}
		return XMLValueTypes.newInstance(cls);
	}

	/**
	 * Unpack settings in the XML node into the Java object. 
	 * @param settingsNode XML SETTINGS node
	 * @param dataObj Java parameter object. 
	 * @return true if success, false otherwise. 
	 */
	private int unpackSettingsNode(Node settingsNode, Object dataObj) {
		PamParameterSet parameterSet;
		if (dataObj instanceof ManagedParameters) {
			parameterSet = ((ManagedParameters) dataObj).getParameterSet();
		}
		else {
			int exclusions = Modifier.STATIC | Modifier.FINAL;
			parameterSet = PamParameterSet.autoGenerate(dataObj, exclusions);
		}
		java.util.Collection<PamParameterData> params = parameterSet == null ? new ArrayList<>() : parameterSet.getParameterCollection();
		int nStack = readStack.size();
		readStack.add(dataObj);
		try {
			return unpackFields(settingsNode, dataObj, params);
		}
		finally {
			while (readStack.size() > nStack) {
				readStack.remove(readStack.size()-1);
			}
		}
	}

	/**
	 * Objects currently being read, to resolve references to them. 
	 */
	private ArrayList<Object> readStack = new ArrayList<>();

	private int unpackFields(Node settingsNode, Object dataObj, java.util.Collection<PamParameterData> params) {
		int missing = 0;
		java.util.Set<String> done = new java.util.HashSet<>();
		// objects described by the parameter set, which may not be dataObj itself. 
		int nStack = readStack.size();
		for (PamParameterData pamParam : params) {
			Object parent = pamParam.getParentObject();
			if (parent != null && readStack.stream().noneMatch(o -> o == parent)) {
				readStack.add(parent);
			}
		}
		for (PamParameterData pamParam:params) {
			done.add(pamParam.getFieldName());
			String fieldName = pamParam.getFieldName();
			//			fieldNode = findNodesByType(settingsNode, fieldName, null);
			Node fieldNode = findNode(settingsNode, fieldName);
			if (fieldNode == null) {
				missing ++;
//				System.out.printf("Unable to find node for field %s\n", fieldName);
				continue;
			}
			Object existing = null;
			try {
				existing = pamParam.getData();
			}
			catch (Exception e) {
			}
			Class<?> declared = null;
			java.lang.reflect.Type generic = null;
			try {
				declared = pamParam.getDataClass();
				generic = pamParam.getField().getGenericType();
			}
			catch (Exception e) {
			}
			Object obj = readValue(fieldNode, declared, generic, existing);
			if (obj == null) {
				/*
				 * The value couldn't be read. Leave the existing value alone rather than 
				 * setting it to null, so that unpacking into an existing settings object 
				 * only changes what's in the XML.  
				 */
				missing++;
				continue;
			}
			try {
				pamParam.setData(obj);
			} catch (IllegalArgumentException | IllegalAccessException e) {
				e.printStackTrace();
			}
		}
		while (readStack.size() > nStack) {
			readStack.remove(readStack.size()-1);
		}
		/*
		 * Then the fields which aren't in the parameter set, which PamguardXMLWriter also 
		 * writes, so that everything that's in a psfx can be read from XML. 
		 */
		for (java.lang.reflect.Field field : XMLValueTypes.storedFields(dataObj.getClass())) {
			if (done.contains(field.getName())) {
				continue;
			}
			done.add(field.getName());
			Node fieldNode = findNode(settingsNode, field.getName());
			if (fieldNode == null) {
				continue;
			}
			try {
				field.setAccessible(true);
				Object obj = readValue(fieldNode, field.getType(), field.getGenericType(), field.get(dataObj));
				if (obj != null) {
					field.set(dataObj, obj);
				}
				else {
					missing++;
				}
			}
			catch (Exception | LinkageError e) {
				System.out.printf("PamguardXMLReader: can't set %s.%s: %s\n", dataObj.getClass().getName(), field.getName(), e.getMessage());
				missing++;
			}
		}
		return missing;
	}
	/**
	 * Read a value from an XML element written by PamguardXMLWriter.  
	 * @param node element holding the value
	 * @param declared the declared type of the field or item (may be null) 
	 * @param generic the generic type of the field, used for list and map items (may be null) 
	 * @param existing the current value, which objects are read into if it's the right class (may be null)
	 * @return value or null if it can't be read. 
	 */
	private Object readValue(Node node, Class<?> declared, Type generic, Object existing) {
		Class<?> cls = loadClass(getAttrString(node.getAttributes(), "Class"));
		if (getAttrString(node.getAttributes(), "Reference") != null) {
			/*
			 * a reference to an object further up the hierarchy (e.g. a Streamer's PamArray), 
			 * which isn't written again, so use the nearest object of that class being read. 
			 */
			for (int i = readStack.size()-1; i >= 0; i--) {
				if (readStack.get(i).getClass() == cls) {
					return readStack.get(i);
				}
			}
			return existing;
		}
		if (cls == null || (declared != null && !declared.isPrimitive() && !ClassUtils.isAssignable(cls, declared, true))) {
			cls = declared;
		}
		if (cls == null) {
			return null;
		}
		String value = getAttrString(node.getAttributes(), "Value");
		if (cls.isPrimitive() || cls == String.class || ClassUtils.isPrimitiveWrapper(cls)) {
			if (value == null) {
				return null;
			}
			if (cls.isPrimitive()) {
				return readPrimitive(cls, value);
			}
			return convertText(value, cls);
		}
		if (cls.isEnum()) {
			return readEnum(cls, value);
		}
		if (XMLValueTypes.isValueType(cls)) {
			return XMLValueTypes.fromString(cls, value);
		}
		if (cls.isArray()) {
			return readArrayValue(node, cls, existing);
		}
		if (List.class.isAssignableFrom(cls)) {
			return readListValue(node, cls, generic, existing);
		}
		if (Map.class.isAssignableFrom(cls)) {
			return readMapValue(node, cls, generic, existing);
		}
		if (value != null) {
			// some other simple type, e.g. a primitive written into an object list
			return convertText(value, cls);
		}
		// an object, read field by field
		Object obj = existing;
		if (obj == null || obj.getClass() != cls) {
			obj = XMLValueTypes.newInstance(cls);
		}
		if (obj == null) {
			System.out.println("PamguardXMLReader: can't create an object of class " + cls.getName());
			return null;
		}
		unpackSettingsNode(node, obj);
		return obj;
	}

	private Class<?> loadClass(String className) {
		if (className == null) {
			return null;
		}
		try {
			return ClassUtils.getClass(className);
		} catch (ClassNotFoundException | LinkageError e) {
			return null;
		}
	}

	private Object convertText(String text, Class<?> cls) {
		if (text == null || (text.equals("null") && cls != String.class)) {
			return null;
		}
		if (cls == String.class) {
			return text;
		}
		if (cls == Object.class) {
			return text;
		}
		if (cls.isEnum()) {
			return readEnum(cls, text);
		}
		if (XMLValueTypes.isValueType(cls)) {
			return XMLValueTypes.fromString(cls, text);
		}
		try {
			return pamguard.cli.SettingsPath.convert(text, cls, null);
		} catch (pamguard.cli.SettingsPathException e) {
			System.out.println("PamguardXMLReader: " + e.getMessage());
			return null;
		}
	}

	/**
	 * The element nodes inside a node (ignoring text). 
	 */
	private ArrayList<Node> childElements(Node node) {
		ArrayList<Node> list = new ArrayList<>();
		NodeList children = node.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			if (children.item(i).getNodeType() == Node.ELEMENT_NODE) {
				list.add(children.item(i));
			}
		}
		return list;
	}

	/**
	 * Read the items of an array or list, either from a comma separated Value or 
	 * from child elements with Index attributes. 
	 * @return items, or null if they can't be read. 
	 */
	private ArrayList<Object> readItems(Node node, Class<?> itemClass, Object[] existing) {
		String value = getAttrString(node.getAttributes(), "Value");
		ArrayList<Object> items = new ArrayList<>();
		if (value != null) {
			if (value.length() == 0) {
				return items;
			}
			for (String bit : value.split(",", -1)) {
				Object o = convertText(bit, itemClass);
				if (o == null && !bit.equals("null")) {
					return null;
				}
				items.add(o);
			}
			return items;
		}
		String lenStr = getAttrString(node.getAttributes(), "Length");
		int len = 0;
		if (lenStr != null) {
			try {
				len = Integer.valueOf(lenStr);
			}
			catch (NumberFormatException e) {
			}
		}
		ArrayList<Node> children = childElements(node);
		for (int i = 0; i < len; i++) {
			items.add(null);
		}
		int next = 0;
		for (Node child : children) {
			int index = next;
			String indStr = getAttrString(child.getAttributes(), "Index");
			if (indStr != null) {
				try {
					index = Integer.valueOf(indStr);
				}
				catch (NumberFormatException e) {
				}
			}
			next = index+1;
			while (items.size() <= index) {
				items.add(null);
			}
			Object old = (existing != null && index < existing.length) ? existing[index] : null;
			items.set(index, readValue(child, itemClass, null, old));
		}
		return items;
	}

	private Object readArrayValue(Node node, Class<?> arrayClass, Object existing) {
		Class<?> component = arrayClass.getComponentType();
		Object[] old = null;
		if (existing != null && existing.getClass().isArray() && !existing.getClass().getComponentType().isPrimitive()) {
			old = (Object[]) existing;
		}
		Class<?> itemClass = loadClass(getAttrString(node.getAttributes(), "ElementClass"));
		if (itemClass == null || !component.isAssignableFrom(itemClass)) {
			itemClass = component;
		}
		ArrayList<Object> items = readItems(node, itemClass, old);
		if (items == null) {
			return null;
		}
		Object array = Array.newInstance(component, items.size());
		for (int i = 0; i < items.size(); i++) {
			Object o = items.get(i);
			if (o == null && component.isPrimitive()) {
				return null;
			}
			try {
				Array.set(array, i, o);
			}
			catch (IllegalArgumentException e) {
				System.out.printf("PamguardXMLReader: can't put %s in %s array\n", o, component.getName());
				return null;
			}
		}
		return array;
	}

	/**
	 * The class of a generic type argument, e.g. Integer for List&lt;Integer&gt;
	 */
	private Class<?> typeArgument(Type generic, int i) {
		if (generic instanceof ParameterizedType) {
			Type[] args = ((ParameterizedType) generic).getActualTypeArguments();
			if (args.length > i) {
				Type t = args[i];
				if (t instanceof ParameterizedType) {
					t = ((ParameterizedType) t).getRawType();
				}
				if (t instanceof Class) {
					return (Class<?>) t;
				}
			}
		}
		return null;
	}

	private Object readListValue(Node node, Class<?> listClass, Type generic, Object existing) {
		Class<?> itemClass = loadClass(getAttrString(node.getAttributes(), "ElementClass"));
		if (itemClass == null) {
			itemClass = typeArgument(generic, 0);
		}
		Object[] old = null;
		if (existing instanceof List) {
			old = ((List) existing).toArray();
			if (itemClass == null) {
				for (Object o : old) {
					if (o != null) {
						itemClass = o.getClass();
						break;
					}
				}
			}
		}
		if (itemClass == null) {
			itemClass = Object.class;
		}
		ArrayList<Object> items = readItems(node, itemClass, old);
		if (items == null) {
			return null;
		}
		Object list = XMLValueTypes.newInstance(listClass);
		if (!(list instanceof List) || listClass.getName().startsWith("java.util.Collections") 
				|| listClass.getName().startsWith("java.util.Arrays") || listClass.getName().startsWith("java.util.ImmutableCollections")) {
			list = new ArrayList<Object>();
		}
		((List) list).clear();
		((List) list).addAll(items);
		return list;
	}

	private Object readMapValue(Node node, Class<?> mapClass, Type generic, Object existing) {
		Map map = null;
		Object newMap = XMLValueTypes.newInstance(mapClass);
		if (newMap instanceof Map) {
			map = (Map) newMap;
		}
		else {
			map = new java.util.HashMap();
		}
		map.clear();
		Class<?> keyType = typeArgument(generic, 0);
		Class<?> valueType = typeArgument(generic, 1);
		Map oldMap = existing instanceof Map ? (Map) existing : null;
		for (Node entry : childElements(node)) {
			String keyStr = getAttrString(entry.getAttributes(), "Key");
			Class<?> keyClass = loadClass(getAttrString(entry.getAttributes(), "KeyClass"));
			if (keyClass == null) {
				keyClass = keyType != null ? keyType : String.class;
			}
			Object key = convertText(keyStr, keyClass);
			if (key == null) {
				continue;
			}
			Object old = oldMap != null ? oldMap.get(key) : null;
			Object val;
			if (getAttrString(entry.getAttributes(), "Class") == null && getAttrString(entry.getAttributes(), "Value") == null 
					&& old == null) {
				// written by an older PAMGuard, without the value's class. 
				continue;
			}
			val = readValue(entry, valueType != null ? valueType : Object.class, null, old);
			if (val != null) {
				try {
					map.put(key, val);
				}
				catch (Exception e) {
				}
			}
		}
		return map;
	}

	private double[] readDoubleArray(String[] bits) {
		double data[] = new double[bits.length];
		try {
		for (int i = 0; i < bits.length; i++) {
			data[i] = Double.valueOf(bits[i]);
		}
		}
		catch (NumberFormatException e) {
			e.printStackTrace();
			return null;
		}
		return data;
	}

	private boolean[] readBooleanArray(String[] bits) {
		boolean data[] = new boolean[bits.length];
		try {
		for (int i = 0; i < bits.length; i++) {
			data[i] = Boolean.valueOf(bits[i]);
		}
		}
		catch (NumberFormatException e) {
			e.printStackTrace();
			return null;
		}
		return data;
	}

	private int[] readIntArray(String[] bits) {
		int data[] = new int[bits.length];
		try {
		for (int i = 0; i < bits.length; i++) {
			data[i] = Integer.valueOf(bits[i]);
		}
		}
		catch (NumberFormatException e) {
			e.printStackTrace();
			return null;
		}
		return data;
	}

	/**
	 * Get a class type for an array. 
	 * @param arrayClass something like [D, [ something, etc. 
	 * @return
	 * @throws ClassNotFoundException 
	 */
	private Class getArrayTypeClass(String arrayClass) throws ClassNotFoundException {
		if (arrayClass == null) {
			return null;
		}
		if (arrayClass.charAt(0) != '[') {
			return null;
		}
		// e.g. [D, [F, [Ljava.lang.String;
		return Class.forName(arrayClass).getComponentType();
	}

	//	private boolean setEnum(Object dataObj, Class fieldClass, PamParameterData pamParam, String stringValue) {
	//		Enum val = readEnum(dataObj, fieldClass, pamParam, stringValue);
	//		try {
	//			pamParam.setData(val);
	//			return true;
	//		} catch (IllegalArgumentException | IllegalAccessException e) {
	//			e.printStackTrace();
	//		}
	//		return false;
	//	}
	//	
	private Enum readEnum(Class fieldClass, String stringValue) {
		//		Enum extEnum = null;
		try {
			// the enum will either be in it's raw form, or one of the toString functions, which 
			// makes life more complicated ! So need to scan all options to see what the value is. 
			Method method = fieldClass.getMethod("values", null);
			Object ans = method.invoke(null);
			Enum[] list = (Enum[]) ans;
			for (int i = 0; i < list.length; i++) {
				if (list[i].toString().equals(stringValue)) {
					return list[i];
				}
			}
			for (int i = 0; i < list.length; i++) {
				if (list[i].name().equals(stringValue)) {
					return list[i];
				}
			}

			return null;
		} catch (IllegalArgumentException e1) {
			e1.printStackTrace();
		} catch (IllegalAccessException e1) {
			e1.printStackTrace();
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		}
		return null;
	}

	//	private boolean setPrimitive(Object dataObj, Class fieldClass, PamParameterData pamParam, String value) {
	//		Object val = readPrimitive(dataObj, fieldClass, pamParam, value);
	//		if (val == null) {
	//			return false;
	//		}
	//		try {
	//			pamParam.setData(val);
	//		} catch (IllegalArgumentException | IllegalAccessException e) {
	//			e.printStackTrace();
	//			return false;
	//		}
	//		return true;
	//	}

	private Object readPrimitive(Class fieldClass, String value) {
		if (fieldClass == String.class) {
			return value;
		}
		switch (fieldClass.getName()) {
		case "byte":
			return Byte.valueOf(value);
		case "short":
			return Short.valueOf(value);
		case "int":
			return Integer.valueOf(value);
		case "long":
			return Long.valueOf(value);
		case "float":
			return Float.valueOf(value);
		case "double":
			return Double.valueOf(value);
		case "boolean":
			return Boolean.valueOf(value);
		case "char":
			if (value.length() >= 1) {
				return value.charAt(0);
			}
			else {
				return null;
			}
		}

		System.out.println("Unknown primitive class " + fieldClass.getName());
		return null;
	}
	

//	public Object getModuleForClass(String className) {
//		ArrayList<Node> modules = findSettingsForClass(className);
//		if (modules == null || modules.size() == 0) {
//			WarnOnce.showWarning("XML Import Error", "file contains no modules for class " + className, WarnOnce.WARNING_MESSAGE);
//			return null;
//		}
//		if (modules.size() > 1) {
//			
//		}
//		// for now just take the first. In future, make a list and ask which one user wants. 
//		return unpackSettingsNode(modules.get(0));
//		
//	}

	/**
	 * Find all module nodes which are for a particular parameter class. 
	 * @param className
	 * @return module nodes
	 */
	public ArrayList<ModuleNode> modulesForClass(String className) {
		if (moduleNodes == null || className == null) {
			return null;
		}
		ArrayList<ModuleNode> classNodes = new ArrayList<ModuleNode>();
		for (ModuleNode aNode : moduleNodes) {
			if (className.equals(aNode.getjClass())) {
				classNodes.add(aNode);
			}
		}
		return classNodes;
	}

	
	public ArrayList<Node> findSettingsForClass(String className) {
		ArrayList<Node> allSettings = new ArrayList<>();
		if (moduleNodes == null) {
			return allSettings;
		}
		for (int i = 0; i < moduleNodes.size(); i++) {
//			System.out.println(moduleNodes.get(i));
			ArrayList<Node> settingsNodes = findNodesByType(moduleNodes.get(i).getNode(), "SETTINGS", null);
			if (settingsNodes == null) {
				continue;
			}
			for (int n = 0; n < settingsNodes.size(); n++) {
				String classStr = getAttrString(settingsNodes.get(n).getAttributes(), "Class");
				if (classStr == null) {
					continue;
				}
				if (className.equals(classStr)) {
					allSettings.add(settingsNodes.get(n));
				}
			}
		}
		return allSettings;
	}

	/**
	 * Find all module nodes which are for a particular module type. 
	 * @param unitType PAMGuard unit type
	 * @return module nodes
	 */
	public ArrayList<ModuleNode> modulesForType(String unitType) {
		if (moduleNodes == null || unitType == null) {
			return null;
		}
		ArrayList<ModuleNode> classNodes = new ArrayList<ModuleNode>();
		for (ModuleNode aNode : moduleNodes) {
			if (unitType.equals(aNode.getUnitType())) {
				classNodes.add(aNode);
			}
		}
		return classNodes;
	}

	/**
	 * iterate recursively through all nodes in an xml document node 
	 * @param childNodes
	 * @param moduleNodes
	 */
	private void findModuleNodes(NodeList childNodes, ArrayList<ModuleNode> moduleNodes) {
		if (childNodes == null) {
			return;
		}
		int nChild = childNodes.getLength();
		for (int i = 0; i < nChild; i++) {
			Node node = childNodes.item(i);
			String nodeName = node.getNodeName();
			if (nodeName.equals("MODULE")) {
				//				System.out.println(node + " : " + node.getNodeName());
				NamedNodeMap attributes = node.getAttributes();
				String jClass = getAttrString(attributes, "Java.class");
				String unitType = getAttrString(attributes, "UnitType");
				String unitName = getAttrString(attributes, "UnitName");
				//				System.out.printf("Module %s, %s, %s\n", jClass, unitName, unitType);
				moduleNodes.add(new ModuleNode(node, jClass, unitType, unitName));
			}
			findModuleNodes(node.getChildNodes(), moduleNodes);
		}
	}

	/**
	 * Get a list of all nodes in a tree with the given name. 
	 * @param parent
	 * @param nodeName
	 * @param nodeList
	 * @return
	 */
	private ArrayList<Node> findNodesByType(Node parent, String nodeName, ArrayList<Node> nodeList) {
		if (nodeList == null) {
			nodeList = new ArrayList<>();
		}
		String name = parent.getNodeName();
		if (name.equals(nodeName)) {
			nodeList.add(parent);
		}
		NodeList children = parent.getChildNodes();
		int nCh = children.getLength();
		for (int i = 0; i < nCh; i++) {
			nodeList = findNodesByType(children.item(i), nodeName, nodeList);
		}
		return nodeList;
	}

	/**
	 * find a note as a child of the current node
	 * @param parent
	 * @param nodeName
	 * @return
	 */
	private Node findNode(Node parent, String nodeName) {
		NodeList children = parent.getChildNodes();
		int nCh = children.getLength();
		for (int i = 0; i < nCh; i++) {
			Node aNode = children.item(i);
			if (aNode.getNodeName().equals(nodeName)) {
				return aNode;
			}
		}
		return null;
	}

	public String getAttrString(NamedNodeMap attrMap, String attName) {
		Node node = attrMap.getNamedItem(attName);
		if (node == null) {
			return null;
		}
		return node.getNodeValue();
	}

	/**
	 * @return the moduleNodes
	 */
	public ArrayList<ModuleNode> getModuleNodes() {
		return moduleNodes;
	}


}
