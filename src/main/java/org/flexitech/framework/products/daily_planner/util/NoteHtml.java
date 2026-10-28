package org.flexitech.framework.products.daily_planner.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public final class NoteHtml {

	private static final String DROPPED_TAGS = "script, style, meta, link, iframe, object, embed";
	private static final String INLINE_TAGS = "span, b, i, u, strong, em";
	private static final Set<String> KEPT_STYLES = Set.of("color", "background-color", "font-weight", "font-style",
			"text-decoration", "text-align");
	private static final Set<String> KEPT_ATTRIBUTES = Set.of("href", "src", "alt", "style", "colspan", "rowspan");

	private NoteHtml() {
	}

	public static String cleanForDoc(String html) {
		if (html == null || html.isBlank()) {
			return "";
		}
		Document document = Jsoup.parse(html);
		document.outputSettings().prettyPrint(false);
		document.select(DROPPED_TAGS).remove();
		convertFonts(document);
		for (Element element : document.body().getAllElements()) {
			if (element == document.body()) {
				continue;
			}
			cleanStyle(element);
			cleanAttributes(element);
		}
		removeEmptyInline(document);
		return "<html><body>" + document.body().html() + "</body></html>";
	}

	private static void convertFonts(Document document) {
		for (Element font : document.select("font")) {
			String color = font.attr("color");
			font.tagName("span");
			font.removeAttr("face");
			font.removeAttr("size");
			font.removeAttr("color");
			if (!color.isBlank()) {
				String existing = font.attr("style");
				font.attr("style", "color:" + color + ";" + existing);
			}
		}
	}

	private static void cleanStyle(Element element) {
		String style = element.attr("style");
		if (style.isBlank()) {
			element.removeAttr("style");
			return;
		}
		Map<String, String> kept = new LinkedHashMap<String, String>();
		for (String declaration : style.split(";")) {
			int split = declaration.indexOf(':');
			if (split < 0) {
				continue;
			}
			String name = declaration.substring(0, split).trim().toLowerCase();
			String value = declaration.substring(split + 1).trim();
			if (KEPT_STYLES.contains(name) && !value.isEmpty() && !kept.containsKey(name)) {
				kept.put(name, value);
			}
		}
		if (kept.isEmpty()) {
			element.removeAttr("style");
			return;
		}
		StringBuilder builder = new StringBuilder();
		kept.forEach((name, value) -> builder.append(name).append(':').append(value).append(';'));
		element.attr("style", builder.toString());
	}

	private static void cleanAttributes(Element element) {
		List<String> names = new ArrayList<String>();
		for (Attribute attribute : element.attributes()) {
			names.add(attribute.getKey());
		}
		for (String name : names) {
			if (!KEPT_ATTRIBUTES.contains(name.toLowerCase())) {
				element.removeAttr(name);
			}
		}
	}

	private static void removeEmptyInline(Document document) {
		boolean removed = true;
		while (removed) {
			removed = false;
			for (Element element : document.body().select(INLINE_TAGS)) {
				if (element.text().isBlank() && element.select("img, br").isEmpty()) {
					element.remove();
					removed = true;
				}
			}
		}
	}
}