package org.phidias.compile;

import java.io.File;
import java.io.IOException;

import java.net.URISyntaxException;
import java.net.URL;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

import org.osgi.framework.Bundle;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;


public class BundleJavaManager implements Constants, StandardJavaFileManager {

	public BundleJavaManager(
			Bundle bundle, StandardJavaFileManager standardJavaFileManager)
		throws IOException {

		_bundle = bundle;
		_standardJavaFileManager = standardJavaFileManager;

		BundleWiring bundleWiring = _bundle.adapt(BundleWiring.class);

		_classLoader = bundleWiring.getClassLoader();

		_bundleWirings = new ArrayList<BundleWiring>();

		_bundleWirings.add(bundleWiring);

		List<BundleWire> providedWires = bundleWiring.getRequiredWires(null);

		for (BundleWire bundleWire : providedWires) {
			BundleWiring providerWiring = bundleWire.getProviderWiring();

			if (_bundleWirings.contains(providerWiring)) {
				continue;
			}

			_bundleWirings.add(providerWiring);
		}
	}

	public void close() throws IOException {
		_standardJavaFileManager.close();
	}

	public void flush() throws IOException {
		_standardJavaFileManager.flush();
	}

	public ClassLoader getClassLoader() {
		return _classLoader;
	}

	public ClassLoader getClassLoader(Location location) {
		if (location != StandardLocation.CLASS_PATH) {
			return _standardJavaFileManager.getClassLoader(location);
		}

		return getClassLoader();
	}

	public FileObject getFileForInput(
			Location location, String packageName, String relativeName)
		throws IOException {

		return _standardJavaFileManager.getFileForInput(
			location, packageName, relativeName);
	}

	public FileObject getFileForOutput(
			Location location, String packageName, String relativeName,
			FileObject sibling)
		throws IOException {

		return _standardJavaFileManager.getFileForOutput(
			location, packageName, relativeName, sibling);
	}

	public JavaFileObject getJavaFileForInput(
		Location location, String className, Kind kind) throws IOException {

		return _standardJavaFileManager.getJavaFileForInput(
			location, className, kind);
	}

	public JavaFileObject getJavaFileForOutput(
			Location location, String className, Kind kind,
			FileObject sibling)
		throws IOException {

		return _standardJavaFileManager.getJavaFileForOutput(
			location, className, kind, sibling);
	}

	public Iterable<? extends JavaFileObject> getJavaFileObjects(
		File... files) {

		return _standardJavaFileManager.getJavaFileObjects(files);
	}

	public Iterable<? extends JavaFileObject> getJavaFileObjects(
		String... names) {

		return _standardJavaFileManager.getJavaFileObjects(names);
	}

	public Iterable<? extends JavaFileObject> getJavaFileObjectsFromFiles(
		Iterable<? extends File> files) {

		return _standardJavaFileManager.getJavaFileObjectsFromFiles(files);
	}

	public Iterable<? extends JavaFileObject> getJavaFileObjectsFromStrings(
		Iterable<String> names) {

		return _standardJavaFileManager.getJavaFileObjectsFromStrings(
			names);
	}

	public Iterable<? extends File> getLocation(Location location) {
		return _standardJavaFileManager.getLocation(location);
	}

	public boolean handleOption(String current, Iterator<String> remaining) {
		return _standardJavaFileManager.handleOption(current, remaining);
	}

	public boolean hasLocation(Location location) {
		return _standardJavaFileManager.hasLocation(location);
	}

	public String inferBinaryName(Location location, JavaFileObject file) {
		if ((location == StandardLocation.CLASS_PATH) &&
			(file instanceof BundleJavaFileObject)) {

			BundleJavaFileObject bundleJavaFileObject =
				(BundleJavaFileObject)file;

			return bundleJavaFileObject.inferBinaryName();
		}

		return _standardJavaFileManager.inferBinaryName(location, file);
	}

	public boolean isSameFile(FileObject a, FileObject b) {
		return _standardJavaFileManager.isSameFile(a, b);
	}

	public int isSupportedOption(String option) {
		return _standardJavaFileManager.isSupportedOption(option);
	}

	public Iterable<JavaFileObject> list(
			Location location, String packageName, Set<Kind> kinds,
			boolean recurse)
		throws IOException {

		List<JavaFileObject> javaFileObjects =
			new ArrayList<JavaFileObject>();

		if ((location == StandardLocation.CLASS_PATH) &&
			!packageName.startsWith(JAVA_PACKAGE)) {

			int options = recurse ? BundleWiring.LISTRESOURCES_RECURSE : 0;

			packageName = packageName.replace('.', '/');

			System.out.println(packageName);

			for (Kind kind : kinds) {
				for (BundleWiring bundleWiring : _bundleWirings) {
					list(
						packageName, kind, options, bundleWiring,
						javaFileObjects);
				}
			}
		}

		for (JavaFileObject javaFileObject : _standardJavaFileManager.list(
				location, packageName, kinds, recurse)) {

			javaFileObjects.add(javaFileObject);
		}

		return javaFileObjects;
	}

	public void setLocation(Location location, Iterable<? extends File> path)
		throws IOException {

		_standardJavaFileManager.setLocation(location, path);
	}

	private String getClassNameFromPath(URL resource, String packageName) {
		String className = resource.getPath();

		int x = className.indexOf(packageName);
		int y = className.indexOf('.');

		className = className.substring(x, y).replace('/', '.');

		if (className.startsWith(PERIOD)) {
			className = className.substring(1);
		}

		return className;
	}

	private void list(
		String packageName, Kind kind, int options,
		BundleWiring bundleWiring, List<JavaFileObject> javaFileObjects) {

		Collection<String> resources = bundleWiring.listResources(
			packageName, STAR.concat(kind.extension),
			options);

		if ((resources == null) || resources.isEmpty()) {
			return;
		}

		Bundle provider = bundleWiring.getBundle();

		for (String resourceName : resources) {
			URL resource = provider.getResource(resourceName);

			if (resource != null) {
				String className = getClassNameFromPath(
					resource, packageName);

				try {
					JavaFileObject javaFileObject = new BundleJavaFileObject(
						resource.toURI(), className);

					javaFileObjects.add(javaFileObject);
				}
				catch (URISyntaxException e) {
					// Can't really happen
				}
			}
		}
	}

	private Bundle _bundle;
	private ArrayList<BundleWiring> _bundleWirings;
	private ClassLoader _classLoader;
	private StandardJavaFileManager _standardJavaFileManager;

}