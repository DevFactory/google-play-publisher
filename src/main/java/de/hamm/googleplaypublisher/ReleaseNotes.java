package de.hamm.googleplaypublisher;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;

public class ReleaseNotes implements Describable<ReleaseNotes>, Serializable {
	private static final String DISLPLAY_NAME = "Release Notes";
	private final String language;
	private String releaseNotes;

	@DataBoundConstructor
	public ReleaseNotes(String language, String releaseNotes) {
		this.language = language;
		this.releaseNotes = releaseNotes;
	}

	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) Jenkins.getInstance().getDescriptorOrDie(ReleaseNotes.class);
	}

	public String getLanguage() {
		return language;
	}

	public String getReleaseNotes() {
		return releaseNotes;
	}

	public void expand(EnvVars envVars) {
		releaseNotes = envVars.expand(releaseNotes);
	}

	@Extension
	public static final class DescriptorImpl extends Descriptor<ReleaseNotes> {
		@Override
		public String getDisplayName() {
			return DISLPLAY_NAME;
		}

		public ListBoxModel doFillLanguageItems() {
			ListBoxModel languageItems = new ListBoxModel();
			languageItems.add("Afrikaans – af", "af");
			languageItems.add("Amharic – am", "am");
			languageItems.add("Arabic – ar", "ar");
			languageItems.add("Belarusian – be", "be");
			languageItems.add("Bulgarian – bg", "bg");
			languageItems.add("Catalan – ca", "ca");
			languageItems.add("Chinese (Simplified) – zh-CN", "zh-CN");
			languageItems.add("Chinese (Traditional) – zh-TW", "zh-TW");
			languageItems.add("Croatian – hr", "hr");
			languageItems.add("Czech – cs-CZ", "cs-CZ");
			languageItems.add("Danish – da-DK", "da-DK");
			languageItems.add("Dutch – nl-NL", "nl-NL");
			languageItems.add("English (United Kingdom) – en-GB", "en-GB");
			languageItems.add("English (United States) - en-US", "en-US");
			languageItems.add("Estonian – et", "et");
			languageItems.add("Filipino – fil", "fil");
			languageItems.add("Finnish – fi-FI", "fi-FI");
			languageItems.add("French – fr-FR", "fr-FR");
			languageItems.add("French (Canada) – fr-CA", "fr-CA");
			languageItems.add("German - de-DE", "de-DE");
			languageItems.add("Greek – el-GR", "el-GR");
			languageItems.add("Hebrew – iw-IL", "iw-IL");
			languageItems.add("Hindi – hi-IN", "hi-IN");
			languageItems.add("Hungarian – hu-HU", "hu-HU");
			languageItems.add("Indonesian – id", "id");
			languageItems.add("Italian – it-IT", "it-IT");
			languageItems.add("Japanese – ja-JP", "ja-JP");
			languageItems.add("Korean (South Korea) – ko-KR", "ko-KR");
			languageItems.add("Latvian – lv", "lv");
			languageItems.add("Lithuanian – lt", "lt");
			languageItems.add("Malay – ms", "ms");
			languageItems.add("Norwegian – no-NO", "no-NO");
			languageItems.add("Persian – fa", "fa");
			languageItems.add("Polish – pl-PL", "pl-PL");
			languageItems.add("Portuguese (Brazil) – pt-BR", "pt-BR");
			languageItems.add("Portuguese (Portugal) – pt-PT", "pt-PT");
			languageItems.add("Romanian – ro", "ro");
			languageItems.add("Romansh – rm", "rm");
			languageItems.add("Russian – ru-RU", "ru-RU");
			languageItems.add("Serbian – sr", "sr");
			languageItems.add("Slovak – sk", "sk");
			languageItems.add("Slovenian – sl", "sl");
			languageItems.add("Spanish (Latin America) – es-419", "es-419");
			languageItems.add("Spanish (Spain) – es-ES", "es-ES");
			languageItems.add("Spanish (United States) – es-US", "es-US");
			languageItems.add("Swahili – sw", "sw");
			languageItems.add("Swedish – sv-SE", "sv-SE");
			languageItems.add("Thai – th", "th");
			languageItems.add("Turkish – tr-TR", "tr-TR");
			languageItems.add("Ukrainian – uk", "uk");
			languageItems.add("Vietnamese – vi", "vi");
			languageItems.add("Zulu – zu", "zu");
			return languageItems;
		}
	}
}
