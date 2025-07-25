package info.openrocket.swing.gui.help.tours;

import java.awt.Color;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.text.html.StyleSheet;

import info.openrocket.core.util.TextLineReader;
import info.openrocket.swing.gui.util.GUIUtil;
import info.openrocket.swing.gui.theme.UITheme;

import info.openrocket.core.util.BugException;

/**
 * A manager that loads a number of slide sets from a defined base directory
 * and provides access to them.
 * 
 * @author Sampo Niskanen <sampo.niskanen@iki.fi>
 */
public class SlideSetManager {
	private static final String TOURS_BASE_DIR = "datafiles/tours";
	
	private static final String TOURS_FILE = "tours.txt";
	private static final String STYLESHEET_FILE = "style.css";
	
	private static SlideSetManager slideSetManager = null;
	
	
	private final String baseDir;
	private final Map<String, SlideSet> slideSets = new LinkedHashMap<>();

	private static Color textColor;

	static {
		initColors();
	}
	
	/**
	 * Sole constructor.
	 * 
	 * @param baseDir	the base directory containing the tours and style files.
	 */
	public SlideSetManager(String baseDir) {
		if (baseDir.length() > 0 && !baseDir.endsWith("/")) {
			baseDir = baseDir + "/";
		}
		this.baseDir = baseDir;
	}

	private static void initColors() {
		updateColors();
		UITheme.Theme.addUIThemeChangeListener(SlideSetManager::updateColors);
	}

	public static void updateColors() {
		textColor = GUIUtil.getUITheme().getTextColor();
	}
	
	
	/**
	 * Load all the tours.
	 */
	public void load() throws IOException {
		slideSets.clear();
		
		List<String> tours = loadTourList();
		StyleSheet styleSheet = loadStyleSheet();
		
		for (String fileAndDir : tours) {
			String base;
			String file;
			
			String fullFileAndDir = baseDir + fileAndDir;
			int index = fullFileAndDir.lastIndexOf('/');
			if (index >= 0) {
				base = fullFileAndDir.substring(0, index);
				file = fullFileAndDir.substring(index + 1);
			} else {
				base = "";
				file = "";
			}
			
			SlideSetLoader loader = new SlideSetLoader(base);
			SlideSet set = loader.load(file);
			set.setStyleSheet(styleSheet);
			slideSets.put(fileAndDir, set);
		}
		
	}
	
	
	/**
	 * Return a set containing all the slide set names.
	 */
	public List<String> getSlideSetNames() {
		return new ArrayList<>(slideSets.keySet());
	}
	
	/**
	 * Retrieve an individual slide set.
	 * 
	 * @param name	the name of the slide set to retrieve.
	 * @return		the slide set (never null)
	 * @throws IllegalArgumentException		if the slide set with the name does not exist.
	 */
	public SlideSet getSlideSet(String name) {
		SlideSet s = slideSets.get(name);
		if (s == null) {
			throw new IllegalArgumentException("Slide set with name '" + name + "' not found.");
		}
		return s;
	}
	
	
	private List<String> loadTourList() throws IOException {
		InputStream in = ClassLoader.getSystemResourceAsStream(baseDir + TOURS_FILE);

		try (in) {
			if (in == null) {
				throw new FileNotFoundException("File '" + baseDir + TOURS_FILE + "' not found.");
			}

			List<String> tours = new ArrayList<>();
			TextLineReader reader = new TextLineReader(in);
			while (reader.hasNext()) {
				tours.add(reader.next());
			}
			return tours;

		}
	}
	
	
	private StyleSheet loadStyleSheet() throws IOException {
		InputStream in = ClassLoader.getSystemResourceAsStream(baseDir + STYLESHEET_FILE);

		try (in) {
			if (in == null) {
				throw new FileNotFoundException("File '" + baseDir + STYLESHEET_FILE + "' not found.");
			}

			StyleSheet ss = new StyleSheet();
			ss.addRule(String.format("p { color: rgb(%d, %d, %d, %d)",
					textColor.getRed(), textColor.getGreen(), textColor.getBlue(), textColor.getAlpha()));
			InputStreamReader reader = new InputStreamReader(in, "UTF-8");
			ss.loadRules(reader, null);
			return ss;

		}
		
	}
	
	
	
	/**
	 * Return a singleton implementation that has loaded the default tours.
	 */
	public static SlideSetManager getSlideSetManager() {
		if (slideSetManager == null) {
			try {
				SlideSetManager ssm = new SlideSetManager(TOURS_BASE_DIR);
				ssm.load();
				
				if (ssm.getSlideSetNames().isEmpty()) {
					throw new FileNotFoundException("No tours found.");
				}
				
				slideSetManager = ssm;
			} catch (IOException e) {
				throw new BugException(e);
			}
		}
		return slideSetManager;
	}
}
