package org.jenkinsci.plugins.configfiles.sec;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import hudson.util.VersionNumber;
import java.util.concurrent.atomic.AtomicReference;
import jenkins.model.GlobalConfiguration;
import org.hamcrest.CoreMatchers;
import org.htmlunit.html.HtmlAnchor;
import org.htmlunit.html.HtmlElement;
import org.htmlunit.html.HtmlPage;
import org.jenkinsci.plugins.configfiles.GlobalConfigFiles;
import org.jenkinsci.plugins.configfiles.HtmlElementUtil;
import org.jenkinsci.plugins.configfiles.custom.CustomConfig;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class Security2002Test {
    private static final String CONFIG_ID = "ConfigFilesTestId";

    @Test
    @Issue("SECURITY-2202")
    void xssPrevention(JenkinsRule j) throws Exception {

        // ----------
        // Create a new configuration
        GlobalConfigFiles store =
                j.getInstance().getExtensionList(GlobalConfiguration.class).get(GlobalConfigFiles.class);
        assertNotNull(store);
        assertThat(store.getConfigs(), empty());

        CustomConfig config = new CustomConfig(CONFIG_ID, "My configuration", "comment", "content");
        store.save(config);

        assertThat(store.getConfigs(), hasSize(1));

        // ----------
        // Check removing it by GET doesn't work
        JenkinsRule.WebClient wc = j.createWebClient();

        // If we try to call the URL directly (via GET), it fails with a 405 - Method not allowed
        wc.assertFails("configfiles/removeConfig?id=" + CONFIG_ID, 405);

        // ----------
        // Clicking the button works
        // If we click on the link, it goes via POST, therefore it removes it successfully
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
            assertThat(confirmCalled.get(), CoreMatchers.is(false));
            removeAnchor.click();
            assertThat(confirmCalled.get(), CoreMatchers.is(true));
        } else {
            HtmlElement document = configFiles.getDocumentElement();
            HtmlElementUtil.clickDialogOkButton(removeAnchor, document);
        }
        assertThat(store.getConfigs(), empty());
    }
}
