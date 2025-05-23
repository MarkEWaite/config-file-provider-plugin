package org.jenkinsci.plugins.configfiles;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import hudson.util.VersionNumber;
import java.util.concurrent.atomic.AtomicReference;
import org.htmlunit.html.HtmlAnchor;
import org.htmlunit.html.HtmlElement;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlInput;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlRadioButtonInput;
import org.jenkinsci.plugins.configfiles.custom.CustomConfig;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class ConfigFilesSEC1253Test {

    private static final String CONFIG_ID = "ConfigFilesTestId";

    @Test
    @Issue("SECURITY-1253")
    void regularCaseStillWorking(JenkinsRule j) throws Exception {
        GlobalConfigFiles store =
                j.getInstance().getExtensionList(GlobalConfigFiles.class).get(GlobalConfigFiles.class);
        assertNotNull(store);
        assertThat(store.getConfigs(), empty());

        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage createConfig = wc.goTo("configfiles/selectProvider");
        HtmlRadioButtonInput groovyRadioButton = createConfig
                .getDocumentElement()
                .getOneHtmlElementByAttribute(
                        "input", "value", "org.jenkinsci.plugins.configfiles.groovy.GroovyScript");
        groovyRadioButton.click();
        HtmlForm addConfigForm = createConfig.getFormByName("addConfig");

        HtmlPage createGroovyConfig = j.submit(addConfigForm);
        HtmlInput configIdInput = createGroovyConfig.getElementByName("config.id");
        configIdInput.setValue(CONFIG_ID);

        HtmlInput configNameInput = createGroovyConfig.getElementByName("config.name");
        configNameInput.setValue("Regular name");

        HtmlForm saveConfigForm = createGroovyConfig.getForms().stream()
                .filter(htmlForm -> htmlForm.getActionAttribute().equals("saveConfig"))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No config with action [saveConfig] in that page"));
        j.submit(saveConfigForm);

        assertThat(store.getConfigs(), hasSize(1));

        HtmlPage configFiles = wc.goTo("configfiles");
        HtmlAnchor removeAnchor = configFiles
                .getDocumentElement()
                .getFirstByXPath("//a[contains(@data-url, 'removeConfig?id=" + CONFIG_ID + "')]");

        if (j.jenkins.getVersion().isOlderThan(new VersionNumber("2.415"))) {
            AtomicReference<Boolean> confirmCalled = new AtomicReference<>(false);
            wc.setConfirmHandler((page, s) -> {
                confirmCalled.set(true);
                return true;
            });
            assertThat(confirmCalled.get(), is(false));
            removeAnchor.click();
            assertThat(confirmCalled.get(), is(true));
        } else {
            HtmlElement document = configFiles.getDocumentElement();
            HtmlElementUtil.clickDialogOkButton(removeAnchor, document);
        }

        assertThat(store.getConfigs(), empty());
    }

    @Test
    @Issue("SECURITY-1253")
    void xssPrevention(JenkinsRule j) throws Exception {
        GlobalConfigFiles store =
                j.getInstance().getExtensionList(GlobalConfigFiles.class).get(GlobalConfigFiles.class);
        assertNotNull(store);
        assertThat(store.getConfigs(), empty());

        CustomConfig config = new CustomConfig(CONFIG_ID, "GroovyConfig')+alert('asw", "comment", "content");
        store.save(config);

        assertThat(store.getConfigs(), hasSize(1));

        JenkinsRule.WebClient wc = j.createWebClient();

        HtmlPage configFiles = wc.goTo("configfiles");
        HtmlAnchor removeAnchor = configFiles
                .getDocumentElement()
                .getFirstByXPath("//a[contains(@data-url, 'removeConfig?id=" + CONFIG_ID + "')]");

        AtomicReference<Boolean> alertCalled = new AtomicReference<>(false);
        wc.setAlertHandler((page, s) -> alertCalled.set(true));
        assertThat(alertCalled.get(), is(false));
        if (j.jenkins.getVersion().isOlderThan(new VersionNumber("2.415"))) {
            AtomicReference<Boolean> confirmCalled = new AtomicReference<>(false);
            wc.setConfirmHandler((page, s) -> {
                confirmCalled.set(true);
                return true;
            });
            assertThat(confirmCalled.get(), is(false));
            removeAnchor.click();
            assertThat(confirmCalled.get(), is(true));
        } else {
            HtmlElement document = configFiles.getDocumentElement();
            HtmlElementUtil.clickDialogOkButton(removeAnchor, document);
        }

        assertThat(alertCalled.get(), is(false));

        assertThat(store.getConfigs(), empty());
    }
}
