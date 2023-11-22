package org.octopusden.octopus.dms.client;

import org.octopusden.octopus.dms.client.common.dto.ArtifactType;
import java.io.File;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

public class ExportArtifactsTask extends DefaultTask {
    public String dmsUrl;
    public String dmsToken;
    public String dmsUser;
    public String dmsPassword;
    public String cregUrl;
    public String component;
    public String version;
    public ArtifactType type;
    public File targetDir;
    public boolean clear = false;
    public boolean downloadPrevious = false;
    public String startPreviousVersion = "0";

    @TaskAction
    public void export() {
        new ArtifactDownloader(dmsUrl, dmsToken, dmsUser, dmsPassword, cregUrl)
                .download(component, version, type, downloadPrevious, targetDir, clear, startPreviousVersion);
    }
}
